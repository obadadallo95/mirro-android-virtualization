package app.mirro.android

import app.mirro.android.domain.engine.container.inspector.ApkDexClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkDexClassifierTest {

    @Test
    fun `language split without dex is not executable`() {
        val apk = createApk("resources.arsc", "res/values-ar/strings.xml")

        assertFalse(ApkDexClassifier.containsDex(apk.path))
    }

    @Test
    fun `base or code split with dex is executable`() {
        val apk = createApk("classes.dex", "classes2.dex")

        assertTrue(ApkDexClassifier.containsDex(apk.path))
    }

    @Test
    fun `missing or invalid apk is not executable`() {
        val missing = File.createTempFile("missing-apk", ".apk").apply {
            delete()
        }

        assertFalse(ApkDexClassifier.containsDex(missing.path))
    }

    private fun createApk(vararg entries: String): File {
        val apk = File.createTempFile("split", ".apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(0))
                zip.closeEntry()
            }
        }
        return apk
    }
}
