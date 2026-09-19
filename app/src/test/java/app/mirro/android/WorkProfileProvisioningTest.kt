package app.mirro.android

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import app.mirro.android.domain.engine.workprofile.MirroDeviceAdminReceiver
import app.mirro.android.domain.engine.workprofile.ProfileProvisioningManager
import app.mirro.android.domain.engine.workprofile.ProvisioningStatus
import app.mirro.android.domain.engine.workprofile.WorkProfileCloneEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkProfileProvisioningTest {

    private lateinit var context: Context
    private lateinit var provisioningManager: ProfileProvisioningManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        provisioningManager = ProfileProvisioningManager(context)
    }

    @Test
    fun `device admin receiver component name matches receiver class`() {
        val component = MirroDeviceAdminReceiver.getComponentName(context)
        assertEquals(context.packageName, component.packageName)
        assertEquals(MirroDeviceAdminReceiver::class.java.name, component.className)
    }

    @Test
    fun `provisioning intent contains required device admin extra`() {
        val intent = provisioningManager.createProvisioningIntent()
        assertEquals(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE, intent.action)

        val componentExtra = IntentCompat.getParcelableExtra(
            intent,
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            ComponentName::class.java
        )
        assertNotNull(componentExtra)
        assertEquals(context.packageName, componentExtra?.packageName)
        assertEquals(MirroDeviceAdminReceiver::class.java.name, componentExtra?.className)
    }

    @Test
    fun `activity result cancellation returns Failed state`() {
        val result = provisioningManager.handleActivityResult(Activity.RESULT_CANCELED)
        assertTrue(result is ProvisioningStatus.Failed)
        val failed = result as ProvisioningStatus.Failed
        assertTrue(failed.error.contains("canceled", ignoreCase = true))
    }

    @Test
    fun `work profile engine reports unavailable until provisioned`() {
        val engine = WorkProfileCloneEngine(context, provisioningManager)
        val availability = engine.checkAvailability()

        // In Robolectric standard env without profile owner, availability must honestly be false
        assertFalse(availability.isAvailable)
        assertTrue(availability.missingRequirements.isNotEmpty())
    }

    @Test
    fun `provisioning status states represent correct device conditions`() {
        val activeStatus = ProvisioningStatus.Active(isCurrentProcessInProfile = true, profileCount = 2)
        assertTrue(activeStatus.isCurrentProcessInProfile)
        assertEquals(2, activeStatus.profileCount)

        val conflictStatus = ProvisioningStatus.Conflict("Existing MDM profile detected")
        assertEquals("Existing MDM profile detected", conflictStatus.message)

        val notSupportedStatus = ProvisioningStatus.NotSupported("Missing FEATURE_MANAGED_USERS")
        assertEquals("Missing FEATURE_MANAGED_USERS", notSupportedStatus.reason)
    }
}
