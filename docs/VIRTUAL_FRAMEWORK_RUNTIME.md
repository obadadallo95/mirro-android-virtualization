# Mirro Virtual Framework Runtime

This document describes the Phase 2 and Core Phase 3 foundation. It defines what Mirro can
represent inside a clone and where Android still owns the physical identity, UID, signing,
Binder, and system-service boundary.

## Package registry and query policy

Each `VirtualContext` owns a `VirtualPackageRegistry` built from the inspected target descriptor.
The registry stores target `ApplicationInfo`, `PackageInfo`, activities, services, receivers,
providers, requested permissions, split/source paths, process names, native paths, and descriptive
signing/installer metadata. Target-owned queries are answered from this snapshot; they do not
fall through to a host query when a target record is incomplete.

Every registry result has a provenance classification:

| Origin | Meaning |
|---|---|
| `GUEST_VALUE` | Value comes from the clone's target package record or logical state. |
| `HOST_VALUE` | Physical host value is exposed as-is. |
| `HOST_MEDIATED` | Host Android performs the operation while Mirro preserves the boundary. |
| `UNSUPPORTED` | Mirro cannot provide the target contract truthfully. |

The target `ApplicationInfo.uid` and UID queries expose the physical process UID as a truthful
host-mediated value. They never fabricate a second Android UID. Signing metadata is descriptive
only and must not be used as physical signing identity in security-verified calls.

## Identity, permissions, and AppOps

`Context.getPackageName()` and target `ApplicationInfo.packageName` expose the logical target
package. `Context.getOpPackageName()` remains the package owned by the physical host process,
because AppOps and framework services validate it against the real UID. The runtime session keeps
both sides in `HostGuestIdentityVector`.

`VirtualPermissionManager` tracks permission state per clone. Requested permissions begin in
`REQUESTED`; host grants do not automatically become virtual grants. Explicit virtual grants,
denials, host mediation, and unsupported/system permissions remain separate states.

`VirtualAppOpsManager` does not spoof AppOps. Each operation is classified as guest-local,
host-mediated, host-denied, identity-required, or unsupported. Identity-required operations are
reported instead of being made to look like they came from the target UID.

## Storage

`VirtualStorageManager` is the single path authority for files, cache, code cache, no-backup,
databases, preferences, device/credential-protected data, and clone-scoped external directories.
The host process can still observe the physical parent path; this is explicitly classified as
`HOST_MEDIATED`, not presented as native filesystem isolation.

## Activity and task model

`VirtualActivityManager` owns logical Activity routing and result callbacks. It uses the shared
`IntentRouter`, `VirtualPackageRegistry`, and `DynamicCodeManager` pipeline. `TargetActivityHost`
continues to adapt the logical record to the host window/lifecycle bridge; it is not the source of
package/class resolution policy.

`VirtualActivityTaskManager` supports the useful subset of standard, singleTop, singleTask,
clear-top, new-task, finish, result, and back-stack behavior. Android's complete task manager,
background persistence, and cross-process task identity remain unsupported boundaries.

## Intent routing

`IntentRouter` classifies intents as target-internal, host-external, browser, system settings,
content URI, or unsupported. Target-internal routes stay clone-scoped. External routes are
delegated through the host boundary and are not selected by searching arbitrary available
Activities.

## Providers and FileProvider

`VirtualContentManager` is an explicit local provider router. It registers target providers by
authority and supports query, insert, update, delete, call, getType, openFile, and openAssetFile
when a provider is locally initialized. Host/system authorities continue to use the host
`ContentResolver`; Mirro does not globally replace it.

`VirtualFileProviderUriMapper` namespaces generated authorities with the clone identity, rejects
traversal, and tracks ownership of generated URIs. Host file paths are not encoded into guest
URIs. Full Android URI-grant enforcement remains unsupported unless the host controls the grant.

## Services and broadcasts

The existing `VirtualServiceManager` hosts ordinary target services in the container process. The
new service contract registry records per-clone start, bind, stop, and foreground requests.
Foreground persistence is explicitly host-mediated/degraded; full Android background service
durability is not claimed.

`VirtualBroadcastManager` provides clone-scoped explicit/runtime receiver registration and basic
delivery. Manifest/system wakeup behavior remains unsupported and Mirro does not add broad host
manifest receivers.

## PendingIntents and notifications

`VirtualPendingIntentDescriptor` carries clone ID, target package/component, request code, intent,
mutability, session, and expiry. Dispatch rejects clone/session mismatches. A real Android
`PendingIntent` is still host-owned when one is required by the platform.

`VirtualNotificationManager` namespaces channel identifiers and retains clone-scoped notification
records/actions. Delivery is host-mediated and notification permission state comes from the
virtual permission model. FCM is not implemented in this phase.

## Shared component pipeline and failure categories

The intended resolution pipeline is:

```text
VirtualPackageRegistry
    -> DynamicCodeManager
    -> virtual component manager
    -> host lifecycle/window/service adapter
```

Structured categories include incomplete package records, host-blocked permissions,
identity-required AppOps, unsupported Activity/task routes, missing providers, authority
collisions, unsupported URI grants, invalid PendingIntent routes, system identity blocks,
`DYNAMIC_LOADER_UNSEEN`, and `NATIVE_LOAD_FAILED`.

## Explicit non-goals

This phase does not modify Google Credential Manager, Play Services identity, OAuth pre-auth,
Play Integrity, physical UID, signing identity, Binder caller identity, linker namespaces, or
native inline hooks. Those are separate identity/security phases.

## Device validation snapshot — 2026-09-20

The updated debug APK was installed on the real API 36 arm64 device and the same clone probes
were repeated after the registry integration:

| App probe | Bootstrap / first Activity | Visible UI | First failure / category | Newly working layer |
|---|---|---|---|---|
| Lightweight baseline (BA-mobil) | Application and root Activity reached; static class resolution succeeded. | Target window was embedded. | Firebase/GMS rejected the logical package under the physical Mirro UID: `SYSTEM_IDENTITY_BLOCKED` boundary. | Package registry, logical Activity record, local service start/bind. |
| ChatGPT | Application and `MainActivity` reached; `CLASS_FOUND_STATIC` from root loader. | ChatGPT input/login surface remained visible. | Login-related GMS caller identity warnings remain outside this phase. | Registry-backed metadata preserved attach flags; logical Activity/task record. |
| Discord | Launch reached native initialization. | No stable target UI. | `UnsatisfiedLinkError`: `libkv_storage.so` was not found; native boundary, not class routing. | Failure is distinguishable from Activity/class resolution. |
| Facebook | Application bootstrap completed; `LoginActivity` remained unresolved. | Mirro diagnostic surface. | `DYNAMIC_LOADER_UNSEEN`. Native split libraries loaded, but no supported late loader exposed the missing class. | Structured loader/identity/component evidence. |

These results are probe evidence only. No package-specific branch was added, and no tokens,
credentials, cookies, messages, account content, or device-private identifiers were committed.
