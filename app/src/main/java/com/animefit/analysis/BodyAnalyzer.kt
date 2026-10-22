package com.animefit.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.animefit.data.Geometry
import com.animefit.data.Measure
import com.animefit.data.Pt
import com.animefit.data.Seg
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AnalysisResult {
    data class Success(
        val measures: Map<Measure, Double>,
        val geometry: Geometry,
        val warnings: List<String>,
        /** Sagoma rimpicciolita (bianco su trasparente) da salvare per i confronti. */
        val mask: Bitmap,
    ) : AnalysisResult()

    data class Failure(val message: String) : AnalysisResult()
}

/**
 * Analizza la foto direttamente sul telefono (nessun dato esce dal telefono):
 * ML Kit trova i punti del corpo e separa la persona dallo sfondo, poi
 * [MaskMeasurer] ricava le misure.
 */
object BodyAnalyzer {

    private const val MASK_DOWNSCALE = 4
    private const val MIN_LIKELIHOOD = 0.5f

    private val landmarkTypes = mapOf(
        Lm.L_SHOULDER to PoseLandmark.LEFT_SHOULDER,
        Lm.R_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        Lm.L_ELBOW to PoseLandmark.LEFT_ELBOW,
        Lm.R_ELBOW to PoseLandmark.RIGHT_ELBOW,
        Lm.L_WRIST to PoseLandmark.LEFT_WRIST,
        Lm.R_WRIST to PoseLandmark.RIGHT_WRIST,
        Lm.L_HIP to PoseLandmark.LEFT_HIP,
        Lm.R_HIP to PoseLandmark.RIGHT_HIP,
        Lm.L_KNEE to PoseLandmark.LEFT_KNEE,
        Lm.R_KNEE to PoseLandmark.RIGHT_KNEE,
        Lm.L_ANKLE to PoseLandmark.LEFT_ANKLE,
        Lm.R_ANKLE to PoseLandmark.RIGHT_ANKLE,
    )

    suspend fun analyze(bitmap: Bitmap): AnalysisResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val poseDetector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
        )
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
        return try {
            val pose = poseDetector.process(image).await()
            val mask = segmenter.process(image).await()
            withContext(Dispatchers.Default) { compute(bitmap, pose, mask) }
        } catch (e: Exception) {
            AnalysisResult.Failure("Analisi non riuscita (${e.message ?: e.javaClass.simpleName}). Riprova.")
        } finally {
            poseDetector.close()
            segmenter.close()
        }
    }

    private fun compute(bitmap: Bitmap, pose: Pose, seg: SegmentationMask): AnalysisResult {
        if (pose.allPoseLandmarks.isEmpty()) {
            return AnalysisResult.Failure(
                "Non vedo nessuna persona nella foto. Inquadra tutto il corpo, di fronte, con buona luce."
            )
        }
        val mw = seg.width
        val mh = seg.height
        val sx = mw.toFloat() / bitmap.width
        val sy = mh.toFloat() / bitmap.height

        val buffer = seg.buffer
        buffer.rewind()
        val data = BooleanArray(mw * mh) { buffer.getFloat() >= 0.5f }
        val mask = BodyMask(mw, mh, data)

        val lmMask = mutableMapOf<String, Pt>()
        for ((name, type) in landmarkTypes) {
            val l = pose.getPoseLandmark(type) ?: continue
            if (l.inFrameLikelihood < MIN_LIKELIHOOD) continue
            lmMask[name] = Pt(l.position.x * sx, l.position.y * sy)
        }

        val work = if (mw == bitmap.width && mh == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, mw, mh, true)
        val pixels = IntArray(mw * mh)
        work.getPixels(pixels, 0, mw, 0, 0, mw, mh)
        val lum = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }

        return when (val r = MaskMeasurer(mask, lmMask, lum).run()) {
            is MeasureOutcome.Error -> AnalysisResult.Failure(r.message)
            is MeasureOutcome.Ok -> {
                fun toImg(p: Pt) = Pt(p.x / sx, p.y / sy)
                val small = smallMask(mask)
                val geometry = Geometry(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    maskWidth = small.width,
                    maskHeight = small.height,
                    torsoPx = r.torsoPx / sy,
                    shoulderMid = toImg(r.shoulderMid),
                    hipMid = toImg(r.hipMid),
                    landmarks = lmMask.mapValues { toImg(it.value) },
                    segments = r.segments.map { Seg(it.measure, toImg(it.a), toImg(it.b)) },
                )
                AnalysisResult.Success(r.measures, geometry, r.warnings, small)
            }
        }
    }

    private fun smallMask(mask: BodyMask): Bitmap {
        val w = maxOf(1, mask.width / MASK_DOWNSCALE)
        val h = maxOf(1, mask.height / MASK_DOWNSCALE)
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (mask.at(x * MASK_DOWNSCALE + MASK_DOWNSCALE / 2, y * MASK_DOWNSCALE + MASK_DOWNSCALE / 2)) {
                px[y * w + x] = Color.WHITE
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
