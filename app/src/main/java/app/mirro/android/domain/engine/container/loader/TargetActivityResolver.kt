package app.mirro.android.domain.engine.container.loader

import app.mirro.android.domain.engine.container.model.ApkDescriptor

class TargetActivityResolutionException(
    val resolution: ClassResolutionResult
) : IllegalStateException(
    "Unable to resolve target Activity ${resolution.className}: " +
        "category=${resolution.category}, ${resolution.message}"
)

/**
 * Resolves the launch Activity against the classes that the container actually loaded.
 *
 * PackageManager metadata is not proof that a class is present in the DEX set. This is common
 * with generated aliases and applications that install code through a secondary loader. The
 * resolver verifies the actual launch Activity and reports its availability. The host may retry
 * this class after Application bootstrap when the target installs secondary code asynchronously.
 */
object TargetActivityResolver {

    data class Resolution(
        val activityClassName: String?,
        val attemptedClassNames: List<String>,
        val missingClassNames: List<String>,
        val classResolution: ClassResolutionRecord? = null,
        val failureCategory: ClassResolutionCategory? = null
    )

    fun resolve(descriptor: ApkDescriptor, dynamicCodeManager: DynamicCodeManager): Resolution {
        val candidates = listOfNotNull(descriptor.mainActivity)
        val missing = mutableListOf<String>()
        candidates.forEach { className ->
            val result = dynamicCodeManager.resolveClass(
                className = className,
                phase = LoaderObservationPhase.COMPONENT_RESOLUTION,
                componentResolution = true
            )
            if (result.found) {
                return Resolution(
                    activityClassName = className,
                    attemptedClassNames = candidates,
                    missingClassNames = missing,
                    classResolution = result.toRecord(),
                    failureCategory = result.category
                )
            }
            missing += className
            return Resolution(
                activityClassName = null,
                attemptedClassNames = candidates,
                missingClassNames = missing,
                classResolution = result.toRecord(),
                failureCategory = result.category
            )
        }
        return Resolution(null, candidates, missing)
    }

    fun resolve(descriptor: ApkDescriptor, classLoader: ClassLoader): Resolution {
        return resolveCandidates(descriptor) { className ->
            try {
                classLoader.loadClass(className)
                true
            } catch (_: ClassNotFoundException) {
                false
            } catch (_: LinkageError) {
                false
            }
        }
    }

    /** Fast preflight for the runtime path. It never loads or links classes on the UI thread. */
    fun resolve(descriptor: ApkDescriptor, classIndex: TargetClassIndex): Resolution {
        return resolveCandidates(descriptor, classIndex::contains)
    }

    private fun resolveCandidates(
        descriptor: ApkDescriptor,
        isAvailable: (String) -> Boolean
    ): Resolution {
        val candidates = listOfNotNull(descriptor.mainActivity)
        val missing = mutableListOf<String>()
        candidates.forEach { className ->
            if (isAvailable(className)) return Resolution(className, candidates, missing)
            missing += className
        }
        return Resolution(null, candidates, missing)
    }
}
