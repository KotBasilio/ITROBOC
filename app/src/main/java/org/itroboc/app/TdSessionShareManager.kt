package org.itroboc.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal class TdSessionShareManager(
    private val context: Context,
) {
    private val exportFile = File(context.cacheDir, "td-session-export.pbn")

    fun createShareIntent(exportText: String): Intent {
        exportFile.writeText(exportText)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ITROBOC TD board export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
