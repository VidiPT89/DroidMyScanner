package dev.ividi.myscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ividi.myscanner.R
import dev.ividi.myscanner.data.DocumentFolder
import dev.ividi.myscanner.data.DocumentKind
import dev.ividi.myscanner.data.ScanDocument

@Composable
fun DocumentCard(
    document: ScanDocument,
    folders: List<DocumentFolder>,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAssignFolder: (String?) -> Unit,
    onShareImportedFile: () -> Unit
) {
    val isImportedFile = document.documentKind is DocumentKind.ImportedFile
    var menuExpanded by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var pickingFolder by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isImportedFile) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(stringResource(R.string.imported_file_badge)) },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.document_pages_count, document.pageCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_rename)) },
                            onClick = { menuExpanded = false; renaming = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_move_to_folder)) },
                            onClick = { menuExpanded = false; pickingFolder = true }
                        )
                        if (isImportedFile) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_share)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { menuExpanded = false; onShareImportedFile() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_delete)) },
                            onClick = { menuExpanded = false; confirmingDelete = true }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = renaming, enter = fadeIn(), exit = fadeOut()) {
                RenameDialogInline(
                    initialName = document.name,
                    onConfirm = { newName -> onRename(newName); renaming = false },
                    onDismiss = { renaming = false }
                )
            }
        }
    }

    if (confirmingDelete) {
        DeleteConfirmDialog(
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false }
        )
    }

    if (pickingFolder) {
        FolderPickerDialog(
            folders = folders,
            currentFolderId = document.folderId,
            onPick = { folderId -> onAssignFolder(folderId); pickingFolder = false },
            onDismiss = { pickingFolder = false }
        )
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<DocumentFolder>,
    currentFolderId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.document_move_to_folder)) },
        text = {
            Column {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_all_documents)) },
                    onClick = { onPick(null) },
                    leadingIcon = if (currentFolderId == null) {
                        { Icon(Icons.Default.Folder, contentDescription = null) }
                    } else null
                )
                folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.name) },
                        onClick = { onPick(folder.id) },
                        leadingIcon = if (currentFolderId == folder.id) {
                            { Icon(Icons.Default.Folder, contentDescription = null) }
                        } else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
