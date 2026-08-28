package dev.ividi.myscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.ividi.myscanner.data.AppLanguage
import dev.ividi.myscanner.data.AppThemeMode
import dev.ividi.myscanner.data.LocaleManager
import dev.ividi.myscanner.data.DocumentKind
import dev.ividi.myscanner.pdf.DocxExporter
import dev.ividi.myscanner.pdf.ImageExporter
import dev.ividi.myscanner.pdf.PdfExporter
import dev.ividi.myscanner.scanner.DocumentScannerClient
import dev.ividi.myscanner.ui.Routes
import dev.ividi.myscanner.ui.screens.AboutScreen
import dev.ividi.myscanner.ui.screens.EditorScreen
import dev.ividi.myscanner.ui.screens.HomeScreen
import dev.ividi.myscanner.ui.screens.OnboardingScreen
import dev.ividi.myscanner.ui.screens.PermissionRationaleScreen
import dev.ividi.myscanner.ui.screens.SettingsScreen
import dev.ividi.myscanner.ui.screens.ViewerScreen
import dev.ividi.myscanner.ui.theme.DroidMyScannerTheme
import dev.ividi.myscanner.viewmodel.AppViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val language by viewModel.language.collectAsState()

            androidx.compose.runtime.LaunchedEffect(language) {
                LocaleManager.apply(language)
            }

            val darkTheme = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            DroidMyScannerTheme(darkTheme = darkTheme) {
                AppRoot(viewModel = viewModel, activity = this)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel, activity: ComponentActivity) {
    val navController = rememberNavController()
    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        permissionRequestedOnce = true
    }

    val scannerClient = remember { DocumentScannerClient(activity) }
    var pendingDocumentTarget by remember { mutableStateOf<String?>(null) }
    var fallbackMessage by remember { mutableStateOf<String?>(null) }

    suspend fun importPickedPages(uris: List<String>, targetDoc: String?) {
        val localPaths = uris.map { uriString ->
            copyUriToLocalFile(activity, Uri.parse(uriString), viewModel, targetDoc)
        }.filterNotNull()
        if (localPaths.isEmpty()) return

        if (targetDoc != null) {
            viewModel.addPagesToDocument(targetDoc, localPaths)
            navController.navigate(Routes.viewer(targetDoc))
        } else {
            val newDocId = viewModel.createDocumentFromScan(localPaths)
            navController.navigate(Routes.viewer(newDocId))
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uris = scanResult?.let { DocumentScannerClient.pageUris(it) } ?: emptyList()
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val targetDoc = pendingDocumentTarget
        scope.launch {
            importPickedPages(uris, targetDoc)
            pendingDocumentTarget = null
        }
    }

    // Fallback used when the camera-based ML Kit scanner cannot start at all (e.g. missing
    // or outdated Google Play services, or any other startup failure) -- lets the user pick
    // existing photos from the gallery to use as scanned pages instead.
    val galleryFallbackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val targetDoc = pendingDocumentTarget
        if (uris.isNotEmpty()) {
            scope.launch {
                importPickedPages(uris.map { it.toString() }, targetDoc)
                pendingDocumentTarget = null
            }
        } else {
            pendingDocumentTarget = null
        }
    }

    fun launchGalleryFallback() {
        scope.launch {
            if (viewModel.consumeScanFallbackNotice()) {
                fallbackMessage = activity.getString(R.string.scan_fallback_message)
            }
            galleryFallbackLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }

    fun launchScanner(existingDocId: String?) {
        pendingDocumentTarget = existingDocId
        scannerClient.start(scanLauncher) { launchGalleryFallback() }
    }

    // "Choose Photos" from the Add Document menu: a first-class gallery picker, independent
    // of the camera-scanner fallback above (always creates a brand-new document).
    val choosePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch { importPickedPages(uris.map { it.toString() }, null) }
        }
    }

    fun launchChoosePhotos() {
        choosePhotosLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // "Browse Files" from the Add Document menu: SAF picker supporting images, PDFs and Word
    // documents at once. Each picked file's bytes are copied into app storage immediately
    // rather than relying on persistable URI permissions.
    val browseFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch { importBrowsedFiles(activity, uris, viewModel, navController) }
        }
    }

    fun launchBrowseFiles() {
        browseFilesLauncher.launch(
            arrayOf(
                "image/*",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    fun openOrViewDocument(docId: String) {
        val doc = viewModel.getDocument(docId)
        val kind = doc?.documentKind
        val importedPath = doc?.importedFilePath
        if (kind is DocumentKind.ImportedFile && importedPath != null) {
            openImportedFile(activity, importedPath, kind.originalFileName)
        } else {
            navController.navigate(Routes.viewer(docId))
        }
    }

    fun shareImportedFileDocument(docId: String) {
        val doc = viewModel.getDocument(docId) ?: return
        val kind = doc.documentKind
        val path = doc.importedFilePath ?: return
        if (kind !is DocumentKind.ImportedFile) return
        shareFile(activity, java.io.File(path), mimeTypeForFileName(kind.originalFileName))
    }

    androidx.compose.runtime.LaunchedEffect(fallbackMessage) {
        fallbackMessage?.let { message ->
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show()
            fallbackMessage = null
        }
    }

    NavHost(navController = navController, startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                viewModel.completeOnboarding()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            if (!hasCameraPermission) {
                PermissionRationaleScreen(
                    permanentlyDenied = permissionRequestedOnce,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = { openAppSettings(activity) }
                )
            } else {
                val filteredDocuments by viewModel.filteredDocuments.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val selectedFolderId by viewModel.selectedFolderId.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                HomeScreen(
                    documents = filteredDocuments,
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSelectFolder = { viewModel.selectFolder(it) },
                    onCreateFolder = { name -> viewModel.createFolder(name) },
                    onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                    onDeleteFolder = { id -> viewModel.deleteFolder(id) },
                    onAssignFolder = { docId, folderId -> viewModel.setDocumentFolder(docId, folderId) },
                    onScanClick = { launchScanner(null) },
                    onChoosePhotosClick = { launchChoosePhotos() },
                    onBrowseFilesClick = { launchBrowseFiles() },
                    onOpenDocument = { docId -> openOrViewDocument(docId) },
                    onRenameDocument = { docId, name -> viewModel.renameDocument(docId, name) },
                    onDeleteDocument = { docId -> viewModel.deleteDocument(docId) },
                    onShareImportedFile = { docId -> shareImportedFileDocument(docId) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: return@composable
            val document = documents.find { it.id == docId }
            if (document != null) {
                var isBusy by remember { mutableStateOf(false) }
                ViewerScreen(
                    document = document,
                    extractedText = extractedText,
                    isBusy = isBusy,
                    onBack = { navController.popBackStack() },
                    onPageClick = { page -> navController.navigate(Routes.editor(docId, page.id)) },
                    onAddPage = { launchScanner(docId) },
                    onExtractText = { page -> viewModel.extractTextFromPage(page) },
                    onDismissExtractedText = { viewModel.clearExtractedText() },
                    onExportPdf = {
                        scope.launch {
                            isBusy = true
                            val file = PdfExporter.export(activity, document)
                            isBusy = false
                            shareFile(activity, file, "application/pdf")
                        }
                    },
                    onExportImages = {
                        scope.launch {
                            isBusy = true
                            val files = ImageExporter.export(activity, document)
                            isBusy = false
                            shareFiles(activity, files, "image/jpeg")
                        }
                    },
                    onExportWord = {
                        scope.launch {
                            isBusy = true
                            val file = DocxExporter.export(activity, document)
                            isBusy = false
                            shareFile(
                                activity,
                                file,
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        }
                    },
                    onShare = {
                        scope.launch {
                            isBusy = true
                            val file = PdfExporter.export(activity, document)
                            isBusy = false
                            shareFile(activity, file, "application/pdf")
                        }
                    },
                    onAddTag = { tag -> viewModel.addTag(docId, tag) },
                    onRemoveTag = { tag -> viewModel.removeTag(docId, tag) }
                )
            }
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("pageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: return@composable
            val pageId = backStackEntry.arguments?.getString("pageId") ?: return@composable
            val document = documents.find { it.id == docId }
            val page = document?.pages?.find { it.id == pageId }
            if (page != null) {
                EditorScreen(
                    page = page,
                    onBack = { navController.popBackStack() },
                    onRotate = { viewModel.rotatePage(docId, pageId, 90) },
                    onFilterSelected = { filter -> viewModel.setPageFilter(docId, pageId, filter) },
                    onCropApplied = { l, t, r, b -> viewModel.cropPage(docId, pageId, l, t, r, b) },
                    onDeletePage = {
                        viewModel.deletePage(docId, pageId)
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Routes.SETTINGS) {
            val themeMode by viewModel.themeMode.collectAsState()
            val language by viewModel.language.collectAsState()
            SettingsScreen(
                themeMode = themeMode,
                language = language,
                onThemeModeChange = { viewModel.setThemeMode(it) },
                onLanguageChange = { viewModel.setLanguage(it) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenWebsite = { openUrl(activity, "https://ividi.dev") },
                onOpenGithub = { openUrl(activity, "https://github.com/VidiPT89") }
            )
        }
    }
}

// File-import/export helpers (SAF display-name lookup, MIME sniffing, sharing, opening
// imported files externally) live in DocumentImportUtils.kt, same package.
