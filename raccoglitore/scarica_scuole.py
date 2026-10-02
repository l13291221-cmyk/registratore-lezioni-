"""Scarica dagli open data del Ministero dell'Istruzione l'anagrafica delle
scuole (statali e paritarie), tiene le superiori dell'Emilia-Romagna e genera
una pagina HTML autonoma con le email gia' dentro.

Uso: python raccoglitore/scarica_scuole.py  ->  dist/raccoglitore-email.html
"""
import csv
import datetime
import io
import json
import pathlib
import re
import sys
import urllib.request

BASE = "https://dati.istruzione.it/opendata/opendata/catalogo/elements1/"
DATASET = {"statali": "SCUANAGRAFESTAT", "paritarie": "SCUANAGRAFEPAR"}
REGIONE = "EMILIA ROMAGNA"

# Superiori: si tengono licei/istituti, si scartano infanzia, primaria,
# medie, comprensivi e scuole per adulti.
INCLUDI = re.compile(r"LICEO|IST|SUPERIORE|SECONDO GRADO|CONVITTO|EDUCANDATO")
ESCLUDI = re.compile(r"INFANZIA|PRIMARIA|PRIMO GRADO|COMPRENSIVO|CIRCOLO|CPIA|"
                     r"CENTRO TERRITORIALE|ADULTI")

QUI = pathlib.Path(__file__).parent
USCITA = QUI.parent / "dist" / "raccoglitore-email.html"


def scarica(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def trova_csv(prefisso):
    """Cerca il link al CSV piu' recente nella pagina del dataset; se la pagina
    non lo contiene, prova i nomi standard degli ultimi anni scolastici."""
    candidati = []
    try:
        pagina = scarica(f"{BASE}leaf/?area=Scuole&datasetId=DS0400{prefisso}")
        candidati = sorted(set(re.findall(prefisso + r"\d+\.csv",
                                          pagina.decode("utf-8", "replace"))),
                           reverse=True)
    except Exception as e:
        print(f"Pagina dataset {prefisso} non leggibile: {e}")
    anno = datetime.date.today().year
    for a in range(anno, anno - 4, -1):
        candidati.append(f"{prefisso}{a}{(a + 1) % 100:02d}{a}0901.csv")
    for nome in candidati:
        try:
            dati = scarica(BASE + nome)
        except Exception:
            continue
        if dati[:200].upper().find(b"REGIONE") >= 0:
            print(f"Uso {nome}")
            return dati
    sys.exit(f"Nessun CSV trovato per {prefisso}: controlla {BASE}")


def leggi(dati):
    testo = dati.decode("utf-8-sig", "replace")
    sep = ";" if testo.split("\n", 1)[0].count(";") > testo.split("\n", 1)[0].count(",") else ","
    return csv.DictReader(io.StringIO(testo), delimiter=sep)


def valido(email):
    email = (email or "").strip().lower()
    return email if re.fullmatch(r"[^@\s]+@[^@\s]+\.[a-z]{2,}", email) else ""


def main():
    scuole, viste, tipologie = [], set(), {}
    for tipo, prefisso in DATASET.items():
        for r in leggi(trova_csv(prefisso)):
            if (r.get("REGIONE") or "").strip().upper() != REGIONE:
                continue
            grado = (r.get("DESCRIZIONETIPOLOGIAGRADOISTRUZIONESCUOLA") or "").upper()
            tenuta = bool(INCLUDI.search(grado)) and not ESCLUDI.search(grado)
            tipologie[(grado, tenuta)] = tipologie.get((grado, tenuta), 0) + 1
            email = valido(r.get("INDIRIZZOEMAILSCUOLA")) or valido(r.get("INDIRIZZOPECSCUOLA"))
            if not tenuta or not email or email in viste:
                continue
            viste.add(email)
            scuole.append({
                "email": email,
                "nome": (r.get("DENOMINAZIONESCUOLA") or "").strip(),
                "comune": (r.get("DESCRIZIONECOMUNE") or "").strip(),
                "provincia": (r.get("PROVINCIA") or "").strip(),
                "tipo": tipo,
            })

    print("\nTipologie trovate in Emilia-Romagna (SI = tenuta):")
    for (grado, tenuta), n in sorted(tipologie.items()):
        print(f"  {'SI' if tenuta else 'no'}  {n:5d}  {grado}")
    if not scuole:
        sys.exit("Nessuna scuola trovata: il formato dei dati e' cambiato?")

    scuole.sort(key=lambda s: (s["provincia"], s["comune"], s["nome"]))
    html = (QUI / "pagina.html").read_text(encoding="utf-8")
    html = html.replace("/*DATI*/[]", json.dumps(scuole, ensure_ascii=False))
    html = html.replace("{{AGGIORNATO}}", datetime.date.today().strftime("%d/%m/%Y"))
    USCITA.parent.mkdir(exist_ok=True)
    USCITA.write_text(html, encoding="utf-8")
    print(f"\n{len(scuole)} email uniche -> {USCITA}")


if __name__ == "__main__":
    main()
