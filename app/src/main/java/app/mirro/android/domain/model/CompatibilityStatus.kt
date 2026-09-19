package app.mirro.android.domain.model

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Android application isolation compatibility status.
 *
 * Status is derived from real Android PackageInfo inspection and runtime verification:
 * - [SUPPORTED]: Standard launchable application with no anti-virtualization blockers (Ready to try).
 * - [LIMITED]: Application features external services (e.g., GMS push tokens, custom content providers) that may have partial isolation limitations.
 * - [PROTECTED]: Application declares system or shared-UID restrictions visible in package metadata.
 * - [CONTAINER_LAUNCH_VERIFIED]: Runtime user-space container launch executed and verified.
 * - [UNKNOWN]: Unanalyzed or pending package structure verification.
 */
enum class CompatibilityStatus(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int
) {
    SUPPORTED(
        labelRes = R.string.compat_status_supported,
        descRes = R.string.compat_desc_supported
    ),
    LIMITED(
        labelRes = R.string.compat_status_limited,
        descRes = R.string.compat_desc_limited
    ),
    PROTECTED(
        labelRes = R.string.compat_status_protected,
        descRes = R.string.compat_desc_protected
    ),
    CONTAINER_LAUNCH_VERIFIED(
        labelRes = R.string.compat_status_container_verified,
        descRes = R.string.compat_desc_container_verified
    ),
    UNKNOWN(
        labelRes = R.string.compat_status_unknown,
        descRes = R.string.compat_desc_unknown
    );

    val displayName: String
        get() = when (this) {
            SUPPORTED -> "Ready to Try"
            LIMITED -> "Limited"
            PROTECTED -> "Not Supported"
            CONTAINER_LAUNCH_VERIFIED -> "Verified"
            UNKNOWN -> "Needs Testing"
        }

}

/**
 * Detailed analysis breakdown for an inspected application.
 */
data class CompatibilityReport(
    val status: CompatibilityStatus,
    val summary: String,
    val technicalDetails: List<String> = emptyList(),
    val riskFactors: List<String> = emptyList()
)
