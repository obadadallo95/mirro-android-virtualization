package app.mirro.android.domain.engine.container.framework

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Process
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity

/**
 * Per-clone package metadata. It is deliberately a snapshot: target package answers do not
 * silently fall through to a host query after the record has been built.
 */
data class VirtualPackageRecord(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val applicationInfo: ApplicationInfo,
    val packageInfo: PackageInfo,
    val activities: Map<String, ActivityInfo>,
    val services: Map<String, ServiceInfo>,
    val receivers: Map<String, ActivityInfo>,
    val providers: Map<String, ProviderInfo>,
    val requestedPermissions: Set<String>,
    val splitApks: List<String>,
    val processNames: Set<String>,
    val nativeLibraryDir: String,
    val sourceDir: String,
    val resourcePaths: List<String>,
    val descriptiveSigningMetadata: String,
    val installerMetadata: String?,
    val cloneId: String,
    val capabilityFlags: Set<String>
)

/** One source of truth for target-owned package/component metadata. */
class VirtualPackageRegistry(
    private val hostPackageManager: PackageManager,
    private val descriptor: ApkDescriptor,
    private val identity: VirtualRuntimeIdentity,
    private val hostPackageName: String
) {
    val targetRecord: VirtualPackageRecord by lazy { buildTargetRecord() }

    fun classification(packageName: String): VirtualValueOrigin = when (packageName) {
        descriptor.packageName -> VirtualValueOrigin.GUEST_VALUE
        hostPackageName -> VirtualValueOrigin.HOST_MEDIATED
        else -> VirtualValueOrigin.HOST_MEDIATED
    }

    fun targetApplicationInfo(flags: Int): ApplicationInfo {
        val base = runCatching {
            hostPackageManager.getApplicationInfo(descriptor.packageName, flags)
        }.getOrElse { targetRecord.applicationInfo }
        return ApplicationInfo(base).apply { applyTargetFields(this) }
    }

    fun targetPackageInfo(flags: Int): PackageInfo {
        val base = runCatching {
            hostPackageManager.getPackageInfo(descriptor.packageName, flags)
        }.getOrElse { targetRecord.packageInfo }
        return PackageInfo().apply {
            packageName = descriptor.packageName
            versionName = base.versionName ?: descriptor.versionName
            versionCode = base.longVersionCode.toInt()
            applicationInfo = targetApplicationInfo(flags)
            activities = base.activities?.map { ActivityInfo(it).apply { applicationInfo = this@apply.applicationInfo } }?.toTypedArray()
            services = base.services?.map { ServiceInfo(it).apply { applicationInfo = this@apply.applicationInfo } }?.toTypedArray()
            receivers = base.receivers?.map { ActivityInfo(it).apply { applicationInfo = this@apply.applicationInfo } }?.toTypedArray()
            providers = base.providers?.map { ProviderInfo(it).apply { applicationInfo = this@apply.applicationInfo } }?.toTypedArray()
            permissions = base.permissions
            requestedPermissions = base.requestedPermissions
            signatures = base.signatures
            signingInfo = base.signingInfo
        }
    }

    fun activityInfo(component: ComponentName): ClassifiedValue<ActivityInfo> {
        if (component.packageName != descriptor.packageName) {
            return ClassifiedValue(null, VirtualValueOrigin.HOST_MEDIATED, "component belongs to another package")
        }
        return targetRecord.activities[component.className]?.let {
            ClassifiedValue(ActivityInfo(it), VirtualValueOrigin.GUEST_VALUE)
        } ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "activity is not in the target record")
    }

    fun serviceInfo(component: ComponentName): ClassifiedValue<ServiceInfo> {
        if (component.packageName != descriptor.packageName) {
            return ClassifiedValue(null, VirtualValueOrigin.HOST_MEDIATED, "component belongs to another package")
        }
        return targetRecord.services[component.className]?.let {
            ClassifiedValue(ServiceInfo(it), VirtualValueOrigin.GUEST_VALUE)
        } ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "service is not in the target record")
    }

    fun receiverInfo(component: ComponentName): ClassifiedValue<ActivityInfo> {
        if (component.packageName != descriptor.packageName) {
            return ClassifiedValue(null, VirtualValueOrigin.HOST_MEDIATED, "component belongs to another package")
        }
        return targetRecord.receivers[component.className]?.let {
            ClassifiedValue(ActivityInfo(it), VirtualValueOrigin.GUEST_VALUE)
        } ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "receiver is not in the target record")
    }

    fun providerInfo(component: ComponentName): ClassifiedValue<ProviderInfo> {
        if (component.packageName != descriptor.packageName) {
            return ClassifiedValue(null, VirtualValueOrigin.HOST_MEDIATED, "component belongs to another package")
        }
        return targetRecord.providers[component.className]?.let {
            ClassifiedValue(ProviderInfo(it), VirtualValueOrigin.GUEST_VALUE)
        } ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "provider is not in the target record")
    }

    fun resolveActivity(intent: Intent): ClassifiedValue<ResolveInfo> {
        val component = intent.component
        if (component != null) {
            return activityInfo(component).map { info ->
                ResolveInfo().apply { activityInfo = info }
            }
        }
        val packageName = intent.`package`
        if (packageName != descriptor.packageName) {
            return ClassifiedValue(null, VirtualValueOrigin.HOST_MEDIATED, "implicit external activity resolution")
        }
        val main = descriptor.mainActivity ?: targetRecord.activities.keys.firstOrNull()
        return main?.let { name ->
            targetRecord.activities[name]?.let { info ->
                ClassifiedValue(ResolveInfo().apply { activityInfo = ActivityInfo(info) }, VirtualValueOrigin.GUEST_VALUE)
            }
        } ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "target has no registered activity")
    }

    fun resolveService(intent: Intent): ClassifiedValue<ResolveInfo> {
        val component = intent.component
        if (component != null) {
            return serviceInfo(component).map { info -> ResolveInfo().apply { serviceInfo = info } }
        }
        return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "implicit target service resolution is not indexed")
    }

    fun resolveProvider(authority: String): ClassifiedValue<ProviderInfo> {
        val provider = targetRecord.providers.values.firstOrNull { info ->
            info.authority?.split(';')?.contains(authority) == true
        }
        return provider?.let { ClassifiedValue(ProviderInfo(it), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "authority is not in the target record")
    }

    fun visibleTargetPackages(): List<PackageInfo> = listOf(targetPackageInfo(0))

    private fun buildTargetRecord(): VirtualPackageRecord {
        val original = runCatching { hostPackageManager.getPackageInfo(descriptor.packageName, packageInfoFlags()) }.getOrNull()
        val application = runCatching { hostPackageManager.getApplicationInfo(descriptor.packageName, PackageManager.GET_META_DATA) }
            .getOrElse { ApplicationInfo() }
            .let(::ApplicationInfo)
            .apply {
                packageName = descriptor.packageName
                className = descriptor.applicationClassName
                name = descriptor.applicationClassName
                sourceDir = descriptor.baseApkPath
                publicSourceDir = descriptor.baseApkPath
                splitSourceDirs = descriptor.splitApkPaths.toTypedArray()
                splitPublicSourceDirs = descriptor.splitApkPaths.toTypedArray()
                nativeLibraryDir = descriptor.nativeLibraryDir
                targetSdkVersion = descriptor.targetSdk
                minSdkVersion = descriptor.minSdk
                dataDir = identity.sandboxRootDir.absolutePath
                deviceProtectedDataDir = identity.sandboxRootDir.absolutePath
                processName = descriptor.processName ?: descriptor.packageName
                // This is the physical process UID, not a fabricated target UID.
                uid = Process.myUid()
            }
        val packageInfo = PackageInfo().apply {
            packageName = descriptor.packageName
            versionName = original?.versionName ?: descriptor.versionName
            versionCode = (original?.longVersionCode ?: descriptor.versionCode).toInt()
            applicationInfo = application
            activities = original?.activities?.map { ActivityInfo(it).apply { applicationInfo = application } }?.toTypedArray()
            services = original?.services?.map { ServiceInfo(it).apply { applicationInfo = application } }?.toTypedArray()
            receivers = original?.receivers?.map { ActivityInfo(it).apply { applicationInfo = application } }?.toTypedArray()
            providers = original?.providers?.map { ProviderInfo(it).apply { applicationInfo = application } }?.toTypedArray()
            requestedPermissions = (original?.requestedPermissions ?: descriptor.requestedPermissions.toTypedArray())
        }
        val activities = packageInfo.activities.orEmpty().associateBy { it.name }.ifEmpty {
            descriptor.declaredActivities.associateWith { ActivityInfo().apply { name = it; packageName = descriptor.packageName; applicationInfo = application } }
        }
        val services = packageInfo.services.orEmpty().associateBy { it.name }.ifEmpty {
            descriptor.declaredServices.associateWith { ServiceInfo().apply { name = it; packageName = descriptor.packageName; applicationInfo = application } }
        }
        val receivers = packageInfo.receivers.orEmpty().associateBy { it.name }.ifEmpty {
            descriptor.declaredReceivers.associateWith { ActivityInfo().apply { name = it; packageName = descriptor.packageName; applicationInfo = application } }
        }
        val providers = packageInfo.providers.orEmpty().associateBy { it.name }.ifEmpty {
            descriptor.declaredProviders.associateWith { ProviderInfo().apply { name = it; packageName = descriptor.packageName; applicationInfo = application } }
        }
        return VirtualPackageRecord(
            packageName = descriptor.packageName,
            versionName = packageInfo.versionName ?: descriptor.versionName,
            versionCode = packageInfo.longVersionCode,
            targetSdk = descriptor.targetSdk,
            minSdk = descriptor.minSdk,
            applicationInfo = application,
            packageInfo = packageInfo,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            requestedPermissions = packageInfo.requestedPermissions?.toSet().orEmpty(),
            splitApks = descriptor.splitApkPaths,
            processNames = setOf(descriptor.processName ?: descriptor.packageName),
            nativeLibraryDir = descriptor.nativeLibraryDir,
            sourceDir = descriptor.baseApkPath,
            resourcePaths = listOf(descriptor.baseApkPath) + descriptor.splitApkPaths,
            descriptiveSigningMetadata = "target signing metadata; not physical host identity",
            installerMetadata = null,
            cloneId = identity.cloneId,
            capabilityFlags = setOf("PACKAGE_METADATA", "COMPONENT_METADATA", "SPLIT_INVENTORY")
        )
    }

    private fun packageInfoFlags(): Int = PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
        PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA

    private fun applyTargetFields(info: ApplicationInfo) {
        info.packageName = descriptor.packageName
        info.className = descriptor.applicationClassName
        info.name = descriptor.applicationClassName
        info.sourceDir = descriptor.baseApkPath
        info.publicSourceDir = descriptor.baseApkPath
        info.splitSourceDirs = descriptor.splitApkPaths.toTypedArray()
        info.splitPublicSourceDirs = descriptor.splitApkPaths.toTypedArray()
        info.nativeLibraryDir = descriptor.nativeLibraryDir
        info.targetSdkVersion = descriptor.targetSdk
        info.minSdkVersion = descriptor.minSdk
        info.dataDir = identity.sandboxRootDir.absolutePath
        info.deviceProtectedDataDir = identity.sandboxRootDir.absolutePath
        info.processName = descriptor.processName ?: descriptor.packageName
        // This is the physical process UID, not a fabricated target UID.
        info.uid = Process.myUid()
    }
}

private inline fun <T, R> ClassifiedValue<T>.map(transform: (T) -> R): ClassifiedValue<R> =
    value?.let { ClassifiedValue(transform(it), origin, reason) } ?: ClassifiedValue(null, origin, reason)
