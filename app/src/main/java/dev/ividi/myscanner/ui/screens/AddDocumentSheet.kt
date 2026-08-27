package dev.ividi.myscanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ividi.myscanner.R

/**
 * Bottom sheet shown from the Home screen's "Add document" action, letting the user pick
 * where new content comes from instead of jumping straight into the camera scanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentSheet(
    onScanClick: () -> Unit,
    onChoosePhotosClick: () -> Unit,
    onBrowseFilesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.add_document_menu_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            AddDocumentOption(
                icon = Icons.Default.DocumentScanner,
                label = stringResource(R.string.add_document_scan_camera),
                onClick = onScanClick
            )
            AddDocumentOption(
                icon = Icons.Default.PhotoLibrary,
                label = stringResource(R.string.add_document_choose_photos),
                onClick = onChoosePhotosClick
            )
            AddDocumentOption(
                icon = Icons.Default.FolderOpen,
                label = stringResource(R.string.add_document_browse_files),
                onClick = onBrowseFilesClick
            )
        }
    }
}

@Composable
private fun AddDocumentOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
}
