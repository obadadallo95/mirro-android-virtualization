package app.mirro.android.domain.model

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Android application isolation compatibility status.
 *
 * Status is derived from real Android PackageInfo/ApplicationInfo inspection:
 * - [SUPPORTED]: Normal launchable application with no anti-virtualization or strict isolation blockers.
 * - [LIMITED]: Application features external services (e.g., GMS push tokens, custom content providers) that may have partial isolation limitations.
 * - [PROTECTED]: Application declares device administration, Play Integrity hardware binding, or vendor KNOX/MDM policies.
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
    UNKNOWN(
        labelRes = R.string.compat_status_unknown,
        descRes = R.string.compat_desc_unknown
    );

    val displayName: String
        get() = when (this) {
            SUPPORTED -> "Supported"
            LIMITED -> "Limited"
            PROTECTED -> "Protected"
            UNKNOWN -> "Unknown"
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
