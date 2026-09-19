# Mirro Dynamic Code Runtime

**Milestone:** Phase 0/1 implementation contract

This document describes the runtime-observability implementation introduced for Mirro v2. It does not promise universal dynamic-code support and does not install hidden hooks, native inline hooks, linker-namespace bypasses, anti-tamper workarounds, or remote executable-code loading.

## Runtime ownership

Each `LoadedApkRuntime` owns one `DynamicCodeManager`. The manager owns the observable loader graph for that clone launch and is surfaced through `ContainerRuntimeDiagnostics.runtimeSession`.

```text
RuntimeSession
├── HostGuestIdentityVector
├── LoaderGraphSnapshot
├── NativeRuntimeState
├── componentResolutionAttempts
├── capabilityDecisions
└── firstFailure
```

The runtime session is local diagnostics state. It does not change Android package, UID, signing, AppOps, Binder, GMS, Credential Manager, Play Integrity, or hardware identity.

## Loader graph model

Every observable loader is represented by a `LoaderNode`:

| Field | Meaning |
|---|---|
| `id` | Stable identifier for the loader object during this runtime session |
| `loaderClass` | Runtime class name of the loader, or `UNOBSERVED_SOURCE` for metadata-only split code |
| `parentLoaderId` | Known parent node, if the parent was observable |
| `delegationMode` | Parent-first, child-first, delegate-last, or unknown |
| `firstSeenAt` | Local timestamp when Mirro registered the node |
| `sourceType` | Root, installed split, DEX/path/in-memory loader, feature, target-created, native-origin or unknown |
| `codeSourcePaths` | Known APK/DEX/module paths; empty is valid |
| `nativeLibrarySearchPaths` | Known native search paths |
| `registrationState` | Resolvable loader or metadata-only source |
| `firstObservedPhase` | Before/after Application.onCreate, late observation, or component resolution |
| `canResolveClasses` | Whether Mirro has an actual `ClassLoader` reference |
| `noFileSource` | True for an in-memory loader with no invented path |

The root node is `MirroTargetClassLoader`. Its internal target `PathClassLoader` is treated as the same root node because classes loaded through the delegate report the internal loader from `Class.getClassLoader()`.

Installed executable splits are represented twice in the evidence model:

1. their paths are part of the root target loader's known code sources; and
2. each split has a metadata-only `INSTALLED_SPLIT` node until a distinct runtime loader is observed.

This prevents Mirro from claiming that a split has a separate defining loader merely because its APK path is known.

## Resolution rules

Component resolution now follows:

```text
component class
    -> DynamicCodeManager.resolveClass(..., componentResolution = true)
    -> registered resolvable loader nodes
    -> actual defining ClassLoader check
    -> class/source/loader evidence
    -> structured category
```

Registered target-created loaders are searched before the root loader so that a late child loader can provide a class absent from the initial DEX set. Each loader's own delegation behavior remains authoritative for its dependencies. A class returned by a host or platform loader is not accepted as a target component class.

The manager records every attempt, including loader node, loader class, source paths, defining loader when available, exception class/message, phase and timestamp. A retry of the same graph produces another resolution record; it does not create a loader node and is never reported as dynamic discovery.

## Supported discovery boundaries

The implementation currently supports:

- root `MirroTargetClassLoader` registration at startup;
- installed executable split source registration from inspected APK metadata;
- explicit registration of a later `ClassLoader` reference supplied by a supported caller;
- observation of `Application.class.classLoader`, `Context.classLoader`, and thread context loader;
- observation of the defining loader of a successfully resolved target class;
- explicit in-memory loader registration when a real loader object is made available;
- explicit feature-unavailable and packed/unsupported markers;
- native load success/failure recording when a legitimate integration point can report it;
- APK/native-library inventory and ABI/path diagnostics without linker hooks.

The supported-boundary observer does not walk private framework fields, replace global `ServiceManager`/class-loader state, patch ART, hook `dlopen`, bypass linker namespaces, or install process-wide native hooks.

## Unsupported or unobservable boundaries

Mirro reports a limitation instead of guessing when:

- code is supplied by `InMemoryDexClassLoader` but no loader object reaches a supported boundary;
- native code decrypts/extracts code and keeps the defining loader private;
- a packed or anti-tamper runtime deliberately refuses to expose code in the host process;
- a Play Feature Delivery session is declared but the module is not installed or its loader is not observable;
- a target class is declared but absent from all known static code sources and no late loader is registered.

The last case is `DYNAMIC_LOADER_UNSEEN` for declared components. A generic class that is absent from the static index and all registered loaders is `CLASS_NOT_FOUND_STATIC`. A known installed feature can be marked `FEATURE_NOT_AVAILABLE`. A deliberately guarded class can be marked `PACKED_UNSUPPORTED`.

## Native runtime diagnostics

`NativeRuntimeState` records:

- device-supported ABI list;
- target native library directory;
- ABI directories found in target APK native entries;
- native library files visible in the target library directory and APK inventory;
- explicit load success/failure events, including `UnsatisfiedLinkError` details when a caller reports them;
- observation phase and timestamp.

The current runtime does not automatically intercept arbitrary `System.loadLibrary` or `dlopen` calls. That limitation is intentional. A native library's process UID/PID, Binder identity, linker namespace, `/proc` view, static state and JNI environment remain host/process behavior. Native support is therefore an evidence category, not a claim of target identity.

## Capability decisions

The runtime session uses four capability outcomes:

- `SUPPORTED`: the tested operation completed inside the current virtual boundary;
- `HOST_MEDIATED`: the operation completed through a host-owned API or identity;
- `DEGRADED`: a partial behavior completed with a known limitation;
- `UNSUPPORTED_WITH_REASON`: Mirro cannot provide the target contract truthfully.

This milestone records the model and loader evidence. It does not change GMS, Credential Manager, OAuth, UID, signature, or Play Integrity behavior.

## Facebook probe interpretation

Facebook is a diagnostic probe only. The current repository evidence establishes that its Application reaches `Application.onCreate()` and that the previous single loader did not resolve the expected Activity during the bounded wait. This milestone changes the failure from an ambiguous class-loader retry to an evidence record containing:

- loader nodes before Application bootstrap;
- loader nodes observed after Application.onCreate();
- installed executable split metadata;
- resolution attempts and their categories;
- native inventory and explicitly reported native load events;
- defining loader/source when a class resolves;
- final Activity category.

The exact Facebook mechanism is not inferred from the Activity name. `DYNAMIC_LOADER_UNSEEN`, `FEATURE_NOT_AVAILABLE`, `NATIVE_LOAD_FAILED`, `PACKED_UNSUPPORTED`, and `CLASS_NOT_FOUND_STATIC` are kept separate. No Facebook package name or Activity-specific branch exists in the shared runtime.

Device validation is required before claiming a Facebook result. The correct sanitized report must include the graph snapshots and final category, but must omit credentials, cookies, tokens, user content and private target data.

## Device evidence — 2026-09-20

The debug APK was installed on a Samsung SM-S928B test device running Android API 36 with `arm64-v8a`. The following probes were run from the Mirro launcher after the runtime/session changes:

| Probe | Result | Evidence category |
|---|---|---|
| ChatGPT clone | Hosted UI reached its login/input surface. The target Activity resolved from the root target loader. | `CLASS_FOUND_STATIC` |
| Facebook clone | Application bootstrap completed, but the declared login Activity remained absent from the registered loader graph after the bounded observation window. | `DYNAMIC_LOADER_UNSEEN` |
| Lightweight BA-mobil clone | Target Activity resolved statically, then the host lifecycle bridge failed and the process hit a GMS calling-package `SecurityException`. This is not counted as a known-working baseline. | Host lifecycle / identity boundary |

The Facebook log slice also showed legitimate native libraries loading from the target split/native namespace, including the target's unwind/breakpad libraries and Mirro's already-present compressed-Dex support library. That evidence does not establish that the missing Activity is native-packed or feature-delivered, so the final category remains `DYNAMIC_LOADER_UNSEEN`, not `NATIVE_LOAD_FAILED`, `FEATURE_NOT_AVAILABLE`, or `PACKED_UNSUPPORTED`.

No credentials, cookies, tokens, user content, clone identifiers, or private target data were added to the repository. The device evidence is a validation snapshot, not a compatibility claim for Facebook.

## Test contract

The focused unit tests cover:

- a class available only from a registered late DEX-style loader;
- a declared component absent from the static index becoming `DYNAMIC_LOADER_UNSEEN`;
- generic static class-not-found;
- installed executable split metadata;
- in-memory loader with no fabricated filesystem path;
- explicit feature and packed boundaries;
- native wrong-ABI/load failure diagnostics distinct from class resolution;
- a class becoming resolvable after a later loader is registered.

Device fixtures should extend this with actual APK/split, dynamic-feature, NDK and in-memory DEX applications when available. The milestone does not require Facebook itself to become runnable.
