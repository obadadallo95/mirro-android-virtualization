package app.mirro.android.domain.engine.container.framework

/** Provenance of a value exposed through the guest-facing framework facade. */
enum class VirtualValueOrigin {
    GUEST_VALUE,
    HOST_VALUE,
    HOST_MEDIATED,
    UNSUPPORTED
}

data class ClassifiedValue<T>(
    val value: T?,
    val origin: VirtualValueOrigin,
    val reason: String? = null
)

enum class VirtualPermissionState {
    REQUESTED,
    GRANTED_VIRTUAL,
    DENIED_VIRTUAL,
    HOST_GRANTED,
    HOST_DENIED,
    HOST_MEDIATED,
    UNSUPPORTED
}

enum class VirtualAppOpMode {
    GUEST_LOCAL,
    HOST_MEDIATED,
    HOST_DENIED,
    IDENTITY_REQUIRED,
    UNSUPPORTED
}

enum class VirtualFailureCategory {
    PACKAGE_RECORD_INCOMPLETE,
    PERMISSION_HOST_BLOCKED,
    APPOPS_IDENTITY_REQUIRED,
    ACTIVITY_ROUTE_UNSUPPORTED,
    TASK_SEMANTICS_UNSUPPORTED,
    SERVICE_BACKGROUND_UNSUPPORTED,
    PROVIDER_ROUTE_MISSING,
    AUTHORITY_COLLISION,
    URI_GRANT_UNSUPPORTED,
    PENDING_INTENT_ROUTE_INVALID,
    SYSTEM_IDENTITY_BLOCKED,
    DYNAMIC_LOADER_UNSEEN,
    NATIVE_LOAD_FAILED
}

data class VirtualCapabilityEvent(
    val capability: String,
    val origin: VirtualValueOrigin,
    val detail: String,
    val failureCategory: VirtualFailureCategory? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class VirtualFrameworkSnapshot(
    val packageName: String,
    val packageOrigin: VirtualValueOrigin,
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val providerCount: Int,
    val providerAuthorities: Set<String>,
    val permissionStates: Map<String, VirtualPermissionState>,
    val appOpsModes: Map<String, VirtualAppOpMode>,
    val storageRoot: String,
    val physicalPathIsolation: VirtualValueOrigin = VirtualValueOrigin.HOST_MEDIATED
)
