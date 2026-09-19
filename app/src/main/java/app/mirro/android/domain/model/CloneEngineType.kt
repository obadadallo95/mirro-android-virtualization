package app.mirro.android.domain.model

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Cloning engine types for Mirro.
 * Mirro uses an isolated user-space Container runtime.
 */
enum class CloneEngineType(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val isImplemented: Boolean
) {
    VIRTUALIZED_CONTAINER(
        titleRes = R.string.engine_title_container,
        descRes = R.string.engine_desc_container,
        isImplemented = true
    );

    val title: String get() = "Mirro Container Sandbox"
    val description: String get() = "Runs isolated APK in an internal user-space sandbox runtime."
}

