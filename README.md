# Registratore Lezioni

App Android che registra le lezioni **da sola**, secondo l'orario scolastico, e
salva ogni registrazione già divisa **per materia** con nome **materia + data +
giorno + ora**.

Pensata per girare su un telefono dedicato (Realme 12 Pro 5G, Android 14),
**completamente offline**: non ha il permesso Internet, usa solo il microfono e
la memoria del telefono.

---

## Cosa fa

- **La mattina premi "Avvia giornata"** (con l'app aperta), poi spegni lo
  schermo e metti via il telefono.
- Il **microfono resta sempre acceso** tutto il giorno: non viene mai spento e
  riacceso, quindi funziona anche a schermo spento.
- L'audio viene **diviso da solo in un file per lezione** seguendo l'orario:
  al cambio d'ora si chiude un file e se ne apre un altro.
- Negli intervalli / prima della prima ora l'audio viene scartato.
- Dopo l'**ultima lezione del giorno si ferma da solo**.
- I file finiscono in una cartella per materia, con nome ordinabile:
  `Matematica/Matematica_2026-09-16_Lunedi_12-00.m4a`
- Pulsante per **registrazione manuale** (materia "Manuale") quando serve.
- Nella notifica c'è il tasto **Ferma**.

> Perché così: da Android 14 un'app a schermo spento **non può riaccendere il
> microfono**. Prima l'app spegneva il microfono a fine lezione e non riusciva
> a riaccenderlo per la lezione dopo. Ora lo accende una volta sola.

---

## Come si ottiene l'app (APK)

Non serve installare niente sul computer: la compila **GitHub** da solo.

1. Vai nella tab **Actions** del repository su GitHub.
2. Apri l'ultima esecuzione **"Build APK"** (pallino verde = riuscita).
3. In fondo, sezione **Artifacts**, scarica **`registratore-lezioni-apk`**.
4. È uno zip: estrai il file `app-debug.apk`.

## Come si installa sul telefono

1. Passa l'`app-debug.apk` al telefono (USB, oppure caricalo dove preferisci).
2. Aprilo dal telefono: Android chiederà di **consentire installazione da
   origini sconosciute** → consenti.
3. Installa.

> È una app "debug" firmata automaticamente: va bene per uso personale via
> sideload. Non passa dal Play Store.

---

## Prima accensione: i 3 pulsanti in ordine

Apri l'app e premi in ordine:

1. **Concedi permessi** → microfono + notifiche.
2. **Disattiva ottimizzazione batteria** → altrimenti Android spegne l'app.
3. **Avvio automatico (realme)** → nella lista, attiva questa app.

Poi, su realme/ColorOS, fai anche (una volta):
- **App recenti** → tieni premuta l'app → **lucchetto / blocca**, così il
  sistema non la chiude.
- Impostazioni batteria dell'app → consumo energia in background → **consenti**.

Poi, ogni mattina: apri l'app → **Avvia giornata** → spegni lo schermo.

## Aggiornare l'app

Scarica il nuovo APK dal link diretto (Release **apk-latest**) e installalo
sopra quello vecchio. Dalla versione 2.0 l'APK è firmato sempre con la stessa
chiave, quindi si aggiorna senza disinstallare.

> **Solo la prima volta** che passi dalla versione 1.0 alla 2.0 Android
> potrebbe dire "app non installata" (la vecchia era firmata con una chiave
> diversa). In quel caso **prima copia le registrazioni** sul PC o condividile,
> poi disinstalla la vecchia e installa la nuova: disinstallando si cancellano
> anche i file registrati.

---

## L'orario

L'orario è nel file **`orario.json`**, che l'app copia alla prima apertura in:

```
Android/data/com.registratorelezioni/files/orario.json
```

Puoi modificarlo dal telefono (file manager) o via USB. Formato:

```json
{
  "lezioni": [
    { "giorno": "LUNEDI", "materia": "Matematica", "inizio": "08:00", "fine": "09:00" }
  ]
}
```

- `giorno`: LUNEDI, MARTEDI, MERCOLEDI, GIOVEDI, VENERDI, SABATO, DOMENICA
- `inizio` / `fine`: formato 24 ore `HH:MM`
- Ore consecutive della stessa materia = mettile come un unico blocco (un file).

Dopo aver modificato l'orario, apri l'app e premi **Ricarica orario / aggiorna
stato**. L'orario viene letto quando premi **Avvia giornata**: se lo cambi a
giornata già avviata, ferma e riavvia la giornata.

---

## Dove sono le registrazioni

```
Android/data/com.registratorelezioni/files/<Materia>/
```

Per copiarle sul PC: collega il telefono via USB (modalità "Trasferimento
file") e naviga in quella cartella.

---

## Sicurezza batteria

Il telefono è piegato: **caricalo su un piano, a vista, mai incustodito nello
zaino**. A scuola tienilo scollegato e in modalità aereo. Se noti la scocca che
si gonfia o calore anomalo in carica, smetti di usarlo.
