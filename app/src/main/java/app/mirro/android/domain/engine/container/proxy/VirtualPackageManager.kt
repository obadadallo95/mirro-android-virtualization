package app.mirro.android.domain.engine.container.proxy

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ChangedPackages
import android.content.pm.FeatureInfo
import android.content.pm.InstrumentationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.SharedLibraryInfo
import android.content.pm.VersionedPackage
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.UserHandle
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.engine.container.framework.VirtualAppOpsManager
import app.mirro.android.domain.engine.container.framework.VirtualPackageRegistry
import app.mirro.android.domain.engine.container.framework.VirtualPermissionManager
import app.mirro.android.domain.engine.container.framework.VirtualPermissionState
import java.util.concurrent.ConcurrentHashMap

/**
 * PackageManager facade exposed to code running inside a clone.
 *
 * The old implementation was only a collection of helper methods and
 * VirtualContext returned the host ApplicationPackageManager directly. That
 * meant target code could mutate or query the host package identity. This
 * facade delegates ordinary device queries, but owns target package metadata
 * and absorbs target component state changes inside the clone.
 */
class VirtualPackageManager(
    private val hostPackageManager: PackageManager,
    private val descriptor: ApkDescriptor,
    private val identity: VirtualRuntimeIdentity,
    private val hostPackageName: String
) : PackageManager() {

    private companion object {
        // PackageManager's public signature result is not exposed consistently across SDK stubs.
        const val SIGNATURE_UNVERIFIED = -3
    }

    private val componentStates = ConcurrentHashMap<String, Int>()
    val registry = VirtualPackageRegistry(hostPackageManager, descriptor, identity, hostPackageName)
    val permissionManager = VirtualPermissionManager(descriptor) { permission ->
        hostPackageManager.checkPermission(permission, hostPackageName)
    }
    val appOpsManager = VirtualAppOpsManager()

    fun getPackageName(): String = descriptor.packageName

    fun getApplicationInfo(flags: Int): ApplicationInfo = registry.targetApplicationInfo(flags)

    fun getPackageInfo(flags: Int): PackageInfo = registry.targetPackageInfo(flags)

    override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo {
        return if (packageName == descriptor.packageName) {
            registry.targetApplicationInfo(flags)
        } else {
            hostPackageManager.getApplicationInfo(packageName, flags)
        }
    }

    override fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
        return if (packageName == descriptor.packageName) {
            registry.targetPackageInfo(flags)
        } else {
            hostPackageManager.getPackageInfo(packageName, flags)
        }
    }

    override fun getPackageInfo(packageName: VersionedPackage, flags: Int): PackageInfo {
        return getPackageInfo(packageName.packageName, flags)
    }

    override fun getApplicationInfo(packageName: String, flags: PackageManager.ApplicationInfoFlags): ApplicationInfo =
        getApplicationInfo(packageName, flags.value.toInt())

    override fun getPackageInfo(packageName: String, flags: PackageManager.PackageInfoFlags): PackageInfo =
        getPackageInfo(packageName, flags.value.toInt())

    override fun getPackageInfo(packageName: VersionedPackage, flags: PackageManager.PackageInfoFlags): PackageInfo =
        getPackageInfo(packageName.packageName, flags.value.toInt())

    override fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? {
        val targetPackage = intent.`package` ?: intent.component?.packageName
        if (targetPackage == descriptor.packageName && intent.component != null) {
            return registry.resolveActivity(intent).value
        }
        return hostPackageManager.resolveActivity(intent, flags)
    }

    override fun checkPermission(permName: String, pkgName: String): Int {
        return if (pkgName == descriptor.packageName) {
            permissionManager.check(permName)
        } else {
            hostPackageManager.checkPermission(permName, pkgName)
        }
    }

    fun setVirtualPermissionState(permission: String, state: VirtualPermissionState) {
        permissionManager.setState(permission, state)
    }

    /**
     * Present on newer framework PackageManager implementations although it is
     * not exposed by every compile SDK stub. Activity delegates to this method
     * through the runtime PackageManager vtable, so the facade must provide it.
     */
    @Suppress("UNUSED")
    fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return try {
            val method = hostPackageManager.javaClass.getMethod(
                "shouldShowRequestPermissionRationale",
                String::class.java
            )
            method.invoke(hostPackageManager, permission) as? Boolean ?: false
        } catch (_: ReflectiveOperationException) {
            false
        }
    }

    override fun getComponentEnabledSetting(componentName: ComponentName): Int {
        if (componentName.packageName == descriptor.packageName) {
            return componentStates[componentName.className] ?: COMPONENT_ENABLED_STATE_DEFAULT
        }
        return hostPackageManager.getComponentEnabledSetting(componentName)
    }

    override fun setComponentEnabledSetting(componentName: ComponentName, newState: Int, flags: Int) {
        if (componentName.packageName == descriptor.packageName) {
            componentStates[componentName.className] = newState
            return
        }
        hostPackageManager.setComponentEnabledSetting(componentName, newState, flags)
    }

    override fun getApplicationEnabledSetting(packageName: String): Int {
        return if (packageName == descriptor.packageName) {
            COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            hostPackageManager.getApplicationEnabledSetting(packageName)
        }
    }

    override fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int) {
        if (packageName != descriptor.packageName) {
            hostPackageManager.setApplicationEnabledSetting(packageName, newState, flags)
        }
    }

    override fun getActivityInfo(componentName: ComponentName, flags: Int): ActivityInfo {
        return if (componentName.packageName == descriptor.packageName) {
            registry.activityInfo(componentName).value
                ?: throw NameNotFoundException(componentName.flattenToString())
        } else {
            hostPackageManager.getActivityInfo(componentName, flags)
        }
    }

    override fun getReceiverInfo(componentName: ComponentName, flags: Int): ActivityInfo {
        return if (componentName.packageName == descriptor.packageName) {
            registry.receiverInfo(componentName).value
                ?: throw NameNotFoundException(componentName.flattenToString())
        } else {
            hostPackageManager.getReceiverInfo(componentName, flags)
        }
    }

    override fun getServiceInfo(componentName: ComponentName, flags: Int): ServiceInfo {
        return if (componentName.packageName == descriptor.packageName) {
            registry.serviceInfo(componentName).value
                ?: throw NameNotFoundException(componentName.flattenToString())
        } else {
            hostPackageManager.getServiceInfo(componentName, flags)
        }
    }

    override fun getProviderInfo(componentName: ComponentName, flags: Int): ProviderInfo {
        return if (componentName.packageName == descriptor.packageName) {
            registry.providerInfo(componentName).value
                ?: throw NameNotFoundException(componentName.flattenToString())
        } else {
            hostPackageManager.getProviderInfo(componentName, flags)
        }
    }

    override fun getActivityInfo(componentName: ComponentName, flags: PackageManager.ComponentInfoFlags): ActivityInfo =
        getActivityInfo(componentName, flags.value.toInt())

    override fun getReceiverInfo(componentName: ComponentName, flags: PackageManager.ComponentInfoFlags): ActivityInfo =
        getReceiverInfo(componentName, flags.value.toInt())

    override fun getServiceInfo(componentName: ComponentName, flags: PackageManager.ComponentInfoFlags): ServiceInfo =
        getServiceInfo(componentName, flags.value.toInt())

    override fun getProviderInfo(componentName: ComponentName, flags: PackageManager.ComponentInfoFlags): ProviderInfo =
        getProviderInfo(componentName, flags.value.toInt())

    override fun getActivityIcon(componentName: ComponentName): Drawable = hostPackageManager.getActivityIcon(componentName) ?: throw NameNotFoundException(componentName.flattenToString())
    override fun getActivityIcon(intent: Intent): Drawable = hostPackageManager.getActivityIcon(intent) ?: throw NameNotFoundException(intent.toString())
    override fun getActivityBanner(componentName: ComponentName): Drawable = hostPackageManager.getActivityBanner(componentName) ?: throw NameNotFoundException(componentName.flattenToString())
    override fun getActivityBanner(intent: Intent): Drawable = hostPackageManager.getActivityBanner(intent) ?: throw NameNotFoundException(intent.toString())
    override fun getActivityLogo(componentName: ComponentName): Drawable = hostPackageManager.getActivityLogo(componentName) ?: throw NameNotFoundException(componentName.flattenToString())
    override fun getActivityLogo(intent: Intent): Drawable = hostPackageManager.getActivityLogo(intent) ?: throw NameNotFoundException(intent.toString())
    override fun getApplicationBanner(info: ApplicationInfo): Drawable = hostPackageManager.getApplicationBanner(info) ?: throw NameNotFoundException(info.packageName)
    override fun getApplicationBanner(packageName: String): Drawable = hostPackageManager.getApplicationBanner(packageName) ?: throw NameNotFoundException(packageName)
    override fun getApplicationIcon(info: ApplicationInfo): Drawable = hostPackageManager.getApplicationIcon(info) ?: throw NameNotFoundException(info.packageName)
    override fun getApplicationIcon(packageName: String): Drawable = hostPackageManager.getApplicationIcon(packageName) ?: throw NameNotFoundException(packageName)
    override fun getApplicationLogo(info: ApplicationInfo): Drawable = hostPackageManager.getApplicationLogo(info) ?: throw NameNotFoundException(info.packageName)
    override fun getApplicationLogo(packageName: String): Drawable = hostPackageManager.getApplicationLogo(packageName) ?: throw NameNotFoundException(packageName)
    override fun getDefaultActivityIcon(): Drawable = hostPackageManager.defaultActivityIcon ?: throw NameNotFoundException("default activity icon")
    override fun getDrawable(packageName: String, resId: Int, appInfo: ApplicationInfo?): Drawable =
        hostPackageManager.getDrawable(packageName, resId, appInfo) ?: throw NameNotFoundException(packageName)

    override fun getResourcesForActivity(componentName: ComponentName): Resources = hostPackageManager.getResourcesForActivity(componentName)
    override fun getResourcesForApplication(info: ApplicationInfo): Resources = hostPackageManager.getResourcesForApplication(info)
    override fun getResourcesForApplication(packageName: String): Resources = hostPackageManager.getResourcesForApplication(packageName)

    override fun getInstalledApplications(flags: Int): List<ApplicationInfo> = registry.visibleTargetPackages().mapNotNull { it.applicationInfo }
    override fun getInstalledPackages(flags: Int): List<PackageInfo> = registry.visibleTargetPackages()
    override fun getPackagesHoldingPermissions(permissions: Array<String>, flags: Int): List<PackageInfo> =
        hostPackageManager.getPackagesHoldingPermissions(permissions, flags)
    override fun getInstalledApplications(flags: PackageManager.ApplicationInfoFlags): List<ApplicationInfo> =
        getInstalledApplications(flags.value.toInt())
    override fun getInstalledPackages(flags: PackageManager.PackageInfoFlags): List<PackageInfo> =
        getInstalledPackages(flags.value.toInt())
    override fun getPackagesHoldingPermissions(permissions: Array<String>, flags: PackageManager.PackageInfoFlags): List<PackageInfo> =
        getPackagesHoldingPermissions(permissions, flags.value.toInt())
    override fun getAllPermissionGroups(flags: Int): List<PermissionGroupInfo> = hostPackageManager.getAllPermissionGroups(flags)
    override fun getPermissionGroupInfo(name: String, flags: Int): PermissionGroupInfo = hostPackageManager.getPermissionGroupInfo(name, flags)
    override fun getPermissionInfo(name: String, flags: Int): PermissionInfo = hostPackageManager.getPermissionInfo(name, flags)
    override fun queryPermissionsByGroup(group: String?, flags: Int): List<PermissionInfo> = hostPackageManager.queryPermissionsByGroup(group, flags)

    override fun getInstrumentationInfo(componentName: ComponentName, flags: Int): InstrumentationInfo = hostPackageManager.getInstrumentationInfo(componentName, flags)
    override fun queryInstrumentation(targetPackage: String, flags: Int): List<InstrumentationInfo> = hostPackageManager.queryInstrumentation(targetPackage, flags)
    override fun getApplicationLabel(info: ApplicationInfo): CharSequence = hostPackageManager.getApplicationLabel(info)
    override fun getSharedLibraries(flags: Int): List<SharedLibraryInfo> = hostPackageManager.getSharedLibraries(flags)
    override fun getSharedLibraries(flags: PackageManager.PackageInfoFlags): List<SharedLibraryInfo> =
        getSharedLibraries(flags.value.toInt())

    override fun queryIntentActivities(intent: Intent, flags: Int): List<ResolveInfo> {
        val target = intent.`package` == descriptor.packageName || intent.component?.packageName == descriptor.packageName
        return if (target) registry.resolveActivity(intent).value?.let(::listOf).orEmpty()
        else hostPackageManager.queryIntentActivities(intent, flags)
    }
    override fun queryIntentActivities(intent: Intent, flags: PackageManager.ResolveInfoFlags): List<ResolveInfo> =
        queryIntentActivities(intent, flags.value.toInt())
    override fun queryIntentActivityOptions(caller: ComponentName?, specifics: Array<Intent>?, intent: Intent, flags: Int): List<ResolveInfo> =
        hostPackageManager.queryIntentActivityOptions(caller, specifics, intent, flags)
    override fun queryBroadcastReceivers(intent: Intent, flags: Int): List<ResolveInfo> {
        if (intent.`package` != descriptor.packageName && intent.component?.packageName != descriptor.packageName) {
            return hostPackageManager.queryBroadcastReceivers(intent, flags)
        }
        val component = intent.component ?: return emptyList()
        return registry.receiverInfo(component).value?.let { ResolveInfo().apply { activityInfo = it } }?.let(::listOf).orEmpty()
    }
    override fun queryBroadcastReceivers(intent: Intent, flags: PackageManager.ResolveInfoFlags): List<ResolveInfo> =
        queryBroadcastReceivers(intent, flags.value.toInt())
    override fun queryIntentServices(intent: Intent, flags: Int): List<ResolveInfo> {
        val target = intent.`package` == descriptor.packageName || intent.component?.packageName == descriptor.packageName
        return if (target) registry.resolveService(intent).value?.let(::listOf).orEmpty()
        else hostPackageManager.queryIntentServices(intent, flags)
    }
    override fun queryIntentServices(intent: Intent, flags: PackageManager.ResolveInfoFlags): List<ResolveInfo> =
        queryIntentServices(intent, flags.value.toInt())
    override fun queryIntentContentProviders(intent: Intent, flags: Int): List<ResolveInfo> = hostPackageManager.queryIntentContentProviders(intent, flags)
    override fun queryIntentContentProviders(intent: Intent, flags: PackageManager.ResolveInfoFlags): List<ResolveInfo> =
        queryIntentContentProviders(intent, flags.value.toInt())
    override fun resolveContentProvider(name: String, flags: Int): ProviderInfo? =
        registry.resolveProvider(name).value ?: hostPackageManager.resolveContentProvider(name, flags)
    override fun resolveService(intent: Intent, flags: Int): ResolveInfo? =
        if (intent.`package` == descriptor.packageName || intent.component?.packageName == descriptor.packageName) {
            registry.resolveService(intent).value
        } else hostPackageManager.resolveService(intent, flags)
    override fun queryContentProviders(processName: String?, uid: Int, flags: Int): List<ProviderInfo> = hostPackageManager.queryContentProviders(processName, uid, flags)
    override fun queryContentProviders(processName: String?, uid: Int, flags: PackageManager.ComponentInfoFlags): List<ProviderInfo> =
        queryContentProviders(processName, uid, flags.value.toInt())

    override fun getLaunchIntentForPackage(packageName: String): Intent? {
        if (packageName == descriptor.packageName) {
            return descriptor.mainActivity?.let { Intent(Intent.ACTION_MAIN).setClassName(packageName, it) }
        }
        return hostPackageManager.getLaunchIntentForPackage(packageName)
    }
    override fun getLeanbackLaunchIntentForPackage(packageName: String): Intent? = hostPackageManager.getLeanbackLaunchIntentForPackage(packageName)
    override fun getNameForUid(uid: Int): String? = if (uid == android.os.Process.myUid()) descriptor.packageName else hostPackageManager.getNameForUid(uid)
    override fun getPackageGids(packageName: String): IntArray =
        if (packageName == descriptor.packageName) hostPackageManager.getPackageGids(hostPackageName)
        else hostPackageManager.getPackageGids(packageName)
    override fun getPackageGids(packageName: String, flags: Int): IntArray =
        if (packageName == descriptor.packageName) hostPackageManager.getPackageGids(hostPackageName, flags)
        else hostPackageManager.getPackageGids(packageName, flags)
    override fun getPackageGids(packageName: String, flags: PackageManager.PackageInfoFlags): IntArray =
        getPackageGids(packageName, flags.value.toInt())
    override fun getPackageUid(packageName: String, flags: Int): Int =
        if (packageName == descriptor.packageName) android.os.Process.myUid() else hostPackageManager.getPackageUid(packageName, flags)
    override fun getPackageUid(packageName: String, flags: PackageManager.PackageInfoFlags): Int =
        getPackageUid(packageName, flags.value.toInt())
    override fun getPackagesForUid(uid: Int): Array<String>? =
        if (uid == android.os.Process.myUid()) arrayOf(descriptor.packageName) else hostPackageManager.getPackagesForUid(uid)
    override fun getInstallerPackageName(packageName: String): String? =
        if (packageName == descriptor.packageName) null else hostPackageManager.getInstallerPackageName(packageName)
    override fun getPackageInstaller(): PackageInstaller = hostPackageManager.packageInstaller

    override fun checkSignatures(uid1: Int, uid2: Int): Int =
        if (uid1 == android.os.Process.myUid() || uid2 == android.os.Process.myUid()) SIGNATURE_UNVERIFIED
        else hostPackageManager.checkSignatures(uid1, uid2)
    override fun checkSignatures(packageName1: String, packageName2: String): Int =
        if (packageName1 == descriptor.packageName || packageName2 == descriptor.packageName) SIGNATURE_UNVERIFIED
        else hostPackageManager.checkSignatures(packageName1, packageName2)
    override fun canonicalToCurrentPackageNames(names: Array<String>): Array<String> = hostPackageManager.canonicalToCurrentPackageNames(names)
    override fun currentToCanonicalPackageNames(names: Array<String>): Array<String> = hostPackageManager.currentToCanonicalPackageNames(names)
    override fun getSystemAvailableFeatures(): Array<FeatureInfo> = hostPackageManager.systemAvailableFeatures
    override fun getSystemSharedLibraryNames(): Array<String>? = hostPackageManager.systemSharedLibraryNames
    override fun hasSystemFeature(name: String): Boolean = hostPackageManager.hasSystemFeature(name)
    override fun hasSystemFeature(name: String, version: Int): Boolean = hostPackageManager.hasSystemFeature(name, version)
    override fun isSafeMode(): Boolean = hostPackageManager.isSafeMode
    override fun canRequestPackageInstalls(): Boolean = hostPackageManager.canRequestPackageInstalls()
    override fun isInstantApp(): Boolean = hostPackageManager.isInstantApp
    override fun isInstantApp(packageName: String): Boolean = hostPackageManager.isInstantApp(packageName)
    override fun isPermissionRevokedByPolicy(permName: String, packageName: String): Boolean = hostPackageManager.isPermissionRevokedByPolicy(permName, packageName)

    override fun getText(packageName: String, resid: Int, appInfo: ApplicationInfo?): CharSequence? = hostPackageManager.getText(packageName, resid, appInfo)
    override fun getXml(packageName: String, resid: Int, appInfo: ApplicationInfo?): XmlResourceParser = hostPackageManager.getXml(packageName, resid, appInfo) ?: throw NameNotFoundException(packageName)
    override fun getUserBadgedIcon(icon: Drawable, user: UserHandle): Drawable = hostPackageManager.getUserBadgedIcon(icon, user)
    override fun getUserBadgedDrawableForDensity(icon: Drawable, user: UserHandle, badgeLocation: Rect?, badgeDensity: Int): Drawable = hostPackageManager.getUserBadgedDrawableForDensity(icon, user, badgeLocation, badgeDensity)
    override fun getUserBadgedLabel(label: CharSequence, user: UserHandle): CharSequence = hostPackageManager.getUserBadgedLabel(label, user)

    override fun getChangedPackages(sequenceNumber: Int): ChangedPackages? = hostPackageManager.getChangedPackages(sequenceNumber)
    override fun getPreferredActivities(outFilters: MutableList<IntentFilter?>, outActivities: MutableList<ComponentName?>, packageName: String?): Int = hostPackageManager.getPreferredActivities(outFilters, outActivities, packageName)
    override fun getPreferredPackages(flags: Int): List<PackageInfo> = hostPackageManager.getPreferredPackages(flags)
    override fun clearPackagePreferredActivities(packageName: String) = hostPackageManager.clearPackagePreferredActivities(packageName)
    override fun addPackageToPreferred(packageName: String) = hostPackageManager.addPackageToPreferred(packageName)
    override fun removePackageFromPreferred(packageName: String) = hostPackageManager.removePackageFromPreferred(packageName)
    override fun addPreferredActivity(filter: IntentFilter, match: Int, set: Array<ComponentName>?, activity: ComponentName) = hostPackageManager.addPreferredActivity(filter, match, set, activity)

    override fun addPermission(info: PermissionInfo): Boolean = hostPackageManager.addPermission(info)
    override fun addPermissionAsync(info: PermissionInfo): Boolean = hostPackageManager.addPermissionAsync(info)
    override fun removePermission(name: String) = hostPackageManager.removePermission(name)
    override fun setApplicationCategoryHint(packageName: String, categoryHint: Int) = hostPackageManager.setApplicationCategoryHint(packageName, categoryHint)
    override fun setInstallerPackageName(targetPackage: String, installerPackageName: String?) = hostPackageManager.setInstallerPackageName(targetPackage, installerPackageName)
    override fun updateInstantAppCookie(cookie: ByteArray?) = hostPackageManager.updateInstantAppCookie(cookie)
    override fun clearInstantAppCookie() = hostPackageManager.clearInstantAppCookie()
    override fun getInstantAppCookie(): ByteArray = hostPackageManager.instantAppCookie
    override fun getInstantAppCookieMaxBytes(): Int = hostPackageManager.instantAppCookieMaxBytes
    override fun extendVerificationTimeout(id: Int, verificationCodeAtTimeout: Int, millisecondsToDelay: Long) = hostPackageManager.extendVerificationTimeout(id, verificationCodeAtTimeout, millisecondsToDelay)
    override fun verifyPendingInstall(id: Int, verificationCode: Int) = hostPackageManager.verifyPendingInstall(id, verificationCode)

}
