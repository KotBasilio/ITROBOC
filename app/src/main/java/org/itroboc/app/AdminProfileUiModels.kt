package org.itroboc.app

import org.itroboc.core.BuiltInDeckProfiles
import org.itroboc.core.DeckProfile
import org.itroboc.core.DeckProfileMetadata
import org.itroboc.core.DeckProfileSignatureModels

data class ProfileListItem(
    val id: String,
    val displayName: String,
    val isBuiltIn: Boolean,
    val isDemo: Boolean,
    val signatureModel: String? = null,
)

data class AdminProfileUiState(
    val profiles: List<ProfileListItem>,
    val activeProfileId: String
) {
    val activeProfile: ProfileListItem?
        get() = profiles.find { it.id == activeProfileId }

    fun hasProfileNamed(name: String): Boolean {
        val normalizedName = name.trim().lowercase()
        return profiles.any { it.displayName.trim().lowercase() == normalizedName }
    }

    fun nextCustomProfileId(): String {
        val nextSuffix = profiles
            .mapNotNull { profile -> profile.id.removePrefix("custom-profile-").toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 0

        return "custom-profile-$nextSuffix"
    }

    fun findProfile(id: String): ProfileListItem? = profiles.find { it.id == id }
}

fun DeckProfileMetadata.toProfileListItem(): ProfileListItem =
    ProfileListItem(
        id = profileId,
        displayName = displayName,
        isBuiltIn = isBuiltIn,
        isDemo = isDemo,
        signatureModel = signatureModel,
    )

fun ProfileListItem.toDeckProfileMetadata(): DeckProfileMetadata =
    DeckProfileMetadata(
        profileId = id,
        displayName = displayName,
        isBuiltIn = isBuiltIn,
        isDemo = isDemo,
        signatureModel = signatureModel ?: DeckProfileSignatureModels.GRID13_V2,
    )

fun ProfileListItem.toEmptyDeckProfile(): DeckProfile =
    DeckProfile(
        signatureToCard = emptyMap(),
        metadata = toDeckProfileMetadata(),
    )

fun ProfileListItem.toMockDeckProfile(baseProfile: DeckProfile = BuiltInDeckProfiles.demoBridge52()): DeckProfile =
    if (isBuiltIn && id == baseProfile.metadata.profileId) {
        baseProfile
    } else {
        baseProfile.withMetadata(toDeckProfileMetadata())
    }
