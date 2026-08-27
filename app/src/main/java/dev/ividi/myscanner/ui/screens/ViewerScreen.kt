package dev.ividi.myscanner.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ividi.myscanner.R
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.data.ScanPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    document: ScanDocument,
    extractedText: String?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onPageClick: (ScanPage) -> Unit,
    onAddPage: () -> Unit,
    onExtractText: (ScanPage) -> Unit,
    onDismissExtractedText: () -> Unit,
    onExportPdf: () -> Unit,
    onExportImages: () -> Unit,
    onExportWord: () -> Unit,
    onShare: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    var exportMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { exportMenuExpanded = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.viewer_export_menu))
                        }
                        DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_export_pdf)) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = { exportMenuExpanded = false; onExportPdf() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_export_images)) },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = { exportMenuExpanded = false; onExportImages() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_export_word)) },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                onClick = { exportMenuExpanded = false; onExportWord() }
                            )
                        }
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.viewer_share))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isBusy) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
            TagsSection(tags = document.tags, onAddTag = onAddTag, onRemoveTag = onRemoveTag)
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(document.pages, key = { it.id }) { page ->
                    PageThumbnail(
                        page = page,
                        onClick = { onPageClick(page) },
                        onExtractText = { onExtractText(page) }
                    )
                }
                item {
                    Card(
                        onClick = onAddPage,
                        modifier = Modifier.aspectRatio(0.7f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.viewer_add_page))
                        }
                    }
                }
            }
        }
    }

    if (extractedText != null) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissExtractedText,
            title = { Text(stringResource(R.string.extracted_text_title)) },
            text = {
                Text(
                    if (extractedText.isBlank()) stringResource(R.string.extracted_text_empty) else extractedText
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissExtractedText) { Text(stringResource(R.string.editor_done)) }
            },
            dismissButton = {
                if (extractedText.isNotBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(extractedText))
                        android.widget.Toast.makeText(context, R.string.extracted_text_copied, android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.extracted_text_copy))
                    }
                }
            }
        )
    }
}

@Composable
private fun PageThumbnail(page: ScanPage, onClick: () -> Unit, onExtractText: () -> Unit) {
    val bitmap = remember(page.editedPath) { BitmapFactory.decodeFile(page.editedPath) }
    Column {
        Card(
            onClick = onClick,
            modifier = Modifier
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onExtractText) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = stringResource(R.string.viewer_extract_text),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagsSection(tags: List<String>, onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tags) { tag ->
                AssistChip(
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tag_remove)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            item {
                AssistChip(
                    onClick = { adding = true },
                    label = { Text(stringResource(R.string.tag_add)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                )
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false; newTag = "" },
            title = { Text(stringResource(R.string.tag_add)) },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text(stringResource(R.string.tag_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTag.isNotBlank()) onAddTag(newTag)
                    newTag = ""
                    adding = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false; newTag = "" }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
