# Registratore Lezioni

App Android che registra le lezioni **da sola**, secondo l'orario scolastico, e
salva ogni registrazione già divisa **per materia** con nome **materia + data +
giorno + ora**.

Pensata per girare su un telefono dedicato (Realme 12 Pro 5G, Android 14),
**completamente offline**: non ha il permesso Internet, usa solo il microfono e
la memoria del telefono.

---

## Cosa fa

- All'orario di inizio di una lezione **avvia la registrazione** da sola.
- All'orario di fine **la ferma** e salva il file.
- I file finiscono in una cartella per materia, con nome ordinabile:
  `Matematica/Matematica_2026-09-16_Lunedi_12-00.m4a`
- Pulsante per **registrazione manuale** (materia "Manuale") quando serve.
- Riprogramma tutto **dopo il riavvio** del telefono.

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

## Prima accensione: i 4 pulsanti in ordine

Apri l'app e premi in ordine:

1. **Concedi permessi** → microfono + notifiche.
2. **Consenti sveglie precise** → serve per partire all'orario esatto.
3. **Disattiva ottimizzazione batteria** → altrimenti Android spegne l'app.
4. **Avvio automatico (realme)** → nella lista, attiva questa app.

Poi, su realme/ColorOS, fai anche (una volta):
- **App recenti** → tieni premuta l'app → **lucchetto / blocca**, così il
  sistema non la chiude.
- Impostazioni batteria dell'app → consumo energia in background → **consenti**.

Infine premi **Attiva registrazione automatica**.

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
stato**.

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
