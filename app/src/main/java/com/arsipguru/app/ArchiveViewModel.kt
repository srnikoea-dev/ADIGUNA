package com.arsipguru.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val PALETTE = listOf(0xFF4F46E5, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEF4444, 0xFFEC4899, 0xFF8B5CF6, 0xFF14B8A6)
val EMOJIS = listOf("📁", "📝", "🗂️", "📊", "✉️", "🏆", "📚", "🧾", "👨‍🏫", "🎓", "🏫", "📅", "💼", "📄")

class ArchiveViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ArchiveDb.get(app).dao()

    val categories = dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents = dao.documents().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** File yang baru diimpor/discan, menunggu dipilihkan kategori. */
    var pending by mutableStateOf<List<Imported>>(emptyList())
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Store.clearShare(app)
            if (dao.categoryCount() == 0) {
                val defaults = listOf(
                    Triple("RPP & Modul Ajar", "📝", PALETTE[0]),
                    Triple("Administrasi", "🗂️", PALETTE[1]),
                    Triple("Nilai & Rapor", "📊", PALETTE[2]),
                    Triple("Surat", "✉️", PALETTE[3]),
                    Triple("Sertifikat", "🏆", PALETTE[5]),
                    Triple("Lainnya", "📁", PALETTE[6])
                )
                defaults.forEachIndexed { i, (n, e, c) ->
                    dao.insertCategory(Category(name = n, emoji = e, color = c, sortOrder = i))
                }
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { Store.import(getApplication<Application>(), it) }.getOrNull() }
            }
            if (r.isNotEmpty()) pending = r
        }
    }

    fun importScan(uri: Uri) {
        viewModelScope.launch {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    Store.import(getApplication<Application>(), uri, "Scan $stamp.pdf", "application/pdf")
                }.getOrNull()
            }
            if (r != null) pending = listOf(r)
        }
    }

    fun savePending(title: String, categoryId: Long) {
        val items = pending
        pending = emptyList()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            items.forEachIndexed { i, f ->
                val t = if (items.size == 1 && title.isNotBlank()) title.trim() else f.name.substringBeforeLast('.')
                dao.insertDoc(Doc(title = t, fileName = f.stored, mime = f.mime, sizeBytes = f.size, categoryId = categoryId, createdAt = now + i))
            }
        }
    }

    fun cancelPending() {
        val items = pending
        pending = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { File(Store.dir(getApplication<Application>()), it.stored).delete() }
        }
    }

    fun renameDoc(d: Doc, title: String) { viewModelScope.launch { dao.updateDoc(d.copy(title = title)) } }
    fun moveDoc(d: Doc, categoryId: Long) { viewModelScope.launch { dao.updateDoc(d.copy(categoryId = categoryId)) } }

    fun deleteDocs(list: List<Doc>) {
        viewModelScope.launch(Dispatchers.IO) {
            list.forEach { File(Store.dir(getApplication<Application>()), it.fileName).delete() }
            dao.deleteDocs(list)
        }
    }

    fun saveCategory(existing: Category?, name: String, emoji: String, color: Long) {
        viewModelScope.launch {
            if (existing == null) {
                dao.insertCategory(Category(name = name, emoji = emoji, color = color, sortOrder = categories.value.size))
            } else {
                dao.updateCategory(existing.copy(name = name, emoji = emoji, color = color))
            }
        }
    }

    /** Dokumen di kategori yang dihapus dipindahkan ke kategori lain (tidak ikut terhapus). */
    fun deleteCategory(c: Category) {
        viewModelScope.launch {
            val other = categories.value.firstOrNull { it.id != c.id } ?: return@launch
            dao.moveDocs(c.id, other.id)
            dao.deleteCategory(c)
        }
    }
}
