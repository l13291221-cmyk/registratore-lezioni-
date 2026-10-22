package com.animefit

import com.animefit.analysis.BodyMask
import com.animefit.analysis.Lm
import com.animefit.analysis.MaskMeasurer
import com.animefit.analysis.MeasureOutcome
import com.animefit.data.Measure
import com.animefit.data.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Corpo sintetico con misure note: busto lungo 200 px (spalle y=200, anche y=400),
 * braccia aperte a 40°, gambe leggermente divaricate.
 */
class MaskMeasurerTest {
    private val w = 600
    private val h = 1000
    private val torso = 200.0

    private fun body(armAngleDeg: Double = 40.0): Pair<BodyMask, Map<String, Pt>> {
        val data = BooleanArray(w * h)
        fun set(x: Int, y: Int) { if (x in 0 until w && y in 0 until h) data[y * w + x] = true }

        // Busto: spalle (deltoidi) 200 px, torace 150, vita 114, fianchi 140.
        for (y in 180..430) {
            val hw = when {
                y <= 230 -> 100
                y <= 300 -> 75
                y <= 380 -> 57
                else -> 70
            }
            for (x in 300 - hw..300 + hw) set(x, y)
        }
        // Testa.
        for (y in 80..180) for (x in 260..340) set(x, y)

        val lm = mutableMapOf<String, Pt>()
        lm[Lm.L_SHOULDER] = Pt(215f, 200f)
        lm[Lm.R_SHOULDER] = Pt(385f, 200f)
        lm[Lm.L_HIP] = Pt(260f, 400f)
        lm[Lm.R_HIP] = Pt(340f, 400f)

        val a = Math.toRadians(armAngleDeg)
        for (side in listOf(-1, 1)) {
            val s = if (side < 0) lm[Lm.L_SHOULDER]!! else lm[Lm.R_SHOULDER]!!
            val e = Pt((s.x + side * 120 * sin(a)).toFloat(), (s.y + 120 * cos(a)).toFloat())
            val wr = Pt((e.x + side * 110 * sin(a)).toFloat(), (e.y + 110 * cos(a)).toFloat())
            capsule(data, s, e, 22.0)
            capsule(data, e, wr, 18.0)
            lm[if (side < 0) Lm.L_ELBOW else Lm.R_ELBOW] = e
            lm[if (side < 0) Lm.L_WRIST else Lm.R_WRIST] = wr

            val hip = if (side < 0) lm[Lm.L_HIP]!! else lm[Lm.R_HIP]!!
            val knee = Pt(hip.x + side * 10f, 600f)
            val ankle = Pt(hip.x + side * 15f, 790f)
            capsule(data, hip, knee, 38.0)
            capsule(data, knee, ankle, 25.0)
            lm[if (side < 0) Lm.L_KNEE else Lm.R_KNEE] = knee
            lm[if (side < 0) Lm.L_ANKLE else Lm.R_ANKLE] = ankle
        }
        return BodyMask(w, h, data) to lm
    }

    /** Riempie i punti a distanza <= r dal segmento a-b. */
    private fun capsule(data: BooleanArray, a: Pt, b: Pt, r: Double) {
        val minX = (minOf(a.x, b.x) - r).toInt()
        val maxX = (maxOf(a.x, b.x) + r).toInt()
        val minY = (minOf(a.y, b.y) - r).toInt()
        val maxY = (maxOf(a.y, b.y) + r).toInt()
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val len2 = dx * dx + dy * dy
        for (y in minY..maxY) for (x in minX..maxX) {
            if (x !in 0 until w || y !in 0 until h) continue
            val t = (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(x - (a.x + t * dx), y - (a.y + t * dy))
            if (d <= r) data[y * w + x] = true
        }
    }

    /** Larghezza del tratto continuo di sagoma sulla riga [y] che contiene [x]. */
    private fun runWidth(mask: BodyMask, y: Int, x: Int): Int {
        var l = x
        var r = x
        while (mask.at(l - 1, y)) l--
        while (mask.at(r + 1, y)) r++
        return r - l + 1
    }

    private fun ok(outcome: MeasureOutcome): MeasureOutcome.Ok {
        assertTrue("atteso Ok ma era $outcome", outcome is MeasureOutcome.Ok)
        return outcome as MeasureOutcome.Ok
    }

    private fun assertNear(expectedPx: Double, actual: Double?, tolPx: Double = 3.0) {
        val got = (actual ?: error("misura mancante")) * torso
        assertEquals(expectedPx, got, tolPx)
    }

    @Test
    fun measuresKnownBody() {
        val (mask, lm) = body()
        val r = ok(MaskMeasurer(mask, lm).run())
        assertEquals(200f, r.torsoPx, 0.5f)
        // Alle spalle la sagoma include i deltoidi, cioè la parte alta delle braccia aperte.
        assertNear(runWidth(mask, 204, 300).toDouble(), r.measures[Measure.SPALLE])
        assertTrue(r.measures[Measure.SPALLE]!! * torso > 201)
        assertNear(151.0, r.measures[Measure.TORACE])
        assertNear(115.0, r.measures[Measure.VITA])
        assertNear(runWidth(mask, 400, 300).toDouble(), r.measures[Measure.FIANCHI])
        assertNear(45.0, r.measures[Measure.BRACCIO])
        assertNear(37.0, r.measures[Measure.AVAMBRACCIO])
        assertNear(77.0, r.measures[Measure.COSCIA])
        assertNear(51.0, r.measures[Measure.POLPACCIO])
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.segments.any { it.measure == Measure.SPALLE })
    }

    @Test
    fun scaleDoesNotChangeMeasures() {
        // Stesso corpo visto più da lontano (metà dimensione): le misure normalizzate restano uguali.
        val (mask, lm) = body()
        val small = BooleanArray(300 * 500) { i -> mask.at((i % 300) * 2, (i / 300) * 2) }
        val lmSmall = lm.mapValues { Pt(it.value.x / 2, it.value.y / 2) }
        val big = ok(MaskMeasurer(mask, lm).run()).measures
        val half = ok(MaskMeasurer(BodyMask(300, 500, small), lmSmall).run()).measures
        for (m in listOf(Measure.SPALLE, Measure.TORACE, Measure.VITA, Measure.COSCIA)) {
            assertEquals("misura $m", big[m]!!, half[m]!!, 0.03)
        }
    }

    @Test
    fun armsAlongBodyAreRejected() {
        val (mask, lm) = body(armAngleDeg = 5.0)
        val r = MaskMeasurer(mask, lm).run()
        assertTrue(r is MeasureOutcome.Error)
        assertTrue((r as MeasureOutcome.Error).message.contains("braccia", ignoreCase = true))
    }

    @Test
    fun missingShouldersAreRejected() {
        val (mask, lm) = body()
        val r = MaskMeasurer(mask, lm - Lm.L_SHOULDER).run()
        assertTrue(r is MeasureOutcome.Error)
    }

    @Test
    fun missingLegsGiveWarningNotError() {
        val (mask, lm) = body()
        val noLegs = lm - listOf(Lm.L_KNEE, Lm.R_KNEE, Lm.L_ANKLE, Lm.R_ANKLE)
        val r = ok(MaskMeasurer(mask, noLegs).run())
        assertTrue(r.measures[Measure.COSCIA] == null)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test
    fun definitionGrowsWithContrast() {
        val (mask, lm) = body()
        val flat = IntArray(w * h) { 150 }
        // Bande orizzontali chiaro/scuro sull'addome: simulano addominali marcati.
        val striped = IntArray(w * h) { i -> if ((i / w / 10) % 2 == 0) 90 else 210 }
        val dFlat = ok(MaskMeasurer(mask, lm, flat).run()).measures[Measure.DEFINIZIONE]!!
        val dStriped = ok(MaskMeasurer(mask, lm, striped).run()).measures[Measure.DEFINIZIONE]!!
        assertEquals(0.0, dFlat, 0.01)
        assertTrue("definizione con contrasto: $dStriped", dStriped > 0.5)
    }
}
