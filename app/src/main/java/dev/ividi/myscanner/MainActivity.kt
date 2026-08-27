package dev.ividi.myscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
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
            val themeMode by viewModel.themeMode.collectAsStateSafe(AppThemeMode.DARK)
            val language by viewModel.language.collectAsStateSafe(AppLanguage.ENGLISH)

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
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateSafe(initial: T) =
    androidx.compose.runtime.collectAsState()

@Composable
private fun AppRoot(viewModel: AppViewModel, activity: ComponentActivity) {
    val navController = rememberNavController()
    val onboardingDone by viewModel.onboardingDone.collectAsStateSafe(false)
    val documents by viewModel.documents.collectAsStateSafe(emptyList())
    val extractedText by viewModel.extractedText.collectAsStateSafe(null)
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

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uris = scanResult?.let { DocumentScannerClient.pageUris(it) } ?: emptyList()
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val targetDoc = pendingDocumentTarget
        scope.launch {
            val localPaths = uris.map { uriString ->
                copyUriToLocalFile(activity, Uri.parse(uriString), viewModel, targetDoc)
            }.filterNotNull()

            if (targetDoc != null) {
                viewModel.addPagesToDocument(targetDoc, localPaths)
                navController.navigate(Routes.viewer(targetDoc))
            } else {
                val newDocId = viewModel.createDocumentFromScan(localPaths)
                navController.navigate(Routes.viewer(newDocId))
            }
            pendingDocumentTarget = null
        }
    }

    fun launchScanner(existingDocId: String?) {
        pendingDocumentTarget = existingDocId
        scannerClient.start(scanLauncher) { /* scanner failed to start; silently ignore */ }
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
                HomeScreen(
                    documents = documents,
                    onScanClick = { launchScanner(null) },
                    onOpenDocument = { docId -> navController.navigate(Routes.viewer(docId)) },
                    onRenameDocument = { docId, name -> viewModel.renameDocument(docId, name) },
                    onDeleteDocument = { docId -> viewModel.deleteDocument(docId) },
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
                    onShare = {
                        scope.launch {
                            isBusy = true
                            val file = PdfExporter.export(activity, document)
                            isBusy = false
                            shareFile(activity, file, "application/pdf")
                        }
                    }
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
                    onResetCrop = { viewModel.resetPageCrop(docId, pageId) },
                    onDeletePage = {
                        viewModel.deletePage(docId, pageId)
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Routes.SETTINGS) {
            val themeMode by viewModel.themeMode.collectAsStateSafe(AppThemeMode.DARK)
            val language by viewModel.language.collectAsStateSafe(AppLanguage.ENGLISH)
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

private suspend fun copyUriToLocalFile(
    activity: ComponentActivity,
    uri: Uri,
    viewModel: AppViewModel,
    targetDocId: String?
): String? {
    val tempId = targetDocId ?: "inbox"
    val destFile = viewModel.newPageFile(tempId)
    return runCatching {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    }.getOrNull()
}

private fun shareFile(activity: ComponentActivity, file: java.io.File, mimeType: String) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, null))
}

private fun shareFiles(activity: ComponentActivity, files: List<java.io.File>, mimeType: String) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, null))
}

private fun openUrl(activity: ComponentActivity, url: String) {
    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun openAppSettings(activity: ComponentActivity) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", activity.packageName, null)
    }
    activity.startActivity(intent)
}
