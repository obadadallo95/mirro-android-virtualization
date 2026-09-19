package app.mirro.android

import app.mirro.android.domain.engine.container.loader.TargetActivityResolver
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetActivityResolverTest {

    @Test
    fun `does not guess a different declared activity when launch alias is unavailable`() {
        val descriptor = ApkDescriptor(
            packageName = "com.example.target",
            mainActivity = "com.example.target.GeneratedAlias",
            declaredActivities = listOf(
                "com.example.target.GeneratedAlias",
                "com.example.target.RealActivity"
            )
        )
        val loader = object : ClassLoader(TargetActivityResolverTest::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == "com.example.target.GeneratedAlias") {
                    throw ClassNotFoundException(name)
                }
                if (name == "com.example.target.RealActivity") {
                    return String::class.java
                }
                return super.loadClass(name, resolve)
            }
        }

        val result = TargetActivityResolver.resolve(descriptor, loader)

        assertNull(result.activityClassName)
        assertEquals(listOf("com.example.target.GeneratedAlias"), result.missingClassNames)
    }

    @Test
    fun `reports missing launch activity instead of returning a false success`() {
        val descriptor = ApkDescriptor(
            packageName = "com.example.target",
            mainActivity = "com.example.target.LoginActivity",
            declaredActivities = listOf("com.example.target.LoginActivity")
        )

        val result = TargetActivityResolver.resolve(
            descriptor,
            object : ClassLoader(null) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> =
                    throw ClassNotFoundException(name)
            }
        )

        assertNull(result.activityClassName)
        assertEquals(listOf("com.example.target.LoginActivity"), result.missingClassNames)
    }
}
