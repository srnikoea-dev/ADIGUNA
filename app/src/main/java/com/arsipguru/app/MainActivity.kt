@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.arsipguru.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: ArchiveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Status bar transparan dengan ikon terang (header selalu berwarna)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { ArsipTheme { AppRoot(vm) } }
    }
}

enum class DocAction { OPEN, SHARE, RENAME, MOVE, DELETE }

/* ---------------------------------------------------------------- helpers */

fun fmtSize(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "${b / 1024} KB"
    else -> "$b B"
}

fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(t))

fun extColor(ext: String): Color = when (ext.lowercase()) {
    "pdf" -> Color(0xFFEF4444)
    "doc", "docx" -> Color(0xFF2563EB)
    "xls", "xlsx", "csv" -> Color(0xFF16A34A)
    "ppt", "pptx" -> Color(0xFFEA580C)
    "jpg", "jpeg", "png", "webp" -> Color(0xFF8B5CF6)
    else -> Color(0xFF64748B)
}

/* ---------------------------------------------------------------- root */

@Composable
fun AppRoot(vm: ArchiveViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableIntStateOf(0) }          // 0 beranda, 1 dokumen, 2 kelola
    var filter by rememberSaveable { mutableLongStateOf(-1L) }      // -1 = semua
    var showAdd by remember { mutableStateOf(false) }
    var renameDoc by remember { mutableStateOf<Doc?>(null) }
    var moveDoc by remember { mutableStateOf<Doc?>(null) }
    var deleteList by remember { mutableStateOf<List<Doc>>(emptyList()) }

    BackHandler(page != 0) { page = 0 }

    // ---- Impor file
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris)
    }

    // ---- Scan dokumen (ML Kit Document Scanner)
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(30)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pdf?.uri?.let { vm.importScan(it) }
        }
    }
    fun startScan() {
        scanner.getStartScanIntent(ctx as Activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                Toast.makeText(ctx, "Scanner tidak tersedia. Pastikan Google Play Services aktif.", Toast.LENGTH_LONG).show()
            }
    }

    fun share(list: List<Doc>) {
        scope.launch {
            val uris = withContext(Dispatchers.IO) { list.map { Store.shareUri(ctx, it) } }
            Store.sendShare(ctx, list, uris)
        }
    }

    fun open(d: Doc) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) { Store.shareUri(ctx, d) }
            Store.sendView(ctx, d, uri)
        }
    }

    val onAction: (Doc, DocAction) -> Unit = { d, a ->
        when (a) {
            DocAction.OPEN -> open(d)
            DocAction.SHARE -> share(listOf(d))
            DocAction.RENAME -> renameDoc = d
            DocAction.MOVE -> moveDoc = d
            DocAction.DELETE -> deleteList = listOf(d)
        }
    }

    when (page) {
        0 -> HomeScreen(
            cats = cats, docs = docs,
            onCategory = { filter = it; page = 1 },
            onAll = { filter = -1L; page = 1 },
            onManage = { page = 2 },
            onAdd = { showAdd = true },
            onDocClick = { open(it) },
            onAction = onAction
        )
        1 -> DocsScreen(
            cats = cats, docs = docs, filter = filter,
            onFilter = { filter = it },
            onBack = { page = 0 },
            onAdd = { showAdd = true },
            onDocClick = { open(it) },
            onAction = onAction,
            onShareMany = { share(it) },
            onDeleteMany = { deleteList = it }
        )
        else -> ManageScreen(
            cats = cats, docs = docs,
            onBack = { page = 0 },
            onSave = { c, n, e, col -> vm.saveCategory(c, n, e, col) },
            onDelete = { vm.deleteCategory(it) }
        )
    }

    // ---- Sheet "Tambah"
    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tambah dokumen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                ActionCard("📷", "Scan dokumen", "Foto kertas jadi PDF rapi, otomatis terpotong") {
                    showAdd = false; startScan()
                }
                ActionCard("📂", "Impor dari file", "Pilih
