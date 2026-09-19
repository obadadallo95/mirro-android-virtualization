package app.mirro.android.domain.engine.container.loader

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import dalvik.system.PathClassLoader
import java.io.File

/**
 * Result container for loaded classes, resources, and classloader instance.
 */
data class LoadedApkRuntime(
    val descriptor: ApkDescriptor,
    val classLoader: ClassLoader,
    val resources: Resources,
    val applicationClass: Class<*>?
)

/**
 * Loads target APK bytecode and Android resource assets into an isolated ClassLoader.
 */
class DexRuntimeLoader(private val context: Context) {

    /**
     * Loads the target application's classes and resources.
     */
    fun load(descriptor: ApkDescriptor): LoadedApkRuntime {
        // Construct classpath string: base.apk + splits
        val dexPath = descriptor.allApkPaths.joinToString(File.pathSeparator)
        val nativeLibPath = descriptor.nativeLibraryDir

        // Create isolated PathClassLoader delegating to application ClassLoader
        val classLoader = PathClassLoader(
            dexPath,
            nativeLibPath,
            context.classLoader
        )

        // Load resources for the target package
        val resources = loadResources(descriptor)

        // Resolve application class if declared
        val appClass = descriptor.applicationClassName?.let { className ->
            try {
                Class.forName(className, false, classLoader)
            } catch (_: Exception) {
                null
            }
        }

        return LoadedApkRuntime(
            descriptor = descriptor,
            classLoader = classLoader,
            resources = resources,
            applicationClass = appClass
        )
    }

    private fun loadResources(descriptor: ApkDescriptor): Resources {
        return try {
            val targetContext = context.createPackageContext(
                descriptor.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            targetContext.resources
        } catch (_: Exception) {
            try {
                context.packageManager.getResourcesForApplication(descriptor.packageName)
            } catch (_: Exception) {
                // Fallback to host resources
                context.resources
            }
        }
    }
}
