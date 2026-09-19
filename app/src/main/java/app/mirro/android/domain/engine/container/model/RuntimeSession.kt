package app.mirro.android.domain.engine.container.model

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import app.mirro.android.domain.engine.container.loader.ClassResolutionRecord
import app.mirro.android.domain.engine.container.loader.LoaderGraphSnapshot
import java.security.MessageDigest

data class HostGuestIdentityVector(
    val guestContextPackage: String,
    val guestApplicationPackage: String?,
    val logicalCloneId: String,
    val targetMetadataPackage: String,
    val requestedTargetProcessName: String?,
    val physicalHostPackage: String,
    val physicalUid: Int,
    val physicalPid: Int,
    val physicalProcessName: String?,
    val physicalOpPackageName: String?,
    val physicalSigningIdentitySummary: String?,
    val physicalInstallerPackage: String?,
    val capturedAt: Long = System.currentTimeMillis()
)

enum class CapabilityStatus {
    SUPPORTED,
    HOST_MEDIATED,
    DEGRADED,
    UNSUPPORTED_WITH_REASON
}

data class CapabilityDecision(
    val capability: String,
    val status: CapabilityStatus,
    val reason: String,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class RuntimeFailure(
    val category: String,
    val message: String,
    val exceptionClass: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * One coherent, local-only record for a clone launch. The target-facing values and the physical
 * host values are intentionally stored side by side; this model never claims they are equal.
 */
data class RuntimeSession(
    val cloneId: String,
    val targetPackage: String,
    val processId: Int,
    val processSlot: String,
    val hostPackage: String,
    val hostUid: Int,
    val targetLogicalPackage: String,
    val targetSdk: Int,
    val abi: String?,
    val baseApk: String,
    val splitApks: List<String>,
    val identityVector: HostGuestIdentityVector,
    val loaderGraph: LoaderGraphSnapshot = LoaderGraphSnapshot(),
    val nativeRuntimeState: NativeRuntimeState = NativeRuntimeState(
        supportedAbis = emptyList(),
        targetNativeLibraryDir = "",
        targetNativeAbis = emptyList(),
        apkNativeLibraryInventory = emptyList()
    ),
    val componentResolutionAttempts: List<ClassResolutionRecord> = emptyList(),
    val capabilityDecisions: List<CapabilityDecision> = emptyList(),
    val firstFailure: RuntimeFailure? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun withFailure(failure: RuntimeFailure): RuntimeSession = copy(
        firstFailure = firstFailure ?: failure,
        updatedAt = System.currentTimeMillis()
    )

    fun withCapability(decision: CapabilityDecision): RuntimeSession = copy(
        capabilityDecisions = capabilityDecisions + decision,
        updatedAt = System.currentTimeMillis()
    )

    companion object {
        fun create(
            context: Context,
            descriptor: ApkDescriptor,
            cloneId: String,
            processSlot: String
        ): RuntimeSession {
            val hostPackage = context.packageName
            val identity = HostGuestIdentityVector(
                guestContextPackage = descriptor.packageName,
                guestApplicationPackage = null,
                logicalCloneId = cloneId,
                targetMetadataPackage = descriptor.packageName,
                requestedTargetProcessName = descriptor.processName,
                physicalHostPackage = hostPackage,
                physicalUid = Process.myUid(),
                physicalPid = Process.myPid(),
                physicalProcessName = physicalProcessName(context),
                physicalOpPackageName = physicalOpPackageName(context),
                physicalSigningIdentitySummary = signingIdentitySummary(context),
                physicalInstallerPackage = installerPackage(context)
            )
            return RuntimeSession(
                cloneId = cloneId,
                targetPackage = descriptor.packageName,
                processId = Process.myPid(),
                processSlot = processSlot,
                hostPackage = hostPackage,
                hostUid = Process.myUid(),
                targetLogicalPackage = descriptor.packageName,
                targetSdk = descriptor.targetSdk,
                abi = Build.SUPPORTED_ABIS.firstOrNull(),
                baseApk = descriptor.baseApkPath,
                splitApks = descriptor.splitApkPaths,
                identityVector = identity
            )
        }

        fun updateGuestApplicationPackage(
            session: RuntimeSession,
            application: Application?
        ): RuntimeSession {
            val guestPackage = application?.packageName ?: session.identityVector.guestApplicationPackage
            return session.copy(
                identityVector = session.identityVector.copy(
                    guestApplicationPackage = guestPackage,
                    capturedAt = System.currentTimeMillis()
                ),
                updatedAt = System.currentTimeMillis()
            )
        }

        private fun physicalProcessName(context: Context): String? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                context.applicationInfo.processName
            }
        }

        private fun physicalOpPackageName(context: Context): String? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.opPackageName
            } else {
                null
            }
        }

        private fun installerPackage(context: Context): String? = runCatching {
            context.packageManager.getInstallerPackageName(context.packageName)
        }.getOrNull()

        private fun signingIdentitySummary(context: Context): String? = runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.toList().orEmpty()
            }
            signatures.firstOrNull()?.let { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }
        }.getOrNull()
    }
}
