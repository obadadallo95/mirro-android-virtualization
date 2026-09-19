package app.mirro.android.domain.engine.container.framework

import android.content.ComponentName
import android.content.Intent
import java.util.UUID

enum class ActivityLaunchMode { STANDARD, SINGLE_TOP, SINGLE_TASK, CLEAR_TOP, NEW_TASK }

data class VirtualActivityRecord(
    val token: String = UUID.randomUUID().toString(),
    val cloneId: String,
    val component: ComponentName,
    val taskId: Int,
    val intent: Intent,
    val launchMode: ActivityLaunchMode = ActivityLaunchMode.STANDARD,
    val taskAffinity: String? = null,
    var finished: Boolean = false
)

class VirtualActivityTaskManager(private val cloneId: String) {
    private val tasks = LinkedHashMap<Int, MutableList<VirtualActivityRecord>>()
    private var nextTaskId = 1

    fun start(record: VirtualActivityRecord): VirtualActivityRecord {
        val stack = tasks.getOrPut(record.taskId) { mutableListOf() }
        when (record.launchMode) {
            ActivityLaunchMode.SINGLE_TOP -> if (stack.lastOrNull()?.component == record.component) return stack.last()
            ActivityLaunchMode.SINGLE_TASK,
            ActivityLaunchMode.CLEAR_TOP -> {
                val existing = stack.indexOfFirst { it.component == record.component }
                if (existing >= 0) {
                    while (stack.size > existing + 1) stack.removeLast().finished = true
                    return stack[existing]
                }
            }
            else -> Unit
        }
        stack += record
        return record
    }

    fun start(component: ComponentName, intent: Intent, mode: ActivityLaunchMode = ActivityLaunchMode.STANDARD, affinity: String? = null): VirtualActivityRecord {
        val taskId = if (mode == ActivityLaunchMode.NEW_TASK) nextTaskId++ else tasks.keys.lastOrNull() ?: nextTaskId++
        return start(VirtualActivityRecord(cloneId = cloneId, component = component, taskId = taskId, intent = Intent(intent), launchMode = mode, taskAffinity = affinity))
    }

    fun finish(token: String): Boolean {
        val record = tasks.values.asSequence().flatMap { it.asSequence() }.firstOrNull { it.token == token } ?: return false
        record.finished = true
        tasks[record.taskId]?.remove(record)
        return true
    }

    fun back(): VirtualActivityRecord? {
        val task = tasks.values.lastOrNull() ?: return null
        if (task.isEmpty()) return null
        task.removeLast().finished = true
        return task.lastOrNull()
    }

    fun snapshot(): List<VirtualActivityRecord> = tasks.values.flatten().map { it.copy(intent = Intent(it.intent)) }
}
