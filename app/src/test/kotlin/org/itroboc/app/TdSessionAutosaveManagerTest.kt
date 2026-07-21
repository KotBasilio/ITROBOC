package org.itroboc.app

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.itroboc.core.BoardEditState
import org.itroboc.core.BoardState
import org.itroboc.core.CardId
import org.itroboc.core.Seat
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TdSessionAutosaveManagerTest {
    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var manager: TdSessionAutosaveManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @BeforeTest
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        tempDir = Files.createTempDirectory("itroboc-test").toFile()
        context = mockk()
        every { context.filesDir } returns tempDir
        manager = TdSessionAutosaveManager(context)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `save and restore session with partial board`() {
        val board1 = BoardState().addCard(Seat.NORTH, CardId.parse("SA"))
        val session = TdSessionState(
            boards = mapOf(1 to BoardEditState(1, boardState = board1))
        )
        val filename = "test-autosave.pbn"

        manager.save(session, filename)
        
        val restored = manager.restore(filename, TdSessionState())
        
        assertEquals(1, restored.boards.size)
        assertEquals(board1, restored.boards.getValue(1).boardState)
    }

    @Test
    fun `findOldAutosaves detects files older than 30 days`() {
        val prefix = "ITROBOC-autosave"
        val oldFile = File(tempDir, "$prefix-2020-01-01.pbn")
        oldFile.writeText("some pbn")
        oldFile.setLastModified(System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000))

        val newFile = File(tempDir, "$prefix-2024-01-01.pbn")
        newFile.writeText("some pbn")
        newFile.setLastModified(System.currentTimeMillis())

        val oldFiles = manager.findOldAutosaves(prefix)
        
        assertEquals(1, oldFiles.size)
        assertEquals(oldFile.name, oldFiles.first().name)
    }

    @Test
    fun `archiveFiles renames files to archived`() {
        val prefix = "ITROBOC-autosave"
        val oldFile = File(tempDir, "$prefix-2020-01-01.pbn")
        oldFile.writeText("some pbn")
        
        manager.archiveFiles(listOf(oldFile))
        
        val archivedFile = File(tempDir, prefix.replace("autosave", "archived") + "-2020-01-01.pbn")
        assertTrue(archivedFile.exists())
        assertTrue(!oldFile.exists())
    }

    @Test
    fun `smartRestore prefers today file even in morning window`() {
        val prefix = "ITROBOC-autosave"
        val now = Calendar.getInstance().apply { set(2024, 6, 21, 1, 30) }.time // 01:30 AM
        val todayFilename = manager.generateFilename(prefix, now)
        val yesterdayFilename = manager.generateFilename(prefix, Date(now.time - 24 * 60 * 60 * 1000))

        val boardToday = BoardState().addCard(Seat.NORTH, CardId.parse("SA"))
        val boardYesterday = BoardState().addCard(Seat.NORTH, CardId.parse("SK"))

        manager.save(TdSessionState(boards = mapOf(1 to BoardEditState(1, boardState = boardToday))), todayFilename)
        manager.save(TdSessionState(boards = mapOf(1 to BoardEditState(1, boardState = boardYesterday))), yesterdayFilename)

        val restored = manager.smartRestore(prefix, TdSessionState(), now)
        assertEquals(boardToday, restored.boards.getValue(1).boardState)
    }

    @Test
    fun `smartRestore falls back to yesterday in morning window if today is missing`() {
        val prefix = "ITROBOC-autosave"
        val now = Calendar.getInstance().apply { set(2024, 6, 21, 1, 30) }.time // 01:30 AM
        val yesterdayFilename = manager.generateFilename(prefix, Date(now.time - 24 * 60 * 60 * 1000))

        val boardYesterday = BoardState().addCard(Seat.NORTH, CardId.parse("SK"))
        manager.save(TdSessionState(boards = mapOf(1 to BoardEditState(1, boardState = boardYesterday))), yesterdayFilename)

        val restored = manager.smartRestore(prefix, TdSessionState(), now)
        assertEquals(boardYesterday, restored.boards.getValue(1).boardState)
    }

    @Test
    fun `smartRestore does not fall back to yesterday after morning window`() {
        val prefix = "ITROBOC-autosave"
        val now = Calendar.getInstance().apply { set(2024, 6, 21, 10, 0) }.time // 10:00 AM
        val yesterdayFilename = manager.generateFilename(prefix, Date(now.time - 24 * 60 * 60 * 1000))

        val boardYesterday = BoardState().addCard(Seat.NORTH, CardId.parse("SK"))
        manager.save(TdSessionState(boards = mapOf(1 to BoardEditState(1, boardState = boardYesterday))), yesterdayFilename)

        val restored = manager.smartRestore(prefix, TdSessionState(), now)
        assertTrue(restored.boards.isEmpty())
    }
}
