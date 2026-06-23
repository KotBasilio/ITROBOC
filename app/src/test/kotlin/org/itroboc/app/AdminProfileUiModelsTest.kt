package org.itroboc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AdminProfileUiModelsTest {
    @Test
    fun `new custom profile starts with empty mapping`() {
        val item = ProfileListItem(
            id = "custom-profile-0",
            displayName = "Fresh calibration",
            isBuiltIn = false,
            isDemo = false,
        )

        val profile = item.toEmptyDeckProfile()

        assertEquals(0, profile.mappingCount())
        assertEquals("custom-profile-0", profile.metadata.profileId)
        assertEquals("Fresh calibration", profile.metadata.displayName)
        assertFalse(profile.metadata.isBuiltIn)
        assertFalse(profile.metadata.isDemo)
    }
}
