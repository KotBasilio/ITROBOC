package org.itroboc.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages silent autosaving of TD sessions to internal storage.
 * Format: PBN (supporting partial boards).
 */
internal class TdSessionAutosaveManager(private val context: Context) {
    private val tag = "TdAutosave"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getAutosaveDirectory(): File = context.filesDir

    fun generateFilename(prefix: String): String {
        val dateStr = dateFormat.format(Date())
        return "${prefix}-${dateStr}.pbn"
    }

    fun save(sessionState: TdSessionState, filename: String) {
        val pbn = TdSessionExchange.exportBoards(sessionState, allowPartial = true)
        if (pbn == null) {
            Log.d(tag, "Nothing to autosave.")
            return
        }

        try {
            val file = File(getAutosaveDirectory(), filename)
            file.writeText(pbn)
            Log.d(tag, "Autosaved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to autosave: ${e.message}")
        }
    }

    fun restore(filename: String, currentSession: TdSessionState): TdSessionState {
        val file = File(getAutosaveDirectory(), filename)
        if (!file.exists()) {
            Log.d(tag, "No autosave file found to restore: ${file.absolutePath}")
            return currentSession
        }

        return try {
            val rawText = file.readText()
            val result = TdSessionExchange.importCumulative(currentSession, rawText, allowPartial = true)
            Log.i(tag, "Restored session from ${file.name}. Boards: ${result.importedBoardNumbers}")
            result.sessionState
        } catch (e: Exception) {
            Log.e(tag, "Failed to restore autosave: ${e.message}")
            currentSession
        }
    }

    /**
     * Finds files matching the autosave pattern that are older than 30 days.
     */
    fun findOldAutosaves(prefix: String): List<File> {
        val dir = getAutosaveDirectory()
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        return dir.listFiles { file ->
            file.isFile && 
            file.name.startsWith(prefix) && 
            file.name.endsWith(".pbn") &&
            file.lastModified() < thirtyDaysAgo
        }?.toList() ?: emptyList()
    }

    fun deleteFiles(files: List<File>) {
        files.forEach { file ->
            if (file.delete()) {
                Log.i(tag, "Deleted old autosave: ${file.name}")
            }
        }
    }

    fun archiveFiles(files: List<File>) {
        files.forEach { file ->
            val archivedName = file.name.replace("autosave", "archived")
            val archivedFile = File(file.parentFile, archivedName)
            if (file.renameTo(archivedFile)) {
                Log.i(tag, "Archived old autosave: ${file.name} -> $archivedName")
            }
        }
    }
}
