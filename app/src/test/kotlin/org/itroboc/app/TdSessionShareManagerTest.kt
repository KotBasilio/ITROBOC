package org.itroboc.app

import android.content.Intent
import android.net.Uri
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class TdSessionShareManagerTest {

    @Test
    fun `buildShareIntent should populate intent with correct extras`() {
        val mockUri = mockk<Uri>()
        val mockIntent = mockk<Intent>(relaxed = true)
        val context = mockk<android.content.Context>()
        val manager = TdSessionShareManager(context)
        
        val exportText = "PBN content"
        val subject = "Test Subject"
        
        manager.buildShareIntent(mockIntent, mockUri, exportText, subject)
        
        verify {
            mockIntent.action = Intent.ACTION_SEND
            mockIntent.type = "text/plain"
            mockIntent.putExtra(Intent.EXTRA_STREAM, mockUri)
            mockIntent.putExtra(Intent.EXTRA_TEXT, exportText)
            mockIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
            mockIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
