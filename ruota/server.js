// Ruota della Sorte: server senza dipendenze per Render (piano gratuito).
// Dal computer "/" mostra solo la ruota; dal telefono mostra il telecomando.
// Dal telefono si fissa l'ordine di uscita: resta valido anche dopo "Ricomincia".
// Lo stato vive in memoria: si azzera quando Render spegne o riavvia il servizio.

const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORTA = Number(process.env.PORT) || 3000;
const PIN = (process.env.PIN || "").trim();
const NUMERI_INIZIALI = 22;
const MAX_NUMERI = 99;

const pagine = {
  ruota: fs.readFileSync(path.join(__dirname, "public", "ruota.html")),
  telecomando: fs.readFileSync(path.join(__dirname, "public", "telecomando.html")),
};

const stato = {
  numeri: NUMERI_INIZIALI,
  usciti: [],
  ordine: [],
  giro: null,
  contatore: 0,
};

function rimasti() {
  const fuori = new Set(stato.usciti);
  const lista = [];
  for (let n = 1; n <= stato.numeri; n++) if (!fuori.has(n)) lista.push(n);
  return lista;
}

// Quello che vede il computer: niente ordine, così la ruota non svela il trucco.
function vistaRuota() {
  return { rimasti: rimasti(), giro: stato.giro };
}

function vistaTelecomando() {
  return {
    numeri: stato.numeri,
    rimasti: rimasti(),
    usciti: stato.usciti,
    ordine: stato.ordine,
    giro: stato.giro,
  };
}

// Il primo numero dell'ordine fissato che non è ancora uscito; finito l'ordine, uno a caso.
// L'ordine non si consuma: dopo "Ricomincia" i numeri escono di nuovo uguali.
function prossimoDellOrdine() {
  const fuori = new Set(stato.usciti);
  const n = stato.ordine.find((x) => x <= stato.numeri && !fuori.has(x));
  return n === undefined ? null : n;
}

function gira() {
  const r = rimasti();
  if (!r.length) return null;
  let numero = prossimoDellOrdine();
  if (numero === null) numero = r[crypto.randomInt(r.length)];
  stato.usciti.push(numero);
  stato.giro = { id: ++stato.contatore, numero, ruota: r, quando: Date.now() };
  return stato.giro;
}

function pulisciOrdine(lista) {
  const visti = new Set();
  const ordine = [];
  for (const valore of Array.isArray(lista) ? lista : []) {
    const n = Number(valore);
    if (Number.isInteger(n) && n >= 1 && n <= stato.numeri && !visti.has(n)) { visti.add(n); ordine.push(n); }
  }
  return ordine;
}

function eTelefono(req) {
  return /Mobi|Android|iPhone|iPod|iPad|Windows Phone/i.test(req.headers["user-agent"] || "");
}

function inviaJson(res, codice, dati) {
  res.writeHead(codice, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
  res.end(JSON.stringify(dati));
}

function inviaPagina(res, nome) {
  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-cache" });
  res.end(pagine[nome]);
}

function leggiCorpo(req) {
  return new Promise((risolvi) => {
    let dati = "";
    req.on("data", (pezzo) => {
      dati += pezzo;
      if (dati.length > 10000) req.destroy();
    });
    req.on("end", () => {
      try { risolvi(dati ? JSON.parse(dati) : {}); } catch (e) { risolvi({}); }
    });
    req.on("error", () => risolvi({}));
  });
}

function pinValido(req) {
  return !PIN || req.headers["x-pin"] === PIN;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  const percorso = url.pathname.replace(/\/+$/, "") || "/";
  const metodo = req.method;

  if (metodo === "GET" && percorso === "/") return inviaPagina(res, eTelefono(req) ? "telecomando" : "ruota");
  if (metodo === "GET" && percorso === "/ruota") return inviaPagina(res, "ruota");
  if (metodo === "GET" && percorso === "/telecomando") return inviaPagina(res, "telecomando");
  if (metodo === "GET" && percorso === "/salute") return inviaJson(res, 200, { ok: true });

  if (metodo === "GET" && percorso === "/api/ruota") return inviaJson(res, 200, vistaRuota());

  if (metodo === "POST" && percorso === "/api/gira") {
    const giro = gira();
    if (!giro) return inviaJson(res, 409, { errore: "Sono usciti tutti i numeri", ...vistaRuota() });
    return inviaJson(res, 200, { giro, ...vistaRuota() });
  }

  // Anche dalla ruota si può ricominciare: tutti i numeri tornano in ruota.
  if (metodo === "POST" && percorso === "/api/ricomincia") {
    stato.usciti = [];
    stato.giro = null;
    return inviaJson(res, 200, vistaRuota());
  }

  if (percorso.startsWith("/api/")) {
    if (!pinValido(req)) return inviaJson(res, 401, { errore: "PIN sbagliato" });

    if (metodo === "GET" && percorso === "/api/telecomando") return inviaJson(res, 200, vistaTelecomando());

    if (metodo === "POST" && percorso === "/api/ordine") {
      const corpo = await leggiCorpo(req);
      stato.ordine = pulisciOrdine(corpo.ordine);
      return inviaJson(res, 200, vistaTelecomando());
    }

    if (metodo === "POST" && percorso === "/api/impostazioni") {
      const corpo = await leggiCorpo(req);
      const numeri = Math.round(Number(corpo.numeri));
      if (!Number.isFinite(numeri) || numeri < 2 || numeri > MAX_NUMERI) {
        return inviaJson(res, 400, { errore: `Scegli un numero tra 2 e ${MAX_NUMERI}` });
      }
      stato.numeri = numeri;
      stato.usciti = stato.usciti.filter((n) => n <= numeri);
      stato.ordine = pulisciOrdine(stato.ordine);
      return inviaJson(res, 200, vistaTelecomando());
    }
  }

  inviaJson(res, 404, { errore: "Pagina non trovata" });
});

server.listen(PORTA, "0.0.0.0", () => {
  console.log(`Ruota della Sorte pronta sulla porta ${PORTA}${PIN ? " (telecomando protetto da PIN)" : ""}`);
});
