package app.mirro.android.domain.model

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Android application isolation compatibility status.
 *
 * Status is derived from real Android PackageInfo/ApplicationInfo inspection and runtime verification:
 * - [SUPPORTED]: Standard launchable application with no anti-virtualization or strict isolation blockers.
 * - [LIMITED]: Application features external services (e.g., GMS push tokens, custom content providers) that may have partial isolation limitations.
 * - [PROTECTED]: Application declares device administration, Play Integrity hardware binding, or vendor KNOX/MDM policies.
 * - [CONTAINER_NOT_TESTED]: Package prepared for container runtime isolation; initial launch test pending.
 * - [CONTAINER_RUNTIME_READY]: Dex bytecode, resources, and WebView isolation sandbox successfully loaded.
 * - [CONTAINER_LAUNCH_VERIFIED]: Runtime user-space container launch executed and verified.
 * - [CONTAINER_LAUNCH_FAILED]: Container launch encountered classloader, native ABI, or component initialization error.
 * - [WORK_PROFILE_AVAILABLE]: Package installed inside Mirro Space (Work Profile).
 * - [WORK_PROFILE_INSTALL_REQUIRED]: Application must be added to Mirro Space.
 * - [VERIFIED_WORK_PROFILE]: Runtime cross-profile execution verified.
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
    CONTAINER_NOT_TESTED(
        labelRes = R.string.compat_status_container_not_tested,
        descRes = R.string.compat_desc_container_not_tested
    ),
    CONTAINER_RUNTIME_READY(
        labelRes = R.string.compat_status_container_runtime_ready,
        descRes = R.string.compat_desc_container_runtime_ready
    ),
    CONTAINER_LAUNCH_VERIFIED(
        labelRes = R.string.compat_status_container_verified,
        descRes = R.string.compat_desc_container_verified
    ),
    CONTAINER_LAUNCH_FAILED(
        labelRes = R.string.compat_status_container_failed,
        descRes = R.string.compat_desc_container_failed
    ),
    WORK_PROFILE_AVAILABLE(
        labelRes = R.string.compat_status_work_profile_available,
        descRes = R.string.compat_desc_work_profile_available
    ),
    WORK_PROFILE_INSTALL_REQUIRED(
        labelRes = R.string.compat_status_work_profile_install_required,
        descRes = R.string.compat_desc_work_profile_install_required
    ),
    VERIFIED_WORK_PROFILE(
        labelRes = R.string.compat_status_verified_work_profile,
        descRes = R.string.compat_desc_verified_work_profile
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
            CONTAINER_NOT_TESTED -> "Container (Not Tested)"
            CONTAINER_RUNTIME_READY -> "Container Ready"
            CONTAINER_LAUNCH_VERIFIED -> "Container Verified"
            CONTAINER_LAUNCH_FAILED -> "Container Stoppage"
            WORK_PROFILE_AVAILABLE -> "Work Profile Ready"
            WORK_PROFILE_INSTALL_REQUIRED -> "Install Required"
            VERIFIED_WORK_PROFILE -> "Verified in Work Profile"
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
