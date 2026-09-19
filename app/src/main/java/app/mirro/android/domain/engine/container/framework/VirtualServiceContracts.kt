package app.mirro.android.domain.engine.container.framework

import android.content.ComponentName
import android.content.Intent

data class VirtualServiceRecord(
    val cloneId: String,
    val component: ComponentName,
    val foregroundRequested: Boolean,
    val started: Boolean = false,
    val boundClients: Int = 0
)

class VirtualServiceContractRegistry(private val cloneId: String) {
    private val services = LinkedHashMap<String, VirtualServiceRecord>()

    fun start(component: ComponentName, foreground: Boolean): ClassifiedValue<VirtualServiceRecord> {
        val record = VirtualServiceRecord(cloneId, component, foreground, started = true)
        services[component.className] = record
        return if (foreground) {
            ClassifiedValue(record, VirtualValueOrigin.HOST_MEDIATED, "foreground persistence is not provided")
        } else ClassifiedValue(record, VirtualValueOrigin.GUEST_VALUE)
    }

    fun bind(component: ComponentName): ClassifiedValue<VirtualServiceRecord> {
        val current = services[component.className] ?: VirtualServiceRecord(cloneId, component, false)
        val updated = current.copy(boundClients = current.boundClients + 1)
        services[component.className] = updated
        return ClassifiedValue(updated, VirtualValueOrigin.GUEST_VALUE)
    }

    fun stop(component: ComponentName): Boolean = services.remove(component.className) != null
    fun snapshot(): List<VirtualServiceRecord> = services.values.toList()
}

data class VirtualReceiverRegistration(
    val cloneId: String,
    val receiverId: String,
    val action: String,
    val receiver: (Intent) -> Unit
)

class VirtualBroadcastManager(private val cloneId: String) {
    private val receivers = LinkedHashMap<String, VirtualReceiverRegistration>()

    fun register(receiverId: String, action: String, receiver: (Intent) -> Unit): ClassifiedValue<Boolean> {
        receivers[receiverId] = VirtualReceiverRegistration(cloneId, receiverId, action, receiver)
        return ClassifiedValue(true, VirtualValueOrigin.GUEST_VALUE)
    }

    fun unregister(receiverId: String): Boolean = receivers.remove(receiverId) != null

    fun send(intent: Intent): Int {
        val matches = receivers.values.filter { it.action == intent.action }
        matches.forEach { it.receiver(Intent(intent)) }
        return matches.size
    }

    fun snapshot(): List<VirtualReceiverRegistration> = receivers.values.toList()
}

data class VirtualPendingIntentDescriptor(
    val cloneId: String,
    val targetPackage: String,
    val component: ComponentName?,
    val requestCode: Int,
    val intent: Intent,
    val mutable: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val sessionId: String
)

class VirtualPendingIntentManager(
    private val cloneId: String,
    private val sessionId: String
) {
    private val descriptors = LinkedHashMap<String, VirtualPendingIntentDescriptor>()

    fun create(targetPackage: String, component: ComponentName?, requestCode: Int, intent: Intent, mutable: Boolean, expiresAt: Long? = null): ClassifiedValue<VirtualPendingIntentDescriptor> {
        if (targetPackage.isBlank()) return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "${VirtualFailureCategory.PENDING_INTENT_ROUTE_INVALID}: empty package")
        val descriptor = VirtualPendingIntentDescriptor(cloneId, targetPackage, component, requestCode, Intent(intent), mutable, expiresAt = expiresAt, sessionId = sessionId)
        descriptors[key(descriptor)] = descriptor
        return ClassifiedValue(descriptor, VirtualValueOrigin.GUEST_VALUE)
    }

    fun send(descriptor: VirtualPendingIntentDescriptor, currentCloneId: String): ClassifiedValue<Intent> {
        if (descriptor.cloneId != currentCloneId || descriptor.sessionId != sessionId) {
            return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "${VirtualFailureCategory.PENDING_INTENT_ROUTE_INVALID}: clone/session mismatch")
        }
        if (descriptor.expiresAt != null && descriptor.expiresAt < System.currentTimeMillis()) {
            return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "PendingIntent expired")
        }
        return ClassifiedValue(Intent(descriptor.intent), VirtualValueOrigin.GUEST_VALUE)
    }

    private fun key(value: VirtualPendingIntentDescriptor): String = "${value.targetPackage}:${value.requestCode}:${value.component}"
}

data class VirtualNotificationAction(val title: CharSequence, val pendingIntent: VirtualPendingIntentDescriptor?)

data class VirtualNotification(
    val cloneId: String,
    val id: Int,
    val channelId: String,
    val title: CharSequence,
    val text: CharSequence,
    val actions: List<VirtualNotificationAction>
)

class VirtualNotificationManager(private val cloneId: String) {
    private val notifications = LinkedHashMap<Int, VirtualNotification>()

    fun post(id: Int, channelId: String, title: CharSequence, text: CharSequence, actions: List<VirtualNotificationAction> = emptyList()): ClassifiedValue<VirtualNotification> {
        val notification = VirtualNotification(cloneId, id, "$cloneId:$channelId", title, text, actions)
        notifications[id] = notification
        return ClassifiedValue(notification, VirtualValueOrigin.HOST_MEDIATED, "delivery uses host notification manager")
    }

    fun cancel(id: Int): Boolean = notifications.remove(id) != null
    fun snapshot(): List<VirtualNotification> = notifications.values.toList()
}
