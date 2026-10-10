# Ruota della Sorte

Ruota della fortuna con i numeri da 1 a 22.

- **Dal computer** il link mostra **solo la ruota** (da mostrare a schermo): si
  gira cliccandola e il pulsante **Ricomincia** (doppio clic) rimette tutti i
  numeri in ruota.
- **Dal telefono** lo stesso link apre il **telecomando**: fissi l'ordine in cui
  escono i numeri e cambi quanti numeri ci sono.
- L'ordine è fisso finché non lo cambi dal telefono: dopo ogni Ricomincia i
  numeri escono di nuovo uguali, anche se Render riavvia il servizio. Finito
  l'ordine, i numeri rimasti escono a caso. Un numero uscito non può uscire di
  nuovo fino al prossimo Ricomincia.

## Attivarla su Render (gratis)

Clic qui, accedi a Render e conferma con **Apply**:

https://render.com/deploy?repo=https://github.com/l13291221-cmyk/registratore-lezioni-/tree/claude/funny-noether-9coh7z

Se quel link non funziona: su Render **New → Blueprint**, scegli questo
repository e il branch `claude/funny-noether-9coh7z`, poi **Apply**. Le
impostazioni sono già nel file `render.yaml`.

Dopo un paio di minuti Render ti dà l'indirizzo, del tipo
`https://ruota-della-sorte.onrender.com`.

## Indirizzi

| Indirizzo | Cosa apre |
|---|---|
| `/` | ruota dal computer, telecomando dal telefono |
| `/ruota` | sempre la ruota (anche da tablet o telefono) |
| `/telecomando` | sempre il telecomando (anche dal computer) |

## Da sapere

- Sul piano gratuito Render spegne il servizio dopo 15 minuti senza visite. Il
  server si visita da solo ogni 10 minuti per restare acceso (consuma le 750 ore
  gratuite al mese di Render: bastano per un servizio sempre acceso).
- Se Render riavvia comunque il servizio (per esempio dopo un "Deploy"), l'ordine
  torna da solo appena si apre la ruota o il telecomando: entrambe le pagine ne
  tengono una copia (quella della ruota è cifrata e illeggibile). I numeri già
  usciti invece ripartono da zero, come dopo un Ricomincia.
- Chiunque apra il link da telefono vede il telecomando. Per proteggerlo, su
  Render aggiungi la variabile d'ambiente `PIN` (es. `4821`): il telecomando la
  chiederà una volta.
- La pagina della ruota non riceve mai l'ordine scelto, quindi non c'è modo di
  scoprirlo guardando il computer.

## Provarla sul PC

```bash
cd ruota
node server.js
```

Poi apri <http://localhost:3000/ruota> e <http://localhost:3000/telecomando>.
