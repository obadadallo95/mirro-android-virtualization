package app.mirro.android.domain.engine.container.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.model.CloneInstance
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages runtime process state, memory stats, and freeze state for container instances.
 */
class VirtualProcessController(private val context: Context) {

    private val frozenInstances = ConcurrentHashMap<String, Boolean>()

    fun isInstanceFrozen(cloneId: String): Boolean {
        return frozenInstances[cloneId] == true
    }

    fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        frozenInstances[instance.id] = true
        return EngineExecutionResult.Success(
            data = Unit,
            message = "Virtual instance '${instance.customName}' background activity suspended."
        )
    }

    fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        frozenInstances[instance.id] = false
        return EngineExecutionResult.Success(
            data = Unit,
            message = "Virtual instance '${instance.customName}' resumed."
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
