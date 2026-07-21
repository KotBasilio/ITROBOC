package org.itroboc.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.itroboc.core.BuiltInDeckProfiles
import org.itroboc.core.DeckProfile
import org.itroboc.core.DeckProfileSignatureModels

/**
 * Durable anchor for ITROBOC app state.
 * Survives configuration changes (orientation, etc).
 */
class ItrobocMainViewModel : ViewModel() {
    // --- Navigation State ---
    var currentScreen by mutableStateOf<Screen>(Screen.MainMenu)
    var barcodeOrientationMode by mutableStateOf(BarcodeOrientationMode.BRM)

    // --- Profile State ---
    private val builtInDefault = BuiltInDeckProfiles.defaultProfile()
    private val builtInDefaultMetadata = builtInDefault.metadata.toProfileListItem()
    private val builtInDemo = BuiltInDeckProfiles.demoBridge52()
    private val builtInDemoMetadata = builtInDemo.metadata.toProfileListItem()

    var profileState by mutableStateOf(
        AdminProfileUiState(
            profiles = listOf(builtInDefaultMetadata, builtInDemoMetadata),
            activeProfileId = builtInDefaultMetadata.id,
        )
    )

    var editedProfiles by mutableStateOf(
        mapOf(
            builtInDefaultMetadata.id to builtInDefault,
            builtInDemoMetadata.id to builtInDemo,
        )
    )

    val activeProfileItem: ProfileListItem
        get() = profileState.activeProfile ?: builtInDefaultMetadata

    val activeDeckProfile: DeckProfile
        get() = editedProfiles[activeProfileItem.id] ?: builtInDefault

    // --- TD Session State ---
    var sessionState by mutableStateOf(TdSessionState())

    // --- Actions ---
    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun updateOrientation(mode: BarcodeOrientationMode) {
        barcodeOrientationMode = mode
    }

    fun updateSession(updatedState: TdSessionState) {
        sessionState = updatedState
    }

    fun selectProfile(profileId: String) {
        profileState = profileState.copy(activeProfileId = profileId)
    }

    fun addProfile(profileName: String) {
        val newProfile = ProfileListItem(
            id = profileState.nextCustomProfileId(),
            displayName = profileName,
            isBuiltIn = false,
            isDemo = false,
            signatureModel = DeckProfileSignatureModels.GRID13_V2,
        )
        profileState = profileState.copy(
            profiles = profileState.profiles + newProfile,
            activeProfileId = newProfile.id,
        )
        editedProfiles = editedProfiles + (newProfile.id to newProfile.toEmptyDeckProfile())
    }

    fun deleteActiveProfile() {
        val toDelete = profileState.activeProfileId
        val newProfiles = profileState.profiles.filter { it.id != toDelete }
        profileState = profileState.copy(
            profiles = newProfiles,
            activeProfileId = newProfiles.firstOrNull()?.id ?: builtInDefaultMetadata.id,
        )
        editedProfiles = editedProfiles - toDelete
    }

    fun importProfile(importedProfile: DeckProfile) {
        val metadataItem = importedProfile.metadata.toProfileListItem()
            .copy(
                id = profileState.nextCustomProfileId(),
                isBuiltIn = false,
                isDemo = false
            )
        val finalizedProfile = importedProfile.withMetadata(metadataItem.toDeckProfileMetadata())

        profileState = profileState.copy(
            profiles = profileState.profiles + metadataItem,
            activeProfileId = metadataItem.id,
        )
        editedProfiles = editedProfiles + (metadataItem.id to finalizedProfile)
    }

    fun saveEditedProfile(updatedProfile: DeckProfile) {
        editedProfiles = editedProfiles + (activeProfileItem.id to updatedProfile)
        currentScreen = Screen.AdminActions
    }
}
