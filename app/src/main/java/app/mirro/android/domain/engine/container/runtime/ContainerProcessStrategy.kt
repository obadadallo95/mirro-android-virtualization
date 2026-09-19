package app.mirro.android.domain.engine.container.runtime

import java.util.concurrent.atomic.AtomicReference

enum class ProcessSlotClaim {
    ACQUIRED,
    ALREADY_BOUND_TO_CLONE,
    BOUND_TO_OTHER_CLONE
}

data class ProcessSlotBinding(
    val processName: String,
    val cloneId: String,
    val webViewSuffix: String
)

/**
 * Foundation for strategy A: one clone owns the dedicated container process slot for its
 * lifetime. A process restart is required before another clone can use a different WebView
 * suffix; this phase deliberately does not pretend that slots can be reused in-process.
 */
interface ContainerProcessStrategy {
    val processName: String

    fun claim(cloneId: String, webViewSuffix: String): ProcessSlotClaim

    fun currentBinding(): ProcessSlotBinding?
}

class SingleCloneProcessSlotStrategy(
    override val processName: String = ":container",
    internal val binding: AtomicReference<ProcessSlotBinding?> = GLOBAL_BINDING
) : ContainerProcessStrategy {
    override fun claim(cloneId: String, webViewSuffix: String): ProcessSlotClaim {
        synchronized(LOCK) {
            val current = binding.get()
            if (current == null) {
                binding.set(ProcessSlotBinding(processName, cloneId, webViewSuffix))
                return ProcessSlotClaim.ACQUIRED
            }

            return if (current.cloneId == cloneId && current.webViewSuffix == webViewSuffix) {
                ProcessSlotClaim.ALREADY_BOUND_TO_CLONE
            } else {
                ProcessSlotClaim.BOUND_TO_OTHER_CLONE
            }
        }
    }

    override fun currentBinding(): ProcessSlotBinding? = binding.get()

    companion object {
        private val LOCK = Any()
        private val GLOBAL_BINDING = AtomicReference<ProcessSlotBinding?>(null)
    }
}
