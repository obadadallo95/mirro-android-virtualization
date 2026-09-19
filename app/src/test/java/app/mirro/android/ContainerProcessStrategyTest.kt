package app.mirro.android

import app.mirro.android.domain.engine.container.runtime.ProcessSlotClaim
import app.mirro.android.domain.engine.container.runtime.SingleCloneProcessSlotStrategy
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerProcessStrategyTest {

    @Test
    fun `one process slot stays bound to one clone and suffix`() {
        val strategy = SingleCloneProcessSlotStrategy(
            binding = AtomicReference(null)
        )

        assertEquals(ProcessSlotClaim.ACQUIRED, strategy.claim("clone-a", "mirro_a"))
        assertEquals(ProcessSlotClaim.ALREADY_BOUND_TO_CLONE, strategy.claim("clone-a", "mirro_a"))
        assertEquals(ProcessSlotClaim.BOUND_TO_OTHER_CLONE, strategy.claim("clone-b", "mirro_b"))
        assertEquals("clone-a", strategy.currentBinding()?.cloneId)
    }
}
