package app.mirro.android.domain.engine.container.framework

import java.util.concurrent.ConcurrentHashMap

class VirtualAppOpsManager {
    private val modes = ConcurrentHashMap<String, VirtualAppOpMode>()

    fun setMode(op: String, mode: VirtualAppOpMode) { modes[op] = mode }

    fun mode(op: String): VirtualAppOpMode = modes[op] ?: VirtualAppOpMode.IDENTITY_REQUIRED

    fun note(op: String): ClassifiedValue<Boolean> = when (mode(op)) {
        VirtualAppOpMode.GUEST_LOCAL -> ClassifiedValue(true, VirtualValueOrigin.GUEST_VALUE)
        VirtualAppOpMode.HOST_MEDIATED -> ClassifiedValue(true, VirtualValueOrigin.HOST_MEDIATED)
        VirtualAppOpMode.HOST_DENIED -> ClassifiedValue(false, VirtualValueOrigin.HOST_VALUE, "host AppOps denied")
        VirtualAppOpMode.IDENTITY_REQUIRED -> ClassifiedValue(false, VirtualValueOrigin.UNSUPPORTED, "physical identity required")
        VirtualAppOpMode.UNSUPPORTED -> ClassifiedValue(false, VirtualValueOrigin.UNSUPPORTED)
    }

    fun snapshot(): Map<String, VirtualAppOpMode> = modes.toMap()
}
