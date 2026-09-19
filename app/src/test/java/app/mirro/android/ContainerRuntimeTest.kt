package app.mirro.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mirro.android.domain.engine.container.context.VirtualContext
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import app.mirro.android.domain.engine.container.storage.VirtualFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContainerRuntimeTest {

    private lateinit var context: Context
    private lateinit var virtualFileSystem: VirtualFileSystem

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        virtualFileSystem = VirtualFileSystem(context)
    }

    @Test
    fun `virtual file system creates isolated clone directory hierarchy`() {
        val cloneId = "test-clone-chatgpt"
        val packageName = "com.openai.chatgpt"

        val identity = virtualFileSystem.createSandbox(cloneId, packageName)

        assertNotNull(identity)
        assertEquals(cloneId, identity.cloneId)
        assertEquals(packageName, identity.packageName)
        assertEquals("mirro_c_test-clone-chatgpt", identity.webViewDataDirectorySuffix)

        val baseDir = File(identity.baseDirPath)
        val filesDir = File(identity.filesDirPath)
        val databasesDir = File(identity.databasesDirPath)
        val sharedPrefsDir = File(identity.sharedPrefsDirPath)
        val cacheDir = File(identity.cacheDirPath)

        assertTrue("Base dir should exist", baseDir.exists())
        assertTrue("Files dir should exist", filesDir.exists())
        assertTrue("Databases dir should exist", databasesDir.exists())
        assertTrue("SharedPrefs dir should exist", sharedPrefsDir.exists())
        assertTrue("Cache dir should exist", cacheDir.exists())

        // Cleanup
        val deleted = virtualFileSystem.deleteSandbox(cloneId)
        assertTrue(deleted)
        assertFalse(baseDir.exists())
    }

    @Test
    fun `virtual context redirects files and databases to isolated sandbox`() {
        val cloneId = "test-clone-context"
        val packageName = "com.openai.chatgpt"

        val identity = virtualFileSystem.createSandbox(cloneId, packageName)
        val virtualContext = VirtualContext(context, identity, context.resources, null)

        val redirectedFilesDir = virtualContext.filesDir
        assertEquals(identity.filesDirPath, redirectedFilesDir.absolutePath)

        val redirectedCacheDir = virtualContext.cacheDir
        assertEquals(identity.cacheDirPath, redirectedCacheDir.absolutePath)

        val redirectedDb = virtualContext.getDatabasePath("chatgpt_session.db")
        assertTrue(redirectedDb.absolutePath.startsWith(identity.databasesDirPath))

        // Clean up
        virtualFileSystem.deleteSandbox(cloneId)
    }

    @Test
    fun `apk descriptor models split apks and native architectures correctly`() {
        val descriptor = ApkDescriptor(
            packageName = "com.openai.chatgpt",
            appLabel = "ChatGPT",
            versionName = "1.2024.100",
            versionCode = 12024100L,
            sourceDir = "/data/app/com.openai.chatgpt/base.apk",
            splitSourceDirs = listOf("/data/app/com.openai.chatgpt/split_config.arm64_v8a.apk"),
            nativeLibraryDir = "/data/app/com.openai.chatgpt/lib/arm64",
            dataDir = "/data/user/0/com.openai.chatgpt",
            targetSdkVersion = 34,
            minSdkVersion = 26,
            mainActivityClassName = "com.openai.chatgpt.MainActivity",
            applicationClassName = "com.openai.chatgpt.ChatGPTApp",
            allActivities = listOf("com.openai.chatgpt.MainActivity", "com.openai.chatgpt.AuthActivity"),
            supportedAbis = listOf("arm64-v8a"),
            usesCleartextTraffic = false
        )

        assertEquals("com.openai.chatgpt", descriptor.packageName)
        assertEquals(2, descriptor.allApkPaths.size)
        assertTrue(descriptor.hasSplits)
        assertEquals("com.openai.chatgpt.MainActivity", descriptor.mainActivityClassName)
    }
}
