package dev.ividi.myscanner.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ividi.myscanner.R
import dev.ividi.myscanner.data.PageFilter
import dev.ividi.myscanner.data.ScanPage
import dev.ividi.myscanner.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    page: ScanPage,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onFilterSelected: (PageFilter) -> Unit,
    onCropApplied: (Float, Float, Float, Float) -> Unit,
    onDeletePage: () -> Unit
) {
    var isCropping by remember(page.id) { mutableStateOf(false) }
    val preview = rememberPageBitmap(page.editedPath, page.rotationDegrees, page.filter)
    var confirmingDelete by remember(page.id) { mutableStateOf(false) }
    val hasExistingCrop = page.cropLeft > 0f || page.cropTop > 0f || page.cropRight < 1f || page.cropBottom < 1f
    var topLeft by remember(page.id) { mutableStateOf(Offset(page.cropLeft, page.cropTop)) }
    var bottomRight by remember(page.id) { mutableStateOf(Offset(page.cropRight, page.cropBottom)) }
    var isDragging by remember(page.id) { mutableStateOf(false) }
    var isDetecting by remember(page.id) { mutableStateOf(false) }
    var detectionGeneration by remember(page.id) { mutableStateOf(0) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun runAutoDetect() {
        val generation = ++detectionGeneration
        isDetecting = true
        try {
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.loadDownsampledBitmap(page.originalPath)
            } ?: return
            val bounds = ImageProcessor.detectContentBounds(bitmap)
            if (generation != detectionGeneration) return
            topLeft = Offset(bounds.left, bounds.top)
            bottomRight = Offset(bounds.right, bounds.bottom)
        } finally {
            isDetecting = false
        }
    }

    // Only auto-detect on first open of a page that has no manual crop yet - never
    // override a crop the user already adjusted.
    LaunchedEffect(page.id) {
        if (!hasExistingCrop) {
            runAutoDetect()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_delete_page))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCropping) CropCanvas(
                    imagePath = page.originalPath,
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    isDragging = isDragging,
                    onDragStateChanged = { isDragging = it; if (it) detectionGeneration++ },
                    onTopLeftDrag = { delta -> topLeft = Offset((topLeft.x + delta.x).coerceIn(0f, bottomRight.x - 0.01f), (topLeft.y + delta.y).coerceIn(0f, bottomRight.y - 0.01f)) },
                    onBottomRightDrag = { delta -> bottomRight = Offset((bottomRight.x + delta.x).coerceIn(topLeft.x + 0.01f, 1f), (bottomRight.y + delta.y).coerceIn(topLeft.y + 0.01f, 1f)) }
                ) else preview?.let {
                    Image(bitmap = it, contentDescription = stringResource(R.string.editor_title), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }

            // Fixed-height slot regardless of content so the crop canvas above never
            // resizes when detection starts or stops.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isDetecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " " + stringResource(R.string.editor_detecting_edges),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = !isDetecting,
                    onClick = { isCropping = true; coroutineScope.launch { runAutoDetect() } },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CropFree, contentDescription = null)
                    Text(" " + stringResource(R.string.editor_auto_crop))
                }
                OutlinedButton(
                    onClick = {
                        isCropping = true
                        detectionGeneration++
                        topLeft = Offset(0f, 0f)
                        bottomRight = Offset(1f, 1f)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" " + stringResource(R.string.editor_reset_crop))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRotate,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                    Text(" " + stringResource(R.string.editor_rotate))
                }
                Button(
                    onClick = {
                        if (isCropping) {
                            detectionGeneration++
                            onCropApplied(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                        }
                        isCropping = !isCropping
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(if (isCropping) R.string.save else R.string.editor_crop)) }
            }

            Text(
                text = stringResource(R.string.editor_filter),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    PageFilter.ORIGINAL to R.string.filter_original,
                    PageFilter.BLACK_AND_WHITE to R.string.filter_bw,
                    PageFilter.GRAYSCALE to R.string.filter_grayscale,
                    PageFilter.ENHANCE to R.string.filter_enhance
                )
                filters.forEach { (filter, labelRes) ->
                    FilterChip(
                        selected = page.filter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.editor_delete_page_confirm_title)) },
            text = { Text(stringResource(R.string.document_delete_confirm_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingDelete = false; onDeletePage() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CropCanvas(
    imagePath: String,
    topLeft: Offset,
    bottomRight: Offset,
    isDragging: Boolean,
    onDragStateChanged: (Boolean) -> Unit,
    onTopLeftDrag: (Offset) -> Unit,
    onBottomRightDrag: (Offset) -> Unit
) {
    val bitmap = rememberPageBitmap(imagePath)
    val animatedTopLeftX by animateFloatAsState(topLeft.x, tween(220), label = "cropTopLeftX")
    val animatedTopLeftY by animateFloatAsState(topLeft.y, tween(220), label = "cropTopLeftY")
    val animatedBottomRightX by animateFloatAsState(bottomRight.x, tween(220), label = "cropBottomRightX")
    val animatedBottomRightY by animateFloatAsState(bottomRight.y, tween(220), label = "cropBottomRightY")
    val animatedTopLeft = Offset(animatedTopLeftX, animatedTopLeftY)
    val animatedBottomRight = Offset(animatedBottomRightX, animatedBottomRightY)

    val gridAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(if (isDragging) 100 else 400),
        label = "cropGridAlpha"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val scale = minOf(constraints.maxWidth.toFloat() / (bitmap?.width ?: 1), constraints.maxHeight.toFloat() / (bitmap?.height ?: 1))
        val widthPx = (bitmap?.width ?: 1) * scale
        val heightPx = (bitmap?.height ?: 1) * scale
        Box(modifier = Modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.editor_crop_grid_hint),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val overlayColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = animatedTopLeft.x * widthPx
                val top = animatedTopLeft.y * heightPx
                val right = animatedBottomRight.x * widthPx
                val bottom = animatedBottomRight.y * heightPx

                drawRect(
                    color = overlayColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    style = Stroke(width = 3.dp.toPx())
                )

                if (gridAlpha > 0.01f) {
                    val gridColor = overlayColor.copy(alpha = 0.7f * gridAlpha)
                    val thirdW = (right - left) / 3f
                    val thirdH = (bottom - top) / 3f
                    for (i in 1..2) {
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(left + thirdW * i, top),
                            end = androidx.compose.ui.geometry.Offset(left + thirdW * i, bottom),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(left, top + thirdH * i),
                            end = androidx.compose.ui.geometry.Offset(right, top + thirdH * i),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }

            CropHandle(
                fractionalPosition = animatedTopLeft,
                widthPx = widthPx,
                heightPx = heightPx,
                onDragStart = { onDragStateChanged(true) },
                onDragEnd = { onDragStateChanged(false) },
                onDrag = onTopLeftDrag
            )
            CropHandle(
                fractionalPosition = animatedBottomRight,
                widthPx = widthPx,
                heightPx = heightPx,
                onDragStart = { onDragStateChanged(true) },
                onDragEnd = { onDragStateChanged(false) },
                onDrag = onBottomRightDrag
            )
        }
    }
}

/**
 * Visible handle dot is small so it doesn't obscure the document corner, but the
 * draggable touch target is much larger (56dp) to meet a comfortable minimum touch
 * size, per standard mobile touch-target guidance.
 */
@Composable
private fun CropHandle(
    fractionalPosition: Offset,
    widthPx: Float,
    heightPx: Float,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnStart by rememberUpdatedState(onDragStart)
    val currentOnEnd by rememberUpdatedState(onDragEnd)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val xDp = with(density) { (fractionalPosition.x * widthPx).toDp() }
    val yDp = with(density) { (fractionalPosition.y * heightPx).toDp() }
    val touchTargetSize = 56.dp
    val visibleDotSize = 20.dp

    Box(
        modifier = Modifier
            .offset(x = xDp - touchTargetSize / 2, y = yDp - touchTargetSize / 2)
            .size(touchTargetSize)
            .pointerInput(widthPx, heightPx) {
                detectDragGestures(
                    onDragStart = { currentOnStart() },
                    onDragEnd = { currentOnEnd() },
                    onDragCancel = { currentOnEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    val normalized = Offset(dragAmount.x / widthPx, dragAmount.y / heightPx)
                    currentOnDrag(normalized)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(visibleDotSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}
