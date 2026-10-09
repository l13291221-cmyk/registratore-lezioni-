# Apri-link automatico

Controlla la tua casella di posta e, **ogni volta che arriva una certa email**,
apre il link che contiene ed entra nel tuo account del sito. Basta.

Gira **gratis su GitHub**: non devi tenere acceso il computer né installare
niente.

```
ogni 5 minuti ──► guarda la posta ──► email giusta? ──► apre il link ──► login sul sito
                                         │ no                              (se serve)
                                         └──► non fa niente
```

- Cerca solo le email che corrispondono ai **tuoi filtri** (mittente e/o oggetto).
- Nell'email sceglie il link giusto (per testo del pulsante o per indirizzo).
- Lo apre con un **browser vero** (Chrome), quindi funziona anche sui siti moderni.
- Se il sito chiede utente e password, li **compila da solo** (anche i login a
  due passaggi: prima l'email, poi la password).
- Ogni email viene gestita **una volta sola**. Se qualcosa va storto riprova al
  controllo successivo (massimo 3 volte).

---

## Come si attiva (una volta sola, 10 minuti)

### 1. Crea una "password per le app" di Gmail

Il programma non usa la tua password normale di Gmail, ma una password
speciale che puoi cancellare quando vuoi.

1. Serve la **verifica in due passaggi** attiva sull'account Google
   (<https://myaccount.google.com/signinoptions/twosv>).
2. Vai su <https://myaccount.google.com/apppasswords>.
3. Scrivi un nome qualsiasi (es. `apri-link`) e premi **Crea**.
4. Copia le **16 lettere** che compaiono: ti servono al passo 2.

> Usi Libero, Virgilio, Yahoo o iCloud? Funziona uguale: crea la password per
> le app dalle impostazioni di quel servizio. Outlook/Hotmail invece **non** è
> supportato (Microsoft ha bloccato questo tipo di accesso).

### 2. Inserisci i dati su GitHub (Secrets)

Nel repository su GitHub: **Settings → Secrets and variables → Actions →
New repository secret**. Crea un secret per ogni riga che ti serve
(nome esatto a sinistra, valore a destra).

**Obbligatori**

| Nome | Cosa scrivere | Esempio |
|---|---|---|
| `EMAIL_INDIRIZZO` | il tuo indirizzo email | `tuonome@gmail.com` |
| `EMAIL_PASSWORD` | la password per le app del passo 1 | `abcd efgh ijkl mnop` |
| `EMAIL_MITTENTE` | chi manda l'email (basta un pezzo) | `noreply@sito.it` oppure `sito.it` |
| `EMAIL_OGGETTO` | un pezzo dell'oggetto dell'email | `Accedi al tuo account` |

Di `EMAIL_MITTENTE` ed `EMAIL_OGGETTO` ne basta uno, ma con tutti e due è più
preciso. Maiuscole e minuscole non contano.

**Quale link aprire** (consigliati: senza, il programma prova a indovinare il
pulsante "Accedi / Entra / Conferma")

| Nome | Cosa scrivere | Esempio |
|---|---|---|
| `LINK_TESTO` | la scritta sul pulsante/link nell'email | `Accedi` |
| `LINK_CONTIENE` | un pezzo dell'indirizzo del link | `sito.it/login` |

**Il tuo account sul sito** (solo se, aprendo il link, il sito chiede di
accedere; se il link ti fa entrare direttamente non servono)

| Nome | Cosa scrivere | Esempio |
|---|---|---|
| `SITO_UTENTE` | il tuo nome utente / email sul sito | `mario.rossi@gmail.com` |
| `SITO_PASSWORD` | la tua password del sito | |
| `SITO_DOMINIO` | il dominio del sito: la password viene scritta **solo lì** | `sito.it` |

**Facoltativi**

| Nome | A cosa serve | Predefinito |
|---|---|---|
| `TESTO_SUCCESSO` | una scritta che sul sito vedi solo quando sei dentro (es. `Esci`, il tuo nome): così il programma è sicuro di essere entrato | — |
| `ATTESA_SECONDI` | quanti secondi restare sulla pagina dopo l'accesso | `5` |
| `ORE_MASSIME` | ignora le email più vecchie di queste ore | `24` |
| `CARTELLA` | se l'email non resta nella posta in arrivo (es. un filtro Gmail la archivia): `[Gmail]/Tutti i messaggi` | `INBOX` |
| `IMAP_SERVER` | solo per provider diversi da Gmail, Libero, Virgilio, Yahoo, iCloud | automatico |
| `SELETTORE_UTENTE`, `SELETTORE_PASSWORD`, `SELETTORE_INVIO` | solo se il login automatico non trova i campi del sito (selettori CSS, es. `#username`) | automatici |

### 3. Accendilo

GitHub esegue i controlli automatici **solo dal branch principale** del
repository. Quindi questo codice va unito (merge) nel branch principale, oppure
questo branch va impostato come principale in **Settings → General → Default
branch**.

### 4. Provalo

1. Vai nella scheda **Actions** del repository.
2. A sinistra scegli **"Controlla email e apri il link"**.
3. Premi **Run workflow**, spunta **"Solo prova"** e conferma.
4. Apri l'esecuzione e guarda il passo **"Controlla la posta e apri i link"**.

Con "Solo prova" il programma dice quali email ha trovato e quale link
aprirebbe, **senza aprirlo**. Se è tutto giusto, rilancia senza la spunta (o
aspetta: da lì in poi lavora da solo ogni 5 minuti).

---

## Come capisco se funziona?

Nella scheda **Actions** vedi un'esecuzione ogni ~5 minuti.

- ✅ pallino verde: tutto bene (nessuna email nuova, oppure link aperto con successo).
- ❌ pallino rosso: qualcosa non è andato. GitHub ti avvisa (email o notifica,
  secondo le tue impostazioni di notifica) e nel log trovi il motivo in italiano (es. *"Il sito chiede di accedere: imposta
  SITO_UTENTE e SITO_PASSWORD"*).

Messaggi tipici nel log:

```
Email nuove da gestire: 1
Email del 09/10 08:31: apro il link 'Accedi al tuo account'
Modulo di login compilato, invio
OK: Accesso eseguito
```

## Problemi comuni

| Messaggio | Cosa fare |
|---|---|
| `Accesso alla posta non riuscito` | Controlla `EMAIL_INDIRIZZO` ed `EMAIL_PASSWORD` (deve essere la password per le app, non quella normale). |
| `Nessuna email nuova` ma l'email c'è | Controlla `EMAIL_MITTENTE` / `EMAIL_OGGETTO`. L'email deve essere arrivata da meno di `ORE_MASSIME` ore. Se un filtro di Gmail la sposta, imposta `CARTELLA`. |
| `nessun link adatto` | Imposta `LINK_TESTO` con la scritta esatta del pulsante nell'email. |
| `Dopo il login c'è ancora il campo password` | Utente o password del sito sbagliati, oppure il sito usa un captcha. |
| `La pagina di login non è sul dominio SITO_DOMINIO` | Il link porta a un altro dominio (es. `login.altrosito.com`): metti quello in `SITO_DOMINIO`. Se non te l'aspettavi, l'email potrebbe essere falsa. |
| `Il link ha risposto con errore 4xx` | Il link è scaduto o già usato. |

## Limiti

- **Ritardo**: GitHub promette "ogni 5 minuti" ma nelle ore di punta può
  ritardare di 5–15 minuti. Se serve più velocità, usa il PC (sotto).
- **Captcha e codici via SMS** sul sito non possono essere compilati in
  automatico.
- Alcuni siti bloccano i server di GitHub: in quel caso usa il PC.
- Se il repository resta fermo per 60 giorni GitHub spegne i controlli
  automatici. Il workflow prova a tenersi attivo da solo ogni giorno, ma se mai
  lo trovassi spento basta premere **Enable workflow** nella scheda Actions.
- Lascia il repository **pubblico**: per i repository pubblici GitHub Actions è
  gratis senza limiti. Da privato, un controllo ogni 5 minuti supera i minuti
  gratuiti del mese: in quel caso cambia `*/5` in `*/30` nel file
  `.github/workflows/controlla-email.yml`.

## Sicurezza e privacy

- Il repository è **pubblico**: per questo tutti i dati vanno nei **Secrets**,
  che nessuno può leggere (nemmeno nei log, dove compaiono come `***`).
- Il programma **non scrive mai nei log** il link, l'oggetto o il mittente
  delle email, e non salva screenshot su GitHub.
- La password del sito viene scritta **solo** su pagine di `SITO_DOMINIO`:
  un'email falsa con un link a un sito-trappola non può rubarla.
- Il file `stato.json` (le email già gestite) contiene solo impronte
  anonime (hash), nessun dato leggibile.
- Per revocare tutto in un attimo: cancella la password per le app da
  <https://myaccount.google.com/apppasswords>.

---

## In alternativa: sul tuo PC

Serve [Python](https://www.python.org/downloads/) 3.10 o più recente.

```bash
pip install -r requirements.txt
python -m playwright install chromium
```

Copia `config.esempio.env` come `.env` e compilalo (stessi nomi della tabella
sopra). Poi:

```bash
python bot.py --prova      # mostra cosa farebbe, senza aprire niente
python bot.py              # controlla una volta
python bot.py --ogni 60    # resta acceso e controlla ogni minuto
```

Dopo ogni apertura, `debug/ultima_pagina.png` mostra l'ultima pagina vista dal
browser: utile per capire cosa è successo.

I test si lanciano con `python -m unittest`.
