# Mirro Final Architecture Decision Audit

**Date:** 2026-09-20
**Audited revision:** `2ff1653` (`feat(runtime): add virtual framework foundation`)
**Previous milestones:** `ea106fc` dynamic loader graph; `2ff1653` virtual framework foundation
**Scope:** architecture decision only. No production code, build configuration, dependency, or experimental hook changes are proposed here.

## Executive decision

Mirro should **not continue expanding the current user-space container as a general-purpose clone engine**. The current architecture is a useful compatibility adapter and diagnostic runtime, but it lacks the primitive that makes mature virtualization systems work across native code, Binder services, package identity, process boundaries, and framework lifecycle.

The general product should **PIVOT** to a hybrid product boundary:

1. Keep the current container for the supported subset of ordinary APKs and for diagnostics.
2. Use a real Android profile/secondary user when local, real package/UID/process semantics are required and the device permits it.
3. Treat a remote Android instance as the only broadly scalable way to offer a seamless “second phone” experience without root or OEM privileges, accepting its cost and privacy trade-offs.

Mirro should not attempt to manufacture a full Android guest by adding another sequence of managers to the existing process.

The personal ChatGPT use case should **CONTINUE**, but as a separate, much smaller product. The native app already launches; the remaining blocker is authentication identity. The simplest legitimate solution is two sessions in ChatGPT web’s supported account switcher, or a system profile/second installation if native-app separation is essential. Mirro’s general virtualization engine is not required.

## 1. Audit of the actual repository

### What exists after `2ff1653`

The runtime is an in-process host adapter. A Mirro host Activity starts a target APK’s `Application` and target `Activity` classes in a Mirro-owned process, with a target-first class loader and a guest-facing `Context` facade.

The effective path is:

```text
Mirro host Activity
  -> ContainerRuntime
  -> ApkInspector / installed target APK metadata
  -> DexRuntimeLoader / MirroTargetClassLoader
  -> DynamicCodeManager loader graph
  -> VirtualContext
  -> target Application + selected providers
  -> TargetActivityHost
  -> host Activity window and lifecycle primitives
```

The repository’s own code establishes these facts:

| Area | Mirro currently does | Android still owns |
|---|---|---|
| APK metadata | Inspects target APK/splits and builds a per-clone package record | Installed package database, install sessions, package visibility and platform package authority |
| DEX | Loads base/executable split paths through `MirroTargetClassLoader`; records observable loader nodes | ART, the actual application process, private/native-created loaders and hidden loader creation |
| Dynamic code | Registers supported loader objects and reports `DYNAMIC_LOADER_UNSEEN` when no supported observation boundary exists | In-memory/private/native loader creation and feature delivery state |
| Resources | Loads target resources with host fallback | `LoadedApk`, `ActivityThread`, resource/package binding and platform resource identity |
| Package identity | Exposes selected target values through `VirtualContext` and `VirtualPackageManager` | Linux UID, kernel credentials, Binder caller UID, package signing authority and system-server attribution |
| Permissions/AppOps | Maintains explicit clone-local virtual state and classifies host-mediated operations | Real permission grants, AppOps enforcement and system-service attribution |
| Storage | Redirects common `Context` paths and namespaced preferences | Native absolute paths, external/media storage policy, filesystem labels and host UID ownership |
| Activities/tasks | Records logical Activity/task operations and embeds target Activity UI | Host task, Activity token, window, lifecycle owner and process |
| Providers | Has a registry and URI mapper; declared providers can be registered into the local model | `ContentResolver`, provider process/authority registration and URI grant enforcement |
| Services | Starts/binds selected target Java services in-process and records contracts | System service manager, restart policy, foreground-service policy, jobs, process death and OS scheduling |
| Broadcasts | Has a clone-local registration/recording model | System broadcast delivery and manifest receiver scheduling |
| PendingIntents/notifications | Namespaces local descriptors and rejects wrong-clone routes | Platform-created PendingIntent identity, notification manager, channels and system callbacks |
| Native execution | Inventories ABIs/`.so` files and records reported load failures | Linker namespace, `dlopen`, `/proc`, JNI process identity, native static state and library search behavior |
| WebView | Sets a process-global data-directory suffix; limits one active clone per process slot | WebView provider process/global initialization and platform cookie/runtime behavior |
| System services | Selectively proxies/facades services | Binder calls normally originate from Mirro’s package/UID |

### Current process and loader architecture

The current implementation uses a host process slot, currently one active clone per `:container` slot where WebView isolation requires it. The target code is not installed as its own Android package in a separately scheduled target process. `TargetActivityHost` reflectively attaches target Activities to the host Activity’s instrumentation and window path.

The loader graph is a real improvement over the old single-loader assumption. It can represent startup paths, installed splits, explicitly observed child loaders, in-memory loaders when a real object is supplied, and native/unknown evidence. It cannot observe every loader creation boundary, especially loaders created privately by native code or code that never exposes the loader object to Mirro.

The native boundary is diagnostic, not virtualized. Discord’s missing `libkv_storage.so` is therefore evidence of a process/linker/native compatibility gap, not a missing Kotlin manager.

### Comparison with the previous reports

The earlier reports were directionally correct about the identity and system-service ceiling. Two conclusions have now been implemented or sharpened:

- The loader problem is now modeled as an observable graph instead of a single retryable path.
- Package, permission, AppOps, storage, component, provider, service, broadcast, PendingIntent and notification contracts now have explicit local models with provenance and failure categories.
- Target package metadata preserves flag-sensitive fields sufficiently for ChatGPT to reach its UI.
- The runtime now reports whether a result is guest-local, host-mediated, unsupported, or identity-dependent.

The changes did not change the underlying security boundary. They improve diagnosis and supported-subset behavior; they do not create a target UID, signing identity, Binder caller identity, system-server registration, linker namespace, or guest Android process.

The device evidence confirms this distinction:

- ChatGPT reaches UI, but GMS/native login remains blocked by caller identity.
- Facebook reaches `Application.onCreate()` but remains `DYNAMIC_LOADER_UNSEEN`.
- Discord fails loading `libkv_storage.so` with `UnsatisfiedLinkError`.
- BA-mobil reaches a static/root Activity, then fails at GMS/Firebase host identity and lifecycle bridge boundaries.

These are not four missing facade methods. They are four examples of authority still residing outside the Mirro process.

## 2. Competitor and alternative architecture comparison

Public repositories are evidence of mechanisms and maintenance burden, not proof that every commercial claim is true.

| Architecture | Fundamental mechanism | Capability Mirro currently lacks |
|---|---|---|
| VirtualApp | Host-based application virtualization with virtual PackageManager/ActivityManager, Binder/service interception, client/server roles, path/environment handling and native support | Broad interception of framework IPC and more complete process/native adaptation. Its public code is old while its repository describes a continuing commercial branch; this is evidence of ongoing maintenance, not a guarantee of compatibility. [VirtualApp source](https://github.com/asLody/VirtualApp), [VirtualCore startup/injection](https://github.com/asLody/VirtualApp/blob/master/VirtualApp/lib/src/main/java/com/lody/virtual/client/core/VirtualCore.java) |
| VirtualXposed | VirtualApp container plus Java AOP/Xposed-style hooks and JNI/native redirection | Process-level Java/native interception. Its own documentation says it cannot hook `system_server` and has resource-hook limitations; it supports an older Android range. [How VirtualXposed works](https://github.com/android-hacker/VirtualXposed/wiki/How-does-VirtualXposed-work), [limitations](https://github.com/android-hacker/VirtualXposed/wiki/Difference-between-Xposed-and-VirtualXposed) |
| DroidPlugin | Plugin APKs run without normal installation/repackaging through framework hooks and proxy components | A much wider hook/proxy surface around Android framework calls. Its public project describes the plugin-without-installation model, not a real target UID. [DroidPlugin](https://droidpluginteam.github.io/DroidPlugin/) |
| BlackBox/derivatives | Application-level virtual environment with virtual package/component services, proxying and varying native/hook support | A mature cross-version hook and service surface, often with native compatibility work. Forks differ materially; claims such as device identity customization must not be treated as truthful security identity. [BlackBox documentation example](https://github.com/ALEX5402/NewBlackbox/blob/main/Docs.md) |
| VMOS/Virtual Android-style products | A real or substantially emulated Android guest, usually with a guest OS image, guest system services and its own app installation model | Real guest PackageManager, ActivityManager, UID/process model, filesystem and native runtime. Public product behavior is not proof of Play Integrity, banking, DRM or GMS compatibility. |
| Island/Shelter/Work Profile | Android-managed secondary user/profile; APK is installed by the real PackageManager and runs under a profile UID | Real package installation, process, UID, storage, lifecycle and system-service semantics. AOSP documents profile UIDs as `100000 * userid + appid`. [AOSP managed profiles](https://source.android.google.cn/docs/devices/admin/managed-profiles?hl=en), [Google Work Profile](https://support.google.com/work/android/answer/6191949?hl=en) |
| OEM Dual Apps/Secure Folder | OEM-integrated profile or cloning service with privileged/system integration | OEM privilege, platform-specific clone UI, lifecycle and storage integration. This is closer to the actual Android model than an ordinary app container, but unavailable as a portable third-party primitive. |
| AVF/Microdroid | Hardware-backed/isolated pVM facility for native payloads and a stripped-down Android image | Strong native isolation, but not a full Android guest for arbitrary APKs. Microdroid has no `android.*` Java APIs; AVF APIs are `@SystemApi` and require restricted management permission, generally unavailable to third-party apps. [Microdroid](https://source.android.google.cn/docs/core/virtualization/microdroid?hl=en), [AVF API restrictions](https://android.googlesource.com/platform/packages/modules/Virtualization/%2B/HEAD/libs/framework-virtualization/README.md) |

The common pattern is important: mature containers do not merely add a `VirtualPackageManager`. They intercept or replace a large part of the process/framework boundary, and they spend substantial effort adapting Android-version-specific hidden APIs, Binder transactions, native code and process behavior. Real profiles and VMs avoid much of that emulation by letting Android or a guest Android own the authority.

## 3. External leverage options

### A. Native runtime/hook layer

**Potential gain:** high for native library search paths, loader visibility, JNI observation, filesystem redirection and process-local compatibility. It could improve the Discord class of failure and some Facebook loader failures.

**What it cannot legitimately solve:** a native hook cannot make a normal process have another Linux UID, alter a server-verified signing certificate, create a truthful Play Integrity verdict, or make arbitrary Binder calls appear to originate from the target package. It also cannot guarantee visibility into code intentionally kept inside a protected/native boundary.

**Costs:** Android/OEM/ART/linker fragility, ABI maintenance, crash risk, difficult debugging, security review, potential Play policy implications and a continuing hook matrix. Root is not strictly required for hooks inside Mirro’s own process, but system-wide hooks and process creation require privileges unavailable to an ordinary app. A native layer is a useful compatibility supplement, not the missing complete architecture.

**Decision:** do not add as the next general-purpose milestone. Consider only as a bounded, opt-in diagnostic/compatibility module after the product boundary is changed and only without anti-tamper or attestation bypass.

### B. Shizuku/privileged companion

Shizuku supplies a user-authorized Binder service running with ADB or root-derived privileges and lets approved client apps call selected privileged APIs through that service. Its public documentation describes the service Binder handoff to app processes. [Shizuku introduction](https://shizuku.rikka.app/introduction/)

It could improve:

- package installation/uninstallation and per-user package operations where the exposed API permits;
- querying users, packages and processes;
- selected AppOps/permission administration;
- diagnostics and lifecycle control;
- setup of a secondary user/profile on devices that permit it.

It does **not** make target code execute under the target package’s Binder caller identity, replace a signing certificate, issue Play Integrity, grant hardware attestation, or make GMS believe the call originated from a different installed package. A companion can administer a real profile; it cannot turn the current in-process guest into one.

**Decision:** useful for a profile-based pivot and developer tooling, insufficient as the external primitive for the existing container.

### C. Root mode

Root could materially change the engineering surface: mount/path namespaces, process supervision, filesystem ownership, native injection, system-service access, package installation into users, and stronger control of process startup. It could make a VirtualApp-like architecture more feasible and reduce some host restrictions.

Root still does not guarantee server-side trust. Play Integrity can report package/certificate/device/account signals; its documentation explicitly describes Play-recognized package/certificate matching and device integrity. [Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts)

Root also increases detection, maintenance and safety risk. It would turn Mirro into a root-only product with a device-modification support matrix, not a normal-user Play product. It replaces some host restrictions with kernel/system maintenance and does not solve every GMS, DRM, hardware attestation or anti-tamper boundary.

**Decision:** feasibility reference only, not the product direction.

### D. Local Android VM

A sufficiently complete Android VM would solve the right problems: real guest PackageManager, UID, ActivityManager, services, providers, jobs, native linker, app data and process model. It would remove most of the current virtual framework code from the execution path.

However, AVF/Microdroid is not that VM. AOSP describes Microdroid as a small pVM OS for native payloads and explicitly lists the lack of `android.*` Java APIs. Its APIs are restricted system APIs and require `MANAGE_VIRTUAL_MACHINE`; they are not a general third-party full-Android hosting facility. [Microdroid limitations](https://source.android.google.cn/docs/core/virtualization/microdroid?hl=en), [AVF API](https://android.googlesource.com/platform/packages/modules/Virtualization/%2B/HEAD/libs/framework-virtualization/README.md)

Embedding a full Android guest as an ordinary app would require a supported hypervisor/emulator, guest images, graphics/input/network/storage integration, memory and battery budgets, guest updates, and a security boundary. GMS/Play Store certification and Play Integrity would still be separate questions; a guest does not automatically become certified.

**Decision:** technically the cleanest compatibility architecture, but not realistically embeddable as a general third-party Android app on current devices without OEM/platform support. Pursue only through a platform partnership, an OEM/system build, or a remote guest.

### E. Remote Android/cloud VM

A remote Android instance is the first option that cleanly supplies a real installed package, UID, process, PackageManager, native linker, system services, storage and background behavior. Mirro becomes a secure session client rather than an emulator.

It does not automatically solve GMS or trust-sensitive apps. The remote image needs legitimate Google certification/licensing where applicable, and Play Integrity, DRM, device attestation, account risk, and server-side policy remain independent. It adds latency, video/input streaming, GPU cost, storage cost, regional availability, credential handling, privacy obligations and operational security.

**Decision:** viable for a premium or owner-controlled product, not a low-cost solo Play app. It is the strongest general compatibility pivot if the business can support infrastructure.

### F. Hybrid runtime

Hybrid is the most honest product architecture:

```text
Mirro client
  -> local container for supported, low-authority apps
  -> real profile/secondary-user route for local full-Android semantics
  -> remote Android route for devices where local guest execution is unavailable
```

The UX must show one “Open clone” action, then explain the execution mode only when setup, privacy, latency or profile switching matters. The compatibility report should remain capability-based instead of promising every APK.

**Decision:** recommended general product direction. It preserves the existing useful subset while stopping the attempt to emulate all of Android inside one host process.

### G. Work Profile/secondary Android user

This is not a Parallel Space-style in-process container, but it is the most practical local mechanism for real Android semantics. AOSP documents separate profile data and profile-derived UIDs; the real package manager installs and schedules the app in that profile. [AOSP managed profiles](https://source.android.google.cn/docs/devices/admin/managed-profiles?hl=en)

It solves real package installation, UID/process separation, native library loading, services/providers/jobs, and storage. It does not provide a seamless single-container window, does not make two packages with incompatible account/signing assumptions magically cooperate, varies by OEM and policy, and exposes profile UX to the user. A device-owner/profile-owner flow may require setup that is inappropriate as the default consumer experience.

**Decision:** recommended local fallback/optional mode, especially for the personal use case and complex apps.

## 4. The missing architectural primitive

Yes. Mirro is missing a **real guest authority boundary**.

More precisely, it needs one of:

- a real Android profile/secondary-user installation boundary;
- a full Android guest VM with its own system services;
- or a privileged/root/OEM process-and-Binder virtualization layer.

A native hook layer alone is not enough. The primitive must own, together, the package manager, process/UID identity, Binder/service routing, filesystem/process lifecycle, native loader and component scheduler. Mature app containers approximate this with extensive framework/native hooks; profiles and VMs own it directly.

If a real guest authority existed, these current abstractions would no longer be the execution authority and would become adapters or diagnostics:

- `VirtualPackageManager` -> guest package-query adapter;
- `VirtualPermissionManager`/`VirtualAppOpsManager` -> guest state observer;
- `VirtualStorageManager` -> guest filesystem/session mapper;
- `VirtualActivityManager`/`VirtualActivityTaskManager` -> guest ActivityManager adapter;
- `VirtualContentManager` -> guest ContentResolver adapter;
- `VirtualServiceManager`/broadcast/PendingIntent/notification models -> guest framework bridges;
- `TargetActivityHost` -> surface/session bridge, not reflective lifecycle owner;
- much of `VirtualContext` -> compatibility wrapper or removed;
- loader/native models -> guest diagnostics, with actual guest ART/linker doing the loading.

`DynamicCodeManager` would remain useful as observability and remote-session diagnostics, but it would no longer need to guess how to recreate ART’s loader universe in the host process.

This is why adding another manager to the current process is a dead end for the general goal. The missing thing is authority, not another API facade.

## 5. Value audit of existing work

| Component | Classification | Decision |
|---|---|---|
| `VirtualContext` | USEFUL_BUT_DIAGNOSTIC / TEMPORARY_ARCHITECTURE | Keep as the local-container compatibility boundary; do not expand it into a replacement for all of `Context`. |
| `MirroTargetClassLoader` | ESSENTIAL_RUNTIME for local subset | Keep for ordinary/static target code. It is not a complete ART replacement. |
| `DynamicCodeManager` | ESSENTIAL_RUNTIME for observability; USEFUL_BUT_DIAGNOSTIC for compatibility | Keep the loader graph and evidence model; do not promise unseen native/private loaders. |
| `VirtualPackageRegistry` | ESSENTIAL_RUNTIME for local subset; TEMPORARY_ARCHITECTURE | Keep for target metadata/provenance; replace as authority when a profile/VM exists. |
| `VirtualPermissionManager` | USEFUL_BUT_DIAGNOSTIC | Useful for explicit capability reporting and local-only permissions; cannot grant platform permissions. |
| `VirtualAppOpsManager` | USEFUL_BUT_DIAGNOSTIC | Correctly exposes identity requirements; must not become a spoofing layer. |
| `VirtualStorageManager` | ESSENTIAL_RUNTIME for local subset | Keep for Context paths; native/direct-path limitations remain explicit. |
| `VirtualActivityManager` | TEMPORARY_ARCHITECTURE | Keep as a logical record/router while local mode exists; do not grow it into a second ActivityTaskManager. |
| `VirtualActivityTaskManager` | TEMPORARY_ARCHITECTURE | Useful for deterministic tests and local nested flows; guest/profile/VM should own real tasks. |
| `VirtualContentManager` | TEMPORARY_ARCHITECTURE / USEFUL_BUT_DIAGNOSTIC | Keep for local providers and tests; it is not a general replacement for the platform resolver. |
| `VirtualServiceManager` | USEFUL_BUT_DIAGNOSTIC | Local in-process service support is valuable, but it is not OS service scheduling. |
| `VirtualBroadcastManager` | USEFUL_BUT_DIAGNOSTIC | Keep local routing; do not claim manifest/system broadcast equivalence. |
| `TargetActivityHost` | LIKELY_TO_BE_REPLACED | Retain as the current local surface adapter; a real guest route should make it unnecessary for full Android semantics. |

The existing work is not wasted. It delivered a bounded runtime, honest failure taxonomy, reproducible tests, and evidence that prevents unsafe claims. It is not evidence that continuing abstraction expansion will reach universal cloning.

## 6. General product viability

### Current architecture

For a normal user installing Mirro without root, OS modification, device-owner setup or security bypasses, the current architecture can realistically support a subset:

- ordinary static APKs with conventional Java/UI/storage behavior;
- some WebView/browser flows with explicit process-slot limits;
- selected local services and components;
- apps that do not require target package identity from GMS, Credential Manager, AppOps, Play Integrity, DRM or a native process boundary.

It cannot credibly promise, across popular apps without per-app patches:

- native-heavy apps with strict linker/JNI assumptions;
- late-loaded/packed/dynamic-feature apps whose loader boundary is unseen;
- apps requiring real package/signing/UID/Binder identity;
- GMS, Credential Manager, Play Integrity, hardware attestation, DRM or trust-sensitive flows;
- full background service, job, alarm, notification and provider semantics.

That is a **NO-GO** for the original general-purpose product goal on the current architecture family.

### Policy and distribution constraint

Google Play’s on-device Android container policy requires container apps to honor `REQUIRE_SECURE_ENV`; it does not technically prevent loading but imposes a policy check for Play-distributed containers. [Play policy announcement](https://support.google.com/googleplay/android-developer/answer/13678785?hl=en), [container guidance](https://support.google.com/googleplay/android-developer/answer/13609005?hl=en-GB)

Play Integrity also evaluates package/certificate, licensing, device and account signals. A virtual package string cannot make a host-loaded target become a Play-recognized target binary or a separate licensed installation. [Play Integrity overview](https://developer.android.com/google/play/integrity), [verdict details](https://developer.android.com/google/play/integrity/verdicts)

## 7. Personal-only fallback: Mirro ChatGPT Personal

The personal goal is narrower: two independent ChatGPT accounts on one phone. General Android virtualization is unnecessary.

### Recommended design

**Primary option:** use ChatGPT web’s official account switcher. OpenAI documents two independently signed-in accounts with separate chats, memory, billing and workspaces; it is currently available on ChatGPT web, not the native mobile app. [OpenAI account switching](https://help.openai.com/en/articles/20001068-use-multiple-accounts-with-account-switching)

**If native app UX is mandatory:** use a real Android work profile/secondary user or OEM Secure Folder/Dual Messenger-style installation. This gives the native app a real package/process/profile context and avoids trying to forge GMS caller identity.

**If Mirro remains involved:** make it a launcher/session helper that opens the official web experience or guides the user to the profile route. Do not import cookies, copy tokens, forge signatures, bypass Play Integrity, intercept authentication, or emulate GMS identity.

OpenAI’s Android login documentation also states that the native app relies on a supported browser, with Chrome recommended, for secure login. [OpenAI Android login guidance](https://help.openai.com/en/articles/8194942)

### Personal GO criteria

GO if two accounts can remain independently usable through the official web switcher or a real Android profile, with no credential extraction and no target-app patching.

NO-GO for a native Mirro clone if the only route requires spoofing GMS, Play Integrity, signing, browser cookies, or caller identity.

## 8. Exactly three engineering paths

### PATH A — Continue General Mirro

**Status: not recommended; fails the general GO gate.**

This path would continue expanding the current local facade: more framework managers, more hooks, more component emulation and more app probes.

Expected gain: incremental support for simple apps and better diagnostics.

Expected cost: unbounded Android-version/OEM/API surface, native and Binder fragility, per-app exceptions, and no credible solution for real identity/attestation.

Current code survives, but the product goal does not. This path is acceptable only if Mirro is explicitly redefined as a supported-subset compatibility container and not as a general clone engine.

### PATH B — Architectural Pivot

**Status: recommended for General Mirro.**

Adopt a hybrid authority model:

- local container for the current supported subset;
- work profile/secondary user as the local full-Android route;
- remote Android instance as the broad-compatibility route where a seamless experience justifies infrastructure.

Reuse: diagnostics, capability taxonomy, target inspection, clone metadata, launcher UX, auth callback safety, local container support, and test fixtures.

Replace/de-emphasize: reflective Activity lifecycle ownership, virtual package/permission/AppOps authority, virtual task/provider/service scheduling, and any claim that the host process represents the target identity.

Expected gain: real UID/package/process/native/framework semantics in the profile or guest route; no need to emulate every system service in Mirro.

Risk: profile UX or remote operational cost; GMS/Play Integrity still require legitimate guest/device/account conditions and are never guaranteed by the architecture alone.

### PATH C — Personal ChatGPT Only

**Status: recommended for the owner’s immediate goal.**

Stop general app cloning work. Build only a small session launcher or companion around official ChatGPT web account switching, with optional instructions/integration for a real Android profile when native UI is required.

Reuse: current ChatGPT launch evidence, browser callback understanding and local privacy posture.

Discard: package-general loader graph expansion, framework manager expansion, commercial-app probes and any identity-spoofing work.

Expected gain: two independent usable accounts with low engineering and operational risk.

## 9. Objective GO/NO-GO criteria

### General Mirro

**GO** only if a selected architecture demonstrates, with the same generic runtime and no app-specific patches:

- one ordinary Java app;
- one native-heavy app;
- one dynamic-code/split app;
- one provider/service/background app;
- one GMS-dependent app where the flow is supported by the legitimate profile/guest environment;
- repeatable behavior across at least two Android/OEM environments;
- no security bypass, forged identity, hidden per-app exception, or continuous reverse engineering.

**NO-GO** if success requires any combination of continuous per-app patching, unbounded hidden API hooks, target UID/signature spoofing, Play Integrity bypass, or a solo-maintainership burden that grows with every Android release.

The current user-space container fails this gate based on the ChatGPT, Facebook, Discord and BA-mobil evidence.

### Personal ChatGPT

**GO** if two independently usable official sessions can be maintained through web account switching or a real Android profile, without extracting credentials or changing the target app’s security behavior.

The personal goal meets this gate through supported web/profile mechanisms.

## 10. Decision table

| Architecture | What it solves | What it does not solve | Reuse of current Mirro | Complexity | Operating cost | Privacy | Solo-developer feasibility |
|---|---|---|---|---|---|---|---|
| Current in-process container | Simple APKs, target UI subset, diagnostics, clone-local facade behavior | Real UID/Binder/signing, native boundary, full framework lifecycle, GMS/attestation | High | High and growing | Low runtime cost | Local | Feasible only as a bounded subset |
| Native hooks/rooted container | More native/path/framework interception | Server-side trust, Play Integrity, hardware identity; root/device support | Medium | Very high | Low cloud cost, high maintenance | Local but high privilege | Poor for a normal-user product |
| Shizuku companion + container | Administration, diagnostics, profile setup, selected privileged APIs | Target caller identity, signing, attestation, full guest runtime | Medium | Medium | Low | Local, user-authorized | Feasible as tooling, not as the core fix |
| Work Profile/secondary user | Real package/UID/process/storage/services/native execution | Seamless in-process UX, OEM consistency, every GMS/account policy | Medium as launcher/diagnostics | Medium | Low | Strong OS-managed isolation | Best local option |
| Full local Android VM | Real guest framework and app execution | Device support, GMS certification, resource/UX burden | Low to medium | Extremely high | Device resources | Potentially strong | Not feasible as an ordinary solo app without platform support |
| Remote Android guest | Real guest package/process/framework/native behavior | Latency, cost, privacy, GMS/Integrity/DRM guarantees | Medium as client/telemetry | High | High recurring | Requires strong provider trust | Feasible only with narrow scope/funding |
| Hybrid local/profile/remote | Product coverage matched to app capability | UX complexity and dual operational models | High | High but bounded | Variable | Can remain local or explicit remote | Recommended pivot |
| ChatGPT web/profile companion | Two independent official ChatGPT sessions | Native app-only requirement without a real profile | Low, mostly UX knowledge | Low | Minimal | Local browser/profile controls | Strongly feasible |

## Final decision

**GENERAL MIRRO: PIVOT.** Keep the current runtime as a bounded local compatibility mode and diagnostics layer. Stop treating additional virtual managers as the route to universal cloning. Make real profiles and, if justified, remote Android the authority-bearing execution paths.

**PERSONAL CHATGPT: CONTINUE.** Implement the smallest legitimate session-separation experience around ChatGPT web account switching, with a real Android profile as the native-app fallback.

This conclusion is based on the current code, the observed failures, Android’s UID/profile/virtualization boundaries, public VirtualApp/VirtualXposed mechanisms, and official Play/AVF/Work Profile documentation. It is not influenced by the amount of work already invested.
