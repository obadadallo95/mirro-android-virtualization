package app.mirro.android.domain.model

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Supported and planned isolation strategies for Mirro.
 */
enum class CloneEngineType(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val isImplemented: Boolean
) {
    WORK_PROFILE(
        titleRes = R.string.engine_title_work_profile,
        descRes = R.string.engine_desc_work_profile,
        isImplemented = false
    ),
    VIRTUALIZED_CONTAINER(
        titleRes = R.string.engine_title_container,
        descRes = R.string.engine_desc_container,
        isImplemented = false
    ),
    SYSTEM_USER_PROFILE(
        titleRes = R.string.engine_title_user_profile,
        descRes = R.string.engine_desc_user_profile,
        isImplemented = false
    ),
    BLUEPRINT_STAGING(
        titleRes = R.string.engine_title_blueprint,
        descRes = R.string.engine_desc_blueprint,
        isImplemented = true
    );

    val title: String get() = when (this) {
        WORK_PROFILE -> "Android Managed Work Profile"
        VIRTUALIZED_CONTAINER -> "Virtual Container Sandbox"
        SYSTEM_USER_PROFILE -> "Secondary User Account"
        BLUEPRINT_STAGING -> "Architecture Blueprint (Foundation Staging)"
    }

    val description: String get() = when (this) {
        WORK_PROFILE -> "Uses Android OS multi-user / work profile isolation primitives."
        VIRTUALIZED_CONTAINER -> "Runs isolated APK in an internal user-space sandbox hook engine."
        SYSTEM_USER_PROFILE -> "Leverages Android multi-user manager (UserManager) for dedicated user spaces."
        BLUEPRINT_STAGING -> "Real architectural configuration model."
    }
}
