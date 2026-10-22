package com.animefit.analysis

import com.animefit.data.Measure
import com.animefit.data.Pt
import com.animefit.data.Seg
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Sagoma della persona: true dove c'è il corpo. */
class BodyMask(val width: Int, val height: Int, val data: BooleanArray) {
    fun at(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && data[y * width + x]
    fun at(x: Float, y: Float): Boolean = at(x.roundToInt(), y.roundToInt())
}

/** Nomi dei punti del corpo usati dall'analisi. */
object Lm {
    const val L_SHOULDER = "lShoulder"
    const val R_SHOULDER = "rShoulder"
    const val L_ELBOW = "lElbow"
    const val R_ELBOW = "rElbow"
    const val L_WRIST = "lWrist"
    const val R_WRIST = "rWrist"
    const val L_HIP = "lHip"
    const val R_HIP = "rHip"
    const val L_KNEE = "lKnee"
    const val R_KNEE = "rKnee"
    const val L_ANKLE = "lAnkle"
    const val R_ANKLE = "rAnkle"
}

sealed class MeasureOutcome {
    data class Ok(
        val measures: Map<Measure, Double>,
        val segments: List<Seg>,
        val torsoPx: Float,
        val shoulderMid: Pt,
        val hipMid: Pt,
        val warnings: List<String>,
    ) : MeasureOutcome()

    data class Error(val message: String) : MeasureOutcome()
}

/**
 * Ricava le misure del fisico da sagoma + punti del corpo di una foto frontale
 * in piedi, braccia aperte a "A". Tutte le larghezze sono divise per la
 * lunghezza del busto (centro spalle - centro anche) così da non dipendere
 * dalla distanza dalla fotocamera.
 *
 * [luminance] (0..255, stessa dimensione della sagoma) serve solo alla stima
 * della definizione degli addominali; può mancare.
 */
class MaskMeasurer(
    private val mask: BodyMask,
    private val lm: Map<String, Pt>,
    private val luminance: IntArray? = null,
) {
    private val warnings = mutableListOf<String>()
    private val segments = mutableListOf<Seg>()
    private var torso = 1f
    private var gap = 2
    private lateinit var sm: Pt
    private lateinit var hm: Pt

    fun run(): MeasureOutcome {
        val ls = lm[Lm.L_SHOULDER]
        val rs = lm[Lm.R_SHOULDER]
        val lh = lm[Lm.L_HIP]
        val rh = lm[Lm.R_HIP]
        if (ls == null || rs == null || lh == null || rh == null) {
            return MeasureOutcome.Error(
                "Non riesco a vedere bene spalle e fianchi. Inquadra tutto il corpo, di fronte, con una buona luce."
            )
        }
        sm = mid(ls, rs)
        hm = mid(lh, rh)
        torso = dist(sm, hm)
        if (torso < 0.08f * mask.height) {
            return MeasureOutcome.Error(
                "Sei troppo lontano: avvicinati finché il corpo riempie quasi tutta l'altezza della foto."
            )
        }
        val tilt = Math.toDegrees(kotlin.math.atan2(abs(hm.x - sm.x), hm.y - sm.y).toDouble())
        if (hm.y <= sm.y || tilt > 15) {
            return MeasureOutcome.Error("Tieni il telefono dritto in verticale e stai in piedi diritto.")
        }
        if (dist(ls, rs) < 0.45f * torso) {
            return MeasureOutcome.Error("Sembri girato di lato: mettiti di fronte alla fotocamera.")
        }
        armProblem(ls, lm[Lm.L_ELBOW])?.let { return MeasureOutcome.Error(it) }
        armProblem(rs, lm[Lm.R_ELBOW])?.let { return MeasureOutcome.Error(it) }
        if (!mask.at(sm.x, sm.y + 0.3f * torso)) {
            return MeasureOutcome.Error(
                "Non riesco a separarti dallo sfondo. Mettiti davanti a una parete chiara e uniforme, con più luce."
            )
        }
        gap = maxOf(2, (0.012f * torso).roundToInt())

        val out = linkedMapOf<Measure, Double>()

        // Braccia e avambracci: spessore perpendicolare all'asse dell'osso.
        val armL = limbThickness(Measure.BRACCIO, ls, lm[Lm.L_ELBOW], 0.45f, null)
        val armR = limbThickness(Measure.BRACCIO, rs, lm[Lm.R_ELBOW], 0.45f, null)
        avg(armL, armR)?.let { out[Measure.BRACCIO] = (it / torso).toDouble() }
        if (armL == null && armR == null && (lm[Lm.L_ELBOW] != null || lm[Lm.R_ELBOW] != null)) {
            warnings += "Braccia troppo vicine al corpo: non ho potuto misurarle."
        }
        val foreL = limbThickness(Measure.AVAMBRACCIO, lm[Lm.L_ELBOW], lm[Lm.L_WRIST], 0.35f, null)
        val foreR = limbThickness(Measure.AVAMBRACCIO, lm[Lm.R_ELBOW], lm[Lm.R_WRIST], 0.35f, null)
        avg(foreL, foreR)?.let { out[Measure.AVAMBRACCIO] = (it / torso).toDouble() }

        // Spalle: larghezza esterna dei deltoidi all'altezza delle articolazioni.
        val shoulderY = sm.y + 0.02f * torso
        val shoulders = medianRowSpan(shoulderY, Measure.SPALLE, null, null)
        shoulders?.let { out[Measure.SPALLE] = (it / torso).toDouble() }

        // Torace: sotto le ascelle, senza sconfinare nelle braccia.
        val chestY = sm.y + 0.28f * torso
        val (chestMin, chestMax) = armLimits(chestY, armL, armR)
        medianRowSpan(chestY, Measure.TORACE, chestMin, chestMax)?.let { out[Measure.TORACE] = (it / torso).toDouble() }

        // Vita: punto più stretto tra costole e anche.
        val waistPx = waist()
        waistPx?.let { out[Measure.VITA] = (it / torso).toDouble() }

        // Fianchi.
        medianRowSpan(hm.y, Measure.FIANCHI, null, null)?.let { out[Measure.FIANCHI] = (it / torso).toDouble() }

        // Cosce e polpacci: il lato interno si ferma alla linea centrale tra le gambe.
        val legMid = hm.x
        val thighL = limbThickness(Measure.COSCIA, lh, lm[Lm.L_KNEE], 0.30f, legMid)
        val thighR = limbThickness(Measure.COSCIA, rh, lm[Lm.R_KNEE], 0.30f, legMid)
        avg(thighL, thighR)?.let { out[Measure.COSCIA] = (it / torso).toDouble() }
        val kl = lm[Lm.L_KNEE]
        val kr = lm[Lm.R_KNEE]
        val calfMid = if (kl != null && kr != null) (kl.x + kr.x) / 2f else legMid
        val calfL = limbThickness(Measure.POLPACCIO, kl, lm[Lm.L_ANKLE], 0.30f, calfMid)
        val calfR = limbThickness(Measure.POLPACCIO, kr, lm[Lm.R_ANKLE], 0.30f, calfMid)
        avg(calfL, calfR)?.let { out[Measure.POLPACCIO] = (it / torso).toDouble() }
        if (kl == null && kr == null) {
            warnings += "Non vedo le gambe intere: inquadra anche ginocchia e piedi."
        } else if (lm[Lm.L_ANKLE] == null && lm[Lm.R_ANKLE] == null) {
            warnings += "Non vedo le caviglie: i polpacci non sono stati misurati."
        }

        if (waistPx != null) definition(waistPx)?.let { out[Measure.DEFINIZIONE] = it }

        if (out[Measure.SPALLE] == null || out[Measure.VITA] == null) {
            return MeasureOutcome.Error(
                "La sagoma del busto non è chiara. Usa uno sfondo uniforme, torso nudo o maglia aderente, e più luce."
            )
        }
        return MeasureOutcome.Ok(out, segments.toList(), torso, sm, hm, warnings.toList())
    }

    /** Controlla che il braccio sia aperto a "A" (né lungo il corpo né alzato). */
    private fun armProblem(shoulder: Pt, elbow: Pt?): String? {
        elbow ?: return null
        val down = Pt(hm.x - sm.x, hm.y - sm.y)
        val arm = Pt(elbow.x - shoulder.x, elbow.y - shoulder.y)
        val cos = (down.x * arm.x + down.y * arm.y) / (len(down) * len(arm)).coerceAtLeast(1e-3f)
        val angle = Math.toDegrees(acos(cos.coerceIn(-1f, 1f)).toDouble())
        return when {
            angle < 18 -> "Allarga le braccia di circa 45°, come una A: non devono toccare il corpo."
            angle > 80 -> "Abbassa un po' le braccia: devono stare a circa 45° dal corpo, come una A."
            else -> null
        }
    }

    private fun cx(y: Float): Float = sm.x + (hm.x - sm.x) * (y - sm.y) / (hm.y - sm.y)

    /** Larghezza mediana della sagoma su alcune righe attorno a [y]. */
    private fun medianRowSpan(y: Float, measure: Measure, minX: Float?, maxX: Float?): Float? {
        val half = maxOf(1, (0.015f * torso).roundToInt())
        val spans = (-half..half).mapNotNull { dy -> rowSpan((y + dy).roundToInt(), minX, maxX) }
        if (spans.size < half) return null
        val sorted = spans.sortedBy { it.second - it.first }
        val m = sorted[sorted.size / 2]
        segments += Seg(measure, Pt(m.first, y), Pt(m.second, y))
        return m.second - m.first + 1
    }

    /** Tratto continuo di sagoma sulla riga [y] che contiene il centro del corpo. */
    private fun rowSpan(y: Int, minX: Float?, maxX: Float?): Pair<Float, Float>? {
        if (y !in 0 until mask.height) return null
        val lo = maxOf(0, ceil(minX ?: 0f).toInt())
        val hi = minOf(mask.width - 1, floor(maxX ?: (mask.width - 1).toFloat()).toInt())
        val c = cx(y.toFloat()).roundToInt()
        val search = (0.06f * torso).roundToInt()
        var start = -1
        for (d in 0..search) {
            if (c + d in lo..hi && mask.at(c + d, y)) { start = c + d; break }
            if (c - d in lo..hi && mask.at(c - d, y)) { start = c - d; break }
        }
        if (start < 0) return null
        var left = start
        var x = start
        while (x > lo) {
            x--
            if (mask.at(x, y)) left = x else if (left - x > gap) break
        }
        var right = start
        x = start
        while (x < hi) {
            x++
            if (mask.at(x, y)) right = x else if (x - right > gap) break
        }
        return left.toFloat() to right.toFloat()
    }

    /** Limiti orizzontali del torace: il bordo interno delle braccia. */
    private fun armLimits(y: Float, armL: Float?, armR: Float?): Pair<Float?, Float?> {
        val center = cx(y)
        var minX: Float? = null
        var maxX: Float? = null
        listOf(
            Triple(lm[Lm.L_SHOULDER], lm[Lm.L_ELBOW], armL),
            Triple(lm[Lm.R_SHOULDER], lm[Lm.R_ELBOW], armR),
        ).forEach { (s, e, thick) ->
            if (s == null || e == null || abs(e.y - s.y) < 1f) return@forEach
            val axisX = s.x + (e.x - s.x) * (y - s.y) / (e.y - s.y)
            val inner = (thick ?: 0f) / 2f
            if (axisX < center) {
                val lim = axisX + inner
                if (lim < center) minX = maxOf(minX ?: lim, lim)
            } else {
                val lim = axisX - inner
                if (lim > center) maxX = minOf(maxX ?: lim, lim)
            }
        }
        return minX to maxX
    }

    private fun waist(): Float? {
        val from = (sm.y + 0.55f * torso).roundToInt()
        val to = (sm.y + 0.88f * torso).roundToInt()
        val rows = (from..to).map { y -> y to rowSpan(y, null, null) }
        val widths = rows.map { (_, s) -> s?.let { it.second - it.first + 1 } }
        // Media mobile su 5 righe per non farsi ingannare dal rumore della sagoma.
        var best: Float? = null
        var bestIdx = -1
        for (i in widths.indices) {
            val window = (maxOf(0, i - 2)..minOf(widths.lastIndex, i + 2)).mapNotNull { widths[it] }
            if (window.size < 3) continue
            val w = window.sorted()[window.size / 2]
            if (best == null || w < best) { best = w; bestIdx = i }
        }
        if (bestIdx >= 0) {
            val (y, span) = rows[bestIdx]
            if (span != null) {
                val c = (span.first + span.second) / 2f
                segments += Seg(Measure.VITA, Pt(c - best!! / 2f, y.toFloat()), Pt(c + best / 2f, y.toFloat()))
            }
        }
        return best
    }

    /**
     * Spessore di un arto misurato perpendicolarmente all'asse [a]→[b], nel punto
     * a frazione [at]. Se [midlineX] non è null il lato interno non la oltrepassa
     * (serve per le gambe che si toccano).
     */
    private fun limbThickness(measure: Measure, a: Pt?, b: Pt?, at: Float, midlineX: Float?): Float? {
        if (a == null || b == null) return null
        val l = dist(a, b)
        if (l < 0.2f * torso) return null
        val d = Pt((b.x - a.x) / l, (b.y - a.y) / l)
        val n = Pt(-d.y, d.x)
        val p = Pt(a.x + (b.x - a.x) * at, a.y + (b.y - a.y) * at)
        val side = if (midlineX == null) 0f else Math.signum(p.x - midlineX)
        fun allowed(x: Float): Boolean = midlineX == null || side == 0f || (x - midlineX) * side >= 0f

        val maxD = 0.25f * torso
        val search = 0.06f * torso
        var t0: Float? = null
        var s = 0f
        while (s <= search) {
            if (inside(p, n, s, ::allowed)) { t0 = s; break }
            if (inside(p, n, -s, ::allowed)) { t0 = -s; break }
            s += 1f
        }
        val start = t0 ?: return null
        var clamped = false
        fun expand(dir: Float): Float {
            var last = start
            var t = start
            while (true) {
                t += dir
                if (abs(t) > maxD) { clamped = clamped || abs(last - t) <= gap; break }
                val px = p.x + n.x * t
                if (!allowed(px)) break
                if (inside(p, n, t, ::allowed)) last = t else if (abs(t - last) > gap) break
            }
            return last
        }
        val plus = expand(1f)
        val minus = expand(-1f)
        if (clamped) return null
        segments += Seg(
            measure,
            Pt(p.x + n.x * minus, p.y + n.y * minus),
            Pt(p.x + n.x * plus, p.y + n.y * plus),
        )
        return plus - minus + 1
    }

    private fun inside(p: Pt, n: Pt, t: Float, allowed: (Float) -> Boolean): Boolean {
        val x = p.x + n.x * t
        val y = p.y + n.y * t
        return allowed(x) && mask.at(x, y)
    }

    /**
     * Stima della definizione addominale: contrasto locale (bordi) sulla zona
     * degli addominali, normalizzato per la luminosità. 0 = liscio, 1 = scolpito.
     */
    private fun definition(waistPx: Float): Double? {
        val lum = luminance ?: return null
        val top = sm.y + 0.42f * torso
        val bottom = sm.y + 0.80f * torso
        val cols = 24
        val rows = 32
        val halfW = 0.30f * waistPx
        val cell = Array(rows) { DoubleArray(cols) }
        val valid = Array(rows) { BooleanArray(cols) }
        for (r in 0 until rows) {
            val y0 = top + (bottom - top) * r / rows
            val y1 = top + (bottom - top) * (r + 1) / rows
            val c = cx((y0 + y1) / 2f)
            for (k in 0 until cols) {
                val x0 = c - halfW + 2 * halfW * k / cols
                val x1 = c - halfW + 2 * halfW * (k + 1) / cols
                var sum = 0.0
                var count = 0
                var total = 0
                for (yy in y0.toInt() until maxOf(y0.toInt() + 1, y1.toInt())) {
                    for (xx in x0.toInt() until maxOf(x0.toInt() + 1, x1.toInt())) {
                        if (xx !in 0 until mask.width || yy !in 0 until mask.height) continue
                        total++
                        if (mask.at(xx, yy)) {
                            sum += lum[yy * mask.width + xx]
                            count++
                        }
                    }
                }
                if (total > 0 && count * 2 >= total) {
                    cell[r][k] = sum / count
                    valid[r][k] = true
                }
            }
        }
        var magSum = 0.0
        var lumSum = 0.0
        var n = 0
        for (r in 1 until rows - 1) for (k in 1 until cols - 1) {
            var ok = true
            for (dr in -1..1) for (dk in -1..1) if (!valid[r + dr][k + dk]) ok = false
            if (!ok) continue
            val gx = (cell[r - 1][k + 1] + 2 * cell[r][k + 1] + cell[r + 1][k + 1]) -
                (cell[r - 1][k - 1] + 2 * cell[r][k - 1] + cell[r + 1][k - 1])
            val gy = (cell[r + 1][k - 1] + 2 * cell[r + 1][k] + cell[r + 1][k + 1]) -
                (cell[r - 1][k - 1] + 2 * cell[r - 1][k] + cell[r - 1][k + 1])
            magSum += hypot(gx, gy)
            lumSum += cell[r][k]
            n++
        }
        if (n < (rows - 2) * (cols - 2) / 3) return null
        val raw = (magSum / n) / (lumSum / n + 20.0)
        return ((raw - 0.06) / 0.22).coerceIn(0.0, 1.0)
    }

    private fun avg(a: Float?, b: Float?): Float? = when {
        a != null && b != null -> (a + b) / 2f
        else -> a ?: b
    }

    companion object {
        fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
        private fun len(p: Pt) = hypot(p.x, p.y)
    }
}
