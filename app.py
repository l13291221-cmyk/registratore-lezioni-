"""Avvisi Dipendenti: invio di comunicazioni a molti destinatari tramite Brevo
e raccolta delle risposte (es. "Sì" / "No") con conteggio automatico."""

import csv
import io
import json
import os
import re
import secrets
import sqlite3
import threading
import time
from datetime import datetime
from functools import wraps
from html import escape

import requests
from flask import (Flask, Response, abort, g, redirect, render_template_string,
                   request, url_for)

BREVO_API_KEY = os.environ.get("BREVO_API_KEY", "")
SENDER_EMAIL = os.environ.get("SENDER_EMAIL", "")
SENDER_NAME = os.environ.get("SENDER_NAME", "Comunicazioni Aziendali")
BASE_URL = os.environ.get("BASE_URL", "http://localhost:5000").rstrip("/")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")
DB_PATH = os.environ.get("DB_PATH", "avvisi.db")
# Senza chiave Brevo l'app non invia nulla: simula l'invio (utile per provare).
DRY_RUN = not BREVO_API_KEY

BATCH_SIZE = 1000  # massimo destinatari per singola chiamata API Brevo
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

app = Flask(__name__)


# ---------------------------------------------------------------- database

def get_db():
    if "db" not in g:
        g.db = connect()
    return g.db


def connect():
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


@app.teardown_appcontext
def close_db(_exc):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_db():
    with connect() as conn:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS campagne (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                oggetto TEXT NOT NULL,
                messaggio TEXT NOT NULL,
                opzioni TEXT NOT NULL,
                quantita_attesa INTEGER,
                creata_il TEXT NOT NULL,
                stato TEXT NOT NULL DEFAULT 'in invio',
                inviate INTEGER NOT NULL DEFAULT 0,
                errori INTEGER NOT NULL DEFAULT 0,
                ultimo_errore TEXT
            );
            CREATE TABLE IF NOT EXISTS destinatari (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                campagna_id INTEGER NOT NULL REFERENCES campagne(id),
                email TEXT NOT NULL,
                token TEXT NOT NULL UNIQUE,
                risposta INTEGER,
                risposto_il TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_dest_campagna
                ON destinatari(campagna_id);
        """)


# ------------------------------------------------------------ protezione

def admin_required(view):
    @wraps(view)
    def wrapper(*args, **kwargs):
        if ADMIN_PASSWORD:
            auth = request.authorization
            if not auth or auth.password != ADMIN_PASSWORD:
                return Response("Accesso riservato", 401,
                                {"WWW-Authenticate": 'Basic realm="Avvisi"'})
        return view(*args, **kwargs)
    return wrapper


# ---------------------------------------------------------- elaborazione

def estrai_email(testo):
    """Trova tutti gli indirizzi nel testo incollato (qualsiasi separatore),
    toglie i duplicati e restituisce (validi, duplicati_rimossi)."""
    trovate = [e.lower() for e in EMAIL_RE.findall(testo)]
    uniche = list(dict.fromkeys(trovate))
    return uniche, len(trovate) - len(uniche)


def html_email(messaggio, opzioni, token):
    corpo = escape(messaggio).replace("\n", "<br>")
    bottoni = "".join(
        f'<a href="{BASE_URL}/r/{token}/{i}" style="display:inline-block;'
        f'margin:6px 8px 6px 0;padding:12px 22px;background:#1f5fbf;'
        f'color:#fff;text-decoration:none;border-radius:6px;font-weight:bold">'
        f'{escape(o)}</a>'
        for i, o in enumerate(opzioni)
    )
    blocco_risposte = (
        f'<p style="margin-top:24px"><b>Rispondi con un clic:</b></p>{bottoni}'
        if opzioni else ""
    )
    return (
        '<div style="font-family:Arial,sans-serif;font-size:15px;'
        f'line-height:1.5;color:#222;max-width:600px">{corpo}'
        f'{blocco_risposte}</div>'
    )


def testo_email(messaggio, opzioni, token):
    righe = [messaggio]
    if opzioni:
        righe.append("\nPer rispondere apri uno di questi link:")
        righe += [f"- {o}: {BASE_URL}/r/{token}/{i}"
                  for i, o in enumerate(opzioni)]
    return "\n".join(righe)


def invia_lotto(oggetto, messaggio, opzioni, lotto):
    """Invia un lotto (max 1000) con una sola chiamata: ogni destinatario
    riceve la sua versione con i propri link di risposta."""
    if DRY_RUN:
        time.sleep(0.05)
        return
    payload = {
        "sender": {"email": SENDER_EMAIL, "name": SENDER_NAME},
        "subject": oggetto,
        # Contenuto di base obbligatorio; ogni versione lo sovrascrive.
        "htmlContent": html_email(messaggio, opzioni, "x"),
        "messageVersions": [
            {
                "to": [{"email": r["email"]}],
                "htmlContent": html_email(messaggio, opzioni, r["token"]),
                "textContent": testo_email(messaggio, opzioni, r["token"]),
            }
            for r in lotto
        ],
    }
    for tentativo in range(4):
        resp = requests.post(
            "https://api.brevo.com/v3/smtp/email",
            headers={"api-key": BREVO_API_KEY, "accept": "application/json"},
            json=payload, timeout=60,
        )
        if resp.status_code == 429 or resp.status_code >= 500:
            time.sleep(2 ** (tentativo + 1))
            continue
        if resp.status_code >= 400:
            raise RuntimeError(f"Brevo {resp.status_code}: {resp.text[:300]}")
        return
    raise RuntimeError("Brevo non risponde (troppi tentativi)")


def invia_campagna(campagna_id):
    conn = connect()
    try:
        c = conn.execute("SELECT * FROM campagne WHERE id=?",
                         (campagna_id,)).fetchone()
        opzioni = json.loads(c["opzioni"])
        dest = conn.execute(
            "SELECT email, token FROM destinatari WHERE campagna_id=? "
            "ORDER BY id", (campagna_id,)).fetchall()
        for start in range(0, len(dest), BATCH_SIZE):
            lotto = dest[start:start + BATCH_SIZE]
            try:
                invia_lotto(c["oggetto"], c["messaggio"], opzioni, lotto)
                conn.execute("UPDATE campagne SET inviate=inviate+? "
                             "WHERE id=?", (len(lotto), campagna_id))
            except Exception as exc:  # noqa: BLE001 - registra e prosegue
                conn.execute(
                    "UPDATE campagne SET errori=errori+?, ultimo_errore=? "
                    "WHERE id=?", (len(lotto), str(exc), campagna_id))
            conn.commit()
        conn.execute("UPDATE campagne SET stato='completata' WHERE id=?",
                     (campagna_id,))
        conn.commit()
    finally:
        conn.close()


def statistiche(db, campagna_id):
    c = db.execute("SELECT * FROM campagne WHERE id=?",
                   (campagna_id,)).fetchone()
    if c is None:
        abort(404)
    opzioni = json.loads(c["opzioni"])
    totale = db.execute("SELECT COUNT(*) FROM destinatari WHERE campagna_id=?",
                        (campagna_id,)).fetchone()[0]
    conteggi = dict(db.execute(
        "SELECT risposta, COUNT(*) FROM destinatari WHERE campagna_id=? "
        "AND risposta IS NOT NULL GROUP BY risposta", (campagna_id,)))
    risposto = sum(conteggi.values())
    righe = [
        {"opzione": o, "n": conteggi.get(i, 0),
         "perc": (conteggi.get(i, 0) / risposto * 100) if risposto else 0}
        for i, o in enumerate(opzioni)
    ]
    return c, opzioni, totale, risposto, righe


# ------------------------------------------------------------------ pagine

BASE = """<!doctype html><html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Avvisi Dipendenti</title><style>
:root{--bg:#f4f6f9;--card:#fff;--fg:#1d2330;--mut:#5d6778;--acc:#1f5fbf;
--ok:#1b8a4b;--err:#b3261e;--bd:#d9dee7}
@media (prefers-color-scheme:dark){:root{--bg:#12151b;--card:#1b2029;
--fg:#e7eaf0;--mut:#9aa4b5;--acc:#5b9bff;--bd:#2c3340}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
font:15px/1.5 system-ui,Segoe UI,Roboto,Arial,sans-serif}
main{max-width:860px;margin:0 auto;padding:20px 16px 60px}
h1{font-size:24px;margin:8px 0 16px}h2{font-size:18px;margin:0 0 12px}
.card{background:var(--card);border:1px solid var(--bd);border-radius:10px;
padding:18px;margin-bottom:16px}
label{display:block;font-weight:600;margin:14px 0 6px}
.hint{color:var(--mut);font-size:13px;font-weight:400}
input,textarea{width:100%;padding:10px;border:1px solid var(--bd);
border-radius:6px;background:var(--bg);color:var(--fg);font:inherit}
textarea{min-height:110px;resize:vertical}
button,.btn{display:inline-block;background:var(--acc);color:#fff;border:0;
border-radius:6px;padding:11px 20px;font:inherit;font-weight:600;
cursor:pointer;text-decoration:none;margin-top:14px}
.btn.sec{background:transparent;color:var(--acc);border:1px solid var(--acc)}
table{width:100%;border-collapse:collapse}td,th{padding:8px 6px;
border-bottom:1px solid var(--bd);text-align:left}
.bar{height:10px;background:var(--bd);border-radius:5px;overflow:hidden}
.bar span{display:block;height:100%;background:var(--acc)}
.warn{border-left:4px solid #d08a00}.err{color:var(--err)}.ok{color:var(--ok)}
.big{font-size:28px;font-weight:700}.grid{display:grid;gap:12px;
grid-template-columns:repeat(auto-fit,minmax(150px,1fr))}
#conta{font-size:13px;color:var(--mut);margin-top:6px}
</style></head><body><main>{% block c %}{% endblock %}</main></body></html>"""


def pagina(corpo, **ctx):
    return render_template_string(
        BASE.replace("{% block c %}{% endblock %}", corpo), **ctx)


HOME = """
<h1>Avvisi Dipendenti</h1>
{% if dry %}<div class="card warn"><b>Modalità prova:</b> manca
BREVO_API_KEY, quindi le email <b>non vengono inviate davvero</b>.</div>{% endif %}
{% if errore %}<div class="card warn err">{{ errore }}</div>{% endif %}
<form class="card" method="post" action="{{ url_for('crea') }}">
<h2>Nuovo avviso</h2>
<label>Oggetto dell'email</label>
<input name="oggetto" required maxlength="200" value="{{ f.oggetto }}">
<label>Cosa devo dire <span class="hint">(il testo del messaggio)</span></label>
<textarea name="messaggio" required rows="7">{{ f.messaggio }}</textarea>
<label>Opzioni di risposta <span class="hint">(una per riga, es. Sì / No /
Forse. Lascia vuoto se è solo un avviso)</span></label>
<textarea name="opzioni" rows="3">{{ f.opzioni }}</textarea>
<label>Quantità di persone <span class="hint">(quante email ti
aspetti: serve a controllare che l'elenco sia completo)</span></label>
<input name="quantita" type="number" min="1" value="{{ f.quantita }}">
<label>Elenco email <span class="hint">(incolla tutto: una per riga, separate
da virgole o copiate da Excel)</span></label>
<textarea name="email" required rows="10" id="elenco">{{ f.email }}</textarea>
<div id="conta"></div>
<button type="submit" onclick="return confirm('Confermi l\\'invio?')">
Invia l'avviso</button>
</form>
{% if campagne %}<div class="card"><h2>Avvisi inviati</h2><table>
<tr><th>Data</th><th>Oggetto</th><th>Stato</th><th></th></tr>
{% for c in campagne %}<tr><td>{{ c.creata_il[:16] }}</td>
<td>{{ c.oggetto }}</td><td>{{ c.stato }}</td>
<td><a href="{{ url_for('dettaglio', cid=c.id) }}">Risultati</a></td></tr>
{% endfor %}</table></div>{% endif %}
<script>
const re=/[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/g,
el=document.getElementById('elenco'),out=document.getElementById('conta');
function conta(){const t=(el.value.match(re)||[]).map(e=>e.toLowerCase());
const u=new Set(t).size;out.textContent=u.toLocaleString('it-IT')+
' email valide trovate'+(t.length>u?' ('+(t.length-u)+' duplicati verranno tolti)':'');}
el.addEventListener('input',conta);conta();
</script>
"""

DETTAGLIO = """
<p><a href="{{ url_for('home') }}">&larr; Tutti gli avvisi</a></p>
<h1>{{ c.oggetto }}</h1>
{% if c.stato != 'completata' %}<meta http-equiv="refresh" content="5">{% endif %}
<div class="grid">
<div class="card"><div class="hint">Destinatari</div>
<div class="big">{{ "{:,}".format(totale).replace(",", ".") }}</div></div>
<div class="card"><div class="hint">Inviate</div>
<div class="big ok">{{ "{:,}".format(c.inviate).replace(",", ".") }}</div></div>
<div class="card"><div class="hint">Errori di invio</div>
<div class="big {{ 'err' if c.errori else '' }}">{{ c.errori }}</div></div>
<div class="card"><div class="hint">Hanno risposto</div>
<div class="big">{{ "{:,}".format(risposto).replace(",", ".") }}</div>
<div class="hint">{{ "%.1f"|format(risposto / totale * 100 if totale else 0) }}%
del totale</div></div>
</div>
{% if c.ultimo_errore %}<div class="card warn err">Ultimo errore:
{{ c.ultimo_errore }}</div>{% endif %}
{% if righe %}<div class="card"><h2>Risposte</h2><table>
{% for r in righe %}<tr><td style="width:30%"><b>{{ r.opzione }}</b></td>
<td style="width:18%">{{ r.n }} ({{ "%.1f"|format(r.perc) }}%)</td>
<td><div class="bar"><span style="width:{{ r.perc }}%"></span></div></td></tr>
{% endfor %}<tr><td>Nessuna risposta</td><td>{{ totale - risposto }}</td>
<td></td></tr></table>
<a class="btn sec" href="{{ url_for('esporta', cid=c.id) }}">Scarica elenco
completo (CSV/Excel)</a></div>{% endif %}
<div class="card"><h2>Messaggio inviato</h2>
<div style="white-space:pre-wrap">{{ c.messaggio }}</div></div>
"""

RISPOSTA = """
<div class="card" style="max-width:480px;margin:40px auto;text-align:center">
{% if fatto %}<h1 class="ok">Risposta registrata</h1>
<p>Hai risposto: <b>{{ opzione }}</b>. Puoi chiudere questa pagina.</p>
{% else %}<h1>Confermi la tua risposta?</h1>
<p class="big">{{ opzione }}</p>
<form method="post"><button type="submit">Conferma</button></form>
{% if precedente is not none %}<p class="hint">Avevi già risposto
"{{ opzioni[precedente] }}": confermando la cambi.</p>{% endif %}{% endif %}
</div>
"""


@app.get("/")
@admin_required
def home(errore=None, f=None):
    campagne = get_db().execute(
        "SELECT * FROM campagne ORDER BY id DESC").fetchall()
    vuoto = {"oggetto": "", "messaggio": "", "opzioni": "Sì\nNo",
             "quantita": "", "email": ""}
    return pagina(HOME, campagne=campagne, dry=DRY_RUN, errore=errore,
                  f=f or vuoto)


@app.post("/campagne")
@admin_required
def crea():
    f = request.form
    oggetto = f.get("oggetto", "").strip()
    messaggio = f.get("messaggio", "").strip()
    opzioni = [o.strip() for o in f.get("opzioni", "").splitlines()
               if o.strip()]
    quantita = int(f["quantita"]) if f.get("quantita", "").isdigit() else None
    email, _dup = estrai_email(f.get("email", ""))

    errore = None
    if not oggetto or not messaggio:
        errore = "Oggetto e messaggio sono obbligatori."
    elif not email:
        errore = "Nessun indirizzo email valido trovato nell'elenco."
    elif quantita and quantita != len(email):
        errore = (f"Hai indicato {quantita} persone ma nell'elenco ci sono "
                  f"{len(email)} email valide (duplicati esclusi). Controlla "
                  "l'elenco oppure correggi la quantità.")
    elif not DRY_RUN and not SENDER_EMAIL:
        errore = "Manca SENDER_EMAIL nella configurazione."
    if errore:
        return home(errore=errore, f=f)

    db = get_db()
    cur = db.execute(
        "INSERT INTO campagne (oggetto, messaggio, opzioni, quantita_attesa, "
        "creata_il) VALUES (?,?,?,?,?)",
        (oggetto, messaggio, json.dumps(opzioni), quantita,
         datetime.now().isoformat(timespec="seconds")))
    cid = cur.lastrowid
    db.executemany(
        "INSERT INTO destinatari (campagna_id, email, token) VALUES (?,?,?)",
        [(cid, e, secrets.token_urlsafe(16)) for e in email])
    db.commit()
    threading.Thread(target=invia_campagna, args=(cid,), daemon=True).start()
    return redirect(url_for("dettaglio", cid=cid))


@app.get("/campagne/<int:cid>")
@admin_required
def dettaglio(cid):
    c, _opz, totale, risposto, righe = statistiche(get_db(), cid)
    return pagina(DETTAGLIO, c=c, totale=totale, risposto=risposto,
                  righe=righe)


@app.get("/campagne/<int:cid>/export.csv")
@admin_required
def esporta(cid):
    db = get_db()
    _c, opzioni, *_ = statistiche(db, cid)
    buf = io.StringIO()
    buf.write("﻿")  # BOM: Excel apre correttamente gli accenti
    w = csv.writer(buf, delimiter=";")
    w.writerow(["email", "risposta", "data risposta"])
    for r in db.execute("SELECT email, risposta, risposto_il FROM "
                        "destinatari WHERE campagna_id=? ORDER BY email",
                        (cid,)):
        w.writerow([r["email"],
                    opzioni[r["risposta"]] if r["risposta"] is not None
                    else "nessuna risposta",
                    r["risposto_il"] or ""])
    return Response(buf.getvalue(), mimetype="text/csv", headers={
        "Content-Disposition": f"attachment; filename=risposte_{cid}.csv"})


@app.route("/r/<token>/<int:idx>", methods=["GET", "POST"])
def rispondi(token, idx):
    # Il clic sul link apre una pagina di conferma: la risposta si registra
    # solo col POST, così i filtri antivirus aziendali che "aprono" i link
    # in automatico non falsano i conteggi.
    db = get_db()
    d = db.execute(
        "SELECT d.id, d.risposta, c.opzioni FROM destinatari d "
        "JOIN campagne c ON c.id=d.campagna_id WHERE d.token=?",
        (token,)).fetchone()
    if d is None:
        abort(404)
    opzioni = json.loads(d["opzioni"])
    if not 0 <= idx < len(opzioni):
        abort(404)
    fatto = False
    if request.method == "POST":
        db.execute("UPDATE destinatari SET risposta=?, risposto_il=? "
                   "WHERE id=?",
                   (idx, datetime.now().isoformat(timespec="seconds"),
                    d["id"]))
        db.commit()
        fatto = True
    return pagina(RISPOSTA, opzione=opzioni[idx], opzioni=opzioni,
                  precedente=d["risposta"], fatto=fatto)


init_db()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5000)))
