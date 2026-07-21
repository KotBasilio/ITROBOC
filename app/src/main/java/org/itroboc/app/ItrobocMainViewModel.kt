package org.itroboc.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.itroboc.core.BoardProgressSummary
import org.itroboc.core.BuiltInDeckProfiles
import org.itroboc.core.DeckProfile
import org.itroboc.core.DeckProfileSignatureModels
import java.io.File

/**
 * Durable anchor for ITROBOC app state.
 * Survives configuration changes (orientation, etc).
 * Now includes TD autosave persistence.
 */
class ItrobocMainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("itroboc_settings", Context.MODE_PRIVATE)
    private val autosaveManager = TdSessionAutosaveManager(application)

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
    
    // --- Autosave Settings ---
    var autosaveEnabled by mutableStateOf(prefs.getBoolean("autosave_enabled", true))
        private set
    var autosavePrefix by mutableStateOf(prefs.getString("autosave_prefix", "ITROBOC-autosave") ?: "ITROBOC-autosave")
        private set
    val autosavePath: String
        get() = File(autosaveManager.getAutosaveDirectory(), currentAutosaveFilename).absolutePath

    private var currentAutosaveFilename = autosaveManager.generateFilename(autosavePrefix)

    // --- Housekeeping State ---
    var oldAutosaveFiles by mutableStateOf<List<File>>(emptyList())

    init {
        // Initial restore
        sessionState = autosaveManager.restore(currentAutosaveFilename, sessionState)
        
        // Check for old files
        oldAutosaveFiles = autosaveManager.findOldAutosaves(autosavePrefix)
    }

    // --- Actions ---
    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun updateOrientation(mode: BarcodeOrientationMode) {
        barcodeOrientationMode = mode
    }

    fun updateSession(updatedState: TdSessionState) {
        val previousState = sessionState
        sessionState = updatedState
        
        if (autosaveEnabled) {
            if (shouldTriggerAutosave(previousState, updatedState)) {
                autosaveManager.save(sessionState, currentAutosaveFilename)
            }
        }
    }

    private fun shouldTriggerAutosave(old: TdSessionState, new: TdSessionState): Boolean {
        // Trigger if any hand count changed and reached 13
        // Also trigger on manual fourth-hand fill (all hands reached 13)
        val oldSummary = old.boards.mapValues { BoardProgressSummary.from(it.value.boardState) }
        val newSummary = new.boards.mapValues { BoardProgressSummary.from(it.value.boardState) }

        return newSummary.any { (boardNum, summary) ->
            val oldBoardSummary = oldSummary[boardNum]
            summary.completeSeats.size > (oldBoardSummary?.completeSeats?.size ?: 0)
        }
    }

    fun updateAutosaveEnabled(enabled: Boolean) {
        autosaveEnabled = enabled
        prefs.edit().putBoolean("autosave_enabled", enabled).apply()
    }

    fun updateAutosavePrefix(prefix: String) {
        autosavePrefix = prefix
        prefs.edit().putString("autosave_prefix", prefix).apply()
        currentAutosaveFilename = autosaveManager.generateFilename(prefix)
    }

    fun clearOldFiles(archive: Boolean) {
        if (archive) {
            autosaveManager.archiveFiles(oldAutosaveFiles)
        } else {
            autosaveManager.deleteFiles(oldAutosaveFiles)
        }
        oldAutosaveFiles = emptyList()
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
