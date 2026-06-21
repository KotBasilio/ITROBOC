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
}
