package app.mirro.android

import app.mirro.android.domain.engine.container.loader.ClassLoadOwner
import app.mirro.android.domain.engine.container.loader.ClassLoadRoutingPolicy
import app.mirro.android.domain.engine.container.loader.TargetClassIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetClassLoaderTest {

    @Test
    fun `ChatGPT duplicate ActivityResultContracts class is target owned`() {
        val requestPermission =
            "androidx.activity.result.contract.ActivityResultContracts\$RequestPermission"
        val index = TargetClassIndex.fromClassNames(
            setOf(requestPermission),
            source = "/data/app/com.openai.chatgpt/base.apk"
        )
        val policy = ClassLoadRoutingPolicy(index)

        assertEquals(ClassLoadOwner.TARGET_APK, policy.ownerFor(requestPermission))
        assertEquals(
            "/data/app/com.openai.chatgpt/base.apk",
            policy.sourceFor(requestPermission)
        )
    }

    @Test
    fun `platform and Mirro classes never route to target APK`() {
        val targetClass = "androidx.activity.result.contract.ActivityResultContracts\$GetContent"
        val policy = ClassLoadRoutingPolicy(
            TargetClassIndex.fromClassNames(setOf(targetClass))
        )

        assertEquals(ClassLoadOwner.PLATFORM, policy.ownerFor("android.app.Application"))
        assertEquals(ClassLoadOwner.PLATFORM, policy.ownerFor("java.lang.String"))
        assertEquals(ClassLoadOwner.HOST, policy.ownerFor("app.mirro.android.MainActivity"))
        assertEquals(ClassLoadOwner.HOST_FALLBACK, policy.ownerFor("androidx.activity.ComponentActivity"))
        assertEquals(ClassLoadOwner.TARGET_APK, policy.ownerFor(targetClass))
    }
}
