package app.mirro.android.domain.engine.container.loader

import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.NativeRuntimeState
import java.security.CodeSource
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/** Where a loader or code source came from. This is descriptive, never an identity spoof. */
enum class LoaderSourceType {
    ROOT_TARGET,
    INSTALLED_SPLIT,
    DEX_CLASS_LOADER,
    PATH_CLASS_LOADER,
    IN_MEMORY_DEX,
    FEATURE,
    TARGET_CREATED,
    NATIVE_ORIGIN,
    UNKNOWN
}

enum class LoaderDelegationMode {
    PARENT_FIRST,
    CHILD_FIRST,
    DELEGATE_LAST,
    UNKNOWN
}

enum class LoaderRegistrationState {
    REGISTERED,
    METADATA_ONLY
}

enum class LoaderObservationPhase {
    BEFORE_APPLICATION_ONCREATE,
    AFTER_APPLICATION_ONCREATE,
    LATE_OBSERVATION,
    COMPONENT_RESOLUTION
}

/** Immutable diagnostics representation of one loader graph node. */
data class LoaderNode(
    val id: String,
    val loaderClass: String,
    val parentLoaderId: String?,
    val delegationMode: LoaderDelegationMode,
    val firstSeenAt: Long,
    val sourceType: LoaderSourceType,
    val codeSourcePaths: List<String> = emptyList(),
    val nativeLibrarySearchPaths: List<String> = emptyList(),
    val registrationState: LoaderRegistrationState = LoaderRegistrationState.REGISTERED,
    val firstObservedPhase: LoaderObservationPhase,
    val canResolveClasses: Boolean,
    val noFileSource: Boolean = false
)

data class LoaderGraphSnapshot(
    val nodes: List<LoaderNode> = emptyList(),
    val resolutionCount: Int = 0,
    val lastObservedAt: Long? = null
)

enum class ClassResolutionCategory {
    CLASS_FOUND_STATIC,
    CLASS_FOUND_DYNAMIC,
    CLASS_NOT_FOUND_STATIC,
    DYNAMIC_LOADER_UNSEEN,
    FEATURE_NOT_AVAILABLE,
    PACKED_UNSUPPORTED,
    NATIVE_LOAD_FAILED
}

data class ClassResolutionAttempt(
    val loaderId: String,
    val loaderClass: String,
    val success: Boolean,
    val definingClassLoader: String? = null,
    val sourcePaths: List<String> = emptyList(),
    val errorClass: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ClassResolutionRecord(
    val className: String,
    val category: ClassResolutionCategory,
    val found: Boolean,
    val loaderNodeId: String? = null,
    val definingClassLoader: String? = null,
    val sourcePaths: List<String> = emptyList(),
    val attempts: List<ClassResolutionAttempt> = emptyList(),
    val phase: LoaderObservationPhase,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ClassResolutionResult(
    val className: String,
    val category: ClassResolutionCategory,
    val resolvedClass: Class<*>? = null,
    val loaderNodeId: String? = null,
    val definingClassLoader: String? = null,
    val sourcePaths: List<String> = emptyList(),
    val attempts: List<ClassResolutionAttempt> = emptyList(),
    val phase: LoaderObservationPhase,
    val message: String
) {
    val found: Boolean get() = resolvedClass != null

    fun toRecord(): ClassResolutionRecord = ClassResolutionRecord(
        className = className,
        category = category,
        found = found,
        loaderNodeId = loaderNodeId,
        definingClassLoader = definingClassLoader,
        sourcePaths = sourcePaths,
        attempts = attempts,
        phase = phase,
        message = message
    )
}

/**
 * Owns the observable target loader graph. It deliberately has no hidden global hooks: callers
 * must register a loader or expose it through a public/runtime-visible boundary.
 */
class DynamicCodeManager(
    private val descriptor: ApkDescriptor,
    private val rootLoader: MirroTargetClassLoader,
    private val classIndex: TargetClassIndex,
    private val nativeState: NativeRuntimeState,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private data class Entry(
        val loader: ClassLoader?,
        var node: LoaderNode
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val loaderIds = IdentityHashMap<ClassLoader, String>()
    private val resolutionRecords = mutableListOf<ClassResolutionRecord>()
    private val unavailableFeatures = linkedMapOf<String, String>()
    private val packedUnsupportedClasses = linkedMapOf<String, String>()

    init {
        registerRootLoader()
        descriptor.splitSources.forEach { split ->
            if (split.kind == app.mirro.android.domain.engine.container.model.SplitSourceKind.EXECUTABLE) {
                registerMetadataSource(
                    sourceType = LoaderSourceType.INSTALLED_SPLIT,
                    path = split.path,
                    phase = LoaderObservationPhase.BEFORE_APPLICATION_ONCREATE
                )
            }
        }
    }

    fun registerLoader(
        loader: ClassLoader,
        sourceType: LoaderSourceType = inferSourceType(loader),
        phase: LoaderObservationPhase = LoaderObservationPhase.LATE_OBSERVATION,
        codeSourcePaths: List<String> = emptyList(),
        nativeLibrarySearchPaths: List<String> = emptyList(),
        delegationMode: LoaderDelegationMode = LoaderDelegationMode.UNKNOWN
    ): LoaderNode {
        synchronized(lock) {
            findKnownNode(loader)?.let { return it }

            val now = clock()
            val id = "loader-${Integer.toHexString(System.identityHashCode(loader))}"
            val parentId = loader.parent?.let { parent -> findKnownNode(parent)?.id }
            val node = LoaderNode(
                id = id,
                loaderClass = loader.javaClass.name,
                parentLoaderId = parentId,
                delegationMode = delegationMode,
                firstSeenAt = now,
                sourceType = sourceType,
                codeSourcePaths = codeSourcePaths.distinct(),
                nativeLibrarySearchPaths = nativeLibrarySearchPaths.distinct(),
                registrationState = LoaderRegistrationState.REGISTERED,
                firstObservedPhase = phase,
                canResolveClasses = true,
                noFileSource = sourceType == LoaderSourceType.IN_MEMORY_DEX && codeSourcePaths.isEmpty()
            )
            entries[id] = Entry(loader, node)
            loaderIds[loader] = id
            return node
        }
    }

    /** Registers an in-memory loader without inventing a filesystem path. */
    fun registerInMemoryLoader(
        loader: ClassLoader,
        phase: LoaderObservationPhase = LoaderObservationPhase.LATE_OBSERVATION
    ): LoaderNode = registerLoader(
        loader = loader,
        sourceType = LoaderSourceType.IN_MEMORY_DEX,
        phase = phase,
        codeSourcePaths = emptyList()
    )

    /** Records that a declared feature is unavailable without pretending its code was loaded. */
    fun markFeatureUnavailable(className: String, reason: String) {
        synchronized(lock) {
            unavailableFeatures[className] = reason
        }
    }

    /** Records a deliberately unsupported packed/guarded class boundary. */
    fun markPackedUnsupported(className: String, reason: String) {
        synchronized(lock) {
            packedUnsupportedClasses[className] = reason
        }
    }

    /**
     * Observes only public/runtime-visible loader references. It intentionally does not walk
     * private framework fields or install process-wide hooks.
     */
    fun observeSupportedBoundaries(
        application: android.app.Application?,
        context: android.content.Context?,
        phase: LoaderObservationPhase = LoaderObservationPhase.LATE_OBSERVATION
    ) {
        listOfNotNull(
            application?.javaClass?.classLoader,
            context?.classLoader,
            Thread.currentThread().contextClassLoader
        ).forEach { loader ->
            observeLoaderReference(loader, phase)
        }
    }

    /** Records the actual defining loader when a target class was successfully resolved. */
    fun observeClass(
        loadedClass: Class<*>,
        phase: LoaderObservationPhase = LoaderObservationPhase.LATE_OBSERVATION
    ): LoaderNode? {
        val definingLoader = loadedClass.classLoader ?: return null
        if (rootLoader.isTargetDefiningLoader(definingLoader)) {
            return node("root-target")
        }
        if (definingLoader === rootLoader.hostClassLoader) return null
        return registerLoader(
            loader = definingLoader,
            sourceType = inferSourceType(definingLoader),
            phase = phase,
            codeSourcePaths = codeSourcePathsFor(loadedClass)
        )
    }

    fun resolveClass(
        className: String,
        phase: LoaderObservationPhase = LoaderObservationPhase.COMPONENT_RESOLUTION,
        componentResolution: Boolean = false
    ): ClassResolutionResult {
        val attempts = mutableListOf<ClassResolutionAttempt>()
        val candidateEntries = resolutionEntries()

        candidateEntries.forEach { entry ->
            val loader = entry.loader ?: return@forEach
            val startedAt = clock()
            try {
                val loaded = loader.loadClass(className)
                val definingLoader = loaded.classLoader
                val targetNode = targetNodeForDefiningLoader(definingLoader, loaded, phase)
                if (targetNode == null) {
                    attempts += ClassResolutionAttempt(
                        loaderId = entry.node.id,
                        loaderClass = entry.node.loaderClass,
                        success = false,
                        definingClassLoader = describe(definingLoader),
                        sourcePaths = sourcePathsFor(entry.node, className),
                        errorClass = "NON_TARGET_DEFINING_LOADER",
                        errorMessage = "Resolved class was defined by a host or platform loader",
                        timestamp = startedAt
                    )
                    return@forEach
                }

                val sourcePaths = sourcePathsFor(targetNode, className)
                attempts += ClassResolutionAttempt(
                    loaderId = targetNode.id,
                    loaderClass = targetNode.loaderClass,
                    success = true,
                    definingClassLoader = describe(definingLoader),
                    sourcePaths = sourcePaths,
                    timestamp = startedAt
                )
                val category = if (targetNode.id == "root-target" && classIndex.contains(className)) {
                    ClassResolutionCategory.CLASS_FOUND_STATIC
                } else {
                    ClassResolutionCategory.CLASS_FOUND_DYNAMIC
                }
                return recordResult(
                    ClassResolutionResult(
                        className = className,
                        category = category,
                        resolvedClass = loaded,
                        loaderNodeId = targetNode.id,
                        definingClassLoader = describe(definingLoader),
                        sourcePaths = sourcePaths,
                        attempts = attempts.toList(),
                        phase = phase,
                        message = "Resolved $className through loader node ${targetNode.id}"
                    )
                )
            } catch (error: Throwable) {
                attempts += ClassResolutionAttempt(
                    loaderId = entry.node.id,
                    loaderClass = entry.node.loaderClass,
                    success = false,
                    sourcePaths = sourcePathsFor(entry.node, className),
                    errorClass = error.javaClass.name,
                    errorMessage = error.message,
                    timestamp = startedAt
                )
            }
        }

        val category = synchronized(lock) {
            when {
                packedUnsupportedClasses.containsKey(className) -> ClassResolutionCategory.PACKED_UNSUPPORTED
                unavailableFeatures.containsKey(className) -> ClassResolutionCategory.FEATURE_NOT_AVAILABLE
                classIndex.contains(className) -> ClassResolutionCategory.CLASS_NOT_FOUND_STATIC
                componentResolution && isDeclaredComponent(className) -> ClassResolutionCategory.DYNAMIC_LOADER_UNSEEN
                else -> ClassResolutionCategory.CLASS_NOT_FOUND_STATIC
            }
        }
        val reason = synchronized(lock) {
            packedUnsupportedClasses[className]
                ?: unavailableFeatures[className]
                ?: when (category) {
                    ClassResolutionCategory.DYNAMIC_LOADER_UNSEEN ->
                        "${className} is declared but absent from the registered loader graph"
                    ClassResolutionCategory.CLASS_NOT_FOUND_STATIC ->
                        "${className} was not resolved by any registered loader"
                    else -> "${className} is unavailable"
                }
        }
        return recordResult(
            ClassResolutionResult(
                className = className,
                category = category,
                attempts = attempts.toList(),
                phase = phase,
                message = reason
            )
        )
    }

    fun snapshot(): LoaderGraphSnapshot = synchronized(lock) {
        LoaderGraphSnapshot(
            nodes = entries.values.map { it.node },
            resolutionCount = resolutionRecords.size,
            lastObservedAt = entries.values.maxOfOrNull { it.node.firstSeenAt }
        )
    }

    fun resolutionRecords(): List<ClassResolutionRecord> = synchronized(lock) {
        resolutionRecords.toList()
    }

    fun nativeRuntimeState(): NativeRuntimeState = nativeState.snapshot()

    fun recordNativeLoadSuccess(libraryName: String, path: String? = null, phase: LoaderObservationPhase = LoaderObservationPhase.LATE_OBSERVATION) {
        nativeState.recordLoadSuccess(libraryName, path, phase)
    }

    fun recordNativeLoadFailure(libraryName: String, error: Throwable, phase: LoaderObservationPhase = LoaderObservationPhase.LATE_OBSERVATION) {
        nativeState.recordLoadFailure(libraryName, error, phase)
    }

    private fun registerRootLoader() {
        synchronized(lock) {
            val now = clock()
            val rootNode = LoaderNode(
                id = "root-target",
                loaderClass = rootLoader.javaClass.name,
                parentLoaderId = null,
                delegationMode = LoaderDelegationMode.DELEGATE_LAST,
                firstSeenAt = now,
                sourceType = LoaderSourceType.ROOT_TARGET,
                codeSourcePaths = descriptor.executableApkPaths,
                nativeLibrarySearchPaths = listOf(descriptor.nativeLibraryDir).filter { it.isNotBlank() },
                registrationState = LoaderRegistrationState.REGISTERED,
                firstObservedPhase = LoaderObservationPhase.BEFORE_APPLICATION_ONCREATE,
                canResolveClasses = true,
                noFileSource = false
            )
            entries[rootNode.id] = Entry(rootLoader, rootNode)
            loaderIds[rootLoader] = rootNode.id
        }
    }

    private fun registerMetadataSource(
        sourceType: LoaderSourceType,
        path: String,
        phase: LoaderObservationPhase
    ) {
        synchronized(lock) {
            val id = "source-${Integer.toHexString(path.hashCode())}"
            if (entries.containsKey(id)) return
            entries[id] = Entry(
                loader = null,
                node = LoaderNode(
                    id = id,
                    loaderClass = "UNOBSERVED_SOURCE",
                    parentLoaderId = "root-target",
                    delegationMode = LoaderDelegationMode.UNKNOWN,
                    firstSeenAt = clock(),
                    sourceType = sourceType,
                    codeSourcePaths = listOf(path),
                    nativeLibrarySearchPaths = emptyList(),
                    registrationState = LoaderRegistrationState.METADATA_ONLY,
                    firstObservedPhase = phase,
                    canResolveClasses = false,
                    noFileSource = false
                )
            )
        }
    }

    private fun observeLoaderReference(loader: ClassLoader, phase: LoaderObservationPhase) {
        if (loader === rootLoader || rootLoader.isTargetDefiningLoader(loader)) return
        if (loader === rootLoader.hostClassLoader) return
        registerLoader(loader, phase = phase)
    }

    private fun resolutionEntries(): List<Entry> = synchronized(lock) {
        entries.values
            .filter { it.node.canResolveClasses && it.loader != null }
            .sortedWith(compareByDescending<Entry> { it.node.id != "root-target" }.thenByDescending { it.node.firstSeenAt })
    }

    private fun findKnownNode(loader: ClassLoader): LoaderNode? {
        if (loader === rootLoader || rootLoader.isTargetDefiningLoader(loader)) return node("root-target")
        val id = loaderIds[loader] ?: return null
        return entries[id]?.node
    }

    private fun targetNodeForDefiningLoader(
        definingLoader: ClassLoader?,
        loadedClass: Class<*>,
        phase: LoaderObservationPhase
    ): LoaderNode? {
        if (definingLoader == null || definingLoader === rootLoader.hostClassLoader) return null
        if (rootLoader.isTargetDefiningLoader(definingLoader)) return node("root-target")
        synchronized(lock) {
            loaderIds[definingLoader]?.let { return entries[it]?.node }
        }
        return observeClass(loadedClass, phase)
    }

    private fun node(id: String): LoaderNode? = synchronized(lock) { entries[id]?.node }

    private fun sourcePathsFor(node: LoaderNode, className: String): List<String> {
        val indexed = classIndex.sourceFor(className)
        return if (indexed != null) listOf(indexed) else node.codeSourcePaths
    }

    private fun recordResult(result: ClassResolutionResult): ClassResolutionResult {
        synchronized(lock) {
            resolutionRecords += result.toRecord()
        }
        return result
    }

    private fun isDeclaredComponent(className: String): Boolean {
        return className == descriptor.mainActivity ||
                className in descriptor.declaredActivities ||
                className in descriptor.declaredServices ||
                className in descriptor.declaredProviders ||
                className in descriptor.declaredReceivers
    }

    private fun codeSourcePathsFor(loadedClass: Class<*>): List<String> {
        return runCatching {
            val location: CodeSource? = loadedClass.protectionDomain?.codeSource
            location?.location?.path?.let(::listOf) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun describe(loader: ClassLoader?): String? {
        return loader?.let { "${it.javaClass.name}@${Integer.toHexString(System.identityHashCode(it))}" }
    }

    private fun inferSourceType(loader: ClassLoader): LoaderSourceType {
        val name = loader.javaClass.name
        return when {
            name.contains("InMemoryDexClassLoader") -> LoaderSourceType.IN_MEMORY_DEX
            name.contains("DexClassLoader") -> LoaderSourceType.DEX_CLASS_LOADER
            name.contains("PathClassLoader") -> LoaderSourceType.PATH_CLASS_LOADER
            else -> LoaderSourceType.TARGET_CREATED
        }
    }
}
