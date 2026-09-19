package app.mirro.android.domain.model

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Clean runtime lifecycle state for a cloned instance in Mirro.
 */
enum class CloneRuntimeState(
    val label: String,
    @StringRes val labelRes: Int
) {
    CREATED("Created", R.string.details_status_active),
    READY("Ready", R.string.details_status_active),
    STARTING("Starting…", R.string.loading),
    RUNNING("Running", R.string.details_runtime_verified),
    LIMITED("Limited", R.string.compat_status_limited),
    FAILED("Stopped", R.string.compat_status_container_failed)
}
