package org.itroboc.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal class TdSessionShareManager(
    private val context: Context,
) {
    private val exportFile: File by lazy {
        val baseDir = context.getExternalFilesDir("td-export") ?: File(context.filesDir, "td-export")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        File(baseDir, "td-session-export.pbn")
    }

    fun createShareIntent(exportText: String): Intent {
        exportFile.writeText(exportText)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )
        return buildShareIntent(Intent(), uri, exportText, "ITROBOC-export.pbn")
    }

    internal fun buildShareIntent(intent: Intent, uri: Uri, exportText: String, subject: String): Intent {
        return intent.apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, exportText)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
