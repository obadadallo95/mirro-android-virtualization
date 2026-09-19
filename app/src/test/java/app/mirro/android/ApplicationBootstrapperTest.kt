package app.mirro.android

import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.loader.LoadedApkRuntime
import app.mirro.android.domain.engine.container.loader.TargetClassIndex
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.ApplicationBootstrapStage
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.engine.container.runtime.ApplicationBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.InvocationTargetException

// Test application classes for bootstrapping simulation
class SuccessfulTestApp : Application() {
    var baseContextAttached = false
    var attachedBasePackageName: String? = null
    var onCreateCalled = false

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        baseContextAttached = true
        attachedBasePackageName = base?.packageName
    }

    override fun onCreate() {
        super.onCreate()
        onCreateCalled = true
    }
}

class FailingConstructorTestApp : Application() {
    init {
        throw IllegalStateException("Initialization failed in target constructor")
    }
}

class FailingOnCreateTestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        throw UnsatisfiedLinkError("dlopen failed: library \"libtarget_native.so\" not found")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationBootstrapperTest {

    private lateinit var context: Context
    private lateinit var bootstrapper: ApplicationBootstrapper
    private lateinit var virtualContext: VirtualContext
    private lateinit var identity: VirtualRuntimeIdentity

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        bootstrapper = ApplicationBootstrapper()

        val sandboxDir = File(context.filesDir, "test_sandbox_bootstrap")
        sandboxDir.mkdirs()

        identity = VirtualRuntimeIdentity(
            cloneId = "test-clone-app",
            originalPackageName = "com.openai.chatgpt",
            virtualUserId = 10,
            runtimeInstanceId = "inst_123",
            sandboxRootDir = sandboxDir,
            webViewDataDirectorySuffix = "mirro_test"
        )

        val descriptor = ApkDescriptor(
            packageName = "com.openai.chatgpt",
            versionCode = 100L,
            versionName = "1.0",
            baseApkPath = "/data/app/base.apk",
            mainActivity = "com.openai.chatgpt.MainActivity",
            applicationClassName = SuccessfulTestApp::class.java.name
        )

        val runtime = LoadedApkRuntime(
            descriptor = descriptor,
            classLoader = javaClass.classLoader!!,
            resources = context.resources,
            classIndex = TargetClassIndex.fromClassNames(setOf(SuccessfulTestApp::class.java.name))
        )

        virtualContext = VirtualContext(
            base = context,
            identity = identity,
            runtime = runtime
        )
    }

    @Test
    fun `successful application bootstrap completes all stages and attaches virtual context`() {
        val originalThreadCl = Thread.currentThread().contextClassLoader
        val logs = mutableListOf<String>()

        val result = bootstrapper.bootstrap(
            applicationClassName = SuccessfulTestApp::class.java.name,
            classLoader = javaClass.classLoader!!,
            virtualContext = virtualContext,
            log = { logs.add(it) }
        )

        assertTrue(result.isSuccess)
        assertEquals(ApplicationBootstrapStage.APPLICATION_ONCREATE_COMPLETED, result.currentStage)
        assertNotNull(result.application)

        val app = result.application as SuccessfulTestApp
        assertTrue("Base context should be attached", app.baseContextAttached)
        assertEquals(context.packageName, app.attachedBasePackageName)
        assertTrue("onCreate should be called", app.onCreateCalled)
        assertSame("VirtualContext should return target application", app, virtualContext.getApplicationContext())
        assertEquals("com.openai.chatgpt", virtualContext.packageName)
        assertEquals(context.opPackageName, virtualContext.opPackageName)
        assertEquals(identity.sandboxRootDir.absolutePath, virtualContext.dataDir.absolutePath)
        assertEquals(originalThreadCl, Thread.currentThread().contextClassLoader)
    }

    @Test
    fun `failing constructor records APPLICATION_INSTANCE_CREATED failure stage`() {
        val originalThreadCl = Thread.currentThread().contextClassLoader
        val logs = mutableListOf<String>()

        val result = bootstrapper.bootstrap(
            applicationClassName = FailingConstructorTestApp::class.java.name,
            classLoader = javaClass.classLoader!!,
            virtualContext = virtualContext,
            log = { logs.add(it) }
        )

        assertFalse(result.isSuccess)
        assertEquals(ApplicationBootstrapStage.FAILED, result.currentStage)
        assertEquals(ApplicationBootstrapStage.APPLICATION_INSTANCE_CREATED, result.failedStage)
        assertEquals("java.lang.IllegalStateException", result.exceptionClass)
        assertEquals("Initialization failed in target constructor", result.exceptionMessage)
        assertNotNull(result.stackTrace)
        assertEquals(originalThreadCl, Thread.currentThread().contextClassLoader)
    }

    @Test
    fun `failing onCreate records APPLICATION_ONCREATE_STARTED and native error cause`() {
        val originalThreadCl = Thread.currentThread().contextClassLoader
        val logs = mutableListOf<String>()

        val result = bootstrapper.bootstrap(
            applicationClassName = FailingOnCreateTestApp::class.java.name,
            classLoader = javaClass.classLoader!!,
            virtualContext = virtualContext,
            log = { logs.add(it) }
        )

        assertFalse(result.isSuccess)
        assertEquals(ApplicationBootstrapStage.FAILED, result.currentStage)
        assertEquals(ApplicationBootstrapStage.APPLICATION_ONCREATE_STARTED, result.failedStage)
        assertEquals("java.lang.UnsatisfiedLinkError", result.exceptionClass)
        assertTrue(result.exceptionMessage!!.contains("libtarget_native.so"))
        assertEquals(originalThreadCl, Thread.currentThread().contextClassLoader)
    }

    @Test
    fun `exception unwrapping unwraps nested InvocationTargetExceptions recursively`() {
        val root = UnsatisfiedLinkError("dlopen failed")
        val ite1 = InvocationTargetException(root)
        val ite2 = InvocationTargetException(ite1)
        val runtimeWrapper = RuntimeException("Wrapper", ite2)

        val unwrapped = ApplicationBootstrapper.unwrapException(runtimeWrapper)
        val rootCause = ApplicationBootstrapper.getRootCause(runtimeWrapper)

        assertEquals("dlopen failed", unwrapped.message)
        assertEquals("dlopen failed", rootCause.message)
        assertTrue(unwrapped is UnsatisfiedLinkError)
    }

    @Test
    fun `virtual context routes files and databases to isolated sandbox`() {
        assertEquals("com.openai.chatgpt", virtualContext.packageName)
        assertEquals(identity.filesDir.absolutePath, virtualContext.filesDir.absolutePath)
        assertEquals(identity.cacheDir.absolutePath, virtualContext.cacheDir.absolutePath)
        assertEquals(identity.codeCacheDir.absolutePath, virtualContext.codeCacheDir.absolutePath)
        assertEquals(identity.noBackupDir.absolutePath, virtualContext.noBackupFilesDir.absolutePath)

        val dbFile = virtualContext.getDatabasePath("chatgpt_local.db")
        assertEquals(File(identity.databasesDir, "chatgpt_local.db").absolutePath, dbFile.absolutePath)
        assertTrue(identity.databasesDir.exists())
    }
}
