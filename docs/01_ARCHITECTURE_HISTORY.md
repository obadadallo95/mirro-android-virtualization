# Mirro Architecture History

This is a technical evolution, not a commit dump. Each milestone records the problem, hypothesis, implementation, observed result and lesson.

## 1. Initial app-cloning idea

**Problem:** provide two independent local sessions of an installed Android app without root or cloud infrastructure.

**Hypothesis:** a host APK could load a target APK, redirect its storage, and provide enough framework behavior to make the target believe it was installed normally.

**Implementation:** an Android host UI, clone metadata, private storage and a `CloneEngine` abstraction.

**Observed result:** ordinary UI and persistence surfaces were plausible, but the Android framework remained authoritative for package, UID, Binder and component behavior.

**Lesson:** the product requirement was an Android authority problem, not only a file-copy problem.

## 2. Early profile direction and its removal from the primary design

**Problem:** Work Profile/secondary-user execution naturally provides real package and UID semantics, but it is visibly another Android profile and not a seamless in-process container.

**Hypothesis:** a user-space container would provide better UX and remain portable.

**Implementation:** the primary runtime was kept in Mirro’s process; profile/device-management APIs were not made a product dependency.

**Observed result:** the container UX was controllable, but compatibility ceilings moved into GMS, Binder, native runtime and lifecycle ownership.

**Lesson:** profile execution and in-process virtualization solve different problems. A profile is technically stronger; a container is visually more seamless.

## 3. Application bootstrap

**Problem:** target code must observe a plausible `Application` lifecycle before an Activity can run.

**Hypothesis:** a target-first class loader plus a virtual Context and reflective bootstrap could reach `Application.onCreate()`.

**Implementation:** `ApkInspector`, `DexRuntimeLoader`, `MirroTargetClassLoader`, `VirtualContext` and `ApplicationBootstrapper`.

**Observed result:** simple targets could bootstrap in the host process.

**Lesson:** Application bootstrap is useful, but it is not equivalent to `ActivityThread`/`LoadedApk` ownership.

## 4. Activity hosting

**Problem:** target Activities are not registered in Mirro’s manifest or host task.

**Hypothesis:** reflective `Activity.attach`, instrumentation forwarding and host-window embedding could provide a useful UI subset.

**Implementation:** `TargetActivityHost`, nested target-start interception, lifecycle forwarding and auth callback routing.

**Observed result:** ChatGPT and other static-root apps reached target UI.

**Lesson:** Activity embedding is a compatibility adapter. Host task/window/token/process primitives remain visible and authoritative.

## 5. ChatGPT UI success and GMS login failure

**Problem:** ChatGPT UI launched, but native/browser/GMS login encountered caller identity constraints.

**Hypothesis:** target package metadata and callback routing might be enough to complete login.

**Implementation:** target package overrides, browser callback registry and logical package identity.

**Observed result:** UI and some browser routing worked; GMS/native identity remained tied to Mirro.

**Lesson:** `getPackageName()` and a PackageManager facade do not change Binder caller UID, signing identity or GMS validation.

## 6. Dynamic loader investigation

**Problem:** Facebook reached `Application.onCreate()` but its declared Activity remained unavailable.

**Hypothesis:** bounded retries and better APK/split inspection could reveal the late class.

**Implementation:** `DynamicCodeManager`, `LoaderGraph`, `TargetClassIndex`, `TargetActivityResolver` and structured resolution categories.

**Observed result:** the runtime could distinguish static absence from `DYNAMIC_LOADER_UNSEEN`, register supported loader objects and record source/owner evidence. Facebook still failed when the relevant loader boundary was not observable.

**Lesson:** dynamic code is a graph and observation boundary, not a retry loop. Native/private loaders require deeper runtime instrumentation or an Android-owned guest.

## 7. Virtual framework foundation

**Problem:** package, storage, permissions, AppOps, tasks, providers, services, broadcasts, PendingIntents and notifications were previously implicit or host-backed.

**Hypothesis:** explicit capability-oriented contracts would improve supported-subset behavior and make failures honest.

**Implementation:** `VirtualPackageRegistry`, permission/AppOps managers, storage manager, intent/task/activity managers, content/provider registry, service/broadcast/PendingIntent/notification contracts and `VirtualFrameworkSnapshot`.

**Observed result:** 49 deterministic tests passed; clone-local state and provenance became explicit. ChatGPT required a flag-sensitive metadata fix and then returned to the expected UI path.

**Lesson:** these abstractions are valuable reference models and diagnostics. They do not replace system_server, kernel identity, native linker behavior or Android scheduling.

## 8. Discord native failure

**Problem:** Discord failed in the container while loading `libkv_storage.so`.

**Hypothesis:** native inventory and loader diagnostics could explain the failure without unsafe hooks.

**Implementation:** ABI/native inventory and `NATIVE_LOAD_FAILED` evidence.

**Observed result:** the failure was clearly native/process/linker-related, not a missing Kotlin service manager.

**Lesson:** native compatibility is its own architectural world.

## 9. Architecture audits and pivot decision

**Problem:** two implementation phases improved observability and contracts but did not make additional complex commercial apps fully usable.

**Hypothesis:** a final comparison with mature virtualization engines and Android profiles would identify the missing primitive.

**Implementation:** [Architecture Decision Audit](MIRRO_ARCHITECTURE_DECISION.md) and [Final Engine Decision](FINAL_VIRTUALIZATION_ENGINE_DECISION.md).

**Observed result:** the missing primitive was a real guest authority boundary: profile, full guest OS, OEM/system privilege, or remote Android—not another facade.

**Lesson:** sunk implementation effort must not be confused with product viability.

## 10. Samsung real-profile proof

**Problem:** test whether Android-owned profile execution solves multiple blocker classes without root or cloud cost.

**Hypothesis:** enabling the existing Samsung Dual Apps profile would give the same APK real profile package/process/storage/native semantics.

**Implementation:** ADB `install-existing --user` and `am start --user` for already-installed ChatGPT and Discord. No APK bytes were copied or modified.

**Observed result:** profile-derived package/process/data isolation was confirmed; ChatGPT reached profile-scoped GMS account discovery; Discord reached its normal Welcome screen without the prior native library failure.

**Lesson:** the profile route is technically strong but exposes OEM setup, profile UX and authority limitations.

## 11. Final archival position

Mirro is preserved as a research/reference project. The local container remains valuable for studying APK loading, guest Context boundaries, dynamic loader evidence and compatibility taxonomy. A future product should orchestrate legitimate Android profiles or other authority-bearing environments rather than attempt universal in-process emulation.
