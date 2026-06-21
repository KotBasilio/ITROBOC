package org.itroboc.app

data class ProfileListItem(
    val id: String,
    val displayName: String,
    val isBuiltIn: Boolean,
    val isDemo: Boolean
)

data class AdminProfileUiState(
    val profiles: List<ProfileListItem>,
    val activeProfileId: String
) {
    val activeProfile: ProfileListItem?
        get() = profiles.find { it.id == activeProfileId }
}
