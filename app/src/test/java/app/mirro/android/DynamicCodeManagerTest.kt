package app.mirro.android

import app.mirro.android.domain.engine.container.loader.ClassResolutionCategory
import app.mirro.android.domain.engine.container.loader.DynamicCodeManager
import app.mirro.android.domain.engine.container.loader.LoaderObservationPhase
import app.mirro.android.domain.engine.container.loader.LoaderSourceType
import app.mirro.android.domain.engine.container.loader.MirroTargetClassLoader
import app.mirro.android.domain.engine.container.loader.TargetClassIndex
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import app.mirro.android.domain.engine.container.model.NativeRuntimeState
import app.mirro.android.domain.engine.container.model.SplitSource
import app.mirro.android.domain.engine.container.model.SplitSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicCodeManagerTest {

    private val lateClassName = "app.mirro.android.DynamicCodeManagerTest\$LateComponent"

    @Test
    fun `class available only from registered late loader resolves dynamically`() {
        val descriptor = descriptor(mainActivity = lateClassName)
        val manager = manager(descriptor)
        val lateLoader = newLateLoader()

        val node = manager.registerLoader(
            loader = lateLoader,
            sourceType = LoaderSourceType.DEX_CLASS_LOADER,
            phase = LoaderObservationPhase.AFTER_APPLICATION_ONCREATE,
            codeSourcePaths = listOf("/data/user/0/com.example/code_cache/secondary.dex")
        )

        val result = manager.resolveClass(
            className = lateClassName,
            phase = LoaderObservationPhase.COMPONENT_RESOLUTION,
            componentResolution = true
        )

        assertTrue("result=$result attempts=${result.attempts}", result.found)
        assertEquals(ClassResolutionCategory.CLASS_FOUND_DYNAMIC, result.category)
        assertEquals(node.id, result.loaderNodeId)
        assertEquals(
            "/data/user/0/com.example/code_cache/secondary.dex",
            result.sourcePaths.single()
        )
        assertEquals(LoaderObservationPhase.COMPONENT_RESOLUTION, result.phase)
    }

    @Test
    fun `component missing from static index is classified as unseen dynamic loader`() {
        val descriptor = descriptor(mainActivity = lateClassName)
        val result = manager(descriptor).resolveClass(
            className = lateClassName,
            phase = LoaderObservationPhase.COMPONENT_RESOLUTION,
            componentResolution = true
        )

        assertFalse(result.found)
        assertEquals(ClassResolutionCategory.DYNAMIC_LOADER_UNSEEN, result.category)
        assertTrue(result.attempts.isNotEmpty())
    }

    @Test
    fun `generic missing class remains a static class-not-found result`() {
        val result = manager(descriptor()).resolveClass("com.example.Missing")

        assertFalse(result.found)
        assertEquals(ClassResolutionCategory.CLASS_NOT_FOUND_STATIC, result.category)
    }

    @Test
    fun `installed executable split is represented as metadata until a loader is observed`() {
        val descriptor = descriptor().copy(
            splitApkPaths = listOf("/data/app/com.example/split_feature.apk"),
            executableSplitApkPaths = listOf("/data/app/com.example/split_feature.apk"),
            splitSources = listOf(
                SplitSource(
                    path = "/data/app/com.example/split_feature.apk",
                    kind = SplitSourceKind.EXECUTABLE
                )
            )
        )

        val splitNode = manager(descriptor).snapshot().nodes.single {
            it.sourceType == LoaderSourceType.INSTALLED_SPLIT
        }

        assertEquals("/data/app/com.example/split_feature.apk", splitNode.codeSourcePaths.single())
        assertEquals("UNOBSERVED_SOURCE", splitNode.loaderClass)
        assertFalse(splitNode.canResolveClasses)
        assertEquals("root-target", splitNode.parentLoaderId)
    }

    @Test
    fun `in-memory loader has no invented filesystem source`() {
        val manager = manager(descriptor())
        val inMemoryLoader = object : ClassLoader(null) {}

        val node = manager.registerInMemoryLoader(
            loader = inMemoryLoader,
            phase = LoaderObservationPhase.LATE_OBSERVATION
        )

        assertEquals(LoaderSourceType.IN_MEMORY_DEX, node.sourceType)
        assertTrue(node.noFileSource)
        assertTrue(node.codeSourcePaths.isEmpty())
    }

    @Test
    fun `feature and packed boundaries are explicit categories`() {
        val featureClass = "com.example.FeatureActivity"
        val packedClass = "com.example.PackedActivity"
        val descriptor = descriptor(mainActivity = featureClass).copy(
            declaredActivities = listOf(featureClass, packedClass)
        )
        val manager = manager(descriptor)
        manager.markFeatureUnavailable(featureClass, "feature session was not installed")
        manager.markPackedUnsupported(packedClass, "payload is intentionally guarded")

        assertEquals(
            ClassResolutionCategory.FEATURE_NOT_AVAILABLE,
            manager.resolveClass(featureClass, componentResolution = true).category
        )
        assertEquals(
            ClassResolutionCategory.PACKED_UNSUPPORTED,
            manager.resolveClass(packedClass, componentResolution = true).category
        )
    }

    @Test
    fun `native failure remains separate from class resolution`() {
        val nativeState = NativeRuntimeState(
            supportedAbis = listOf("arm64-v8a"),
            targetNativeLibraryDir = "/data/app/com.example/lib/arm64",
            targetNativeAbis = listOf("armeabi-v7a"),
            apkNativeLibraryInventory = listOf("lib/armeabi-v7a/libtarget.so")
        )
        val manager = DynamicCodeManager(
            descriptor = descriptor(),
            rootLoader = rootLoader(),
            classIndex = TargetClassIndex.fromClassNames(emptySet()),
            nativeState = nativeState
        )

        manager.recordNativeLoadFailure(
            libraryName = "target",
            error = UnsatisfiedLinkError("wrong ABI"),
            phase = LoaderObservationPhase.AFTER_APPLICATION_ONCREATE
        )

        val snapshot = manager.nativeRuntimeState()
        assertEquals(listOf("arm64-v8a"), snapshot.supportedAbis)
        assertEquals(listOf("armeabi-v7a"), snapshot.targetNativeAbis)
        assertEquals(1, snapshot.loadEvents.size)
        assertEquals("java.lang.UnsatisfiedLinkError", snapshot.loadEvents.single().errorClass)
        assertEquals(ClassResolutionCategory.CLASS_NOT_FOUND_STATIC, manager.resolveClass("missing.Class").category)
    }

    @Test
    fun `late loader can be added after an initial bounded observation failure`() {
        val descriptor = descriptor(mainActivity = lateClassName)
        val manager = manager(descriptor)
        val first = manager.resolveClass(lateClassName, componentResolution = true)
        assertEquals(ClassResolutionCategory.DYNAMIC_LOADER_UNSEEN, first.category)

        manager.registerLoader(
            loader = newLateLoader(),
            sourceType = LoaderSourceType.TARGET_CREATED,
            phase = LoaderObservationPhase.LATE_OBSERVATION
        )

        val second = manager.resolveClass(lateClassName, componentResolution = true)
        assertTrue("result=$second attempts=${second.attempts}", second.found)
        assertEquals(ClassResolutionCategory.CLASS_FOUND_DYNAMIC, second.category)
        assertNotNull(second.loaderNodeId)
        assertTrue(manager.snapshot().resolutionCount >= 2)
    }

    private fun descriptor(mainActivity: String? = null): ApkDescriptor = ApkDescriptor(
        packageName = "com.example.target",
        baseApkPath = "/data/app/com.example/base.apk",
        mainActivity = mainActivity,
        declaredActivities = listOfNotNull(mainActivity)
    )

    private fun manager(descriptor: ApkDescriptor): DynamicCodeManager = DynamicCodeManager(
        descriptor = descriptor,
        rootLoader = rootLoader(),
        classIndex = TargetClassIndex.fromClassNames(emptySet()),
        nativeState = NativeRuntimeState.fromDescriptor(descriptor)
    )

    private fun rootLoader(): MirroTargetClassLoader = MirroTargetClassLoader(
        dexPath = "",
        nativeLibraryPath = "",
        hostClassLoader = object : ClassLoader(null) {},
        targetClassIndex = TargetClassIndex.fromClassNames(emptySet())
    )

    private fun newLateLoader(): ClassLoader {
        val resourceName = lateClassName.replace('.', '/') + ".class"
        val bytes = requireNotNull(LateComponent::class.java.classLoader?.getResourceAsStream(resourceName)) {
            "Missing test class bytes for $resourceName"
        }.use { it.readBytes() }
        return object : ClassLoader(null) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == lateClassName) {
                    return defineClass(name, bytes, 0, bytes.size)
                }
                if (name.startsWith("java.")) {
                    return ClassLoader.getSystemClassLoader().loadClass(name)
                }
                throw ClassNotFoundException(name)
            }
        }
    }

    class LateComponent
}
