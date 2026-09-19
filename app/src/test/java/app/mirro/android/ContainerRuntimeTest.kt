package app.mirro.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mirro.android.domain.engine.container.model.ApkDescriptor
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
        assertEquals(packageName, identity.originalPackageName)
        assertEquals("mirro_test_clone_chatgpt", identity.webViewDataDirectorySuffix)

        val baseDir = identity.sandboxRootDir
        val filesDir = identity.filesDir
        val databasesDir = identity.databasesDir
        val sharedPrefsDir = identity.sharedPrefsDir
        val cacheDir = identity.cacheDir

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
    fun `apk descriptor models split apks and native architectures correctly`() {
        val descriptor = ApkDescriptor(
            packageName = "com.openai.chatgpt",
            versionCode = 12024100L,
            versionName = "1.2024.100",
            baseApkPath = "/data/app/com.openai.chatgpt/base.apk",
            splitApkPaths = listOf("/data/app/com.openai.chatgpt/split_config.arm64_v8a.apk"),
            nativeLibraryDir = "/data/app/com.openai.chatgpt/lib/arm64",
            targetSdk = 34,
            minSdk = 26,
            mainActivity = "com.openai.chatgpt.MainActivity",
            applicationClassName = "com.openai.chatgpt.ChatGPTApp",
            declaredActivities = listOf("com.openai.chatgpt.MainActivity", "com.openai.chatgpt.AuthActivity"),
            supportedAbis = listOf("arm64-v8a")
        )

        assertEquals("com.openai.chatgpt", descriptor.packageName)
        assertEquals(2, descriptor.allApkPaths.size)
        assertEquals("com.openai.chatgpt.MainActivity", descriptor.mainActivity)
    }
}

