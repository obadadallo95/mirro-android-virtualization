# Mirro Full Virtualization Architecture Audit

**Status:** research and architecture blueprint; no production implementation was changed by this audit.

**Scope:** repository audit, Android/AOSP research, public framework comparison, compatibility limits, Play distribution risk, and a staged Mirro v2 design.

**Date:** 2026-09-20

## Executive summary

Mirro has crossed the threshold where APK loading and Activity embedding are no longer the dominant problems. It already has a credible user-space guest runtime for a narrow class of applications: ordinary APK code, target resources, a redirected subset of `Context` storage, Application bootstrap, selected in-process services, and an embedded Activity lifecycle. The current implementation is honest about failures and does not pretend that a virtual package name creates a real Android identity.

The next compatibility ceiling is architectural:

1. **A single class loader is not a runtime code-loading boundary.** Modern apps may create `PathClassLoader`, `DexClassLoader`, `InMemoryDexClassLoader`, split loaders, native-created loaders, or encrypted/packed loaders after `Application.onCreate()`. Mirro indexes and loads the APK at startup, but it does not yet own a class-loader graph or observe/register later-created loaders.
2. **`Context.getPackageName()` is not Android identity.** The Linux UID, Binder caller UID, package/signing record in `PackageManagerService`, AppOps attribution, `AttributionSource`, Credential Manager `CallingAppInfo`, Play services checks, and hardware/Play attestation still belong to `app.mirro.android`. A virtual string can improve guest-facing Java behavior but cannot turn a host process into `com.openai.chatgpt`.
3. **System-service calls are mostly host pass-through.** `VirtualPackageManager` is a useful facade, but most service acquisition and Binder calls still reach host services with host UID and package attribution. The current `VirtualServiceManager` handles selected Java `Service` instances locally; it is not an Android service manager, ActivityTaskManager, JobScheduler, AlarmManager, provider, broadcast, notification, or AppOps virtualization layer.
4. **The Activity host is a compatibility adapter, not a virtual Android framework.** It can embed a target Activity and intercept some nested starts, but Android still owns the host task, window, process, tokens, permission state, task callbacks, and system lifecycle. This is appropriate for a supported subset and will not scale to arbitrary component behavior without a component fabric.
5. **WebView isolation is process-scoped, not clone-scoped in one process.** The existing suffix handling is directionally correct, but the platform requires the WebView data-directory suffix to be set once per process and before WebView initialization. One clone per process slot is therefore a real constraint, not just an implementation preference.
6. **GMS and attestation must be capability-gated.** Browser OAuth and some host-mediated APIs can be supported. Native Google Sign-In, Credential Manager, Play Integrity, hardware-bound keys, package-signature checks, and services that require the caller's real UID cannot be made truthful by a `PackageManager` facade. Mirro must return a precise unsupported/host-mediated result rather than spoof a security boundary.
7. **A Play-distributed container is possible but policy-constrained.** Google Play explicitly describes on-device Android container apps and allows them only under container-specific rules, including honoring `REQUIRE_SECURE_ENV`. Play policy also prohibits downloading executable code such as DEX, JAR, or `.so` from outside Google Play. Mirro must treat the target APK as user-selected, policy-reviewed executable input and must not become a remote code downloader or an anti-tamper bypass.

The recommended direction is an explicit **capability-oriented virtual runtime** with five planes:

```text
Target APK / guest code
        |
        v
Guest runtime plane
  class-loader graph, resources, native loader, guest Context
        |
        v
Virtual framework plane
  package, component, content, service, broadcast, job, alarm,
  permissions, AppOps, accounts, notification and intent registries
        |
        v
Host adapter plane
  public Android APIs, declared stub components, host process slots,
  Binder calls with explicit HOST-PASSTHROUGH or GUEST-LOCAL semantics
        |
        v
Android framework / system services / vendor services / GMS
        |
        v
Capability result: supported, host-mediated, degraded, or unsupported
```

The first milestone should be **runtime observability and a generalized dynamic-code contract**, not GMS spoofing. It should prove what code and component loaders appear, which loader owns each class, which native libraries are loaded, and which failures are caused by an unavailable class versus an unavailable system identity. Only after that evidence exists should Mirro invest in broader component and service virtualization.

## Scope, evidence, and terminology

This report distinguishes four states:

- **Fully virtualized:** guest code observes a stable Mirro-owned implementation and does not cross into a host identity for the relevant operation.
- **Partially virtualized:** Mirro provides a facade or adapter for some calls, but the platform or host identity remains observable.
- **HOST-PASSTHROUGH:** the operation uses the host application, host UID, host package, host permission, or host system object.
- **Not implemented:** there is no deliberate Mirro behavior for the relevant platform contract.

“Virtual UID” in this report means an internal clone identifier used by Mirro. It is not a Linux UID and must not be presented as one. “GMS” means Google Play services and related Google identity surfaces. “Guest” means target APK code running inside Mirro; it does not mean a second Android user or a separate OS package installation.

Evidence sources are grouped as follows:

- Mirro source and tests in this repository.
- Android and AOSP documentation/API references linked throughout this report.
- Public repositories and issue trackers. Marketing claims are marked as claims and are not treated as proof.
- Academic architecture research, especially Boxify/Anception, used for comparison rather than as a drop-in implementation.

## Current Mirro runtime map

### Startup path

The current startup path is approximately:

```text
ContainerHostActivity (:container)
    |
    +-- ContainerRuntime.prepareContainer()
    |       |
    |       +-- ApkInspector.inspect()
    |       +-- VirtualRuntimeIdentity / VirtualFileSystem
    |       +-- SingleCloneProcessSlotStrategy
    |       +-- WebView.setDataDirectorySuffix() once per process
    |
    +-- DexRuntimeLoader
    |       |
    |       +-- target base APK + executable split APKs
    |       +-- TargetClassIndex from APK DEX entries
    |       +-- MirroTargetClassLoader
    |
    +-- VirtualContext
    +-- ApplicationBootstrapper
    |       |
    |       +-- target Application constructor
    |       +-- attachBaseContext() with a narrow host-identity window
    |       +-- target Application.onCreate()
    |       +-- selected framework provider bootstrap
    |
    +-- TargetActivityHost
            |
            +-- target Activity.attach() / fallback bridge
            +-- host window/decor embedding
            +-- target lifecycle callbacks
            +-- nested target Activity interception
            +-- external Activity delegation and auth callback registry
```

### Current boundary map

| Boundary | Current state | Evidence in Mirro | Consequence |
|---|---|---|---|
| APK discovery and static metadata | **Partially virtualized** | `ApkInspector` reads base/split APKs, components, requested permissions, native directory | Split metadata is available, but installed/on-demand module state and package identity remain host-backed |
| Initial DEX loading | **Partially virtualized** | `DexRuntimeLoader`, `TargetClassIndex`, `MirroTargetClassLoader` | Startup target classes can resolve; later-created loaders are not registered |
| Class ownership | **Partially virtualized** | static target index plus target-first loader policy | A class absent from the startup index can remain invisible even when the app creates a valid loader later |
| Resources | **Partially virtualized** | target `createPackageContext`/resource fallback | Asset and resource paths are not a general resource-loader graph |
| Application bootstrap | **Partially virtualized** | `ApplicationBootstrapper` and provider bootstrapper | No real `ActivityThread`/`LoadedApk` binding for the target application |
| Guest package facade | **Partially virtualized** | `VirtualContext`, `VirtualPackageManager` | Guest Java code sees selected target values; framework callers see host identity |
| Internal storage | **Partially virtualized** | `VirtualFileSystem`, redirected `filesDir` etc. | Direct absolute paths, native code, external storage, provider URIs, and host UID semantics bypass the facade |
| SharedPreferences | **Partially virtualized** | host preferences keyed as `mirro_<clone>_<name>` | Logical separation exists, but it is not the target `shared_prefs` directory contract |
| PackageManager | **Partially virtualized** | target metadata overrides plus host fallback | UID, signing, installer, package visibility, permission grants, and many queries are host-backed |
| Activity manager/task manager | **Partially virtualized** | `TargetActivityHost` intercepts selected target-owned starts | Host task/window/token remain authoritative; no virtual task stack |
| Activity lifecycle | **Partially virtualized** | reflective `Activity.attach`, lifecycle forwarding, fragment support | Hidden APIs and host lifecycle coupling make behavior API/OEM dependent |
| Java services | **Partially virtualized** | `VirtualServiceManager` | In-process `Service` lifecycle only; no system restart, foreground, job, or separate guest process semantics |
| Content providers | **Not implemented as a general layer** | selected provider bootstrap only | `ContentResolver` is the host resolver; authority routing and provider grants are missing |
| Broadcast receivers | **Not implemented** | no general receiver registry/dispatcher | Manifest and runtime receiver semantics do not exist for target components |
| Jobs and alarms | **Not implemented** | no virtual scheduler | Host scheduler cannot identify or restore arbitrary guest jobs without stub components and a guest registry |
| Notifications | **HOST-PASSTHROUGH** | no guest notification manager facade | Channels, IDs, `PendingIntent`, permission and clone attribution are host-owned |
| Permissions | **Partially virtualized** | requested permission check delegates to host package permission | No per-clone runtime grant state or API-specific permission mediation |
| AppOps/attribution | **HOST-PASSTHROUGH** | `getOpPackageName()` returns host; system calls use host resolver/services | AppOps and attribution checks see Mirro, which explains GMS/Credential failures |
| Binder identity | **HOST-PASSTHROUGH** | normal Binder calls originate from Mirro process | A Java package string cannot change `Binder.getCallingUid()` or kernel credentials |
| Native loading/JNI | **Partially virtualized** | APK native path is supplied to class loader; no native bridge | ABI, linker namespace, `dlopen`, `/proc`, JNI identity, global symbols and native-created loaders remain host/process behavior |
| WebView | **Partially virtualized** | process suffix set once; one clone slot | Per-process data isolation is possible; many clones require separate processes |
| Browser/auth callback | **Partially virtualized** | `TargetAuthSessionRegistry`, `MirroAuthCallbackActivity` | Callback routing works for registered flows; browser cookies, credential identity and target signing remain external |
| Dynamic feature delivery | **Partially virtualized** | executable installed splits can be inspected | On-demand install/session identity and newly delivered code are not integrated |
| Process model | **Not scalable yet** | one clone per `:container` slot, process kill on finish | Good isolation for one active clone; poor concurrency and no process-death restoration fabric |

### Existing worktree state audited but deliberately preserved

At audit time the worktree already contained user changes to `DexRuntimeLoader.kt`, `ContainerRuntime.kt`, and `TargetActivityHost.kt`, plus an untracked `TargetActivityResolver.kt` and its test. Those changes add Activity-class resolution/preflight and bounded retry behavior. They were not modified by this audit. The new design treats them as diagnostic evidence, not as a completed dynamic-loader solution: retrying the same Mirro loader cannot discover a class whose defining loader has not entered Mirro's graph.

## Confirmed architecture gaps

### 1. Dynamic code is a graph, not a path

`DexRuntimeLoader` currently treats the base APK and executable splits as one startup `dexPath`. `TargetClassIndex` scans those APKs, and `MirroTargetClassLoader` routes classes by that static index. This works only when all relevant classes are present in the initial APK paths and the target uses the expected loader delegation.

Android exposes several legitimate runtime-loading mechanisms:

- `DexClassLoader` loads DEX/JAR/APK code from a path.
- `PathClassLoader` represents application and split paths.
- `InMemoryDexClassLoader` loads DEX from `ByteBuffer`; there may be no discoverable path.
- `BaseDexClassLoader` underlies the path-based loaders.
- Dynamic feature delivery adds code/resources after install or on demand.
- Native code can extract/decrypt payloads, call `dlopen`, and create or return Java class loaders through JNI.
- Obfuscators and packers may deliberately keep the defining loader hidden until a guarded code path executes.

The generalized solution is a `DynamicCodeManager` that owns a **loader graph**, not a single target loader:

```text
GuestLoaderGraph
  root loader: MirroTargetClassLoader
      |
      +-- installed split PathClassLoader
      +-- multidex / secondary-dex DexClassLoader
      +-- feature loader
      +-- target-created child loader
      +-- in-memory loader (metadata-only unless observed at creation)
      +-- native-origin loader (metadata-only unless a supported bridge observes it)
```

Each node needs: loader identity, parent/delegation mode, code sources, DEX checksums, native library search paths, defining APK/module, ownership classification, first-seen stack/phase, and registration status. Resolution should walk the graph with explicit parent-first/delegate-last rules and report the actual owner. A fixed retry loop is not a substitute for registration.

There are three observation tiers:

1. **Public, low-risk:** pre-index installed APK/split code; register known `ClassLoader` objects obtained from target Application/context/entrypoint APIs; inspect `Class.getClassLoader()` for successfully loaded classes; monitor Play Feature Delivery completion via the target's public API where possible.
2. **Compatibility instrumentation:** intercept or instrument creation of `BaseDexClassLoader`/`InMemoryDexClassLoader` and native loader boundaries in a controlled, opt-in compatibility build. This is version-sensitive and may require bytecode transformation, a sanctioned instrumentation mechanism, or native/runtime support unavailable to an ordinary Play app. It must not bypass anti-tamper checks.
3. **Hard boundary:** encrypted/packed code that never exposes a usable loader to the guest process, a loader created only inside a protected native runtime, or a library that rejects a nonstandard package/process cannot be promised. Classifying it as unsupported is the correct result.

Android's own `InMemoryDexClassLoader` documentation confirms that code can be supplied from a `ByteBuffer` rather than a filesystem path. Therefore path scanning alone cannot solve the category. Google Play policy also makes the source and delivery of executable code a distribution concern: Mirro may load code already provided by an installed/user-selected APK under its declared product model, but must not download executable DEX/JAR/native code from arbitrary servers.

### 2. System identity is a vector

Mirro currently virtualizes one component of identity: a guest-facing package string in selected Java APIs. Android identity is a vector:

```text
package name
Linux UID / user handle
process name and PID
signing certificate / SigningInfo
PackageManager package record
requested and granted permissions
AppOps package + UID
AttributionSource chain
Binder caller UID/PID
installer / Play package state
account and credential caller metadata
WebView/browser origin and cookies
hardware-backed key/attestation binding
Play Integrity package/certificate verdict
```

The platform documentation says `Context.getOpPackageName()` exists so AppOps UID verification works with the name. `AttributionSource` carries package and UID information and can be verified by framework services. `Process.myUid()` is the process's kernel-owned application UID. A `VirtualPackageManager` return value cannot change these facts.

The observed ChatGPT failure follows directly from this mismatch:

```text
guest Context.getPackageName()       -> com.openai.chatgpt
physical process / Binder UID        -> app.mirro.android / Mirro UID
Context.getOpPackageName()           -> app.mirro.android
Credential Manager CallingAppInfo    -> host package/signing information
Google Play services lookup           -> host call without target package record
```

The pre-callback browser routing can be correct while authentication still fails before a redirect exists. Callback correctness does not solve caller authentication.

### 3. PackageManager is a registry, not a set of overrides

`VirtualPackageManager` has a useful target `ApplicationInfo`/`PackageInfo` overlay, but many methods delegate to the host package manager. This creates contradictory answers:

- guest package name says target;
- `getPackageUid(target)` returns host UID;
- `getPackagesForUid()` describes the host;
- signing information is host/installed package data rather than a deliberate guest record;
- requested permissions may be target metadata while grant checks consult host package state;
- component resolution can use host-installed component metadata while execution uses a target class loader;
- installer, package visibility, app labels, resource paths, data paths and process names may mix.

The v2 package layer needs a complete **virtual package record** with an explicit field policy for every returned value. It should distinguish `guestValue`, `hostValue`, and `unsupported`, rather than silently forwarding. The record must include component declarations, split/module state, ABI/native paths, target SDK, data roots, process names, permission declarations, virtual grants, signing metadata as *descriptive target metadata only*, and a capability result for any API that would require a real installed package.

### 4. ContentProvider and ContentResolver are a missing center of gravity

`VirtualContext.getContentResolver()` returns the host resolver. Provider initialization is a narrow bootstrap for known provider classes, not a general provider runtime. This breaks common app behavior:

- target authorities are not mapped per clone;
- `ContentProvider.attachInfo()` does not receive a complete guest `ProviderInfo`/authority identity;
- URI permissions and persistable grants are not clone-scoped;
- `FileProvider` authorities collide or expose host paths;
- `DocumentsProvider`, MediaStore, contacts, settings, and account providers remain OS-owned;
- provider process/lifecycle/death semantics do not exist;
- native code and libraries can use a resolver directly through system APIs.

A `VirtualContentManager` is required before broad compatibility claims. It needs a guest authority table, URI translation, local target-provider dispatch, host-provider passthrough rules, clone-scoped grants, and a safe `ContentResolver` facade. It must never claim to grant a permission that the host process does not possess.

### 5. Component execution is incomplete

Activities are the most mature target component in Mirro, but the host Activity remains the real Android component. Services are local Java objects. Receivers, providers, jobs, alarms, notifications and pending intents have no equivalent registry. Modern applications rely on these components for login completion, push, media playback, sync, widgets, shortcuts, and process restoration.

Android's manifest contract is significant: components must be declared to be seen by the system; jobs refer to declared `JobService` components; `PendingIntent` contains creator identity; manifest receivers allow the system to launch an app process; foreground services have typed permissions and background-start restrictions. A virtual runtime cannot receive those callbacks merely by knowing the target manifest. It must map them through predeclared Mirro stub components and persist enough clone state to restore the guest.

### 6. Native code has process-wide consequences

Passing a native library directory into a class loader is not native virtualization. Native code can observe:

- process UID/PID/name and `/proc`;
- linker namespace and public-library visibility;
- ABI and instruction set;
- filesystem paths and `/data` layout;
- JNI package/class lookups;
- Binder caller identity;
- loaded library handles and global symbols;
- crash/tombstone behavior;
- WebView/graphics/native singleton state.

The Android linker namespace documentation describes app library namespaces and visibility restrictions; these are platform-managed, not guest-managed. Multiple clones in one process can also collide on native singletons, `JNI_OnLoad`, static state, library SONAMEs, and global callbacks. Native-heavy compatibility therefore requires one active clone per process slot at minimum, careful ABI selection, and a capability result when the app expects a genuine installed package or protected native environment.

### 7. Background work has no owner yet

Mirro currently does not have a persisted guest scheduler. WorkManager and JobScheduler are OS-backed and expect declared components; alarms use `PendingIntent` identity; FCM delivery uses Google services and process/background policy; foreground services require user-visible notifications, typed permissions and launch eligibility. Process death and reboot require a host component to restore the virtual runtime and replay the guest registry.

The correct design is not to let arbitrary guest code register jobs directly with the host scheduler. It is to create a **host-owned scheduler and stub-component fabric** that stores `(cloneId, guestComponent, job/alarm/push metadata)` and explicitly starts/restores the clone subject to host permissions and Android background rules. That is degraded compatibility, but it is truthful and testable.

## Dynamic DEX, feature modules, and the Facebook failure

### What the current evidence proves

The observed Facebook behavior proves that:

- the target APK's Application can instantiate and reach `Application.onCreate()`;
- the current startup class loader does not resolve the expected `LoginActivity` at the point Mirro tries to host it;
- waiting and retrying the same loader does not produce the class;
- callback routing is not the first failure in this path.

It does **not** prove which Facebook mechanism supplies the class. Without the exact APK version, split inventory, `ClassNotFoundException` stack, loader graph, and runtime traces, the following hypotheses remain possible:

| Hypothesis | What would confirm it | Current confidence |
|---|---|---|
| Class is in an installed split APK | `LoginActivity` appears in a split DEX, but not the initial `dexPath`; class loader path changes after split install | Plausible |
| Runtime-created DEX loader | a new `DexClassLoader`/`PathClassLoader` appears after `Application.onCreate()` and owns the class | Plausible |
| In-memory DEX | `InMemoryDexClassLoader`, `ByteBuffer`, or native handoff appears; no file path exists | Plausible |
| Native extraction/decryption | native `dlopen`/JNI path creates or registers a loader after an authenticated code path | Plausible |
| Generated alias/indirection | manifest resolves an alias or wrapper whose class differs from the expected name | Possible; inspect component resolution |
| Packed/anti-tamper gated code | loader creation is deliberately blocked in a nonstandard/container process | Possible |

Public information about a commercial Facebook APK is not enough to identify the exact mechanism for a particular version, and the repository contains no loader trace that proves one. The correct response is a general diagnostic and loader-graph milestone, not a Facebook-specific mapping.

### Required diagnostic for this class

For each target launch, persist a structured trace with:

- APK/split paths and DEX class membership;
- all known `ClassLoader` objects and their parent/delegation policy;
- class lookup attempts, owner candidates, and final defining loader;
- target component resolution before and after `Application.onCreate()`;
- feature-install state and session result;
- native library paths, ABIs, `System.loadLibrary`/`dlopen` outcomes where observable;
- `ClassNotFoundException`, `VerifyError`, `NoClassDefFoundError`, `UnsatisfiedLinkError`, and hidden-API failures separately;
- whether the missing class is absent, unregistered, inaccessible, or deliberately blocked.

The outcome should be one of `STATIC_MISSING`, `DYNAMIC_LOADER_UNSEEN`, `FEATURE_NOT_INSTALLED`, `NATIVE_LOAD_FAILED`, `PACKED_UNSUPPORTED`, `COMPONENT_RESOLUTION_ERROR`, or `SYSTEM_IDENTITY_BLOCKED`.

### Legitimate architecture options

The order of preference is:

1. Pre-index and register all installed base/split code.
2. Register target-created loaders through a `DynamicCodeManager` at the earliest supported boundary.
3. Attach loader nodes to a shared guest namespace with explicit delegation rules.
4. Treat in-memory/native-origin loaders as observable only if an approved instrumentation boundary can report their metadata.
5. If the app intentionally hides or refuses to execute inside the container, stop with a diagnostic.

Mirro should not decrypt proprietary payloads, disable anti-tamper, bypass Play Integrity, or alter security checks to make a class appear. Those are both product and distribution boundaries.

## GMS, Credential Manager, accounts, and identity-sensitive APIs

### Identity classification

| Surface | Usually proxied legitimately? | Real package/signature/UID required? | Mirro v2 position |
|---|---:|---:|---|
| Browser OAuth / Custom Tabs | Yes, with explicit host-mediated flow | Redirect ownership and browser state still matter | Support with clear host/browser capability and clone callback registry |
| App-owned HTTPS API | Yes | Server may bind tokens to app/client identity | Support only with target's normal documented flow; no caller spoofing |
| Credential Manager basic call | Host-mediated only | Framework constructs caller metadata from real package/signing context | Support as Mirro host or classify target flow unsupported; do not forge `CallingAppInfo` |
| Google Sign-In native APIs | Limited | OAuth Android client commonly binds package + SHA-1; GMS validates caller | Support only where official API accepts host configuration and product consent; otherwise unsupported |
| Firebase Auth browser flow | Often | Native SDK and caller/API key/project settings vary | Test per SDK flow; browser-backed flows may work, native identity checks may not |
| FCM registration/delivery | Host-mediated | token/project/app identity and background process are OS/GMS-managed | Host relay can support selected scenarios; per-clone guarantees require a product-level design |
| AccountManager / system accounts | Limited | account authenticators and grants are OS package/UID controlled | Host accounts may be exposed only by explicit policy; no guest account authenticator claim |
| Maps/Location/Drive/Billing | API-dependent | package, certificate, permission, account, AppOps and Play services checks vary | Adapter per API; no blanket GMS promise |
| Play Integrity | No truthful target impersonation | verdict identifies recognized package/certificate and device state | Host-only diagnostic; never bypass or fabricate verdicts |
| Hardware Keystore attestation | No target impersonation | attestation is hardware-rooted and package/signing facts are verifiable | Use Mirro-owned keys, or classify target app-bound attestation unsupported |

### Credential Manager

The Android Credential Manager service creates `CallingAppInfo` containing the calling package and `SigningInfo`; `GetCredentialRequest` can propagate caller app information to providers. `Context.getOpPackageName()` also exists specifically to support AppOps UID verification. That explains why changing `Context.getPackageName()` is insufficient. Mirro can host a credential flow under `app.mirro.android` and disclose that behavior, but it cannot honestly make the system believe the Binder caller is `com.openai.chatgpt` signed by OpenAI.

The browser-origin path is different. A privileged browser may provide an origin to Credential Manager under its documented permission model. Mirro is not automatically a privileged browser, and it must not impersonate one. Passkeys and other origin-bound credentials should be considered capability-specific and tested separately.

### Google Sign-In and GMS-dependent apps

Google's legacy Android OAuth documentation explicitly uses package name and SHA-1 certificate for Android OAuth clients. Google Play services APIs also use their own package and availability checks. A bridge can proxy data or use a host-owned OAuth client only when the target/backend accepts that arrangement. It cannot universalize identity-sensitive native APIs.

GMS itself should not be copied, re-signed, or treated as a guest library. A per-API `GmsBridge` should declare one of:

- `HOST_NATIVE`: use Mirro's real identity and configuration;
- `BROWSER_MEDIATED`: use documented browser OAuth/Custom Tabs;
- `TARGET_METADATA_ONLY`: expose target metadata to guest Java code but do not call an identity-sensitive GMS service;
- `UNSUPPORTED_IDENTITY_REQUIRED`.

## Binder and system-service virtualization

### Current behavior

Mirro's `ServiceProxyRegistry` is a local object map with host fallback, not a Binder interception layer. `VirtualContext.getSystemService()` therefore returns host services for most names. Target service attachment uses reflective framework internals and host `ActivityThread`/AMS objects. Calls made through those services originate from the Mirro process.

### Priority classification

| Service / surface | Priority | Required treatment | Why |
|---|---:|---|---|
| PackageManager / package installer queries | Must virtualize | Complete guest registry with explicit host/guest/unsupported fields | Almost every app queries package, permission, component, UID or signing state |
| ActivityManager / ActivityTaskManager | Must virtualize at the guest boundary | Intent/component routing, task stack, activity results and lifecycle registry | Needed for nested Activities, flags, results, task affinity and restoration |
| Content / provider manager | Must virtualize | Authority and URI router plus provider lifecycle/grants | Central to preferences, accounts, files, media, settings and SDKs |
| Permission manager + AppOps | Must virtualize in policy layer | Per-clone logical grants mapped to host capabilities; report host denial | Prevent contradictory checks and unsafe privilege claims |
| Service lifecycle | Must virtualize | Guest service registry and host stub components | Required for binding, stop/restart, foreground state and process death |
| Broadcast / receiver | Must virtualize | Guest intent registry and host receiver/scheduler dispatch | Required for boot, package, connectivity, alarms, push and SDK initialization |
| JobScheduler / WorkManager | Must virtualize | Host-owned durable scheduler mapping to guest jobs | OS scheduler cannot know target components that are not installed |
| AlarmManager / PendingIntent | Must virtualize | Clone-scoped immutable identity and host alarm mapping | PendingIntent creator identity and alarm restoration matter |
| NotificationManager | Must virtualize at least the guest API | Clone-scoped channels/IDs/actions and host notification policy | Notification permission, actions and pending intents must be consistent |
| AccountManager | Adapter | Explicit host-account policy and token isolation | Accounts are OS-owned and security-sensitive |
| Google Play services | Adapter | Per-API bridge and capability result | Many APIs validate caller package/cert/UID |
| ShortcutManager | Adapter | Clone-scoped IDs and host launcher mapping | IDs, intents and launcher ownership are host-visible |
| Clipboard | Safe host pass-through with policy | Respect Android clipboard privacy and foreground restrictions | No guest identity benefit; data exposure must be explicit |
| Connectivity / power | Safe host pass-through | Host permission and AppOps govern access | Guest may observe host environment; do not claim target isolation |
| Location / sensors / media | Adapter | Host permission mediation and target API adapters | Sensitive data and AppOps need disclosure and correct ownership |
| Window / input method / accessibility | Not generally virtualizable in user space | Host UI only; use public APIs | Deep framework ownership and policy restrictions |
| User / mount / storage service | System-privileged | Host adapter only | Real user/UID/mount namespaces require OS support |

### Binder hard limit

A normal app can own local proxy objects and route calls intentionally made through those objects. It cannot generally replace every framework Binder handle, change kernel caller credentials, or make a system service accept an uninstalled package with a different signature. Techniques that edit hidden `ServiceManager` caches or framework singletons are version-specific, subject to non-SDK restrictions, and unsuitable as the sole Play-compatible architecture. They may be useful in a controlled research build, but must be isolated from the product compatibility contract.

Android's non-SDK restrictions state that hidden interfaces can be blocked regardless of target SDK and may change without notice. Mirro currently uses reflective `Activity.attach`, hidden lifecycle fields and `VMRuntime.setHiddenApiExemptions`; these must be treated as compatibility debt and measured on every supported Android/OEM matrix.

## Component virtualization analysis

| Component | Current Mirro | Required v2 design | Limitation |
|---|---|---|---|
| Activity | embedded host Activity; nested target starts intercepted | virtual task/Activity registry and predeclared host launch surfaces | host window/task/token remain authoritative |
| Service | local target `Service` objects | service registry + declared host stubs + restart/foreground state | arbitrary target component is not dynamically manifest-declared |
| Foreground service | not general | host FGS with documented type/permission, clone notification and user-visible state | host permission and current Android launch restrictions apply |
| BroadcastReceiver | absent | receiver registry, host receiver stubs and durable dispatch | background restrictions and manifest filters still host-owned |
| ContentProvider | narrow eager provider bootstrap | authority registry, local provider dispatch, URI/grant translation | remote/system providers remain host-owned |
| AppWidget | special service lookup only | host AppWidgetHost mapping and clone-specific IDs | launcher and widget provider identity are host-visible |
| Job / WorkManager | absent | virtual durable job store with host scheduler/stub | execution time and quotas are Android-controlled |
| Alarm | absent | host alarm with clone-scoped `PendingIntent` registry | exact alarms and idle policies apply to Mirro |
| PendingIntent | absent | create/resolve guest intent descriptors through host-owned immutable wrappers | creator UID remains Mirro |
| Notification | host pass-through | guest notification facade, channels, IDs and action router | host app is notification owner |
| Deep link / App Link | narrow callback activity | declared host receiver plus session/clone routing | domain verification belongs to host package and browser |
| Custom Tab | callback registry | browser-mediated auth capability | browser cookie/session is not per-clone WebView storage |
| FileProvider | absent as a virtual authority | clone authority translation to host provider and clone path | URI permissions and authority collisions are security-sensitive |
| DocumentsProvider / SAF | absent | host SAF adapter with persisted per-clone URI grants | system picker and provider package remain host/system-owned |

## Storage, permissions, and identity model

### Storage contract

`VirtualRuntimeIdentity` and `VirtualFileSystem` create useful logical roots for files, cache, databases, shared preferences, code cache, no-backup, WebView and temporary data. The following inconsistencies must be corrected in the design contract before implementation:

- `getSharedPreferences()` currently delegates to host preferences with a clone-prefixed name rather than using the physical clone `shared_prefs` root.
- `dataDir` is logically redirected, but a target that asks Android for its package data directory, uses an absolute target path, or invokes native file APIs can detect the host layout.
- external files/cache are appended under host external roots rather than being a platform-owned target package external root.
- `credentialProtectedDataDir` and device-protected storage need separate, boot-aware roots.
- SAF, MediaStore, FileProvider and URI grants are not file-path redirection problems; they require provider and permission routing.
- a clone can be isolated from another clone inside Mirro, but it is not protected by a separate Linux UID from other apps. Host app sandboxing protects Mirro's whole data area.

The storage manager should own a `GuestPath` abstraction and expose a policy for direct filesystem access: `REDIRECTED`, `HOST_SHARED`, `DENIED`, or `UNSUPPORTED`. It should not promise path transparency where the platform cannot provide it.

### Permissions and AppOps

Requested target permissions are metadata. A runtime grant is a tuple involving the installed host package, UID/user, platform permission controller and AppOps. Mirro can maintain logical clone grant state and ask the user for host permissions, but it must never report a host grant as if it were a genuine target UID grant to a system service.

The correct model is:

```text
guest permission request
    -> VirtualPermissionManager logical grant
    -> host capability check / runtime prompt
    -> AppOps policy for app.mirro.android
    -> guest result: granted, host-mediated, denied, unsupported
```

Attribution tags must be included in the logical key, but the system's verified UID remains the host. Sensitive APIs need prominent disclosure and consent consistent with Play's User Data policy.

## Native/JNI compatibility

### What can work

- ABI-compatible libraries packaged in the target APK or installed splits can often be found and loaded in a dedicated Mirro process.
- JNI methods that operate only on target Java objects and public APIs may work under a target class loader.
- per-clone process slots reduce global native-state collisions.
- native libraries can be copied into clone-private code cache only if the source, licensing, integrity, and Play delivery model permit it.

### What cannot be promised

- a native call to `getuid()`, `getpid()`, `/proc`, Binder or package manager will see the target identity;
- a target library can be placed in the target's actual linker namespace;
- arbitrary `dlopen`/JNI-created class loaders can be discovered after the fact;
- two clones can safely load incompatible versions of the same globally named native library in one process;
- hardware-backed attestation will report the target package/signature;
- anti-tamper or DRM code will accept an embedded host process.

The NDK ABI and JNI documentation, Android linker namespace documentation, and the platform's application sandbox documentation together support a conservative native classification. Native support should be measured by library/ABI/load result and not inferred from `nativeLibraryDir` metadata alone.

## WebView, browser, and authentication

### WebView

The current suffix strategy is necessary and should survive. WebView data directories are process-global; the suffix must be set before WebView initialization and only once. Therefore:

- one active clone per WebView process slot is the safe default;
- a process cannot switch clones after WebView initialization;
- cookies, local storage, service workers and WebSQL are separable only within the suffix contract;
- native WebView/Chromium state and browser policies remain host/device behavior;
- an app using external Chrome/Custom Tabs does not inherit WebView suffix isolation.

### OAuth and browser flows

Mirro's `TargetAuthSessionRegistry` and callback router solve a real routing problem: an external browser can return to a host callback Activity and Mirro can deliver the result to the correct clone. They do not isolate browser cookies or change the OAuth client identity. Redirect URIs, claimed HTTPS domains, App Links, package signatures and browser session state remain external.

The supported contract should state whether an auth flow is:

- `WEB_REDIRECT_ROUTED`: callback is routed but browser session is shared;
- `WEBVIEW_CLONE_ISOLATED`: WebView data uses a clone suffix;
- `HOST_CREDENTIAL`: Credential Manager runs as Mirro;
- `TARGET_IDENTITY_REQUIRED`: not supported by user-space virtualization.

Passkeys, Autofill and Credential Manager need separate tests because they use origin, package, signing and provider policy beyond callback routing.

## Background execution, notifications, and process death

Android can kill processes based on component importance; it does not preserve arbitrary in-memory virtual objects. A robust runtime therefore needs durable clone state and host-owned restoration:

```text
OS wakeup (receiver / job / alarm / FCM / user launch)
    -> Mirro stub component
    -> ProcessManager allocates a declared slot
    -> RuntimeSession restores clone + loader graph + component registry
    -> guest callback runs under host policy
    -> state/notification/result is persisted
```

The host must not silently defeat Doze, background-start restrictions, FGS type rules, notification permission, or battery optimization. WorkManager can be used for Mirro's own durable orchestration, but a guest WorkManager database must be translated into a clone registry; simply running the target worker in the host process does not reproduce Android's package/job identity.

FCM is particularly constrained. The FCM token, Firebase Installations identity, Google services caller, process wakeup and notification delivery are not automatically per-clone. A product-level host relay may be possible for selected apps and explicit consent, but Mirro should not advertise independent FCM identity until it is proven for the target SDK and backend.

## Process architecture

### Current model

The current `:container` process with a single `SingleCloneProcessSlotStrategy` is appropriate for the present WebView and native-isolation constraints. Killing the process on final Activity destruction also avoids stale global state. It is not scalable because only one clone is active, process death loses in-memory runtime state, and there is no durable component wakeup path.

### Recommended user-space model

Use a **fixed declared process pool**, not dynamically invented Android processes:

```text
Mirro host process
  ├── :container0  -> one active clone/session
  ├── :container1  -> one active clone/session
  ├── :container2  -> one active clone/session
  └── :background  -> scheduler/router; no arbitrary guest UI
```

Each slot has:

- one `RuntimeSession` and one clone binding;
- one WebView suffix;
- one loader graph;
- one native namespace/process lifetime;
- a persisted lease and clean release protocol;
- diagnostics for process death and restoration.

The number of simultaneously active clones is bounded by manifest-declared slots, memory, native/WebView cost and Android process limits. A slot can be reused only after process teardown and state checkpoint. `android:isolatedProcess` is not a shortcut: an isolated service has no permissions of its own and cannot run a full target environment with normal app access. It is useful for a narrow untrusted worker, not as a guest UID implementation.

### OS-level alternatives

- **Work profile / managed profile:** real OS-managed separation, separate storage and profile boundaries, but changes product UX and requires profile/admin semantics; not Mirro's normal mode.
- **Secondary Android user:** real package/user separation, but explicitly outside product goals and generally requires system/user management.
- **AVF/Microdroid/full VM:** stronger isolation and a guest OS, but AVF/Microdroid is a protected execution environment with a limited Android API surface, not a drop-in full Play Android user. It requires supported hardware and platform integration and is not a normal replacement for Android app virtualization.
- **Root/system privileges:** could alter more framework state but is not a Play-distributed normal-app model.

## Competitor and research comparison

### VirtualApp

The public VirtualApp repository documents a multi-process architecture, including host main/plugin processes, virtual app processes and a server, and describes Java/native hook machinery and GMS support claims. Its README also states that public code stopped receiving updates in 2017 and points to a commercial version. The architecture is useful evidence that broad compatibility requires process roles, service hooks, component routing and native handling. The marketing claim of current GMS support is not proof for present Android releases. Do not vendor or copy it without a full license/IP review.

Source: [VirtualApp README](https://github.com/asLody/VirtualApp/blob/master/README_eng.md).

### DroidPlugin

DroidPlugin's public README explicitly lists limitations: plugin notifications/resources are constrained, plugin components cannot behave like normally installed components outside the host, and the framework lacks native-layer hooks such that many native apps cannot load. This is valuable negative evidence: PackageManager and component virtualization alone do not solve native compatibility. The repository is LGPL-3.0 and old; architecture-only study is the safe current recommendation.

Sources: [DroidPlugin repository](https://github.com/DroidPluginTeam/DroidPlugin), [DroidPlugin license](https://github.com/DroidPluginTeam/DroidPlugin/blob/master/LICENSE).

### VirtualXposed

VirtualXposed's public documentation describes a VirtualApp-based wrapper, Java method hooks, and native/I/O redirection for compatibility. Its README limits Android support, says resource hooks are unsupported, and includes commercial-use restrictions. It illustrates how high compatibility is obtained through deep hooks that are inherently version-sensitive and difficult to reconcile with a Play-safe public-SDK design.

Sources: [VirtualXposed repository](https://github.com/android-hacker/virtualxposed), [VirtualXposed architecture wiki](https://github.com/android-hacker/VirtualXposed/wiki/How-does-VirtualXposed-work).

### BlackBox family

The original BlackBox repository is marked as dissolved and should not be treated as a maintained or licensed dependency. Public forks claim support for services, GMS, UID spoofing, native hooks and Android versions, but those are self-reported claims. Issues in the family include class-loading and component-resolution failures, which reinforce the need for actual device test evidence. Fork provenance and transitive native-hook licenses require independent review.

Sources: [original BlackBox](https://github.com/FBlackBox/BlackBox), [example fork claims](https://github.com/ALEX5402/NewBlackbox), [example loader issue](https://github.com/FBlackBox/BlackBox/issues/142).

### Academic and OS-level approaches

Boxify/Anception research uses a stronger architecture: an isolated process/UID and reference-monitor proxies around Android system interactions, rather than only a Context wrapper. This is useful as a conceptual upper bound, but it is not a recipe for a normal Play app because privileged process/system-service integration and compatibility with Android releases are materially different.

Sources: [USENIX Security 2015 proceedings](https://www.usenix.org/sites/default/files/sec15_full_proceedings.pdf), [Anception paper](https://arxiv.org/abs/1401.6726).

AVF/Microdroid provides a genuine protected virtual environment, but Microdroid does not include the full Android Java framework, SystemServer, Zygote, graphics or HALs. It is a security VM, not a drop-in Parallel Space runtime.

Sources: [AVF overview](https://source.android.com/docs/core/virtualization), [Microdroid](https://source.android.com/docs/core/virtualization/microdroid).

## Play policy and distribution risk

This is a policy review, not legal advice. Re-check the current policy before every distribution decision.

### Container-specific rules

Google Play defines an on-device Android container as an app that loads third-party APKs into its own app space so they reasonably execute as installed through interception/proxying. The policy permits the category under explicit requirements and says containers must check target manifests and respect `REQUIRE_SECURE_ENV`; an app that opts out may not be loaded. The policy also says container apps cannot proxy calls to outside apps in a way that disguises them as inside the container.

Sources: [On-device Android container apps](https://support.google.com/googleplay/android-developer/answer/13609005?hl=en), [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en).

### Executable code

The current Device and Network Abuse policy says Play-distributed apps may not download executable DEX, JAR or `.so` code from a source other than Google Play. It distinguishes code in a VM/interpreter, but that exception is not a blanket license to download arbitrary Android APK code or bypass another app's protections. Mirro should load only code within the product's declared, user-consented APK/container model, keep provenance and integrity metadata, and avoid remote executable delivery.

### Package visibility and user data

`QUERY_ALL_PACKAGES` is restricted to core use cases that require broad installed-app visibility, with examples including device search, antivirus, file managers and browsers. Mirro's product purpose may not automatically qualify; targeted `<queries>` plus explicit user selection is safer. Installed-app inventory and target app data are personal/sensitive data under Play's User Data policy. Mirro needs prominent disclosure, consent, secure handling, a privacy policy and accurate Data Safety declarations.

Sources: [QUERY_ALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en), [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB), [prominent disclosure guidance](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en).

### Security-sensitive surfaces

Mirro must not bypass Play Integrity, hardware attestation, DRM, package signatures or anti-tamper. It must not access another app's protected data, disable secure flags, interfere with ads, or use dynamic code loading as an evasion mechanism. Target apps opting out with `REQUIRE_SECURE_ENV` need to be rejected before execution.

### Non-SDK/API risk

The current Activity/service bridges use hidden framework APIs. Android documents that non-SDK APIs can be blocklisted, can throw under reflection/JNI, and change without notice; Android 16 has a current hidden API list. This is a material compatibility and Play pre-launch risk.

Source: [non-SDK interface restrictions](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces).

## Security hard limits

### Realistically possible in user space

- guest-facing target package/resources in controlled Java APIs;
- clone-specific logical storage roots and databases;
- target code/resource loading from known APK/split paths;
- embedded Activity execution for compatible apps;
- selected local Service/provider/component facades;
- browser callback routing and per-process WebView data directories;
- logical permissions and AppOps policy with truthful host capability results;
- selected host-mediated GMS/browser APIs.

### Not truthful or not generally possible

- changing the process's Linux UID or Binder caller UID to an arbitrary target;
- making the system package manager treat Mirro as the target's installed signed package;
- forging `SigningInfo`, package ownership, installer identity or Play recognition;
- creating target hardware-backed app-bound keys or target Play Integrity verdicts;
- obtaining target-only privileged/signature permissions;
- unlimited dynamic Android processes/components without manifest-declared host support;
- universal FCM/AccountManager/GMS per-clone identity;
- universal native/packed/anti-tamper support;
- making browser cookies, passkeys or App Links per-clone without the relevant external owner participating.

The product must expose these as capability boundaries. A virtual string that claims more than the kernel/framework will accept is not compatibility; it is a security bug and a distribution risk.

## Compatibility taxonomy and representative matrix

The detailed matrix is in [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md). At a high level:

| Type | Application shape | Expected Mirro support after current milestone | Fundamental risk |
|---|---|---|---|
| A | standard APK, ordinary components | high | system APIs and permissions still need adapters |
| B | WebView/browser-heavy | medium-high | process suffix, browser cookies, passkeys, redirects |
| C | GMS-dependent | low-medium, API-specific | caller package/signature/UID checks |
| D | dynamic DEX/native loader | low now; medium after loader graph | packed/in-memory/anti-tamper boundaries |
| E | dynamic feature modules | medium for installed splits; low for on-demand | delivery/session identity and late code |
| F | heavy background service | low now; medium with scheduler fabric | process death, FGS policy and declared stubs |
| G | FCM messaging | low-medium, host-mediated | token/installation identity and background wakeup |
| H | hardware Keystore/attestation | low except host-owned keys | hardware and package binding |
| I | Play Integrity-protected | unsupported as target identity | verdict is server-verifiable |
| J | banking/DRM/anti-tamper | unsupported or explicitly opt-in only | security model rejects containers |

## Full gap table

The priority labels are `P0` contract/measurement, `P1` foundational compatibility, `P2` broad component coverage, and `P3` specialized adapters.

| Subsystem | Current Mirro | Required behavior | Reference approach | Difficulty | Play risk | Security limitation | Priority |
|---|---|---|---|---|---|---|---:|
| ClassLoader | startup target loader + static index | graph registration and ownership | loader graph manager | high | medium if instrumentation is deep | in-memory/packed code may remain invisible | P1 |
| Dynamic DEX | no later-loader integration | observe/register path and in-memory loaders | creation boundary + diagnostics | high | high for remote executable delivery | no anti-tamper bypass | P1 |
| Native loader | path passed to loader | ABI/load/namespace diagnostics and process isolation | native runtime adapter | high | medium | UID/namespace remain host | P1 |
| PackageManager | overlay + host fallback | complete guest registry with explicit policy | virtual PM registry | high | medium | cannot change system PM identity | P1 |
| ActivityManager | selected local interception | virtual intent/task/result registry | component router | high | medium | host task remains real | P1 |
| ActivityTaskManager | absent | task/back-stack/affinity model | virtual task manager | high | medium | window/token are host-owned | P2 |
| Binder | normal host calls | explicit proxy/adapter boundaries | per-service adapters | very high | high | kernel caller UID cannot be spoofed | P1 |
| ContentProvider | narrow bootstrap | authority, URI and grant routing | virtual content manager | high | medium | system providers remain host-owned | P2 |
| Service virtualization | local Java services | binding, restart, FGS and death semantics | registry + host stubs | high | medium/high | service permission is host-owned | P2 |
| Broadcasts | absent | registry and stub dispatch | broadcast manager | medium-high | medium | background rules remain host | P2 |
| Jobs | absent | durable clone job store | host scheduler mapping | medium-high | medium | quotas/timing are OS-controlled | P2 |
| Alarms | absent | clone-safe pending intents and restore | alarm adapter | medium | medium | exact alarm/power policy remains host | P2 |
| Permissions | host package checks | logical grants mapped to host capability | permission manager | medium-high | high for sensitive data | no target UID grants | P1 |
| AppOps | host attribution | guest policy plus host truthful result | AppOps adapter | high | high | UID verification remains host | P1 |
| Process identity | host process/package/UID | explicit capability reporting | fixed process pool | medium | medium | cannot become target process | P1 |
| Virtual UID | internal clone ID | never expose as Linux UID | logical identity only | low | high if misrepresented | no kernel identity | P0 |
| Signatures | target metadata incomplete | descriptive metadata only | signature policy table | medium | high | cannot forge signing cert | P0 |
| Installer source | host-backed | explicit unsupported/host result | package registry field policy | low | medium | Play install ownership stays host | P2 |
| Google Play services | host pass-through | per-API bridge/capability result | GmsBridge | high | high | many callers validated | P3 |
| Credential Manager | host call | host-mediated flow only | credential adapter | high | high | CallingAppInfo/signing is real | P3 |
| AccountManager | absent/host | explicit host-account policy | account adapter | high | high | account authenticator ownership | P3 |
| Firebase | no general bridge | SDK-specific test and host config | facade/allowlist | high | high | installation/app identity varies | P3 |
| FCM | absent | host wakeup and clone routing | host relay/scheduler | high | high | token and GMS identity | P3 |
| WebView | per-process suffix | slot ownership and restore | process manager | medium | low/medium | browser/Chromium external state | P1 |
| Notifications | host pass-through | clone IDs/channels/action router | notification manager | medium | medium | host permission and identity | P2 |
| PendingIntent | absent | immutable clone descriptor map | pending-intent adapter | high | high | creator UID remains host | P2 |
| Native/JNI | no bridge | ABI and load diagnostics | native runtime bridge | high | high | process/linker identity | P1 |
| Dynamic features | installed splits only | feature state and late loader registration | feature adapter | high | medium/high | on-demand identity/Play delivery | P1 |
| Storage | logical roots, host prefs | full guest path/provider contract | storage manager | medium-high | medium | UID protects host root | P1 |
| External storage | host-root append | scoped URI/path adapter | storage/provider bridge | high | high | MediaStore/SAF host-owned | P2 |

## Recommended Mirro v2 architecture

```text
MirroVirtualRuntime
├── RuntimeSession
│   ├── CloneIdentity (logical only; host identity explicitly separate)
│   ├── CapabilityMatrix / PolicyDecision
│   ├── ProcessLease / WebViewLease
│   └── RuntimeDiagnostics
├── GuestRuntimePlane
│   ├── DynamicCodeManager
│   │   ├── GuestLoaderGraph
│   │   ├── SplitCodeRegistry
│   │   ├── InMemoryLoaderObserver
│   │   └── NativeLoadRegistry
│   ├── TargetResourceManager
│   ├── ApplicationBootstrapper (survives, narrowed)
│   └── NativeRuntimeBridge
├── VirtualFrameworkPlane
│   ├── VirtualPackageManager (replace broad host fallback)
│   ├── VirtualPermissionManager
│   ├── VirtualAppOpsManager
│   ├── VirtualActivityManager
│   ├── VirtualActivityTaskManager
│   ├── VirtualServiceManager
│   ├── VirtualContentManager
│   ├── VirtualBroadcastManager
│   ├── VirtualJobManager
│   ├── VirtualAlarmManager
│   ├── VirtualNotificationManager
│   ├── VirtualPendingIntentManager
│   ├── VirtualStorageManager
│   └── VirtualAccount/Gms adapters
├── HostAdapterPlane
│   ├── DeclaredStubComponents
│   ├── BinderProxyRegistry (explicit, version-gated)
│   ├── ProcessPool / SlotController
│   ├── Scheduler/restore service
│   └── Browser/Auth router
└── CapabilityOutput
    ├── SUPPORTED
    ├── HOST_MEDIATED
    ├── DEGRADED
    └── UNSUPPORTED_WITH_REASON
```

### What survives

- `VirtualRuntimeIdentity` as a logical clone/session record, with clearer host/guest fields.
- `VirtualFileSystem` as the basis of a storage manager, after fixing shared preferences and boot-protected roots.
- `ApkInspector` as the static package/split/native inventory source, expanded to installed-module and secure-environment metadata.
- `MirroTargetClassLoader` as the root loader, refactored behind `DynamicCodeManager`.
- `ApplicationBootstrapper` as an application initialization adapter, not a claim of full framework binding.
- `TargetActivityHost` as the first UI adapter, with hidden-API use isolated and capability-gated.
- `TargetAuthSessionRegistry` and callback routing for browser-mediated flows.

### What should be replaced or narrowed now

- `VirtualPackageManager`'s silent host fallback should become a field-by-field virtual package registry.
- `VirtualContext.getContentResolver()` must stop being an unconditional host resolver; it should route through `VirtualContentManager`.
- `getOpPackageName()`/temporary host identity should become an explicit per-operation attribution policy, never a hidden attempt to satisfy guest identity checks.
- `ServiceProxyRegistry` should become a typed adapter registry with no implication that arbitrary Binder services are virtualized.
- `VirtualServiceManager` should separate local guest lifecycle from host stub/foreground scheduling.
- `SingleCloneProcessSlotStrategy` should evolve into a fixed process pool with durable leases and restore state.
- `TargetActivityHost` should not be the place where ActivityTaskManager semantics accumulate; move routing/task state to a virtual component plane.

## Direct answers to the 12 priority questions

### 1. Why does Facebook's `LoginActivity` remain unavailable?

The current evidence shows only that the class is not visible to the startup Mirro loader after `Application.onCreate()`. The likely generalized cause is a late loader or feature/native path that Mirro neither observes nor registers; split code, generated indirection, and packed/anti-tamper paths remain alternatives. A bounded retry cannot discover a class owned by an unseen loader.

### 2. What generalized architecture fixes dynamic/native DEX apps?

A `DynamicCodeManager` with a loader graph, late-loader registration, explicit delegation/ownership, installed-split tracking, in-memory-loader observation where legitimately possible, native load/ABI diagnostics, and per-process native isolation. It must classify packed/anti-tamper apps rather than bypass them.

### 3. Can Mirro legitimately support Google Credential Manager?

Only as a host-mediated capability. The system sees the real Mirro package/signature/UID in `CallingAppInfo` and AppOps. Mirro can use its own credential configuration or a documented browser flow; it cannot make Credential Manager believe the caller is the target app.

### 4. Can Mirro legitimately support Google Sign-In?

Some browser/OAuth flows may work with an explicitly supported host configuration. Native GMS sign-in that binds package name and SHA-1 to the target cannot be universally supported under a Mirro process. Support must be API and backend specific.

### 5. Can Mirro support GMS-dependent apps without virtualizing GMS itself?

For selected APIs, yes, through host-mediated adapters or browser flows. For APIs that validate the calling package, signature, UID, account, or Play installation, no general solution exists without OS-level identity or a per-API integration accepted by the backend.

### 6. Which Binder services are absolutely necessary first?

Do not start with a universal Binder hook. First virtualize the guest-facing package/component/permission/AppOps contracts, then Activity/Task routing, ContentProvider/Resolver routing, service lifecycle, broadcasts, jobs/alarms, notifications and pending intents. Use explicit public-API adapters and only version-gated Binder proxies where measurement proves they are necessary.

### 7. Is the current Activity-hosting approach scalable?

Not as a universal framework. It is a useful supported-subset UI adapter. A virtual task/component fabric must own routing and state; host Activity/window/token limitations and hidden APIs remain hard constraints.

### 8. Is the current process model scalable?

One clone per `:container` is not scalable for concurrent clones or restoration. A fixed declared pool of one clone per process slot is the practical next step. It remains bounded and cannot provide arbitrary UIDs.

### 9. Which abstractions should be replaced now?

Replace silent host fallbacks in `VirtualPackageManager`, the unconditional host `ContentResolver`, the undifferentiated `ServiceProxyRegistry`, and the assumption that retrying a single class loader discovers dynamic code. Narrow `ApplicationBootstrapper` and `TargetActivityHost` to explicit adapter responsibilities.

### 10. Which categories are fundamentally impossible without OS/root/profile/VM support?

Truthful target Linux UID/Binder identity, target signing identity, target Play Integrity, hardware-backed target app binding, target-only signature permissions, universal package-owned GMS/account/FCM identity, and apps intentionally requiring secure non-container execution. Work profiles and full OS/VM approaches solve different parts but violate normal Mirro product constraints.

### 11. Can Play-distributed Mirro approach Parallel Space compatibility?

It can approach useful compatibility for standard, browser-mediated, and selected component-driven apps if it follows container policy, rejects `REQUIRE_SECURE_ENV`, does not disguise calls, does not download executable code outside Play, and documents limitations. It cannot approach universal compatibility across identity-bound, attested, DRM, banking, anti-tamper and privileged apps.

### 12. What architecture likely explains higher compatibility in mature competitors?

Multiple process roles, a virtual package/component registry, Activity/Service/Provider/Broadcast routing, explicit system-service proxies, per-clone process isolation, native/runtime hooks, and extensive version-specific compatibility code. Their higher compatibility is purchased with maintenance, hidden hooks, OS/version risk, commercial licensing, and often non-Play or privileged deployment assumptions. Claims must be separated from reproducible source evidence.

## First implementation milestone

### Milestone: Guest runtime observability and dynamic-code contract

This milestone is intentionally diagnostic and architectural. It should not attempt GMS identity spoofing, hidden Binder cache replacement, anti-tamper bypass, or broad production hooks.

Deliverables for the future implementation phase:

1. A persistent `RuntimeSession` trace schema for clone, process slot, APK/split inventory, loader nodes, code sources, native loads, component resolution and failure classification.
2. A `DynamicCodeManager` interface and in-memory implementation that can register the root loader, known installed split loaders and any loader returned by supported target boundaries.
3. A loader graph resolver that handles parent-first, delegate-last and target-owned policies explicitly and reports the defining loader.
4. A test fixture suite—not Facebook-specific—with apps exercising:
   - `DexClassLoader` from a packaged test DEX;
   - `PathClassLoader` and nested child loaders;
   - `InMemoryDexClassLoader`;
   - installed split code and late feature state;
   - `System.loadLibrary`/JNI and ABI selection;
   - a component whose class is only available after late registration;
   - a deliberately unsupported packed/guarded case.
5. A capability result for each case: `STATIC`, `REGISTERED_DYNAMIC`, `IN_MEMORY_OBSERVED`, `NATIVE_DEPENDENCY`, `FEATURE_PENDING`, `PACKED_UNSUPPORTED`, or `IDENTITY_BLOCKED`.

Success means Mirro can explain the missing class and native failure with evidence, can load a supported late class through a registered loader, and does not claim guest UID/signing/GMS success when the platform still sees Mirro. It does not mean Facebook or ChatGPT becomes compatible during this milestone.

## Exact recommended next implementation prompt

> Implement only the first Mirro virtualization milestone: guest runtime observability and a generalized dynamic-code contract. Do not modify AndroidManifest.xml, build files, Play/GMS identity behavior, production Binder caches, hidden-API exemptions, anti-tamper behavior, or competitor-specific code. Preserve unrelated user changes.
>
> Add a `RuntimeSession` diagnostics model and a `DynamicCodeManager` abstraction behind the existing `DexRuntimeLoader`/`MirroTargetClassLoader`. Track the root loader, installed executable split paths, registered child loaders, parent/delegation policy, DEX/code-source metadata, native library paths, ABI, and component class-resolution attempts. Make loader ownership and failure categories explicit; a retry of the same loader must not be reported as dynamic discovery.
>
> Add fixture-oriented tests for packaged `DexClassLoader`, nested `PathClassLoader`, `InMemoryDexClassLoader` metadata/unsupported behavior, installed split code, `System.loadLibrary`/ABI diagnostics, and a deliberately unavailable/packed case. If production code cannot observe a loader through a supported boundary, record `DYNAMIC_LOADER_UNSEEN` and explain the limitation rather than using hidden global hooks. Keep the current Activity hosting and auth routing behavior unchanged. Run the focused unit tests and report exactly which evidence is available for the Facebook-style missing Activity case.

## Primary references

### Android/AOSP

- [Android app sandbox](https://source.android.com/docs/security/app-sandbox)
- [Android app security overview](https://source.android.com/docs/security/overview/app-security)
- [Application security best practices](https://source.android.com/docs/security/best-practices/app)
- [Context API](https://developer.android.com/reference/android/content/Context)
- [AttributionSource API](https://developer.android.com/reference/android/content/AttributionSource.html)
- [Process API](https://developer.android.com/reference/android/os/Process)
- [AppOpsManager](https://developer.android.com/reference/android/app/AppOpsManager.html)
- [Permissions overview](https://developer.android.com/guide/topics/permissions/overview)
- [Package visibility](https://developer.android.com/training/package-visibility/declaring)
- [Processes and threads](https://developer.android.com/guide/components/processes-and-threads)
- [Process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
- [Dynamic code security tips](https://developer.android.com/privacy-and-security/security-tips)
- [DexClassLoader](https://developer.android.com/reference/dalvik/system/DexClassLoader)
- [InMemoryDexClassLoader](https://developer.android.com/reference/dalvik/system/InMemoryDexClassLoader)
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)
- [On-demand feature delivery](https://developer.android.com/guide/playcore/feature-delivery/on-demand)
- [WebView ProcessGlobalConfig](https://developer.android.com/reference/androidx/webkit/ProcessGlobalConfig)
- [Android ABIs](https://developer.android.com/ndk/guides/abis)
- [JNI tips](https://developer.android.com/ndk/guides/jni-tips)
- [AOSP linker namespaces](https://source.android.com/docs/core/permissions/namespaces_libraries)
- [Credential Manager](https://developer.android.com/reference/android/credentials/CredentialManager)
- [CallingAppInfo](https://developer.android.com/reference/android/service/credentials/CallingAppInfo)
- [GetCredentialRequest.Builder](https://developer.android.com/reference/android/credentials/GetCredentialRequest.Builder)
- [Credential Manager service source](https://android.googlesource.com/platform/frameworks/base/+/ce552219140802457c58437470c2ee4658240bc8/services/credentials/java/com/android/server/credentials/CredentialManagerService.java)
- [CallingAppInfo source](https://android.googlesource.com/platform/frameworks/base/+/8d734f956d1e7a54f15cba068a3723b7a50fb6a8/core/java/android/service/credentials/CallingAppInfo.java)
- [Android services](https://developer.android.com/develop/background-work/services)
- [Foreground services](https://developer.android.com/develop/background-work/services/fgs)
- [WorkManager](https://developer.android.com/reference/androidx/work/WorkManager)
- [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler)
- [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
- [Broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
- [App-specific storage](https://developer.android.com/training/data-storage/app-specific)
- [SAF](https://developer.android.com/training/data-storage/shared/documents-files)
- [Secure file sharing/FileProvider](https://developer.android.com/training/secure-file-sharing)
- [Service manifest element](https://developer.android.com/guide/topics/manifest/service-element)
- [Work profiles](https://developer.android.com/work/managed-profiles)
- [AVF](https://source.android.com/docs/core/virtualization)
- [Microdroid](https://source.android.com/docs/core/virtualization/microdroid)
- [Hardware-backed key attestation](https://developer.android.com/privacy-and-security/security-key-attestation)
- [Play Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts)
- [Non-SDK interface restrictions](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)

### Google identity and services

- [Google Sign-In Android implementation](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Legacy Google Sign-In Android client requirements](https://developers.google.com/identity/sign-in/android/legacy-gsi-start)
- [Google client authentication](https://developers.google.com/android/guides/client-auth)
- [Google Play services utility](https://developers.google.com/android/reference/com/google/android/gms/common/GooglePlayServicesUtil)
- [FCM receive messages](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- [FCM architecture](https://firebase.google.com/docs/cloud-messaging/fcm-architecture)

### Policy

- [On-device Android container apps and `REQUIRE_SECURE_ENV`](https://support.google.com/googleplay/android-developer/answer/13609005?hl=en)
- [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en-NZ)
- [QUERY_ALL_PACKAGES](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)
- [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB)
- [Prominent disclosure guidance](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en)
- [Impersonation FAQ](https://support.google.com/googleplay/android-developer/answer/16341334?hl=en)
