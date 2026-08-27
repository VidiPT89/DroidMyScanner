package dev.ividi.myscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ividi.myscanner.R
import dev.ividi.myscanner.data.DocumentFolder
import dev.ividi.myscanner.data.ScanDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<ScanDocument>,
    folders: List<DocumentFolder>,
    selectedFolderId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onAssignFolder: (String, String?) -> Unit,
    onScanClick: () -> Unit,
    onChoosePhotosClick: () -> Unit,
    onBrowseFilesClick: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onShareImportedFile: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var creatingFolder by remember { mutableStateOf(false) }
    var managingFolder by remember { mutableStateOf<DocumentFolder?>(null) }
    var showAddDocumentSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDocumentSheet = true },
                icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                text = { Text(stringResource(R.string.home_scan_fab)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
                singleLine = true
            )

            FolderChipRow(
                folders = folders,
                documents = documents,
                selectedFolderId = selectedFolderId,
                onSelectFolder = onSelectFolder,
                onCreateFolderClick = { creatingFolder = true },
                onLongPressFolder = { folder -> managingFolder = folder }
            )

            if (documents.isEmpty()) {
                EmptyState(padding = PaddingValues(0.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            folders = folders,
                            onClick = { onOpenDocument(document.id) },
                            onRename = { newName -> onRenameDocument(document.id, newName) },
                            onDelete = { onDeleteDocument(document.id) },
                            onAssignFolder = { folderId -> onAssignFolder(document.id, folderId) },
                            onShareImportedFile = { onShareImportedFile(document.id) }
                        )
                    }
                }
            }
        }
    }

    if (creatingFolder) {
        FolderNameDialog(
            titleRes = R.string.folder_create_title,
            initialName = "",
            onConfirm = { name -> onCreateFolder(name); creatingFolder = false },
            onDismiss = { creatingFolder = false }
        )
    }

    managingFolder?.let { folder ->
        ManageFolderDialog(
            folder = folder,
            onRename = { newName -> onRenameFolder(folder.id, newName) },
            onDelete = { onDeleteFolder(folder.id) },
            onDismiss = { managingFolder = null }
        )
    }

    if (showAddDocumentSheet) {
        AddDocumentSheet(
            onScanClick = { showAddDocumentSheet = false; onScanClick() },
            onChoosePhotosClick = { showAddDocumentSheet = false; onChoosePhotosClick() },
            onBrowseFilesClick = { showAddDocumentSheet = false; onBrowseFilesClick() },
            onDismiss = { showAddDocumentSheet = false }
        )
    }
}

@Composable
private fun FolderChipRow(
    folders: List<DocumentFolder>,
    documents: List<ScanDocument>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onCreateFolderClick: () -> Unit,
    onLongPressFolder: (DocumentFolder) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) },
                label = { Text(stringResource(R.string.folder_all_documents)) }
            )
        }
        items(folders, key = { it.id }) { folder ->
            val count = documents.count { it.folderId == folder.id }
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = {
                    if (selectedFolderId == folder.id) onLongPressFolder(folder) else onSelectFolder(folder.id)
                },
                label = { Text("${folder.name} ($count)") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onCreateFolderClick,
                label = { Text(stringResource(R.string.folder_new)) },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun FolderNameDialog(
    titleRes: Int,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.folder_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ManageFolderDialog(
    folder: DocumentFolder,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (renaming) {
        FolderNameDialog(
            titleRes = R.string.folder_rename_title,
            initialName = folder.name,
            onConfirm = { newName -> onRename(newName); onDismiss() },
            onDismiss = onDismiss
        )
        return
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.folder_delete_confirm_title)) },
            text = { Text(stringResource(R.string.folder_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = { Text(stringResource(R.string.folder_manage_hint)) },
        confirmButton = {
            TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.document_rename)) }
        },
        dismissButton = {
            TextButton(onClick = { confirmingDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.document_delete))
            }
        }
    )
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DocumentScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formattedDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
