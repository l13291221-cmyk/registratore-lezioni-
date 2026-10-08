# Autoclicker per Windows

Codice in `autoclicker/autoclicker.c` (C + Win32, compilato con MinGW-w64 da
`autoclicker/build.sh`). Il workflow GitHub Actions pubblica l'exe nella
Release `autoclicker-latest`.

## Avviare l'autoclicker su richiesta dell'utente

Funziona solo su Windows (sessione Claude Code sul PC dell'utente, non nel
cloud). Lancia sempre tramite lo script, che scarica l'exe se manca:

```
powershell -NoProfile -ExecutionPolicy Bypass -File avvia-autoclicker.ps1 [opzioni]
```

Traduci la richiesta in opzioni:

- velocità: `--cps N` (`0` = massimo, il default)
- tasto: `--tasto sinistro|destro|centrale`
- fermati dopo: `--limite N` (click) e/o `--durata S` (secondi)
- partenza automatica: `--avvia S` (secondi di attesa per spostare il cursore);
  senza, l'utente parte con F6
- `--primopiano`, `--hotkey F1..F24`
- chiudere/fermare: `--chiudi`; aggiornare l'exe: `--aggiorna`

Esempio: "clicca 50 volte al secondo per 10 secondi tra 5 secondi" →
`--cps 50 --durata 10 --avvia 5`.

Dopo l'avvio ricorda all'utente: cursore sull'app da cliccare, F6 avvia/ferma,
Esc ferma sempre.
