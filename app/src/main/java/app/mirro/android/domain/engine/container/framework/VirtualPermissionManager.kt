package app.mirro.android.domain.engine.container.framework

import android.content.pm.PackageManager
import app.mirro.android.domain.engine.container.model.ApkDescriptor
import java.util.concurrent.ConcurrentHashMap

class VirtualPermissionManager(
    descriptor: ApkDescriptor,
    private val hostPermissionCheck: (String) -> Int
) {
    private val requested = descriptor.requestedPermissions.toSet()
    private val states = ConcurrentHashMap<String, VirtualPermissionState>()

    init { requested.forEach { states[it] = VirtualPermissionState.REQUESTED } }

    fun requestedPermissions(): Set<String> = requested

    fun state(permission: String): VirtualPermissionState = states[permission] ?: VirtualPermissionState.UNSUPPORTED

    fun setState(permission: String, state: VirtualPermissionState) {
        if (permission in requested || state == VirtualPermissionState.UNSUPPORTED) states[permission] = state
    }

    fun check(permission: String): Int = when (state(permission)) {
        VirtualPermissionState.GRANTED_VIRTUAL,
        VirtualPermissionState.HOST_GRANTED -> PackageManager.PERMISSION_GRANTED
        VirtualPermissionState.HOST_MEDIATED -> {
            if (hostPermissionCheck(permission) == PackageManager.PERMISSION_GRANTED) {
                PackageManager.PERMISSION_DENIED
            } else PackageManager.PERMISSION_DENIED
        }
        else -> PackageManager.PERMISSION_DENIED
    }

    fun request(permission: String): VirtualPermissionState {
        if (permission !in requested) return VirtualPermissionState.UNSUPPORTED
        return state(permission)
    }

    fun snapshot(): Map<String, VirtualPermissionState> = states.toMap()
}
