package com.stremio.mobile.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Oynatılan videoyu telefona kaydeder.
 *
 * Dosyalar uygulamanın kendi klasörüne iner:
 *   Android/data/com.stremio.mobile/files/Movies/Indirilenler/
 * Bu klasör için hiçbir izin gerekmez.
 */
object StreamDownloader {

    private const val FOLDER = "Indirilenler"

    /** İndirme klasörünü döndürür, yoksa oluşturur. */
    fun folder(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val dir = File(base, FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Bölüm adını dosya adına çevirir. */
    private fun safeName(title: String?): String {
        val cleaned = (title ?: "")
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return if (cleaned.isBlank()) "video" else cleaned
    }

    /** Aynı isim varsa sonuna (1), (2) ekler. */
    private fun uniqueFileName(context: Context, baseName: String): String {
        val dir = folder(context)
        var candidate = "$baseName.mp4"
        var i = 1
        while (File(dir, candidate).exists()) {
            candidate = "$baseName ($i).mp4"
            i++
        }
        return candidate
    }

    /**
     * İndirmeyi başlatır. Kullanıcıya gösterilecek mesajı döndürür.
     */
    fun enqueue(context: Context, url: String?, title: String?): String {
        if (url.isNullOrBlank()) {
            return "Video adresi bulunamadı."
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Bu kaynak indirilemiyor (adres http değil)."
        }
        return try {
            folder(context)
            val fileName = uniqueFileName(context, safeName(title))
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(title ?: fileName)
                .setDescription("Stremio")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_MOVIES,
                    "$FOLDER/$fileName"
                )
                .setAllowedOverRoaming(false)

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            "İndirme başladı: $fileName"
        } catch (e: Exception) {
            "İndirme başlatılamadı: ${e.message}"
        }
    }

    /** İndirilen dosyalar, en yeni en üstte. */
    fun list(context: Context): List<File> {
        val files = folder(context).listFiles() ?: return emptyList()
        return files.filter { it.isFile }.sortedByDescending { it.lastModified() }
    }

    /** Dosyayı telefondaki bir video oynatıcıyla açar. */
    fun open(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Uygun oynatıcı yoksa sessizce geç
        }
    }

    fun delete(file: File): Boolean = try {
        file.delete()
    } catch (_: Exception) {
        false
    }

    /** 1.4 GB gibi okunabilir boyut. */
    fun readableSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.size - 1) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }
}
