package codes.shandon.toto.ui

import android.util.Log
import android.util.Size as CameraSize
import androidx.activity.compose.BackHandler
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import codes.shandon.toto.QrDecoder
import codes.shandon.toto.R
import codes.shandon.toto.ui.theme.LocalWhimsyColors
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

private const val TAG = "Toto"

// Full-screen QR scanner: CameraX preview with ZXing decoding frames in the background.
// Calls [onResult] once with the first QR code's text.
@Composable
fun ScannerScreen(
    onResult: (String) -> Unit,
    onManual: () -> Unit,
    onError: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val latestOnResult by rememberUpdatedState(onResult)
    val latestOnError by rememberUpdatedState(onError)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    // Keep the screen awake while lining up a code
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        // ~720p is plenty for QR codes and keeps decoding fast
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            CameraSize(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        analysis.setAnalyzer(executor, QrAnalyzer { text ->
            mainExecutor.execute {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                latestOnResult(text)
            }
        })

        try {
            // Only binding can fail. Keep the catch away from awaitCancellation(): coroutine
            // cancellation (scanner closing) is an IllegalStateException and must not look like an error.
            camera = try {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: IllegalArgumentException) {
                // No back camera
                Log.e(TAG, "Couldn't bind camera", e)
                null
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Couldn't bind camera", e)
                null
            }
            if (camera == null) {
                latestOnError()
                return@LaunchedEffect
            }
            awaitCancellation()
        } finally {
            provider.unbind(preview, analysis)
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow taps so nothing underneath reacts
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
        }
        ViewfinderOverlay(Modifier.fillMaxSize())

        // Top bar: close + light toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close_scanner),
                    tint = Color.White
                )
            }
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                val label = stringResource(if (torchOn) R.string.light_on else R.string.light_off)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (torchOn) LocalWhimsyColors.current.ink else Color.White,
                    modifier = Modifier
                        .background(
                            if (torchOn) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.4f),
                            MaterialTheme.shapes.small
                        )
                        .border(1.5.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                        .clickable {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom: toto, a hint, and the manual fallback
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Toto(sleeping = false, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.scan_overlay),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onManual) {
                Text(stringResource(R.string.or_enter_setup_key), color = MaterialTheme.colorScheme.primaryContainer)
            }
        }
    }
}

// Dims everything except a square window, outlined with pink corner brackets (cyan offset shadow,
// like the logo) and a sweeping lavender-to-teal scan line
@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val whimsy = LocalWhimsyColors.current
    val bracket = MaterialTheme.colorScheme.primaryContainer
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "sweep"
    )

    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val side = size.minDimension * 0.68f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2 - size.height * 0.06f)
        val window = Size(side, side)
        val radius = CornerRadius(24.dp.toPx())

        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, window, radius, blendMode = BlendMode.Clear)

        val shadowOffset = Offset(3.dp.toPx(), 3.dp.toPx())
        drawBrackets(topLeft + shadowOffset, side, whimsy.shadow)
        drawBrackets(topLeft, side, bracket)

        val y = topLeft.y + side * sweep
        val inset = side * 0.08f
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, whimsy.gradientStart, whimsy.gradientEnd, Color.Transparent),
                startX = topLeft.x + inset,
                endX = topLeft.x + side - inset
            ),
            start = Offset(topLeft.x + inset, y),
            end = Offset(topLeft.x + side - inset, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBrackets(topLeft: Offset, side: Float, color: Color) {
    val length = side * 0.16f
    val width = 5.dp.toPx()
    val left = topLeft.x
    val top = topLeft.y
    val right = left + side
    val bottom = top + side
    for ((corner, dx, dy) in listOf(
        Triple(Offset(left, top), 1f, 1f),
        Triple(Offset(right, top), -1f, 1f),
        Triple(Offset(left, bottom), 1f, -1f),
        Triple(Offset(right, bottom), -1f, -1f)
    )) {
        drawLine(color, corner, corner + Offset(length * dx, 0f), width, StrokeCap.Round)
        drawLine(color, corner, corner + Offset(0f, length * dy), width, StrokeCap.Round)
    }
}

// Feeds each frame's Y plane to ZXing; reports the first hit, then ignores further frames
private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val decoder = QrDecoder()
    private var buffer = ByteArray(0)
    @Volatile private var found = false

    override fun analyze(image: ImageProxy) {
        image.use {
            if (found) return
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val needed = rowStride * image.height
            if (buffer.size < needed) buffer = ByteArray(needed)
            val pixels = plane.buffer.apply { rewind() }
            // The last row may be shorter than rowStride; the tail of the buffer just stays stale
            pixels.get(buffer, 0, minOf(pixels.remaining(), needed))
            val text = decoder.decode(buffer, rowStride, image.width, image.height) ?: return
            found = true
            onQr(text)
        }
    }
}
