package app.mirro.android.domain.engine.container.model

import app.mirro.android.domain.engine.container.loader.LoaderObservationPhase
import java.io.File

enum class NativeLoadOutcome {
    SUCCESS,
    FAILED
}

data class NativeLoadEvent(
    val libraryName: String,
    val outcome: NativeLoadOutcome,
    val path: String? = null,
    val errorClass: String? = null,
    val errorMessage: String? = null,
    val phase: LoaderObservationPhase,
    val timestamp: Long = System.currentTimeMillis()
)

/** Legitimate native diagnostics; no linker or dlopen hooks are installed. */
data class NativeRuntimeState(
    val supportedAbis: List<String>,
    val targetNativeLibraryDir: String,
    val targetNativeAbis: List<String>,
    val apkNativeLibraryInventory: List<String>,
    private val mutableLoadEvents: MutableList<NativeLoadEvent> = mutableListOf()
) {
    val loadEvents: List<NativeLoadEvent>
        get() = synchronized(mutableLoadEvents) { mutableLoadEvents.toList() }

    fun recordLoadSuccess(
        libraryName: String,
        path: String?,
        phase: LoaderObservationPhase
    ) {
        synchronized(mutableLoadEvents) {
            mutableLoadEvents += NativeLoadEvent(
                libraryName = libraryName,
                outcome = NativeLoadOutcome.SUCCESS,
                path = path,
                phase = phase
            )
        }
    }

    fun recordLoadFailure(
        libraryName: String,
        error: Throwable,
        phase: LoaderObservationPhase
    ) {
        synchronized(mutableLoadEvents) {
            mutableLoadEvents += NativeLoadEvent(
                libraryName = libraryName,
                outcome = NativeLoadOutcome.FAILED,
                errorClass = error.javaClass.name,
                errorMessage = error.message,
                phase = phase
            )
        }
    }

    fun snapshot(): NativeRuntimeState = NativeRuntimeState(
        supportedAbis = supportedAbis,
        targetNativeLibraryDir = targetNativeLibraryDir,
        targetNativeAbis = targetNativeAbis,
        apkNativeLibraryInventory = apkNativeLibraryInventory,
        mutableLoadEvents = loadEvents.toMutableList()
    )

    companion object {
        fun fromDescriptor(descriptor: ApkDescriptor): NativeRuntimeState {
            val inventory = runCatching {
                File(descriptor.nativeLibraryDir)
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "so" }
                    .map { it.absolutePath }
                    .toList()
            }.getOrDefault(emptyList())
            return NativeRuntimeState(
                supportedAbis = descriptor.supportedAbis,
                targetNativeLibraryDir = descriptor.nativeLibraryDir,
                targetNativeAbis = descriptor.targetNativeAbis,
                apkNativeLibraryInventory = descriptor.nativeLibraryInventory + inventory
            )
        }
    }
}
