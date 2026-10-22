package com.animefit.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animefit.AnalysisUi
import com.animefit.AppViewModel
import com.animefit.ImageUtils
import com.animefit.catalog.Floors
import com.animefit.data.Sex
import com.animefit.ui.components.FigureSpec
import com.animefit.ui.components.NeonTopBar
import com.animefit.ui.components.PhysiqueFigure
import com.animefit.ui.components.beep
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.ButtonRow
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.NeonChip
import com.animefit.ui.theme.glowText
import com.animefit.ui.theme.neonBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val TIMERS = listOf(0, 5, 10, 15)

@Composable
fun CameraScreen(vm: AppViewModel, onDone: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val sex = state.profile?.sex ?: Sex.UOMO

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.analyzeUri(uri)
    }
    LaunchedEffect(Unit) {
        vm.resetAnalysis()
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(analysis) {
        val a = analysis
        if (a is AnalysisUi.Done) {
            vm.resetAnalysis()
            onDone(a.photoId)
        }
    }

    var front by rememberSaveable { mutableStateOf(true) }
    var timer by rememberSaveable { mutableIntStateOf(10) }
    var countdown by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val ratio = remember {
        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(ratio)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(front, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val provider = context.cameraProvider()
        val preview = Preview.Builder().setResolutionSelector(ratio).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            cameraError = null
        } catch (e: Exception) {
            cameraError = "Fotocamera non disponibile: ${e.message}"
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).let { f ->
                if (f.isDone) f.get().unbindAll()
            }
        }
    }

    fun shoot() {
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bitmap = image.toBitmap()
                image.close()
                vm.analyze(ImageUtils.rotate(bitmap, rotation, mirror = front))
            }

            override fun onError(exception: ImageCaptureException) {
                cameraError = "Scatto non riuscito: ${exception.message}"
            }
        })
    }

    LaunchedEffect(countdown > 0) {
        while (countdown > 0) {
            beep(120)
            delay(1000)
            countdown--
            if (countdown == 0) shoot()
        }
    }

    Column(Modifier.fillMaxSize().background(Neon.Black).verticalScroll(rememberScrollState())) {
        NeonTopBar(if (state.photos.isEmpty()) "Foto di partenza" else "Foto settimanale", Neon.Magenta, onBack)

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(3f / 4f)
                .neonBorder(Neon.Magenta, radius = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPermission) {
                AndroidView({ previewView }, Modifier.fillMaxSize().padding(3.dp))
                // Sagoma guida: mettiti dentro con braccia aperte a "A".
                PhysiqueFigure(
                    FigureSpec.from(emptyMap(), Floors.average(sex)),
                    Neon.Cyan.copy(alpha = 0.8f),
                    Modifier.fillMaxSize(),
                    dashed = true,
                    fill = 0.9f,
                )
            } else {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BodyText("Serve il permesso della fotocamera per scattare la foto del fisico.")
                    NeonButton("Concedi permesso", Neon.Magenta) { permissionLauncher.launch(Manifest.permission.CAMERA) }
                }
            }
            if (countdown > 0) {
                Text("$countdown", style = glowText(Neon.Magenta, 96, FontWeight.Black))
            }
            when (val a = analysis) {
                AnalysisUi.Running -> Box(Modifier.fillMaxSize().background(Neon.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ANALISI IN CORSO…", style = glowText(Neon.Cyan, 22, FontWeight.Black))
                        BodyText("Misuro spalle, vita, braccia e gambe", color = Neon.TextDim, size = 14)
                    }
                }
                is AnalysisUi.Failed -> Box(Modifier.fillMaxSize().background(Neon.Black.copy(alpha = 0.85f)).padding(16.dp), contentAlignment = Alignment.Center) {
                    NeonCard(Neon.Red) {
                        Text("RIPROVA", style = glowText(Neon.Red, 20, FontWeight.Black))
                        BodyText(a.message)
                        NeonButton("Ok", Neon.Red, Modifier.fillMaxWidth()) { vm.resetAnalysis() }
                    }
                }
                else -> Unit
            }
        }

        cameraError?.let { BodyText(it, Modifier.padding(12.dp), color = Neon.Red, size = 13) }

        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("Timer:", style = glowText(Neon.TextDim, 14))
                TIMERS.forEach { t -> NeonChip(if (t == 0) "No" else "${t}s", Neon.Cyan, timer == t) { timer = t } }
            }
            ButtonRow {
                NeonButton(if (front) "Usa posteriore" else "Usa frontale", Neon.Cyan, Modifier.weight(1f)) { front = !front }
                NeonButton("Dalla galleria", Neon.Yellow, Modifier.weight(1f)) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            NeonButton(
                if (countdown > 0) "Annulla" else "Scatta",
                Neon.Magenta, Modifier.fillMaxWidth(), filled = true,
                enabled = hasPermission && analysis != AnalysisUi.Running,
            ) {
                when {
                    countdown > 0 -> countdown = 0
                    timer == 0 -> shoot()
                    else -> countdown = timer
                }
            }
            NeonCard(Neon.Cyan) {
                Text("COME FARE UNA FOTO PERFETTA", style = glowText(Neon.Cyan, 15, FontWeight.Black))
                listOf(
                    "Appoggia il telefono in verticale all'altezza della pancia, a circa 2-2,5 metri.",
                    "Corpo intero nella sagoma: dalla testa ai piedi.",
                    "Di fronte, braccia aperte a circa 45° (come una A), piedi alla larghezza delle spalle.",
                    "Torso nudo o top/maglia aderente: i vestiti larghi falsano le misure.",
                    "Sfondo chiaro e uniforme, buona luce dall'alto o di fronte.",
                    "Ogni settimana: stesso posto, stessa luce, stessa distanza.",
                ).forEach { BodyText("• $it", size = 14) }
            }
        }
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}
