package it.federicorapetti.recalls.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import it.federicorapetti.recalls.R
import kotlin.math.max
import kotlin.math.min

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Fullscreen, zoomable photo viewer shown as a [Dialog] over the caller.
 *
 * [photos] are Coil-compatible models: a URL [String] or an in-memory [Bitmap]. Callers must pass
 * at least one photo. [attribution] is always shown; [pdfUrl], when non-null, adds a button that
 * opens the recall PDF (required next to third-party Operator photos).
 */
@Composable
fun PhotoViewer(
    photos: List<Any>,
    initialPage: Int,
    attribution: String,
    pdfUrl: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            contentColor = Color.White
        ) {
            PhotoViewerContent(photos, initialPage, attribution, pdfUrl, onDismiss)
        }
    }
}

@Composable
private fun PhotoViewerContent(
    photos: List<Any>,
    initialPage: Int,
    attribution: String,
    pdfUrl: String?,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, photos.lastIndex)) { photos.size }
    val zoomStates = remember(photos.size) { List(photos.size) { ZoomState() } }
    val scrollEnabled by remember(pagerState, zoomStates) {
        derivedStateOf { zoomStates[pagerState.currentPage].scale <= 1f }
    }

    LaunchedEffect(pagerState, zoomStates) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            zoomStates.forEachIndexed { index, zoom -> if (index != current) zoom.reset() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = scrollEnabled,
            key = { it },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomablePhoto(
                model = photos[page],
                description = stringResource(R.string.photo_viewer_photo_description, page + 1, photos.size),
                zoom = zoomStates[page]
            )
        }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.photo_viewer_close)
                )
            }
            if (photos.size > 1) {
                Text(
                    text = stringResource(R.string.photo_viewer_position, pagerState.currentPage + 1, photos.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = attribution, style = MaterialTheme.typography.bodySmall)
                if (pdfUrl != null) PdfButton(pdfUrl)
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(model: Any, description: String, zoom: ZoomState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { zoom.viewport = it.toSize() }
            .pointerInput(zoom) { transformGestures(zoom) }
            .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.toggle(it) }) },
        contentAlignment = Alignment.Center
    ) {
        val imageModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            }
        if (model is Bitmap) {
            zoom.intrinsic = Size(model.width.toFloat(), model.height.toFloat())
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
                loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() } },
                error = {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_release_alert),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                },
                onSuccess = { zoom.intrinsic = it.painter.intrinsicSize }
            )
        }
    }
}

/** Pinch-zoom and, while zoomed, pan once past touch slop (so taps still reach the double-tap detector). At 1× with one finger nothing is consumed, so the pager swipes. */
private suspend fun PointerInputScope.transformGestures(zoom: ZoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pastSlop = false
        var slopPan = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val multiTouch = event.changes.count { it.pressed } >= 2
            if (multiTouch || zoom.scale > 1f) {
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                if (!pastSlop) {
                    slopPan += pan
                    pastSlop = multiTouch || slopPan.getDistance() > viewConfiguration.touchSlop
                }
                if (pastSlop && (zoomChange != 1f || pan != Offset.Zero)) {
                    zoom.transform(event.calculateCentroid(useCurrent = false), pan, zoomChange)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Zoom/pan state of one page. Offsets are relative to the viewport centre. */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    var viewport: Size = Size.Zero

    /** Intrinsic size of the photo; unknown until loaded. */
    var intrinsic: Size = Size.Unspecified

    /** The photo's on-screen size at 1×: [intrinsic] fitted into [viewport]. */
    private val content: Size
        get() {
            if (!intrinsic.isSpecified || intrinsic.width <= 0f || intrinsic.height <= 0f) return viewport
            val fit = min(viewport.width / intrinsic.width, viewport.height / intrinsic.height)
            return Size(intrinsic.width * fit, intrinsic.height * fit)
        }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Zooms by [zoom] about [centroid] (the pointers' previous centroid) and pans by [pan]. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val anchor = centroid - viewport.center
        val moved = anchor - (anchor - offset) * (newScale / scale) + pan
        scale = newScale
        offset = if (newScale == 1f) Offset.Zero else clamp(moved)
    }

    fun toggle(tap: Offset) {
        if (scale > 1f) {
            reset()
        } else {
            val anchor = tap - viewport.center
            scale = DOUBLE_TAP_SCALE
            offset = clamp(anchor - anchor * DOUBLE_TAP_SCALE)
        }
    }

    private fun clamp(value: Offset): Offset {
        val maxX = max(0f, (content.width * scale - viewport.width) / 2f)
        val maxY = max(0f, (content.height * scale - viewport.height) / 2f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
}
