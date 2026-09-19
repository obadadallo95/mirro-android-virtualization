package app.mirro.android.domain.engine.container.runtime

import android.content.ComponentName
import android.content.ContentProvider
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import app.mirro.android.domain.engine.container.context.VirtualContext

/**
 * Creates the small set of target manifest providers that are required before Application.onCreate.
 * ActivityThread normally owns this phase; a user-space container has to reproduce it explicitly.
 */
class TargetFrameworkProviderBootstrapper {

    private val eagerProviderNames = setOf(
        "androidx.startup.InitializationProvider",
        "org.jetbrains.compose.resources.AndroidContextProvider"
    )

    fun initialize(
        virtualContext: VirtualContext,
        classLoader: ClassLoader,
        log: (String) -> Unit
    ): List<ContentProvider> {
        val descriptor = virtualContext.runtime.descriptor
        val providers = mutableListOf<ContentProvider>()

        descriptor.declaredProviders
            .filter { it in eagerProviderNames }
            .forEach { providerClassName ->
                runCatching {
                    val provider = classLoader
                        .loadClass(providerClassName)
                        .getDeclaredConstructor()
                        .apply { isAccessible = true }
                        .newInstance() as ContentProvider

                    val originalInfo = virtualContext.packageManager.getProviderInfo(
                        ComponentName(descriptor.packageName, providerClassName),
                        PackageManager.GET_META_DATA
                    )
                    val providerInfo = ProviderInfo(originalInfo).apply {
                        packageName = descriptor.packageName
                        name = providerClassName
                        applicationInfo = virtualContext.virtualPackageManager.getApplicationInfo(0)
                    }

                    val attachInfo = ContentProvider::class.java.declaredMethods
                        .firstOrNull { it.name == "attachInfo" && it.parameterTypes.size == 2 }
                        ?: error("ContentProvider.attachInfo is unavailable")
                    attachInfo.isAccessible = true
                    attachInfo.invoke(provider, virtualContext, providerInfo)
                    if (!provider.onCreate()) {
                        log("Target provider onCreate returned false: $providerClassName")
                    }
                    providerInfo.authority
                        ?.split(';')
                        ?.filter { it.isNotBlank() }
                        ?.forEach { authority ->
                            virtualContext.virtualContentManager.register(
                                authority = authority,
                                providerName = providerClassName,
                                provider = provider
                            )
                        }
                    providers += provider
                    log("Target framework provider initialized before Application.onCreate: $providerClassName")
                }.onFailure { error ->
                    if (providerClassName == "org.jetbrains.compose.resources.AndroidContextProvider") {
                        throw IllegalStateException(
                            "Required target provider failed: $providerClassName",
                            error
                        )
                    }
                    log("Optional target provider initialization failed: $providerClassName: ${error.message}")
                }
            }

        return providers
    }
}
