# AnimeFit

App Android che ti dà **un allenamento ogni giorno** per avvicinarti al fisico di
un **personaggio anime** o di un **personaggio famoso** scelto da te, e che **ogni
settimana ti fa fare una foto** per misurare i progressi.

Grafica: **sfondo nero**, bordi cliccabili **neon** dai colori accesi.

---

## Cosa fa

- **26 personaggi** tra cui scegliere (filtri Anime / Famosi, Uomo / Donna):
  Saitama, Goku, Vegeta, Levi, Zoro, Luffy, Baki, Toji, All Might, Rock Lee,
  Ippo, Yuji, Mikasa, Maki, Erza, Brad Pitt (Fight Club), Chris Hemsworth (Thor),
  Bruce Lee, Cristiano Ronaldo, Hugh Jackman (Wolverine), Henry Cavill
  (Superman), Zac Efron, Sylvester Stallone (Rocky), Dwayne Johnson, Gal Gadot,
  Scarlett Johansson.
  Ognuno ha proporzioni obiettivo, zone chiave, uno stile di allenamento e un
  **allenamento speciale** (es. la routine di Saitama: piegamenti, addominali,
  squat e corsa che crescono fino a 100/100/100/10 km).
- **Allenamento del giorno** generato in base a personaggio, livello
  (principiante / intermedio / avanzato), attrezzatura (casa, casa + sbarra,
  palestra) e settimana: le ripetizioni crescono, ogni 4ª settimana è di scarico,
  la domenica è recupero attivo. Serie da spuntare, timer di recupero, contatore
  dei giorni di fila.
- **Foto settimanale**: la prima foto è il punto di partenza; dopo 7 giorni l'app
  chiede la nuova foto (avviso all'apertura + notifica). La fotocamera ha una
  **sagoma guida** e il **timer** (5/10/15 s) per scattare da soli.
- **Confronto con la prima foto**: foto affiancate con le linee delle misure,
  **prima/dopo con tendina trascinabile**, **sagome sovrapposte** (ciano = prima,
  magenta = ora) ed elenco di cosa è cambiato (spalle +5%, vita -3%...).
- **Confronto con il personaggio**: la tua figura colorata zona per zona
  (rosso = lontano, verde = raggiunto) accanto a quella del personaggio,
  **percentuale di raggiungimento** complessiva e per zona, e la sezione
  **"Dove allenarti di più"** con spiegazione ed esercizi consigliati.
  I primi due punti deboli ricevono **esercizi extra (★)** nella scheda.
- **Storico** con grafico della percentuale nel tempo.

---

## Come funziona l'analisi della foto

Tutto avviene **sul telefono, offline** (Google ML Kit, modelli inclusi
nell'app): le foto non escono mai dal telefono.

1. ML Kit trova i punti del corpo (spalle, gomiti, anche, ginocchia...) e separa
   la persona dallo sfondo.
2. L'app misura le larghezze di spalle, torace, vita, fianchi e lo spessore di
   braccia, avambracci, cosce e polpacci, divise per la lunghezza del busto
   (così non conta la distanza dalla fotocamera). Stima anche la definizione
   degli addominali dal contrasto della pelle.
3. Ogni zona viene confrontata con le proporzioni del personaggio: 0% = fisico
   esile non allenato, 100% = proporzioni del personaggio. Le zone chiave del
   personaggio pesano di più. Se la vita è larga, braccia e gambe vengono
   "ridotte" perché il grasso le gonfia senza che sia muscolo.

Sono **stime**: per risultati affidabili scatta ogni settimana nelle stesse
condizioni e guarda la tendenza, non il singolo numero. Per i personaggi dal
fisico esagerato (Goku, Baki, All Might...) il 100% è una versione umana
raggiungibile.

Per rispetto del copyright l'app **non contiene immagini** dei personaggi: i
fisici sono disegnati come figure neon dalle proporzioni. Nella scheda del
personaggio puoi **aggiungere una tua immagine** dalla galleria, che verrà
mostrata accanto alla tua foto.

### La foto perfetta

- Telefono in verticale all'altezza della pancia, a circa 2-2,5 metri.
- Corpo intero, di fronte, **braccia aperte a circa 45°** (come una A), piedi
  alla larghezza delle spalle.
- Torso nudo o abbigliamento aderente, sfondo chiaro e uniforme, buona luce.
- Ogni settimana stesso posto, stessa luce, stessa distanza.

---

## Come si ottiene l'app (APK)

La compila **GitHub** da solo a ogni modifica (tab **Actions**, "Build APK").

**Link diretto** all'ultimo APK:

```
https://github.com/l13291221-cmyk/registratore-lezioni-/releases/download/animefit-latest/AnimeFit.apk
```

In alternativa: tab **Actions** → ultima esecuzione "Build APK" → sezione
**Artifacts** → `animefit-apk` (è uno zip con dentro `AnimeFit.apk`).

## Come si installa

1. Apri `AnimeFit.apk` dal telefono.
2. Android chiede di **consentire l'installazione da origini sconosciute** →
   consenti.
3. Installa e apri. Concedi fotocamera e notifiche quando richiesto.

> È una app "debug" firmata automaticamente: va bene per uso personale. Non passa
> dal Play Store.

---

## Struttura del codice

```
app/src/main/java/com/animefit/
  catalog/     personaggi, esercizi, zone del corpo
  training/    generatore della scheda giornaliera, regole (foto, serie di giorni)
  analysis/    misure dalla foto (MaskMeasurer), ML Kit (BodyAnalyzer), punteggi
  data/        modelli e salvataggio (JSON nella memoria privata dell'app)
  notify/      promemoria giornaliero (WorkManager)
  ui/          tema neon, figure, schermate (Jetpack Compose)
```

Test automatici: `./gradlew testDebugUnitTest` (misure su una sagoma sintetica,
punteggi, schede per tutti i personaggi/livelli/attrezzature).

---

Prima di iniziare un nuovo allenamento, se hai problemi di salute o dubbi,
chiedi a un medico. L'app non è affiliata ai personaggi o alle persone citate: i
nomi servono solo come obiettivo di allenamento.
