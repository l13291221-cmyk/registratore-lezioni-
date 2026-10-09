"""Test veloci delle parti che non richiedono posta né browser: python -m unittest"""
import os
import tempfile
import unittest
from email.message import EmailMessage
from pathlib import Path
from unittest import mock

import bot


def email_html(html, testo="Versione testo"):
    msg = EmailMessage()
    msg["From"] = "Sito <noreply@sito.it>"
    msg["Subject"] = "Accedi"
    msg.set_content(testo)
    msg.add_alternative(html, subtype="html")
    return msg


class TestLink(unittest.TestCase):
    HTML = """<a href="https://sito.it/"><img alt="Logo"></a>
        <a href="https://track.mail.com/c?u=1&amp;v=2">  Accedi  al tuo account </a>
        <a href="https://sito.it/unsubscribe">Disiscriviti</a>
        <a href="mailto:aiuto@sito.it">Scrivici</a>"""

    def test_estrae_link_html_e_testo(self):
        msg = email_html(self.HTML, "Oppure copia: https://sito.it/login?t=9.")
        link = bot.estrai_link(msg)
        url = [l.url for l in link]
        self.assertIn("https://track.mail.com/c?u=1&v=2", url)  # &amp; decodificato
        self.assertIn("https://sito.it/login?t=9", url)         # punto finale tolto
        self.assertNotIn("mailto:aiuto@sito.it", url)
        self.assertEqual(link[0].testo, "Logo")                  # alt dell'immagine
        self.assertEqual(link[1].testo, "Accedi al tuo account")

    def test_senza_filtri_preferisce_il_pulsante_di_accesso(self):
        link = bot.estrai_link(email_html(self.HTML))
        self.assertEqual(bot.scegli_link(link, "", "").testo, "Accedi al tuo account")

    def test_senza_filtri_evita_disiscrizione(self):
        link = [bot.Link("https://sito.it/unsubscribe", "qui"), bot.Link("https://sito.it/x", "qui")]
        self.assertEqual(bot.scegli_link(link, "", "").url, "https://sito.it/x")

    def test_filtri_link(self):
        link = bot.estrai_link(email_html(self.HTML, "https://sito.it/login?t=9"))
        self.assertEqual(bot.scegli_link(link, "SITO.IT/LOGIN", "").url, "https://sito.it/login?t=9")
        self.assertEqual(bot.scegli_link(link, "", "accedi").url, "https://track.mail.com/c?u=1&v=2")
        self.assertIsNone(bot.scegli_link(link, "altro.it", ""))


class TestFiltriEmail(unittest.TestCase):
    def cfg(self, mittente="", oggetto=""):
        return mock.Mock(email_mittente=mittente, email_oggetto=oggetto)

    def test_corrisponde(self):
        self.assertTrue(bot.corrisponde("Sito <noreply@sito.it>", "Lezione", self.cfg("sito.it")))
        self.assertTrue(bot.corrisponde("x@y.it", "Nuova LEZIONE disponibile", self.cfg(oggetto="lezione")))
        self.assertFalse(bot.corrisponde("x@altro.it", "Lezione", self.cfg("sito.it", "lezione")))
        self.assertFalse(bot.corrisponde("x@sito.it", "Fattura", self.cfg("sito.it", "lezione")))

    def test_data_e_cartella_imap(self):
        import datetime as dt
        self.assertEqual(bot.data_imap(dt.date(2026, 3, 5)), "05-Mar-2026")
        self.assertEqual(bot.cartella_imap("INBOX"), "INBOX")
        self.assertEqual(bot.cartella_imap("[Gmail]/Tutti i messaggi"), '"[Gmail]/Tutti i messaggi"')

    def test_analizza_fetch(self):
        dati = [
            (b'1 (UID 7 INTERNALDATE "09-Oct-2026 10:00:00 +0000" BODY[HEADER] {3}', b"abc"),
            b")",
            (b"2 (BODY[HEADER] {3}", b"def"),
            b' UID 12 INTERNALDATE "09-Oct-2026 11:00:00 +0000")',
        ]
        parti = bot._analizza_fetch(dati)
        self.assertEqual([p[1] for p in parti], [b"abc", b"def"])
        self.assertIn(b"UID 12", parti[1][0])


class TestStato(unittest.TestCase):
    def test_tentativi_e_abbandono(self):
        stato = {"email": {}}
        self.assertEqual(bot.registra_esito(stato, "k", False, 2)["esito"], "da_riprovare")
        self.assertFalse(bot.gia_gestita(stato, "k"))
        self.assertEqual(bot.registra_esito(stato, "k", False, 2)["esito"], "abbandonata")
        self.assertTrue(bot.gia_gestita(stato, "k"))
        bot.registra_esito(stato, "ok", True, 2)
        self.assertTrue(bot.gia_gestita(stato, "ok"))

    def test_salva_e_ricarica(self):
        with tempfile.TemporaryDirectory() as cartella:
            percorso = Path(cartella) / "stato.json"
            self.assertEqual(bot.carica_stato(percorso), {"email": {}})
            stato = {"email": {}}
            bot.registra_esito(stato, bot.chiave_email("<id@x>", ""), True, 3)
            stato["email"]["vecchia"] = {"esito": "ok", "tentativi": 1,
                                        "quando": "2020-01-01T00:00:00+00:00"}
            bot.salva_stato(percorso, stato)
            ricaricato = bot.carica_stato(percorso)
            self.assertNotIn("vecchia", ricaricato["email"])
            self.assertTrue(bot.gia_gestita(ricaricato, bot.chiave_email("<id@x>", "")))
            self.assertNotIn("id@x", percorso.read_text())  # solo impronte


class TestConfig(unittest.TestCase):
    BASE = {"EMAIL_INDIRIZZO": "io@gmail.com", "EMAIL_PASSWORD": "abcd efgh",
            "EMAIL_MITTENTE": "sito.it"}

    def leggi(self, **extra):
        with mock.patch.dict(os.environ, {**self.BASE, **extra}, clear=True):
            return bot.leggi_config()

    def test_valori_predefiniti(self):
        cfg = self.leggi()
        self.assertEqual(cfg.imap_server, "imap.gmail.com")
        self.assertEqual(cfg.email_password, "abcdefgh")  # spazi tolti
        self.assertEqual(cfg.ore_massime, 24)

    def test_errori(self):
        with self.assertRaises(bot.ErroreConfig):
            self.leggi(EMAIL_MITTENTE="")  # nessun filtro
        with self.assertRaises(bot.ErroreConfig):
            self.leggi(SITO_UTENTE="u", SITO_PASSWORD="p")  # manca SITO_DOMINIO
        with self.assertRaises(bot.ErroreConfig):
            self.leggi(EMAIL_INDIRIZZO="io@sconosciuto.xyz")  # server IMAP ignoto
        self.assertEqual(self.leggi(SITO_UTENTE="u", SITO_PASSWORD="p",
                                    SITO_DOMINIO="www.Sito.it").sito_dominio, "sito.it")

    def test_file_env(self):
        with tempfile.TemporaryDirectory() as cartella:
            percorso = Path(cartella) / ".env"
            percorso.write_text('# commento\nA_PROVA="ciao mondo"\nB_PROVA=x=y\n', encoding="utf-8")
            with mock.patch.dict(os.environ, {"B_PROVA": "gia"}, clear=True):
                bot.carica_file_env(percorso)
                self.assertEqual(os.environ["A_PROVA"], "ciao mondo")
                self.assertEqual(os.environ["B_PROVA"], "gia")  # non sovrascrive

    def test_dominio_consentito(self):
        self.assertTrue(bot.dominio_consentito("https://sito.it/login", "sito.it"))
        self.assertTrue(bot.dominio_consentito("https://login.sito.it/", "sito.it"))
        self.assertFalse(bot.dominio_consentito("https://sito.it.truffa.com/", "sito.it"))
        self.assertFalse(bot.dominio_consentito("https://falsosito.it/", "sito.it"))


if __name__ == "__main__":
    unittest.main()
