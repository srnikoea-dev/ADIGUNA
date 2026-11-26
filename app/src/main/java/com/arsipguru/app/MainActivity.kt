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
                ActionCard("📂", "Impor dari file", "Pilih PDF, Word, Excel, gambar, dan lainnya") {
                    showAdd = false; picker.launch(arrayOf("*/*"))
                }
            }
        }
    }

    // ---- Pilih kategori setelah impor/scan
    if (vm.pending.isNotEmpty()) {
        SaveDialog(
            items = vm.pending, cats = cats, defaultCat = filter,
            onCancel = { vm.cancelPending() },
            onSave = { t, c -> vm.savePending(t, c) }
        )
    }

    renameDoc?.let { d ->
        TextDialog("Ganti nama dokumen", d.title, { renameDoc = null }) { vm.renameDoc(d, it); renameDoc = null }
    }

    moveDoc?.let { d ->
        AlertDialog(
            onDismissRequest = { moveDoc = null },
            title = { Text("Pindah ke kategori") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cats.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { vm.moveDoc(d, c.id); moveDoc = null }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, Modifier.weight(1f))
                            if (c.id == d.categoryId) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveDoc = null }) { Text("Batal") } }
        )
    }

    if (deleteList.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteList = emptyList() },
            title = { Text("Hapus ${deleteList.size} dokumen?") },
            text = { Text("Berkas akan dihapus permanen dari perangkat ini dan tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDocs(deleteList); deleteList = emptyList() }) {
                    Text("Hapus", color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { deleteList = emptyList() }) { Text("Batal") } }
        )
    }
}

/* ---------------------------------------------------------------- Beranda */

@Composable
fun HomeScreen(
    cats: List<Category>, docs: List<Doc>,
    onCategory: (Long) -> Unit, onAll: () -> Unit, onManage: () -> Unit, onAdd: () -> Unit,
    onDocClick: (Doc) -> Unit, onAction: (Doc, DocAction) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val counts = remember(docs) { docs.groupingBy { it.categoryId }.eachCount() }
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val results = remember(q, docs) { if (q.isBlank()) emptyList() else docs.filter { it.title.contains(q, true) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Indigo, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Tambah", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 100.dp + navPad)) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(Brush.linearGradient(listOf(Indigo, Violet, Cyan)))
                ) {
                    Column(Modifier.statusBarsPadding().padding(24.dp, 20.dp, 24.dp, 24.dp)) {
                        Text("Halo, Bapak/Ibu Guru 👋", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                        Text("Arsip Digital", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Semua berkas mengajar, rapi di satu tempat.", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatPill("${docs.size}", "dokumen")
                            StatPill("${cats.size}", "kategori")
                            StatPill(fmtSize(docs.sumOf { it.sizeBytes }), "terpakai")
                        }
                        Spacer(Modifier.height(18.dp))
                        SearchField(q) { q = it }
                    }
                }
            }

            if (q.isBlank()) {
                item { SectionTitle("Kategori", "Kelola", onManage) }
                items(cats.chunked(2)) { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { c -> CategoryCard(c, counts[c.id] ?: 0, Modifier.weight(1f)) { onCategory(c.id) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(8.dp)); SectionTitle("Terbaru", "Lihat semua", onAll) }
                if (docs.isEmpty()) {
                    item { EmptyState("Belum ada dokumen", "Ketuk tombol Tambah untuk scan atau impor dokumen pertama Anda.") }
                } else {
                    items(docs.take(8), key = { it.id }) { d ->
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                            DocItem(d, catMap[d.categoryId], false, false, { onDocClick(d) }, {}, { onAction(d, it) })
                        }
                    }
                }
            } else {
                item { SectionTitle("Hasil pencarian (${results.size})", null, {}) }
                if (results.isEmpty()) item { EmptyState("Tidak ditemukan", "Coba kata kunci lain.") }
                items(results, key = { it.id }) { d ->
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                        DocItem(d, catMap[d.categoryId], false, false, { onDocClick(d) }, {}, { onAction(d, it) })
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Daftar dokumen */

@Composable
fun DocsScreen(
    cats: List<Category>, docs: List<Doc>, filter: Long, onFilter: (Long) -> Unit,
    onBack: () -> Unit, onAdd: () -> Unit, onDocClick: (Doc) -> Unit,
    onAction: (Doc, DocAction) -> Unit, onShareMany: (List<Doc>) -> Unit, onDeleteMany: (List<Doc>) -> Unit
) {
    var q by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected = emptySet() }

    val catMap = remember(cats) { cats.associateBy { it.id } }
    val activeFilter = if (cats.any { it.id == filter }) filter else -1L
    val list = docs.filter { (activeFilter == -1L || it.categoryId == activeFilter) && (q.isBlank() || it.title.contains(q, true)) }
    val title = catMap[activeFilter]?.let { "${it.emoji} ${it.name}" } ?: "Semua Dokumen"

    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selecting) {
                AppBar("${selected.size} dipilih", close = true, onNav = { selected = emptySet() }) {
                    IconButton(onClick = {
                        onShareMany(docs.filter { it.id in selected }); selected = emptySet()
                    }) { Icon(Icons.Default.Share, "Bagikan") }
                    IconButton(onClick = {
                        onDeleteMany(docs.filter { it.id in selected }); selected = emptySet()
                    }) { Icon(Icons.Default.Delete, "Hapus") }
                }
            } else AppBar(title, onNav = onBack)
        },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(
                onClick = onAdd, containerColor = Indigo, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Tambah") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Box(Modifier.padding(20.dp, 14.dp, 20.dp, 8.dp)) { SearchField(q) { q = it } }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = activeFilter == -1L, onClick = { onFilter(-1L) }, label = { Text("Semua") })
                cats.forEach { c ->
                    FilterChip(selected = activeFilter == c.id, onClick = { onFilter(c.id) }, label = { Text("${c.emoji} ${c.name}") })
                }
            }
            if (list.isEmpty()) {
                EmptyState("Belum ada dokumen", "Ketuk Tambah untuk scan atau impor dokumen.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { d ->
                        DocItem(
                            d, catMap[d.categoryId], d.id in selected, selecting,
                            onClick = { if (selecting) toggle(d.id) else onDocClick(d) },
                            onLong = { toggle(d.id) },
                            onAction = { onAction(d, it) }
                        )
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Kelola kategori */

@Composable
fun ManageScreen(
    cats: List<Category>, docs: List<Doc>, onBack: () -> Unit,
    onSave: (Category?, String, String, Long) -> Unit, onDelete: (Category) -> Unit
) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var del by remember { mutableStateOf<Category?>(null) }
    val counts = remember(docs) { docs.groupingBy { it.categoryId }.eachCount() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBar("Kelola Kategori", onNav = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true }, containerColor = Indigo, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Kategori baru") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Sesuaikan kategori dengan kebutuhan Anda: ubah nama, ikon, dan warna.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
            }
            items(cats, key = { it.id }) { c ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(c.color).copy(alpha = .16f)),
                            contentAlignment = Alignment.Center
                        ) { Text(c.emoji, fontSize = 24.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${counts[c.id] ?: 0} dokumen", fontSize = 12.sp, color = Color(c.color))
                        }
                        IconButton(onClick = { edit = c }) { Icon(Icons.Default.Edit, "Ubah") }
                        IconButton(onClick = {
                            if (cats.size <= 1) Toast.makeText(ctx, "Minimal harus ada satu kategori", Toast.LENGTH_SHORT).show()
                            else del = c
                        }) { Icon(Icons.Default.Delete, "Hapus", tint = Color(0xFFEF4444)) }
                    }
                }
            }
        }
    }

    if (creating) CategoryEditor(null, { creating = false }) { n, e, col -> onSave(null, n, e, col); creating = false }
    edit?.let { c -> CategoryEditor(c, { edit = null }) { n, e, col -> onSave(c, n, e, col); edit = null } }
    del?.let { c ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Hapus kategori \"${c.name}\"?") },
            text = { Text("Dokumen di dalamnya tidak ikut terhapus, tetapi dipindahkan ke kategori lain.") },
            confirmButton = { TextButton(onClick = { onDelete(c); del = null }) { Text("Hapus", color = Color(0xFFEF4444)) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Batal") } }
        )
    }
}

/* ---------------------------------------------------------------- Komponen */

@Composable
fun AppBar(title: String, close: Boolean = false, onNav: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onNav) {
                Icon(if (close) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Indigo, titleContentColor = Color.White,
            navigationIconContentColor = Color.White, actionIconContentColor = Color.White
        )
    )
}

@Composable
fun StatPill(value: String, label: String) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
    }
}

@Composable
fun SearchField(q: String, onChange: (String) -> Unit) {
    TextField(
        value = q, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("Cari dokumen…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Hapus") } },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun CategoryCard(c: Category, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val col = Color(c.color)
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(col.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text(c.emoji, fontSize = 24.sp) }
            Spacer(Modifier.height(12.dp))
            Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count dokumen", fontSize = 12.sp, color = col, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun DocItem(
    doc: Doc, cat: Category?, selected: Boolean, selecting: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, onAction: (DocAction) -> Unit
) {
    val ext = doc.fileName.substringAfterLast('.', "file").take(4).uppercase()
    val tint = extColor(ext)
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        shape = shape, shadowElevation = 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) { Text(ext, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${cat?.emoji ?: ""} ${cat?.name ?: ""} • ${fmtSize(doc.sizeBytes)} • ${fmtDate(doc.createdAt)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Buka") }, onClick = { menu = false; onAction(DocAction.OPEN) })
                        DropdownMenuItem(text = { Text("Bagikan") }, onClick = { menu = false; onAction(DocAction.SHARE) })
                        DropdownMenuItem(text = { Text("Ganti nama") }, onClick = { menu = false; onAction(DocAction.RENAME) })
                        DropdownMenuItem(text = { Text("Pindah kategori") }, onClick = { menu = false; onAction(DocAction.MOVE) })
                        DropdownMenuItem(
                            text = { Text("Hapus", color = Color(0xFFEF4444)) },
                            onClick = { menu = false; onAction(DocAction.DELETE) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🗂️", fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
fun ActionCard(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 26.sp) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
            }
        }
    }
}

@Composable
fun CatChips(cats: List<Category>, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.forEach { c ->
            FilterChip(selected = c.id == selected, onClick = { onSelect(c.id) }, label = { Text("${c.emoji} ${c.name}") })
        }
    }
}

@Composable
fun SaveDialog(
    items: List<Imported>, cats: List<Category>, defaultCat: Long,
    onCancel: () -> Unit, onSave: (String, Long) -> Unit
) {
    var title by remember { mutableStateOf(items.first().name.substringBeforeLast('.')) }
    var cat by remember { mutableLongStateOf(if (cats.any { it.id == defaultCat }) defaultCat else cats.firstOrNull()?.id ?: 0L) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Simpan ke arsip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (items.size == 1) {
                    OutlinedTextField(title, { title = it }, label = { Text("Judul dokumen") }, singleLine = true)
                } else Text("${items.size} berkas akan disimpan")
                Text("Kategori", fontWeight = FontWeight.SemiBold)
                CatChips(cats, cat) { cat = it }
            }
        },
        confirmButton = { TextButton(enabled = cats.isNotEmpty(), onClick = { onSave(title, cat) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Batal") } }
    )
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (t.isNotBlank()) onOk(t.trim()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun CategoryEditor(initial: Category?, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: EMOJIS[0]) }
    var color by remember { mutableLongStateOf(initial?.color ?: PALETTE[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kategori baru" else "Ubah kategori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama kategori") }, singleLine = true)
                Text("Ikon", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Text("Warna", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PALETTE.forEach { c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(c)).clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) { if (c == color) Icon(Icons.Default.Check, null, tint = Color.White) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), emoji, color) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
