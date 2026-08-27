package dev.ividi.myscanner.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ividi.myscanner.R
import dev.ividi.myscanner.data.PageFilter
import dev.ividi.myscanner.data.ScanPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    page: ScanPage,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onFilterSelected: (PageFilter) -> Unit,
    onCropApplied: (Float, Float, Float, Float) -> Unit,
    onResetCrop: () -> Unit,
    onDeletePage: () -> Unit
) {
    var topLeft by remember(page.id) { mutableStateOf(Offset(0.05f, 0.05f)) }
    var bottomRight by remember(page.id) { mutableStateOf(Offset(0.95f, 0.95f)) }

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
                    IconButton(onClick = onDeletePage) {
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
                CropCanvas(
                    imagePath = page.editedPath,
                    topLeft = topLeft,
                    bottomRight = bottomRight,
                    onTopLeftDrag = { delta -> topLeft = clampPoint(topLeft + delta) },
                    onBottomRightDrag = { delta -> bottomRight = clampPoint(bottomRight + delta) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onCropApplied(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.editor_crop)) }
                Button(
                    onClick = onRotate,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                    Text(" " + stringResource(R.string.editor_rotate))
                }
                Button(
                    onClick = onResetCrop,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" " + stringResource(R.string.editor_reset_crop))
                }
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
}

@Composable
private fun CropCanvas(
    imagePath: String,
    topLeft: Offset,
    bottomRight: Offset,
    onTopLeftDrag: (Offset) -> Unit,
    onBottomRightDrag: (Offset) -> Unit
) {
    val bitmap = remember(imagePath) { dev.ividi.myscanner.scanner.ImageProcessor.loadDownsampledBitmap(imagePath) }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        CropHandle(fractionalPosition = topLeft, widthPx = widthPx, heightPx = heightPx, onDrag = onTopLeftDrag)
        CropHandle(fractionalPosition = bottomRight, widthPx = widthPx, heightPx = heightPx, onDrag = onBottomRightDrag)
    }
}

@Composable
private fun CropHandle(
    fractionalPosition: Offset,
    widthPx: Float,
    heightPx: Float,
    onDrag: (Offset) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val xDp = with(density) { (fractionalPosition.x * widthPx).toDp() }
    val yDp = with(density) { (fractionalPosition.y * heightPx).toDp() }
    Box(
        modifier = Modifier
            .offset(x = xDp - 12.dp, y = yDp - 12.dp)
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val normalized = Offset(dragAmount.x / widthPx, dragAmount.y / heightPx)
                    onDrag(normalized)
                }
            }
    )
}

private fun clampPoint(offset: Offset): Offset =
    Offset(offset.x.coerceIn(0f, 1f), offset.y.coerceIn(0f, 1f))
