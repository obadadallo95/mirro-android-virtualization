package app.mirro.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.engine.workprofile.ProfileAppDiscoveryManager
import app.mirro.android.domain.engine.workprofile.ProfileProvisioningManager
import app.mirro.android.domain.engine.workprofile.WorkProfileCloneEngine
import app.mirro.android.domain.engine.workprofile.WorkProfileLaunchResult
import app.mirro.android.domain.engine.workprofile.WorkProfileLaunchStatus
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.ProfileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkProfileLaunchTest {

    private lateinit var context: Context
    private lateinit var engine: WorkProfileCloneEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val provisioningManager = ProfileProvisioningManager(context)
        val discoveryManager = ProfileAppDiscoveryManager(context, provisioningManager)
        engine = WorkProfileCloneEngine(
            context = context,
            provisioningManager = provisioningManager,
            appDiscoveryManager = discoveryManager
        )
    }

    @Test
    fun `launch without active managed profile returns PROFILE_UNAVAILABLE`() {
        val testInstance = CloneInstance(
            id = "test-chatgpt-1",
            originalPackageName = "com.openai.chatgpt",
            originalAppLabel = "ChatGPT",
            customName = "ChatGPT (Mirro)",
            badgeColorHex = "#6750A4",
            badgeSymbol = "W",
            engineType = CloneEngineType.WORK_PROFILE,
            profileType = ProfileType.MIRRO_MANAGED,
            userSerialNumber = 100L
        )

        val result = engine.executeProfileLaunch(testInstance)
        assertTrue(result is WorkProfileLaunchResult.Failure)
        val failure = result as WorkProfileLaunchResult.Failure
        assertEquals(WorkProfileLaunchStatus.PROFILE_UNAVAILABLE, failure.status)
        assertTrue(failure.reason.contains("not active", ignoreCase = true))
    }

    @Test
    fun `launchInstance does not fake launch and fails gracefully when profile missing`() = runBlocking {
        val testInstance = CloneInstance(
            id = "test-chatgpt-2",
            originalPackageName = "com.openai.chatgpt",
            originalAppLabel = "ChatGPT",
            customName = "ChatGPT (Mirro)",
            badgeColorHex = "#6750A4",
            badgeSymbol = "2",
            engineType = CloneEngineType.WORK_PROFILE,
            profileType = ProfileType.MIRRO_MANAGED,
            userSerialNumber = 0L
        )

        val execResult = engine.launchInstance(testInstance)
        assertTrue(execResult is EngineExecutionResult.Failure)
        val fail = execResult as EngineExecutionResult.Failure
        assertTrue(fail.userMessage.isNotEmpty())
    }

    @Test
    fun `freeze and unfreeze honestly report Android platform restriction`() = runBlocking {
        val testInstance = CloneInstance(
            id = "test-chatgpt-3",
            originalPackageName = "com.openai.chatgpt",
            originalAppLabel = "ChatGPT",
            customName = "ChatGPT (Mirro)",
            badgeColorHex = "#6750A4",
            badgeSymbol = "W",
            engineType = CloneEngineType.WORK_PROFILE
        )

        val freezeResult = engine.freezeInstance(testInstance)
        assertTrue(freezeResult is EngineExecutionResult.Unsupported)
        val freezeUnsupported = freezeResult as EngineExecutionResult.Unsupported
        assertTrue(freezeUnsupported.reason.contains("Quick Settings", ignoreCase = true))

        val unfreezeResult = engine.unfreezeInstance(testInstance)
        assertTrue(unfreezeResult is EngineExecutionResult.Unsupported)
    }

    @Test
    fun `pinned shortcut creation is handled via engine shortcut manager`() = runBlocking {
        val testInstance = CloneInstance(
            id = "test-chatgpt-shortcut",
            originalPackageName = "com.openai.chatgpt",
            originalAppLabel = "ChatGPT",
            customName = "ChatGPT Work",
            badgeColorHex = "#6750A4",
            badgeSymbol = "W",
            engineType = CloneEngineType.WORK_PROFILE
        )

        val result = engine.createShortcut(testInstance)
        // In Robolectric environment with test launcher support
        assertFalse(result is EngineExecutionResult.NotImplemented)
    }
}
