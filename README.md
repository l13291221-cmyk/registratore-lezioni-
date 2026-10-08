# Autoclicker per Windows

Autoclicker leggero (un solo `.exe` da ~90 KB, niente da installare) che clicca
nella posizione del cursore in **qualsiasi applicazione**, alla frequenza che
scegli oppure **il più veloce possibile**.

**Download diretto:**
https://github.com/l13291221-cmyk/registratore-lezioni-/releases/download/autoclicker-latest/autoclicker.exe

## Uso

1. Avvia `autoclicker.exe`.
2. Imposta i click al secondo (**0 = massimo**), il tasto del mouse e, se vuoi,
   dopo quanti click o quanti secondi fermarsi.
3. Porta il cursore sull'app da cliccare e premi **F6**.
4. **F6** di nuovo per fermare. **Esc** ferma sempre (stop di emergenza).

- F6 ed Esc funzionano da qualunque finestra, anche a schermo intero.
- Se il cursore è sopra la finestra dell'autoclicker i click vanno in pausa,
  così puoi usare il pulsante "Ferma" con il mouse.
- Lo stato mostra i click fatti e la velocità reale (click/s).
- Se apri un secondo autoclicker, chiude il primo e prende il suo posto.

## Avviarlo da Claude (o dal terminale)

Con Claude Code sul PC, in questa cartella, basta chiedere ad esempio
*"avvia l'autoclicker a 50 click al secondo per 10 secondi"*: Claude usa
`avvia-autoclicker.ps1`, che scarica l'exe se manca e lo lancia
(vedi `CLAUDE.md`). Puoi farlo anche tu:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File avvia-autoclicker.ps1 --cps 0
autoclicker.exe --cps 50 --durata 10 --avvia 5
autoclicker.exe --chiudi
```

oppure doppio clic su `avvia-autoclicker.bat`.

| Opzione | Significato |
| --- | --- |
| `--cps N` | click al secondo, `0` o `max` = il più veloce possibile (anche decimali, es. `0,5`) |
| `--tasto T` | `sinistro` (default), `destro`, `centrale` |
| `--limite N` | si ferma dopo N click |
| `--durata S` | si ferma dopo S secondi |
| `--avvia [S]` | parte da solo dopo S secondi (default 3), per darti il tempo di spostare il cursore |
| `--hotkey Fn` | tasto avvia/ferma da `F1` a `F24` (default `F6`) |
| `--primopiano` | finestra sempre in primo piano |
| `--chiudi` | chiude l'autoclicker aperto ed esce |
| `--aggiorna` | (solo `avvia-autoclicker.ps1`) riscarica l'ultima versione |

## Quanto è veloce

I click partono da un thread dedicato con `SendInput`, a blocchi di 64 per
chiamata; con un valore impostato la frequenza è tenuta con il timer ad alta
risoluzione (`QueryPerformanceCounter`), quindi 10, 100 o 1000 click/s sono
esatti. Con `0` (massimo) non c'è nessuna attesa: si arriva a decine di migliaia
di click al secondo, in pratica il limite è quanto l'app bersaglio riesce a
gestirne. Se l'app rallenta o continua a ricevere click anche dopo lo stop
(sta smaltendo l'arretrato), imposta un valore come 1000.

## Note

- Se l'app da cliccare è avviata **come amministratore**, avvia anche
  l'autoclicker come amministratore (Windows blocca i click da programmi con
  meno permessi).
- Alcuni giochi online vietano gli autoclicker o li bloccano con l'anti-cheat:
  usalo dove è consentito.
- L'exe non è firmato: se Windows mostra "Windows ha protetto il PC", clicca
  *Ulteriori informazioni → Esegui comunque*.

## Compilare

Il workflow `.github/workflows/build-autoclicker.yml` compila l'exe a ogni push
e lo pubblica nella Release `autoclicker-latest`. In locale:

```sh
sudo apt-get install mingw-w64   # oppure MSYS2 su Windows
./autoclicker/build.sh
```
