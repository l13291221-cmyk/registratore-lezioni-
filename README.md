# Avvisi Dipendenti

Programma web per mandare un avviso via email a molti dipendenti (anche
60.000+) tramite **Brevo** e contare le risposte (es. quanti hanno risposto
"Sì", quanti "No", quanti non hanno risposto).

## Cosa fa

Una pagina con quattro campi:

1. **Oggetto** dell'email
2. **Cosa devo dire**: il testo del messaggio
3. **Opzioni di risposta**: una per riga (es. `Sì` / `No`). Ogni opzione
   diventa un pulsante nell'email.
4. **Quantità di persone** + **elenco email** incollato (va bene una per
   riga, separate da virgole o copiate da Excel). Il programma toglie i
   duplicati e blocca l'invio se il numero non coincide con la quantità.

Dopo l'invio si apre il riepilogo: email inviate, errori, risposte per
opzione con percentuali, e un pulsante per scaricare l'elenco completo
(email → risposta) da aprire in Excel.

Ogni dipendente ha link personali: le risposte vengono contate una per
persona (se cambia idea, vale l'ultima). Il clic apre una pagina di
conferma, così i filtri antivirus aziendali che aprono i link in automatico
non falsano i conteggi.

## Prima di usarlo con Brevo (per non finire in spam)

1. Crea un account su brevo.com. Il piano gratuito permette solo 300
   email al giorno: per 60.000 serve un piano a pagamento.
2. In Brevo, **Mittenti e domini** → aggiungi e **autentica il dominio
   aziendale** (record SPF, DKIM e DMARC nel DNS: lo fa chi gestisce il
   sito/le email aziendali). Senza questo passaggio le email finiscono in
   spam.
3. Avvisa l'IT aziendale: chiedi di mettere in whitelist Brevo sul server
   di posta, altrimenti il filtro interno può bloccare un invio così grande.
4. Genera una chiave API: **SMTP & API** → **API Keys**.

## Configurazione

Variabili d'ambiente:

| Variabile        | Cosa contiene                                              |
|------------------|------------------------------------------------------------|
| `BREVO_API_KEY`  | Chiave API Brevo. Se manca, l'app **simula** l'invio.      |
| `SENDER_EMAIL`   | Mittente, sul dominio autenticato (es. `avvisi@azienda.it`)|
| `SENDER_NAME`    | Nome mittente mostrato (es. `Ufficio Personale`)           |
| `BASE_URL`       | Indirizzo pubblico dove gira l'app (serve per i pulsanti)  |
| `ADMIN_PASSWORD` | Password per la pagina di invio e i risultati              |

## Avvio

```bash
pip install -r requirements.txt
export BREVO_API_KEY=... SENDER_EMAIL=avvisi@azienda.it \
       BASE_URL=https://avvisi.azienda.it ADMIN_PASSWORD=...
gunicorn -w 1 --threads 8 -b 0.0.0.0:5000 app:app
```

L'app deve stare su un server raggiungibile da internet (es. Render,
Railway, una VPS), altrimenti i pulsanti di risposta nelle email non
funzionano. Usa un solo worker (`-w 1`): l'invio gira in background nel
processo. I dati sono salvati nel file `avvisi.db` (SQLite).

Senza `BREVO_API_KEY` puoi provare tutto in locale con `python app.py`:
non parte nessuna email.
