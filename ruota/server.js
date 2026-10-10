// Ruota della Sorte: server senza dipendenze per Render (piano gratuito).
// Dal computer "/" mostra solo la ruota; dal telefono mostra il telecomando.
// Dal telefono si fissa l'ordine di uscita: resta valido finché non lo si cambia dal telefono,
// anche dopo "Ricomincia" e anche se Render riavvia il servizio (vedi "sigillo" più sotto).

const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORTA = Number(process.env.PORT) || 3000;
const PIN = (process.env.PIN || "").trim();
const NUMERI_INIZIALI = 22;
const MAX_NUMERI = 99;
const URL_PUBBLICO = process.env.RENDER_EXTERNAL_URL || "";

// Chiave stabile tra un riavvio e l'altro (RENDER_SERVICE_ID non cambia mai per lo stesso servizio).
const CHIAVE = crypto
  .createHash("sha256")
  .update(process.env.SEGRETO || process.env.RENDER_SERVICE_ID || "ruota-della-sorte-locale")
  .digest();

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
  impostato: false, // false subito dopo un riavvio, finché l'ordine non viene ripristinato o reimpostato
  quando: 0,
};
let sigilloCorrente = null;

// L'ordine viaggia anche verso la ruota, ma cifrato: la pagina lo conserva senza poterlo leggere
// e lo rimanda al server se questo si è riavviato e l'ha perso.
function sigillo() {
  if (!sigilloCorrente) {
    const iv = crypto.randomBytes(12);
    const cifra = crypto.createCipheriv("aes-256-gcm", CHIAVE, iv);
    const testo = JSON.stringify({ ordine: stato.ordine, numeri: stato.numeri, quando: stato.quando });
    const dati = Buffer.concat([cifra.update(testo, "utf8"), cifra.final()]);
    sigilloCorrente = Buffer.concat([iv, cifra.getAuthTag(), dati]).toString("base64url");
  }
  return sigilloCorrente;
}

function apriSigillo(testo) {
  try {
    const b = Buffer.from(String(testo), "base64url");
    const decifra = crypto.createDecipheriv("aes-256-gcm", CHIAVE, b.subarray(0, 12));
    decifra.setAuthTag(b.subarray(12, 28));
    return JSON.parse(Buffer.concat([decifra.update(b.subarray(28)), decifra.final()]).toString("utf8"));
  } catch (e) {
    return null;
  }
}

function salvaImpostazioni(numeri, ordine) {
  stato.numeri = numeri;
  stato.usciti = stato.usciti.filter((n) => n <= numeri);
  stato.ordine = pulisciOrdine(ordine);
  stato.impostato = true;
  stato.quando = Date.now();
  sigilloCorrente = null;
}

function rimasti() {
  const fuori = new Set(stato.usciti);
  const lista = [];
  for (let n = 1; n <= stato.numeri; n++) if (!fuori.has(n)) lista.push(n);
  return lista;
}

// Quello che vede il computer: l'ordine solo cifrato, così la ruota non svela il trucco.
function vistaRuota() {
  return {
    rimasti: rimasti(),
    giro: stato.giro,
    sigillo: stato.impostato ? sigillo() : null,
    chiedeSigillo: !stato.impostato,
  };
}

function vistaTelecomando() {
  return {
    numeri: stato.numeri,
    rimasti: rimasti(),
    usciti: stato.usciti,
    ordine: stato.ordine,
    giro: stato.giro,
    impostato: stato.impostato,
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

  // Dopo un riavvio la ruota rimanda la copia cifrata dell'ordine: vale solo se il server l'ha perso.
  if (metodo === "POST" && percorso === "/api/ripristina") {
    const corpo = await leggiCorpo(req);
    const dati = stato.impostato ? null : apriSigillo(corpo.sigillo);
    if (dati && Number.isInteger(dati.numeri) && dati.numeri >= 2 && dati.numeri <= MAX_NUMERI) {
      salvaImpostazioni(dati.numeri, dati.ordine);
      stato.quando = Number(dati.quando) || stato.quando;
      console.log("Ordine ripristinato dalla ruota dopo un riavvio");
    }
    return inviaJson(res, 200, vistaRuota());
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
      salvaImpostazioni(stato.numeri, corpo.ordine);
      return inviaJson(res, 200, vistaTelecomando());
    }

    if (metodo === "POST" && percorso === "/api/impostazioni") {
      const corpo = await leggiCorpo(req);
      const numeri = Math.round(Number(corpo.numeri));
      if (!Number.isFinite(numeri) || numeri < 2 || numeri > MAX_NUMERI) {
        return inviaJson(res, 400, { errore: `Scegli un numero tra 2 e ${MAX_NUMERI}` });
      }
      // Dal telefono arriva anche l'ordine quando lo rimanda dopo un riavvio del server.
      salvaImpostazioni(numeri, Array.isArray(corpo.ordine) ? corpo.ordine : stato.ordine);
      return inviaJson(res, 200, vistaTelecomando());
    }
  }

  inviaJson(res, 404, { errore: "Pagina non trovata" });
});

server.listen(PORTA, "0.0.0.0", () => {
  console.log(`Ruota della Sorte pronta sulla porta ${PORTA}${PIN ? " (telecomando protetto da PIN)" : ""}`);
});

// Il piano gratuito di Render spegne il servizio dopo 15 minuti senza visite (e l'ordine in memoria
// andrebbe perso): una visita a se stesso ogni 10 minuti lo tiene acceso.
if (URL_PUBBLICO) {
  setInterval(() => {
    fetch(URL_PUBBLICO + "/salute").catch(() => {});
  }, 10 * 60 * 1000);
}
