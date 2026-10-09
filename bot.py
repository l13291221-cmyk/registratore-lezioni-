#!/usr/bin/env python3
"""
Apri-link automatico.

Controlla la casella di posta (IMAP), cerca le email che corrispondono ai filtri
(mittente / oggetto), prende il link giusto dentro l'email, lo apre con un
browser vero (Chrome/Chromium headless) e, se il sito chiede di accedere,
compila utente e password. Ogni email viene gestita una sola volta.

Uso:
    python bot.py               controlla una volta e apre i link nuovi
    python bot.py --ogni 60     resta acceso e controlla ogni 60 secondi
    python bot.py --prova       mostra cosa farebbe, senza aprire niente

La configurazione arriva dalle variabili d'ambiente (su GitHub: i Secrets) o,
sul PC, dal file .env accanto a questo script. Vedi README.md.
"""

import argparse
import datetime as dt
import email
import hashlib
import imaplib
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from email import policy
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse

CARTELLA_PROGETTO = Path(__file__).resolve().parent
SU_GITHUB = os.environ.get("GITHUB_ACTIONS") == "true"

SERVER_IMAP_NOTI = {
    "gmail.com": "imap.gmail.com",
    "googlemail.com": "imap.gmail.com",
    "libero.it": "imapmail.libero.it",
    "virgilio.it": "in.virgilio.it",
    "yahoo.com": "imap.mail.yahoo.com",
    "yahoo.it": "imap.mail.yahoo.com",
    "icloud.com": "imap.mail.me.com",
    "me.com": "imap.mail.me.com",
}

# Parole che, nel testo di un link, fanno pensare al pulsante "entra".
# Usate solo se LINK_TESTO e LINK_CONTIENE sono vuoti.
PAROLE_LINK_ACCESSO = (
    "accedi", "accesso", "entra", "login", "log in", "sign in", "conferma",
    "verifica", "clicca", "click", "apri", "access",
)
PAROLE_LINK_DA_EVITARE = (
    "unsubscribe", "disiscri", "cancella iscrizione", "annulla iscrizione",
    "opt-out", "optout", "privacy", "preferenze",
)
PAROLE_PAGINA_LOGIN = (
    "login", "log in", "logon", "sign in", "signin", "accedi", "accesso",
    "autentica", "auth/", "sso",
)

SELETTORI_UTENTE = (
    "input[autocomplete='username']",
    "input[type='email']",
    "input[name*='user' i]", "input[id*='user' i]",
    "input[name*='email' i]", "input[id*='email' i]",
    "input[name*='login' i]", "input[id*='login' i]",
    "input[name*='utente' i]", "input[id*='utente' i]",
)
SELETTORI_INVIO = (
    "button[type='submit']", "input[type='submit']", "button:not([type])",
)


# --------------------------------------------------------------------------
# Configurazione
# --------------------------------------------------------------------------

class ErroreConfig(Exception):
    pass


@dataclass
class Config:
    email_indirizzo: str
    email_password: str
    imap_server: str
    imap_porta: int
    cartella: str
    email_mittente: str
    email_oggetto: str
    link_contiene: str
    link_testo: str
    sito_utente: str
    sito_password: str
    sito_dominio: str
    selettore_utente: str
    selettore_password: str
    selettore_invio: str
    testo_successo: str
    ore_massime: int
    attesa_secondi: int
    max_tentativi: int
    file_stato: Path


def carica_file_env(percorso):
    """Legge righe CHIAVE=valore. Non sovrascrive variabili già impostate."""
    if not percorso.is_file():
        return
    for riga in percorso.read_text(encoding="utf-8").splitlines():
        riga = riga.strip()
        if not riga or riga.startswith("#") or "=" not in riga:
            continue
        chiave, valore = riga.split("=", 1)
        valore = valore.strip()
        if len(valore) >= 2 and valore[0] == valore[-1] and valore[0] in "\"'":
            valore = valore[1:-1]
        os.environ.setdefault(chiave.strip(), valore)


def _env(nome, predefinito=""):
    return os.environ.get(nome, "").strip() or predefinito


def _env_int(nome, predefinito):
    valore = _env(nome)
    if not valore:
        return predefinito
    try:
        return int(valore)
    except ValueError:
        raise ErroreConfig(f"{nome} deve essere un numero intero")


def leggi_config():
    indirizzo = _env("EMAIL_INDIRIZZO")
    password = _env("EMAIL_PASSWORD").replace(" ", "")
    if not indirizzo or not password:
        raise ErroreConfig("Imposta EMAIL_INDIRIZZO ed EMAIL_PASSWORD")

    dominio_email = indirizzo.rsplit("@", 1)[-1].lower()
    imap_server = _env("IMAP_SERVER", SERVER_IMAP_NOTI.get(dominio_email, ""))
    if not imap_server:
        raise ErroreConfig(
            f"Non conosco il server IMAP di '{dominio_email}': imposta IMAP_SERVER"
        )

    cfg = Config(
        email_indirizzo=indirizzo,
        email_password=password,
        imap_server=imap_server,
        imap_porta=_env_int("IMAP_PORTA", 993),
        cartella=_env("CARTELLA", "INBOX"),
        email_mittente=_env("EMAIL_MITTENTE"),
        email_oggetto=_env("EMAIL_OGGETTO"),
        link_contiene=_env("LINK_CONTIENE"),
        link_testo=_env("LINK_TESTO"),
        sito_utente=_env("SITO_UTENTE"),
        sito_password=_env("SITO_PASSWORD"),
        sito_dominio=_env("SITO_DOMINIO").lower().removeprefix("www."),
        selettore_utente=_env("SELETTORE_UTENTE"),
        selettore_password=_env("SELETTORE_PASSWORD"),
        selettore_invio=_env("SELETTORE_INVIO"),
        testo_successo=_env("TESTO_SUCCESSO"),
        ore_massime=_env_int("ORE_MASSIME", 24),
        attesa_secondi=_env_int("ATTESA_SECONDI", 5),
        max_tentativi=_env_int("MAX_TENTATIVI", 3),
        file_stato=Path(_env("FILE_STATO", str(CARTELLA_PROGETTO / "stato.json"))),
    )

    # Senza filtri il bot aprirebbe i link di qualsiasi email: non lo permettiamo.
    if not cfg.email_mittente and not cfg.email_oggetto:
        raise ErroreConfig("Imposta almeno uno tra EMAIL_MITTENTE ed EMAIL_OGGETTO")
    if bool(cfg.sito_utente) != bool(cfg.sito_password):
        raise ErroreConfig("Imposta sia SITO_UTENTE sia SITO_PASSWORD (o nessuno dei due)")
    # La password del sito viene scritta solo sul dominio indicato: un'email
    # falsa con un link a un sito-trappola non può rubarla.
    if cfg.sito_password and not cfg.sito_dominio:
        raise ErroreConfig("Con SITO_PASSWORD imposta anche SITO_DOMINIO (es. sito.it)")
    return cfg


# --------------------------------------------------------------------------
# Log (niente dati personali: su GitHub i log di un repo pubblico sono visibili)
# --------------------------------------------------------------------------

def log(messaggio):
    ora = dt.datetime.now().strftime("%H:%M:%S")
    print(f"[{ora}] {messaggio}", flush=True)


def nascondi_nei_log(valore):
    """Su GitHub Actions fa sostituire il valore con *** in tutto il log."""
    if SU_GITHUB and valore:
        print(f"::add-mask::{valore}", flush=True)


def scrivi_output_github(chiave, valore):
    percorso = os.environ.get("GITHUB_OUTPUT")
    if percorso:
        with open(percorso, "a", encoding="utf-8") as f:
            f.write(f"{chiave}={valore}\n")


# --------------------------------------------------------------------------
# Stato: quali email sono già state gestite
# --------------------------------------------------------------------------

def carica_stato(percorso):
    try:
        dati = json.loads(percorso.read_text(encoding="utf-8"))
        if isinstance(dati.get("email"), dict):
            return dati
    except (OSError, ValueError, AttributeError):
        pass
    return {"email": {}}


def salva_stato(percorso, stato):
    # Le voci più vecchie di 30 giorni non servono più.
    limite = dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=30)
    stato["email"] = {
        k: v for k, v in stato["email"].items()
        if dt.datetime.fromisoformat(v["quando"]) >= limite
    }
    percorso.write_text(json.dumps(stato, indent=2), encoding="utf-8")


def chiave_email(message_id, ripiego):
    # Nel file salviamo solo un'impronta, non il Message-ID vero.
    return hashlib.sha256((message_id or ripiego).encode("utf-8")).hexdigest()


def gia_gestita(stato, chiave):
    voce = stato["email"].get(chiave)
    return voce is not None and voce["esito"] in ("ok", "abbandonata")


def registra_esito(stato, chiave, ok, max_tentativi):
    voce = stato["email"].get(chiave, {"tentativi": 0})
    voce["tentativi"] += 1
    voce["quando"] = dt.datetime.now(dt.timezone.utc).isoformat()
    if ok:
        voce["esito"] = "ok"
    elif voce["tentativi"] >= max_tentativi:
        voce["esito"] = "abbandonata"
    else:
        voce["esito"] = "da_riprovare"
    stato["email"][chiave] = voce
    return voce


# --------------------------------------------------------------------------
# Email: ricerca e estrazione del link
# --------------------------------------------------------------------------

@dataclass
class Link:
    url: str
    testo: str


@dataclass
class EmailTrovata:
    chiave: str
    arrivata: dt.datetime
    link: Link | None


class EstrattoreLink(HTMLParser):
    def __init__(self):
        super().__init__()
        self.link = []
        self._aperto = None

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            href = dict(attrs).get("href") or ""
            self._aperto = Link(href.strip(), "")
            self.link.append(self._aperto)
        elif tag == "img" and self._aperto is not None:
            # Pulsanti fatti con un'immagine: usa il testo alternativo.
            alt = dict(attrs).get("alt") or ""
            self._aperto.testo += " " + alt

    def handle_endtag(self, tag):
        if tag == "a":
            self._aperto = None

    def handle_data(self, data):
        if self._aperto is not None:
            self._aperto.testo += data


RE_URL_TESTO = re.compile(r"https?://[^\s<>\"')\]]+")


def estrai_link(msg):
    """Tutti i link dell'email: prima quelli dell'HTML, poi quelli del testo."""
    link_html, link_testo = [], []
    for parte in msg.walk():
        tipo = parte.get_content_type()
        if tipo not in ("text/html", "text/plain") or parte.is_attachment():
            continue
        try:
            contenuto = parte.get_content()
        except (LookupError, ValueError):
            continue
        if tipo == "text/html":
            estrattore = EstrattoreLink()
            estrattore.feed(contenuto)
            estrattore.close()
            link_html.extend(estrattore.link)
        else:
            link_testo.extend(Link(u.rstrip(".,;:"), "") for u in RE_URL_TESTO.findall(contenuto))
    link = link_html + link_testo
    for l in link:
        l.testo = " ".join(l.testo.split())
    return [l for l in link if l.url.lower().startswith(("http://", "https://"))]


def scegli_link(link, contiene, testo):
    candidati = link
    if contiene:
        candidati = [l for l in candidati if contiene.lower() in l.url.lower()]
    if testo:
        candidati = [l for l in candidati if testo.lower() in l.testo.lower()]
    if contiene or testo:
        return candidati[0] if candidati else None

    candidati = [
        l for l in candidati
        if not any(p in (l.url + " " + l.testo).lower() for p in PAROLE_LINK_DA_EVITARE)
    ]
    for l in candidati:
        if any(p in l.testo.lower() for p in PAROLE_LINK_ACCESSO):
            return l
    return candidati[0] if candidati else None


def corrisponde(mittente, oggetto, cfg):
    if cfg.email_mittente and cfg.email_mittente.lower() not in mittente.lower():
        return False
    if cfg.email_oggetto and cfg.email_oggetto.lower() not in oggetto.lower():
        return False
    return True


MESI = ("Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")


def data_imap(giorno):
    return f"{giorno.day:02d}-{MESI[giorno.month - 1]}-{giorno.year}"


def cartella_imap(nome):
    if nome.upper() == "INBOX" or re.fullmatch(r"[\w./-]+", nome):
        return nome
    return '"' + nome.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _analizza_fetch(dati):
    """Trasforma la risposta di imaplib in coppie (metadati, contenuto)."""
    risultati = []
    for parte in dati:
        if isinstance(parte, tuple):
            risultati.append([parte[0], parte[1]])
        elif isinstance(parte, bytes) and risultati:
            risultati[-1][0] += parte
    return risultati


def cerca_email_nuove(cfg, stato):
    """Restituisce le email che corrispondono ai filtri e non sono ancora gestite."""
    adesso = dt.datetime.now(dt.timezone.utc)
    da_quando = adesso - dt.timedelta(hours=cfg.ore_massime)

    conn = imaplib.IMAP4_SSL(cfg.imap_server, cfg.imap_porta, timeout=60)
    try:
        conn.login(cfg.email_indirizzo, cfg.email_password)
        tipo, _ = conn.select(cartella_imap(cfg.cartella), readonly=True)
        if tipo != "OK":
            raise RuntimeError(f"Cartella '{cfg.cartella}' non trovata")

        # SINCE lavora a giorni interi: si parte dal giorno prima per sicurezza,
        # il filtro preciso sulle ore si fa dopo con la data di arrivo.
        giorno = (da_quando - dt.timedelta(days=1)).date()
        tipo, dati = conn.uid("SEARCH", None, "SINCE", data_imap(giorno))
        uid = dati[0].split() if tipo == "OK" and dati and dati[0] else []
        uid = uid[-500:]
        if not uid:
            return []

        # Prima solo le intestazioni (veloce), poi il corpo delle email giuste.
        tipo, dati = conn.uid(
            "FETCH", b",".join(uid).decode(),
            "(UID INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM SUBJECT MESSAGE-ID)])",
        )
        candidate = []
        for meta, intestazioni in _analizza_fetch(dati if tipo == "OK" else []):
            trovato_uid = re.search(rb"UID (\d+)", meta)
            data = imaplib.Internaldate2tuple(meta)
            if not trovato_uid or not data:
                continue
            arrivata = dt.datetime.fromtimestamp(time.mktime(data), dt.timezone.utc)
            if arrivata < da_quando:
                continue
            msg = email.message_from_bytes(intestazioni, policy=policy.default)
            try:
                mittente = str(msg.get("From", ""))
                oggetto = str(msg.get("Subject", ""))
                message_id = str(msg.get("Message-ID", "")).strip()
            except (ValueError, IndexError):
                continue
            if not corrisponde(mittente, oggetto, cfg):
                continue
            chiave = chiave_email(message_id, f"{arrivata.isoformat()}|{mittente}|{oggetto}")
            if gia_gestita(stato, chiave):
                continue
            candidate.append((arrivata, int(trovato_uid.group(1)), chiave))

        trovate = []
        for arrivata, numero, chiave in sorted(candidate):
            tipo, dati = conn.uid("FETCH", str(numero), "(BODY.PEEK[])")
            parti = _analizza_fetch(dati if tipo == "OK" else [])
            if not parti:
                continue
            msg = email.message_from_bytes(parti[0][1], policy=policy.default)
            link = scegli_link(estrai_link(msg), cfg.link_contiene, cfg.link_testo)
            trovate.append(EmailTrovata(chiave, arrivata, link))
        return trovate
    finally:
        try:
            conn.logout()
        except Exception:
            pass


# --------------------------------------------------------------------------
# Browser: apre il link e accede
# --------------------------------------------------------------------------

def dominio_consentito(url, dominio):
    host = (urlparse(url).hostname or "").lower()
    return host == dominio or host.endswith("." + dominio)


def _primo_visibile(radice, selettori):
    for selettore in selettori:
        elementi = radice.locator(selettore)
        try:
            totale = min(elementi.count(), 10)
        except Exception:
            continue
        for i in range(totale):
            elemento = elementi.nth(i)
            try:
                if elemento.is_visible() and elemento.is_enabled():
                    return elemento
            except Exception:
                pass
    return None


def _modulo_di(campo):
    modulo = campo.locator("xpath=ancestor::form[1]")
    return modulo if modulo.count() else None


def _trova_password(page, cfg):
    selettori = [cfg.selettore_password] if cfg.selettore_password else ["input[type='password']"]
    return _primo_visibile(page, selettori)


def _trova_utente(page, cfg, campo_password):
    if cfg.selettore_utente:
        return _primo_visibile(page, [cfg.selettore_utente])
    trovato = _primo_visibile(page, SELETTORI_UTENTE)
    if trovato is None and campo_password is not None:
        # Ultima spiaggia: il campo di testo nello stesso modulo della password.
        modulo = _modulo_di(campo_password)
        if modulo is not None:
            trovato = _primo_visibile(modulo, ["input[type='text']", "input:not([type])"])
    return trovato


def _invia(page, campo, cfg):
    pulsante = None
    if cfg.selettore_invio:
        pulsante = _primo_visibile(page, [cfg.selettore_invio])
    else:
        modulo = _modulo_di(campo)
        if modulo is not None:
            pulsante = _primo_visibile(modulo, SELETTORI_INVIO)
    if pulsante is not None:
        try:
            pulsante.click(timeout=5000)
            return
        except Exception:
            pass  # es. un banner dei cookie copre il pulsante: si prova con Invio
    campo.press("Enter")


def _attendi_caricamento(page):
    page.wait_for_timeout(1500)
    for stato in ("load", "networkidle"):
        try:
            page.wait_for_load_state(stato, timeout=15000)
        except Exception:
            pass


def _sembra_pagina_login(page):
    try:
        testo = (page.url + " " + page.title()).lower()
    except Exception:
        return False
    return any(p in testo for p in PAROLE_PAGINA_LOGIN)


def _avvia_browser(p):
    # Su GitHub Actions c'è già Google Chrome installato: si usa quello.
    try:
        return p.chromium.launch(channel="chrome", headless=True)
    except Exception:
        return p.chromium.launch(headless=True)


def _accedi(page, cfg):
    """Compila il modulo di login, se c'è. Restituisce un messaggio d'errore o None."""
    for passo in range(2):
        campo_password = _trova_password(page, cfg)
        campo_utente = _trova_utente(page, cfg, campo_password)
        if campo_password is None:
            # Login in due passaggi (prima l'utente, poi la password): solo se la
            # pagina sembra davvero una pagina di login, per non compilare a caso
            # altri moduli (newsletter, ricerca, profilo...).
            if passo > 0 or campo_utente is None or not _sembra_pagina_login(page):
                return None
        if not cfg.sito_password:
            return "Il sito chiede di accedere: imposta SITO_UTENTE e SITO_PASSWORD"
        if not dominio_consentito(page.url, cfg.sito_dominio):
            return "La pagina di login non è sul dominio SITO_DOMINIO: password non inserita"

        # Alcuni siti, nel secondo passaggio, lasciano l'utente visibile ma bloccato.
        if campo_utente is not None and campo_utente.is_editable():
            campo_utente.fill(cfg.sito_utente)
        if campo_password is None:
            log("Login in due passaggi: inserito l'utente, attendo la password")
            _invia(page, campo_utente, cfg)
            try:
                page.wait_for_selector(
                    cfg.selettore_password or "input[type='password']",
                    state="visible", timeout=15000,
                )
            except Exception:
                return "Dopo l'utente non è comparso il campo password"
            continue

        campo_password.fill(cfg.sito_password)
        log("Modulo di login compilato, invio")
        _invia(page, campo_password, cfg)
        _attendi_caricamento(page)
        return None
    return None


def apri_e_accedi(url, cfg):
    """Apre il link e accede. Restituisce (riuscito, messaggio)."""
    from playwright.sync_api import sync_playwright

    with sync_playwright() as p:
        browser = _avvia_browser(p)
        try:
            versione = browser.version.split(".")[0]
            contesto = browser.new_context(
                locale="it-IT",
                viewport={"width": 1280, "height": 900},
                user_agent=(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    f"(KHTML, like Gecko) Chrome/{versione}.0.0.0 Safari/537.36"
                ),
            )
            page = contesto.new_page()
            page.set_default_timeout(30000)
            try:
                risposta = page.goto(url, wait_until="domcontentloaded", timeout=60000)
                if risposta is not None and risposta.status >= 400:
                    return False, f"Il link ha risposto con errore {risposta.status} (forse è scaduto)"
                _attendi_caricamento(page)

                errore = _accedi(page, cfg)
                if errore:
                    return False, errore

                page.wait_for_timeout(cfg.attesa_secondi * 1000)

                if cfg.testo_successo:
                    if cfg.testo_successo.lower() not in page.inner_text("body").lower():
                        return False, "Nella pagina finale non c'è TESTO_SUCCESSO"
                elif _trova_password(page, cfg) is not None:
                    return False, "Dopo il login c'è ancora il campo password (credenziali sbagliate?)"
                return True, "Accesso eseguito"
            finally:
                cartella_debug = CARTELLA_PROGETTO / "debug"
                cartella_debug.mkdir(exist_ok=True)
                try:
                    page.screenshot(path=str(cartella_debug / "ultima_pagina.png"), full_page=True)
                except Exception:
                    pass
        finally:
            browser.close()


# --------------------------------------------------------------------------
# Programma principale
# --------------------------------------------------------------------------

def controlla_una_volta(cfg, prova):
    """Un giro completo. Restituisce (errori, stato_cambiato)."""
    stato = carica_stato(cfg.file_stato)
    trovate = cerca_email_nuove(cfg, stato)
    if not trovate:
        log("Nessuna email nuova")
        return 0, False

    log(f"Email nuove da gestire: {len(trovate)}")
    errori = 0
    for posta in trovate:
        ora_locale = posta.arrivata.astimezone().strftime("%d/%m %H:%M")
        if posta.link is None:
            log(f"Email del {ora_locale}: nessun link adatto (controlla LINK_CONTIENE / LINK_TESTO)")
            ok, messaggio = False, "nessun link"
        else:
            nascondi_nei_log(posta.link.url)
            if prova:
                dove = "nascosto nei log pubblici" if SU_GITHUB else posta.link.url
                log(f"[PROVA] Email del {ora_locale}: aprirei il link "
                    f"'{posta.link.testo or '(senza testo)'}' ({dove})")
                continue
            log(f"Email del {ora_locale}: apro il link '{posta.link.testo or '(senza testo)'}'")
            try:
                ok, messaggio = apri_e_accedi(posta.link.url, cfg)
            except Exception as e:
                ok, messaggio = False, f"errore del browser: {type(e).__name__}: {e}"
            log(("OK: " if ok else "NON RIUSCITO: ") + messaggio)

        if prova:
            continue
        voce = registra_esito(stato, posta.chiave, ok, cfg.max_tentativi)
        if not ok:
            errori += 1
            if voce["esito"] == "abbandonata":
                log(f"Email lasciata perdere dopo {voce['tentativi']} tentativi")
            else:
                log(f"Riprovo al prossimo controllo (tentativo {voce['tentativi']}/{cfg.max_tentativi})")

    if prova:
        return 0, False
    salva_stato(cfg.file_stato, stato)
    return errori, True


def main():
    parser = argparse.ArgumentParser(description="Apre i link delle email e accede al sito.")
    parser.add_argument("--ogni", type=int, metavar="SECONDI",
                        help="resta acceso e ricontrolla ogni SECONDI secondi")
    parser.add_argument("--prova", action="store_true",
                        help="mostra le email e i link trovati senza aprirli")
    argomenti = parser.parse_args()

    carica_file_env(CARTELLA_PROGETTO / ".env")
    try:
        cfg = leggi_config()
    except ErroreConfig as e:
        log(f"Configurazione incompleta: {e}")
        return 2

    if argomenti.ogni:
        log(f"Controllo la posta ogni {argomenti.ogni} secondi (Ctrl+C per fermare)")
        while True:
            try:
                controlla_una_volta(cfg, argomenti.prova)
            except KeyboardInterrupt:
                raise
            except Exception as e:
                log(f"Errore: {type(e).__name__}: {e}")
            time.sleep(argomenti.ogni)

    try:
        errori, stato_cambiato = controlla_una_volta(cfg, argomenti.prova)
    except imaplib.IMAP4.error as e:
        log(f"Accesso alla posta non riuscito: {e} (controlla EMAIL_INDIRIZZO / EMAIL_PASSWORD)")
        return 1
    except Exception as e:
        log(f"Errore: {type(e).__name__}: {e}")
        return 1
    scrivi_output_github("stato_cambiato", "true" if stato_cambiato else "false")
    return 1 if errori else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        log("Fermato")
