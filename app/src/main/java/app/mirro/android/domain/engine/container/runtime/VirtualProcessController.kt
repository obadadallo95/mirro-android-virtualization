package app.mirro.android.domain.engine.container.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.model.CloneInstance

/**
 * Manages runtime process state, memory stats, and freeze state for container instances.
 */
class VirtualProcessController(private val context: Context) {

    fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.Unsupported(
            reason = "Process freezing is not implemented until Mirro owns the hosted Activity lifecycle."
        )
    }

    fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.Unsupported(
            reason = "Process resuming is not implemented until Mirro owns the hosted Activity lifecycle."
        )
    }

    fun getMemoryUsage(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val memInfo = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
        return if (memInfo.isNotEmpty()) {
            memInfo[0].totalPss.toLong() * 1024L
        } else {
            0L
        }
    }
}
