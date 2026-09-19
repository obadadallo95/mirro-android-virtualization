package app.mirro.android.domain.model

/**
 * Stable, profile-aware identity for an application instance on the Android device.
 *
 * Ensures that two instances of the same package (e.g., com.openai.chatgpt)
 * existing across the Personal space and Mirro Space have distinct, unambiguous identities.
 *
 * Avoids storing transient Android OS UserHandle objects directly.
 */
data class ProfileAppIdentity(
    val packageName: String,
    val profileType: ProfileType,
    val userSerialNumber: Long = 0L
) {
    /**
     * Unique composite key for caching, indexing, and comparison.
     */
    val compositeKey: String
        get() = "${packageName}:${profileType.name}:${userSerialNumber}"
}
