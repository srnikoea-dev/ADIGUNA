package com.arsipguru.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

data class Imported(val name: String, val stored: String, val mime: String, val size: Long)

/** Semua file disimpan di penyimpanan internal aplikasi (privat, offline). */
object Store {
    fun dir(c: Context): File = File(c.filesDir, "arsip").apply { mkdirs() }

    fun import(c: Context, uri: Uri, forcedName: String? = null, forcedMime: String? = null): Imported {
        var name: String? = forcedName
        if (name == null) {
            c.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) name = cur.getString(i)
                }
            }
        }
        val display = name ?: "Dokumen"
        val mime = forcedMime ?: c.contentResolver.getType(uri) ?: "application/octet-stream"
        val ext = display.substringAfterLast('.', "")
        val stored = UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""
        val out = File(dir(c), stored)
        c.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return Imported(display, stored, mime, out.length())
    }

    /** Salinan sementara dengan nama yang rapi, supaya penerima share melihat judul asli. */
    fun shareUri(c: Context, d: Doc): Uri {
        val folder = File(c.cacheDir, "share/${d.id}").apply { mkdirs() }
        val ext = d.fileName.substringAfterLast('.', "")
        val safe = d.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "dokumen" }
        val f = File(folder, if (ext.isEmpty()) safe else "$safe.$ext")
        File(dir(c), d.fileName).copyTo(f, overwrite = true)
        return FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
    }

    fun clearShare(c: Context) {
        File(c.cacheDir, "share").deleteRecursively()
    }

    fun sendShare(c: Context, docs: List<Doc>, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = docs[0].mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
                putExtra(Intent.EXTRA_SUBJECT, docs[0].title)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri("", uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        i.clipData = clip
        try {
            c.startActivity(Intent.createChooser(i, "Bagikan dokumen"))
        } catch (e: Exception) {
            Toast.makeText(c, "Gagal membagikan dokumen", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendView(c: Context, d: Doc, uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, d.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            c.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(c, "Tidak ada aplikasi untuk membuka berkas ini", Toast.LENGTH_LONG).show()
        }
    }
}
