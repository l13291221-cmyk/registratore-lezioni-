# Ruota della Sorte

Ruota della fortuna con i numeri da 1 a 22.

- **Dal computer** il link mostra **solo la ruota** (da mostrare a schermo): si
  gira cliccandola e il pulsante **Ricomincia** (doppio clic) rimette tutti i
  numeri in ruota.
- **Dal telefono** lo stesso link apre il **telecomando**: fissi l'ordine in cui
  escono i numeri e cambi quanti numeri ci sono.
- L'ordine è fisso: dopo ogni Ricomincia i numeri escono di nuovo uguali. Finito
  l'ordine, escono a caso. Un numero uscito non può uscire di nuovo.

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

- Sul piano gratuito Render spegne il servizio dopo 15 minuti senza visite: la
  prima apertura dopo una pausa può impiegare circa un minuto. Apri la ruota un
  paio di minuti prima di iniziare. Finché una pagina è aperta, resta acceso.
- Quando Render spegne o riavvia il servizio, la ruota riparte da zero (numeri da
  1 a 22, nessuno uscito, nessun ordine scelto).
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
