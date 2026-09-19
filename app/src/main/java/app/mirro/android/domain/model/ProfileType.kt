package app.mirro.android.domain.model

/**
 * Distinguishes the user profile space where an Android package instance lives.
 */
enum class ProfileType {
    /**
     * Primary personal user profile (Owner / Main user space).
     */
    PERSONAL,

    /**
     * Managed work profile space managed by Mirro Device Admin.
     */
    MIRRO_MANAGED
}
