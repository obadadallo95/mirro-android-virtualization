package app.mirro.android.domain.engine.container.inspector

import java.io.IOException
import java.util.zip.ZipFile

/** Identifies APKs that can contribute bytecode to the target class loader. */
object ApkDexClassifier {

    private val dexEntryPattern = Regex("^classes(?:[0-9]+)?\\.dex$")

    /**
     * Returns true only when the APK contains a standard Dalvik executable entry.
     * Resource, language, density, and configuration splits normally do not.
     */
    fun containsDex(apkPath: String): Boolean {
        if (apkPath.isBlank()) return false

        return try {
            ZipFile(apkPath).use { zipFile ->
                zipFile.entries().asSequence().any { entry ->
                    !entry.isDirectory && dexEntryPattern.matches(entry.name)
                }
            }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
