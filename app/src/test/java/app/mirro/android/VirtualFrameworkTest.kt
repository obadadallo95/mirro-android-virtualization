package app.mirro.android

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import app.mirro.android.domain.engine.container.framework.ActivityLaunchMode
import app.mirro.android.domain.engine.container.framework.IntentRouter
import app.mirro.android.domain.engine.container.framework.IntentRouteKind
import app.mirro.android.domain.engine.container.framework.VirtualAppOpMode
import app.mirro.android.domain.engine.container.framework.VirtualAppOpsManager
import app.mirro.android.domain.engine.container.framework.VirtualBroadcastManager
import app.mirro.android.domain.engine.container.framework.VirtualFileProviderUriMapper
import app.mirro.android.domain.engine.container.framework.VirtualNotificationManager
import app.mirro.android.domain.engine.container.framework.VirtualPackageRegistry
import app.mirro.android.domain.engine.container.framework.VirtualPendingIntentManager
import app.mirro.android.domain.engine.container.framework.VirtualPermissionManager
import app.mirro.android.domain.engine.container.framework.VirtualPermissionState
import app.mirro.android.domain.engine.container.framework.VirtualStorageManager
import app.mirro.android.domain.engine.container.framework.VirtualActivityTaskManager
import app.mirro.android.domain.engine.container.framework.VirtualServiceContractRegistry
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VirtualFrameworkTest {

    private val descriptor = ApkDescriptor(
        packageName = "com.example.guest",
        baseApkPath = "/data/app/guest/base.apk",
        mainActivity = "com.example.guest.MainActivity",
        declaredActivities = listOf("com.example.guest.MainActivity"),
        requestedPermissions = listOf("android.permission.CAMERA")
    )

    private val identity = VirtualRuntimeIdentity(
        cloneId = "clone-a",
        originalPackageName = descriptor.packageName,
        virtualUserId = 1,
        runtimeInstanceId = "session-a",
        sandboxRootDir = File("/tmp/mirro-framework-test/clone-a"),
        webViewDataDirectorySuffix = "mirro_clone_a"
    )

    @Test
    fun packageRegistry_keepsTargetMetadataGuestOwned() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = VirtualPackageRegistry(context.packageManager, descriptor, identity, context.packageName)
        val record = registry.targetRecord

        assertEquals(VirtualPackageRegistry::class.java, registry::class.java)
        assertEquals("com.example.guest", record.packageName)
        assertEquals("com.example.guest", record.applicationInfo.packageName)
        assertEquals(identity.cloneId, record.cloneId)
        assertTrue(record.descriptiveSigningMetadata.contains("not physical"))
        assertEquals(app.mirro.android.domain.engine.container.framework.VirtualValueOrigin.GUEST_VALUE, registry.classification(record.packageName))
    }

    @Test
    fun permissions_doNotTurnHostGrantIntoVirtualGrant() {
        val manager = VirtualPermissionManager(descriptor) { android.content.pm.PackageManager.PERMISSION_GRANTED }
        assertEquals(VirtualPermissionState.REQUESTED, manager.state("android.permission.CAMERA"))
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, manager.check("android.permission.CAMERA"))
        manager.setState("android.permission.CAMERA", VirtualPermissionState.GRANTED_VIRTUAL)
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, manager.check("android.permission.CAMERA"))
    }

    @Test
    fun appOps_exposesIdentityRequirementInsteadOfFakingIt() {
        val manager = VirtualAppOpsManager()
        assertFalse(manager.note("camera").value == true)
        manager.setMode("camera", VirtualAppOpMode.GUEST_LOCAL)
        assertEquals(true, manager.note("camera").value)
        manager.setMode("camera", VirtualAppOpMode.HOST_DENIED)
        assertEquals(false, manager.note("camera").value)
    }

    @Test
    fun taskManager_supportsSingleTopClearTopAndBack() {
        val manager = VirtualActivityTaskManager("clone-a")
        val componentA = ComponentName(descriptor.packageName, "A")
        val componentB = ComponentName(descriptor.packageName, "B")
        val first = manager.start(componentA, Intent().setComponent(componentA))
        val second = manager.start(componentB, Intent().setComponent(componentB))
        val top = manager.start(componentB, Intent().setComponent(componentB), ActivityLaunchMode.SINGLE_TOP)
        assertEquals(second.token, top.token)
        val cleared = manager.start(componentA, Intent().setComponent(componentA), ActivityLaunchMode.CLEAR_TOP)
        assertEquals(first.token, cleared.token)
        assertEquals(1, manager.snapshot().size)
        assertNull(manager.back())
    }

    @Test
    fun fileProviderUris_areCloneScopedAndRejectTraversal() {
        val mapper = VirtualFileProviderUriMapper(identity)
        val mapped = mapper.map("com.example.files", "photo.jpg")
        assertNotNull(mapped.value)
        assertTrue(mapper.owns(mapped.value!!))
        assertNull(mapper.map("com.example.files", "../secret").value)
    }

    @Test
    fun broadcastAndServiceContracts_areCloneLocal() {
        val broadcasts = VirtualBroadcastManager("clone-a")
        var received = 0
        broadcasts.register("receiver", "com.example.ACTION") { received += 1 }
        assertEquals(1, broadcasts.send(Intent("com.example.ACTION")))
        assertEquals(1, received)

        val services = VirtualServiceContractRegistry("clone-a")
        val component = ComponentName(descriptor.packageName, "Service")
        assertEquals("clone-a", services.start(component, foreground = false).value?.cloneId)
        assertEquals(1, services.bind(component).value?.boundClients)
        assertTrue(services.stop(component))
    }

    @Test
    fun pendingIntent_rejectsWrongCloneAndNotificationNamespacesIds() {
        val pending = VirtualPendingIntentManager("clone-a", "session-a")
        val created = pending.create(descriptor.packageName, null, 7, Intent("com.example.ACTION"), mutable = false).value!!
        assertNotNull(pending.send(created, "clone-a").value)
        assertNull(pending.send(created, "clone-b").value)

        val notifications = VirtualNotificationManager("clone-a")
        val notification = notifications.post(7, "default", "title", "text").value!!
        assertEquals("clone-a:default", notification.channelId)
        assertEquals(7, notification.id)
    }

    @Test
    fun storageManager_keepsCloneDirectoriesDistinct() {
        val manager = VirtualStorageManager(identity)
        assertTrue(manager.filesDir().path.contains("clone-a"))
        assertTrue(manager.database("db.sqlite").path.contains("databases"))
        assertFalse(manager.externalFilesDir(null, null).path == manager.filesDir().path)
    }

    @Test
    fun intentRouter_doesNotRandomlyRouteImplicitExternalIntent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = VirtualPackageRegistry(context.packageManager, descriptor, identity, context.packageName)
        val router = IntentRouter(identity, registry)
        assertEquals(IntentRouteKind.TARGET_INTERNAL, router.classify(Intent().setPackage(descriptor.packageName)).kind)
        assertEquals(IntentRouteKind.BROWSER, router.classify(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test"))).kind)
        assertEquals(IntentRouteKind.UNSUPPORTED, router.classify(Intent("com.example.UNKNOWN")).kind)
    }
}
