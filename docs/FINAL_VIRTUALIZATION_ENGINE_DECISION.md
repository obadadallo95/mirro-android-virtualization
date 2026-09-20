# Final Virtualization Engine Decision

**Date:** 2026-09-20
**Audited repository:** `main` at `2ff1653` (`feat(runtime): add virtual framework foundation`)
**Evidence basis:** current Mirro source and tests, device proof on Samsung SM-S928B / Android 16, prior Mirro audits, public source repositories, Android/AOSP documentation and published research.
**Scope:** final architecture decision. No production code, dependencies, build changes or experimental hooks are introduced.

## Executive finding

Mirro cannot become a useful Parallel Space-style general cloning engine by adding a small finite set of independent managers to the current in-process runtime.

The conceptual core can be summarized in roughly six primitives, but those primitives are not separable feature modules. They form one compatibility boundary around Android’s process, Binder, filesystem, native-loader, package and component authorities. Implementing the boundary on stock Android requires hidden-API adaptation, transaction and parcel maintenance, proxy/stub components, native hooks, process/environment translation, and continuous Android/OEM regression work.

That is the architecture mature app-virtualization engines use. Public evidence shows the result is not a compact trick: VirtualApp’s public repository says its open code stopped in 2017 while its maintained business branch continues; its public changelog lists hundreds of Android-version, Binder, service, filesystem, seccomp, native and OEM fixes. [VirtualApp English README](https://github.com/asLody/VirtualApp/blob/master/README_eng.md), [current public changelog](https://github.com/asLody/VirtualApp)

The real-profile proof is materially different. Samsung’s existing `profile.CLONE` ran ChatGPT and Discord with real profile UIDs, data roots, Android tasks and native loading. That path lets Android own the authority instead of Mirro emulating it.

Therefore:

- A deep local virtual engine is **not realistic for a solo developer** under the original constraints.
- A real profile is a **useful fallback and a strong technical compatibility route**, but not a portable, seamless consumer primitive controlled by an ordinary app.
- The original general-product direction should **PIVOT** to profile-aware orchestration while retaining the current container as a bounded compatibility mode.
- The personal ChatGPT objective should **CONTINUE** separately through supported web/profile session separation.

## 1. What mature app virtualization actually contains

### VirtualApp / Parallel Space-style architecture

Public VirtualApp material describes a host-based virtual Android environment, not merely a package-name facade. Its English architecture description identifies two major native responsibilities: I/O redirection and modification of requests between the virtual app and Android framework. It also describes interception of framework requests and restoration of parameters in responses. [VirtualApp architecture description](https://github.com/asLody/VirtualApp/blob/master/README_eng.md)

The VirtualXposed technical documentation is more explicit about the shape of the system:

- a virtual server process with AMS/PMS hooks;
- virtual client processes with hooks for Android system services;
- a UI process and other/native processes;
- JNI/native I/O redirection;
- IPC structures between virtual client and virtual server;
- virtual multi-user/environment state.

[VirtualXposed architecture notes](https://github.com/android-hacker/VirtualXposed/wiki/How-does-VirtualXposed-work)

The minimum serious engine is therefore:

```text
Core virtualization engine
├── 1. Virtual process/runtime model
│   ├── client/server roles
│   ├── process slots and death/restart
│   ├── ActivityThread/LoadedApk/application binding
│   └── virtual PID/UID/user/app-id translation
├── 2. Framework IPC and system-service interception
│   ├── Binder proxy/service-manager boundary
│   ├── AMS/ATMS/PMS and attribution translation
│   ├── permissions/AppOps/accounts/jobs/notifications
│   └── API-level parcel and transaction compatibility
├── 3. Virtual package/component authority
│   ├── package/split/feature registry
│   ├── Activity/service/provider/receiver resolution
│   ├── proxy/stub components and lifecycle callbacks
│   └── task, PendingIntent and URI-grant routing
├── 4. Native process adaptation
│   ├── filesystem/path/syscall redirection
│   ├── /proc and environment translation
│   ├── JNI/process identity adaptation
│   └── native process/exec and ABI handling
├── 5. Code/resource/native-loader adaptation
│   ├── child/in-memory/feature loader observation
│   ├── native library search/linker behavior
│   ├── resources and split delivery
│   └── ART/runtime-version adaptation
└── 6. Isolation and policy plane
    ├── virtual storage and permissions
    ├── clone lifecycle and cleanup
    ├── security policy and secure-environment handling
    └── diagnostics/compatibility decisions
```

The first five are foundational. The sixth is required for a product users can trust, even though it is not the mechanism that makes an APK launch.

### What is core, what is optional

| Classification | Contents |
|---|---|
| **CORE** | Process/runtime model; package/component authority; Binder/system-service interception; native I/O/environment adaptation; proxy/stub lifecycle; code/resource/native-loader integration |
| **REQUIRED FOR MODERN APPS** | Split APK/feature handling, WebView/process isolation, attribution/AppOps, background limits, notifications/PendingIntent, ABI and linker behavior, API 29–36 hidden/changed framework surfaces |
| **OPTIONAL COMPATIBILITY** | Camera/location shims, device-specific workarounds, particular GMS adapters, anti-tamper compatibility, special vendor services, legacy API support |
| **APP-SPECIFIC** | Packed-code observation, unusual native loaders, custom account/login flows, proprietary IPC, undocumented component assumptions. These cannot be made generic merely by adding a manager. |
| **SECURITY-BOUND / IMPOSSIBLE TO PROMISE** | Truthful target signing identity, arbitrary kernel UID/Binder caller identity, hardware/device attestation, server-verified Play Integrity, DRM trust, and apps that intentionally reject a container. |

### Research corroboration

Boxify, a published stock-Android app-virtualization research system, describes proxying both syscall/I/O channels and Binder channels, including Binder redirection through ServiceManager hooking. That is research evidence of the required boundary, not a drop-in implementation for Mirro. [USENIX Boxify paper](https://www.usenix.org/sites/default/files/sec15_full_proceedings.pdf)

Security research on Android app virtualization likewise describes the common combination of API hooking, Binder proxies and filesystem redirection. [Parallel Space Traveling](https://www.cs.ucr.edu/~heng/pubs/sacmat2020.pdf), [Android virtualization security analysis](https://www.researchgate.net/publication/354506682_Evaluation_and_analysis_of_anti-repackaging_techniques_robustness_in_an_Android_virtual_environment)

## 2. Current Mirro versus the foundational primitives

The classification is strict: a guest-facing facade receives credit only for the calls it actually controls. If Android system_server, the host UID or the host process remains authoritative, the capability is partial or diagnostic.

| Primitive | Mature app-virtualization engine | Current Mirro after `2ff1653` | Gap | Would implementing it replace existing code? | Expected compatibility gain |
|---|---|---|---|---|---|
| Virtual process/runtime | Multiple client/server/native roles, process supervision and guest binding | One host `:container` process slot with one active clone constraint; reflective target Activity attach | **ABSENT / PARTIAL** | Yes. `TargetActivityHost`, parts of `VirtualContext` and lifecycle models would become adapters | High: process death, per-app native state, WebView and background behavior |
| Real guest UID/user mapping | Internal virtual users plus translation at framework/native boundaries | Clone IDs and descriptive physical host UID; no kernel/profile identity | **DIAGNOSTIC_ONLY** | Yes. `VirtualRuntimeIdentity` would become metadata, not authority | High for GMS/AppOps/package checks, but cannot create a truthful kernel UID in-process |
| Virtual PackageManager | Central virtual package registry integrated with all framework hooks | `VirtualPackageRegistry` and `VirtualPackageManager` facade for selected calls | **PARTIAL** | Partly. Registry survives as metadata; facade authority would be replaced | Medium for ordinary component queries; low for system-enforced identity |
| Activity/Task manager | Intercepted AMS/ATMS calls, proxy Activities, tokens, task and lifecycle translation | Logical Activity/task records plus host window/lifecycle bridge | **PARTIAL / DIAGNOSTIC_ONLY** | Yes. `VirtualActivityManager`, `VirtualActivityTaskManager` and much of `TargetActivityHost` would narrow or disappear | High for nested Activities, results, tasks, process death and callbacks |
| Binder/service interception | ServiceManager/Binder proxy or transaction interception across system services | Normal Binder calls still originate from Mirro host package/UID; selected Java services are local | **ABSENT** | Yes. Most virtual service contracts become adapters or test models | Very high for GMS, permissions, attribution, jobs, notifications and providers; still cannot forge server trust legitimately |
| Component proxy/stub runtime | Declared proxy components and routing for Activity/service/provider/receiver callbacks | Local registries and selected in-process dispatch; no system-registered target component fabric | **PARTIAL** | Yes. `VirtualContentManager`, broadcast/service/PendingIntent models would be replaced by guest/system routing | High for component lifecycle and background work |
| Native filesystem/path redirection | Native hooks/syscall/seccomp/path and `/proc` adaptation | Java `Context` path redirection and native inventory; no linker/syscall/proc hooks | **PARTIAL / DIAGNOSTIC_ONLY** | Yes for direct-path compatibility; `VirtualStorageManager` remains policy metadata | High for Discord-like libraries and apps using absolute installed paths |
| Native loader/JNI adaptation | Native hooks, library search namespaces, exec/process handling, loader/runtime integration | APK native inventory and reported load failures; no arbitrary `dlopen`/JNI boundary | **DIAGNOSTIC_ONLY** | Mostly; `NativeRuntimeState` remains diagnostics | High for native-heavy apps, but fragile and ABI/OEM-specific |
| Dynamic DEX/feature loader | Hook/observe loader creation, feature delivery and child process/runtime paths | Observable loader graph only at supported boundaries; explicit `DYNAMIC_LOADER_UNSEEN` | **PARTIAL / DIAGNOSTIC_ONLY** | `DynamicCodeManager` remains useful, but cannot promise generic discovery | Medium to high for dynamic apps; not enough without native/framework boundary |
| Resources/splits | Virtual `LoadedApk`/resource/package integration | Target resources with host fallback; installed split inspection | **PARTIAL** | Some loader/resource code replaced by real guest PackageManager | Medium |
| Permissions/AppOps/attribution | Intercepted calls translated to virtual user/app identity | Explicit virtual state and classification; host services still enforce host identity | **DIAGNOSTIC_ONLY** | Yes as authority; state model remains reporting | High for ordinary permission flows; blocked at identity-sensitive services |
| Providers/URI grants | Virtual provider processes/authorities and resolver/grant routing | Registry/URI mapper; host `ContentResolver` remains authoritative | **PARTIAL / DIAGNOSTIC_ONLY** | Yes. Local provider facade becomes adapter | High for provider-heavy apps |
| Background jobs/alarms/notifications | System-integrated scheduler and callback ownership | Contract records/local services; no OS scheduling equivalence | **ABSENT / DIAGNOSTIC_ONLY** | Yes | High for messaging/social apps, but Android policy remains authoritative |
| Security/policy boundary | Container’s support/deny policy plus platform-aware restrictions | Explicit unsupported/host-mediated taxonomy; no way to create missing authority | **USABLE as diagnostics; ABSENT as authority** | Policy survives | Prevents false claims; does not increase compatibility by itself |

Mirro therefore has a good metadata/diagnostics layer and a useful ordinary-APK subset. It does not have the process/framework/native authority layer that explains mature-engine compatibility.

## 3. The true big levers

### A. Virtual process/client-server runtime

**Observed failures helped:**

- ChatGPT: helps isolate WebView/browser/app process state and lifecycle, but does not by itself solve GMS caller identity.
- Discord: helps separate native process state and process paths, but native library visibility still requires native adaptation.
- Facebook: helps create distinct runtime slots and child processes, but does not discover a loader that remains private/native.
- BA-mobil: helps lifecycle/process semantics, but not GMS identity.
- Ordinary apps: meaningful gain for process death, services, WebView and concurrent clones.

**Assessment:** foundational, but not sufficient. A process model immediately requires `ActivityThread`/`LoadedApk` binding, process supervision, component startup, process death/restart and per-process data/identity translation. This is large work, not a standalone manager.

### B. Binder/system-service proxy layer

**Observed failures helped:**

- ChatGPT: directly targets the current GMS/attribution failure class, but cannot legitimately manufacture a target package certificate or server-verified identity.
- BA-mobil: targets GMS/Firebase caller and job/service attribution failures.
- Ordinary apps: broad gain across PackageManager, ActivityManager, AppOps, notifications, jobs, accounts, providers and permissions.
- Facebook/Discord: indirect gain; it does not solve missing classes or native linker paths.

**Assessment:** the largest single compatibility lever, and the highest-maintenance one. It requires service-by-service interception, parcel/transaction compatibility, attribution-chain handling, hidden API access, API release updates and OEM fixes. Mature VirtualApp evidence shows the scope keeps growing: later updates mention Binder interception, AttributionSource, AppSearch, DomainVerification, StorageStats, notification/provider services and many more surfaces. [VirtualApp changelog](https://github.com/asLody/VirtualApp)

### C. Native filesystem/linker/JNI adaptation

**Observed failures helped:**

- Discord: strongest direct target; the real profile already removed the observed missing-native-library failure by allowing Android’s normal native execution path.
- Facebook: may help if the missing Activity is unpacked/loaded by native code, but cannot be assumed from the current evidence.
- ChatGPT/BA-mobil: limited benefit unless a native SDK is the failing layer.
- Ordinary/native-heavy apps: substantial benefit for absolute paths, `/proc`, native libraries, JNI and process environment.

**Assessment:** foundational for native-heavy apps, but strongly version/ABI/OEM-sensitive. VirtualApp’s public changelog explicitly lists `seccomp-bpf`, inline hooks, `execve`, `/proc`, `setxattr`, `connect`, syscall handling, page-size compatibility and native crash fixes. That is evidence of ongoing engineering, not a finite one-time task. [VirtualApp changelog](https://github.com/asLody/VirtualApp)

### D. Full loader interception

**Observed failures helped:**

- Facebook: direct target for `DYNAMIC_LOADER_UNSEEN` when the loader is observable.
- Discord: limited; its current failure is native loading, not merely DEX discovery.
- ChatGPT/BA-mobil: usually secondary.
- Ordinary apps: helps dynamic features/secondary DEX, but not identity or services.

**Assessment:** necessary for modern dynamic apps, insufficient alone. Native-created or deliberately private loaders remain a hard boundary without deeper instrumentation.

### E. Real profile/user backend

**Observed failures helped:**

- ChatGPT: real profile UID/process/GMS context; current Mirro caller mismatch disappears from the observed path.
- Discord: normal package/native loader path; `libkv_storage.so` failure disappears.
- Facebook/BA-mobil: likely removes host-identity and process/native classes of failure, but was not tested here and does not guarantee app-specific server policy or dynamic feature availability.
- Ordinary and GMS apps: strongest general compatibility gain because Android owns all core authorities.

**Assessment:** the strongest lever for the product constraints, but controlled by Android/OEM/profile policy rather than Mirro. The Samsung proof is a real architecture proof, not a portable public API proof.

### F. Embedded/virtual GMS

This would be a separate Google-services implementation and identity/licensing problem, not a small compatibility layer. It would create policy, security, certification and maintenance risk and still would not provide legitimate target signing or Play Integrity. It is not a viable Mirro primitive.

## 4. Mini-engine hypothesis test

The proposed three additions—virtual process model, Binder/system-service proxying and native runtime adaptation—are not a small implementation.

They immediately require:

- version-specific hidden API access and `ActivityThread`/`LoadedApk` manipulation;
- service transaction and parcel maintenance;
- proxy/stub manifest components and lifecycle/token translation;
- package/UID/attribution mapping across every system-service call;
- native ABI, linker namespace, syscall/path, `/proc`, JNI and process hooks;
- child process/`execve` supervision and restart semantics;
- dynamic code/feature loader observation;
- OEM-specific behavior and API 14–16 regression testing;
- policy handling for background work, notifications, permissions, WebView and accounts;
- app-specific compatibility exceptions for packed/native/identity-sensitive apps.

The package/component models Mirro already has are necessary inputs, but they do not make the above boundary smaller.

**Complexity: `VERY_LARGE` and operationally `OPEN_ENDED`.**

The architecture can be diagrammed with 3–6 boxes. The implementation burden is dozens of mutually dependent service adapters, hooks, proxy components, native paths and regression cases. It is not realistic to complete once and declare compatibility stable.

## 5. Maintenance burden evidence

### VirtualApp evidence

The public source presents a sharp split:

- public code stopped updating in December 2017;
- the maintained commercial branch is described as supporting newer Android versions and GMS;
- the public changelog contains hundreds of fixes and adaptations.

The changelog includes, among other items:

- Binder interception and API-level transaction changes;
- `AttributionSource` and caller validation;
- GMS login invocation;
- `ActivityThread`/Application attachment edge cases;
- split APK and Google Play installation behavior;
- `seccomp-bpf`, inline hooks, `execve`, `/proc`, syscall and filesystem redirection;
- 16 KB page-size support;
- native crash and process-start fixes;
- Android 15/16/17 and Oppo/device-specific adaptations;
- service, notification, AppSearch, DomainVerification, StorageStats, Wi-Fi, location and input service changes.

That is not the maintenance profile of a clever compact core. It is the maintenance profile of an operating-system compatibility layer.

### VirtualXposed evidence

VirtualXposed’s own documentation describes a non-root VirtualApp/Xposed stack but also documents important limitations: it cannot hook `system_server`, does not support resource hooks, and its published compatibility range is old. [VirtualXposed limitations](https://github.com/android-hacker/VirtualXposed/wiki/Difference-between-VirtualXposed)

This supports the conclusion that a user-space engine can be useful for a subset, but the deeper it tries to approach full compatibility, the more it needs hooks that an ordinary app cannot reliably own.

### BlackBox and DroidPlugin evidence

BlackBox repositories expose the expected virtual-engine shape—virtual package management, ActivityThread management and app installation-as-user APIs. [BlackBox repository](https://github.com/FBlackBox/BlackBox), [BlackBox documentation](https://github.com/ALEX5402/NewBlackbox/blob/main/Docs.md)

DroidPlugin publicly describes running third-party APKs without normal installation or repackaging, which implies a proxy/hook lifecycle rather than a real Android package authority. [DroidPlugin](https://droidpluginteam.github.io/DroidPlugin/)

These projects are useful architecture references. They are not evidence that Mirro can avoid their hook, proxy, native and compatibility surface.

## 6. Reuse and shortcut analysis

| Candidate | License/provenance | Maintenance and Android relevance | Does it replace months of work? | Play/distribution assessment |
|---|---|---|---|---|
| AOSP framework/AVF pieces | AOSP licensing is generally permissive by component, but platform integration and permissions matter | AVF/Microdroid is for protected native payloads and is restricted; it is not a full third-party Android guest. [AVF API](https://android.googlesource.com/platform/packages/modules/Virtualization/%2B/HEAD/libs/framework-virtualization/README.md), [Microdroid limits](https://source.android.google.cn/docs/core/virtualization/microdroid) | No. It provides building blocks, not a full guest/profile authority | Requires device/platform support; ordinary apps do not get the needed AVF management permission |
| VirtualApp public tree | Public repository warns that the open tree is old and commercial use requires licensing; the maintained branch is not a safe assumed open-source base | Public tree is stale; maintained branch’s own changelog demonstrates major ongoing work | No. It would import a large undocumented maintenance and provenance burden | Commercial licensing and hidden/native hooks create substantial Play and legal review risk. [VirtualApp licensing statement](https://github.com/asLody/VirtualApp/blob/master/README_eng.md) |
| BlackBox Apache repository | One BlackBox repository declares Apache-2.0. Forks, bundled dependencies and provenance must still be audited file-by-file. [Apache license](https://github.com/dodobrands/BlackBox/blob/main/LICENSE) | Architecture is relevant, but fork quality, Android 16 behavior and maintenance vary | Partially. It can provide a starting codebase, not a reliable compatibility product | Potentially compatible with Play licensing, but container policy, hidden API use, executable target handling and privacy/security review remain |
| DroidPlugin | Public technical project; license/provenance and modern maintenance must be independently audited before reuse | Public architecture is historically important; not evidence of modern API 36 support | No safe shortcut without a full legal and device audit | Uncertain; plugin execution, target APK handling and hidden hooks need policy review |
| Native hook libraries | Individual libraries may be permissive, but a hook library is only a mechanism | Does not provide package/process/Binder/component authority | No | Native hooks increase crash, detection, policy and maintenance risk |
| Boxify/academic research | Research license and implementation scope must be checked independently | Demonstrates syscall/Binder proxy feasibility, not production Android 16 compatibility | No | Research architecture is not a distribution shortcut |

**Shortcut verdict:** partial reuse only. There is no safe permissive foundation that turns the current Mirro project into a maintained Parallel Space engine without importing the same large compatibility program.

Android’s non-SDK restrictions reinforce this conclusion. Android 16 publishes a separate hidden-API list, and OEMs may add interfaces to the blocklist. [Android non-SDK restrictions](https://developer.android.google.cn/guide/app-compatibility/restrictions-non-sdk-interfaces?authuser=9&hl=en)

## 7. Profile backend versus deep virtual engine

| Dimension | Deep VirtualApp-style engine | Android/OEM profile backend |
|---|---|---|
| Compatibility | Potentially broad for apps that tolerate virtualization; still below real Android authority for identity/attestation | Highest local compatibility because Android owns package, UID, process, services and native runtime |
| UX | Can look like one launcher/container if compatibility succeeds | Profile badge, profile settings, pause state and OEM UI remain visible |
| Control | Mirro controls the virtual registry and surface | Android/OEM/profile owner controls lifecycle and policy |
| Portability | Code can theoretically run across devices, but every API/OEM version adds work | Profile model is standard; creation/UX/clone APIs are OEM and policy dependent |
| Play viability | High policy/security risk around containers, target executable handling and hidden APIs | More conventional Android execution; profile-owner/OEM behavior and container policy still require review |
| Maintenance | Very high and ongoing | Lower for execution; higher only for OEM adapters and setup flows |
| Privacy | Local, but Mirro becomes a large privileged compatibility layer around guest code | OS-managed local isolation; OEM profile services are part of the trust boundary |
| Solo developer | Not realistic as a universal product | Realistic as an orchestrator for supported profiles/OEMs |
| OEM dependence | Lower in theory, high in practice due hooks | Explicitly high; Samsung proof does not transfer unchanged to Pixel/Xiaomi/etc. |
| Setup friction | Lower in the ideal UI, high when failures need app-specific fixes | Higher up front; profile creation, consent and switching are unavoidable |
| Long-term robustness | Fragile against Android internals | Strong where the platform supports profiles; not universal across OEMs |

The original product goal values no root, no cloud cost, simple UX and popular-app compatibility. A profile backend wins the technical compatibility contest but loses the “single seamless container” contest. A deep engine wins neither contest reliably for a solo developer: it incurs deep maintenance while still lacking legitimate system identity.

## 8. Original product goal reality

The original goal was a normal-user app that clones social, messaging, native and GMS-dependent apps with no root, no cloud cost, simple UX and privacy-first local execution.

**Classification: `REALISTIC_WITH_LIMITATIONS` only after a product pivot.**

The constraint that breaks first is not native loading; the real profile proof showed Android can solve native loading and identity classes. The first break is **authority and UX portability**:

1. A normal app cannot silently create/manage the required profile on every device.
2. OEM clone/profile APIs are inconsistent and often privileged.
3. Android exposes profile setup, pause, notification and account boundaries.
4. A fully seamless experience would require Mirro to reimplement the very process/Binder/native engine whose maintenance is the stop condition.

A profile-aware Mirro can be useful, but it must state that “clone” may mean an Android-owned profile instance rather than an in-process window.

## 9. Solo-developer cost

| Option | Initial effort | Ongoing burden | Assessment |
|---|---|---|---|
| A. Continue current in-process engine | **Weeks** for more ordinary-app support and diagnostics | **Ongoing major maintenance** if marketed as broad compatibility | Good bounded subset; bad universal engine |
| B. Rebuild as deep virtualization engine | **Months** before a serious native/Binder/component slice is reliable | **Ongoing major maintenance** across every Android/OEM release | Not realistic for a solo universal product |
| C. Profile-aware orchestrator | **Days to weeks** for detection/launch on one supported OEM; **weeks to months** for user-mediated provisioning and multiple OEM adapters | **Medium ongoing maintenance** for OEM/API/policy differences | Best general local direction |
| D. Personal ChatGPT-only tool | **Days to weeks** depending on whether web/profile UX is enough | **Low ongoing maintenance** | Strongly feasible |

These are engineering categories, not schedules. The critical difference is whether compatibility maintenance grows with Android internals and every target app. Deep virtualization does.

## 10. Hard stop rule

Mirro must be abandoned as a general in-process cloning product if any one of these objective conditions is met:

1. A representative matrix of one ordinary app, one native-heavy app, one dynamic-code app, one background/provider app and one GMS app cannot pass without per-app patches.
2. The next compatibility milestone requires process-wide native hooks, Binder transaction hooks or hidden-API maintenance across a new Android release before any product-level user value is delivered.
3. No external base is legally and technically reusable on the target API/OEM set after license/provenance review.
4. A real target UID/signing/Binder/attestation identity remains necessary and cannot be provided by an Android-owned profile, legitimate OEM facility or full guest OS.
5. A normal consumer setup requires root, device-owner enrollment, repeated ADB/Shizuku activation or OEM-specific privileged access on the majority of target devices.
6. The support matrix grows by app-specific exceptions faster than generic capability tests improve.

The current in-process architecture already meets conditions 1, 2 and 4 for the original general goal. The real-profile proof supplies the correct escape hatch: move authority to Android where available rather than continuing to deepen the host facade.

## Evidence boundaries

The Samsung proof demonstrates:

- real package installation/enablement for users 0 and 95;
- distinct profile-derived UIDs and processes;
- distinct `/data/user/0` and `/data/user/95` roots;
- ChatGPT profile-scoped GMS account-discovery flow without the Mirro host package path;
- Discord native/UI startup without the prior Mirro missing-library failure.

It does not demonstrate:

- completed ChatGPT account login;
- generic managed-profile creation by an ordinary Mirro APK;
- portability to non-Samsung devices;
- Play Integrity, DRM, banking or server-side trust success;
- a Mirro-owned cross-user launcher API.

The evidence supports a profile-aware product boundary, not a universal clone promise.

## Final decision sections

### A. FOUNDATIONAL PRIMITIVES

The minimum real primitives are:

1. A virtual process/runtime model with client/server roles, process death/restart and application binding.
2. Binder/system-service interception and attribution translation.
3. A package/component authority with proxy/stub lifecycle routing.
4. Native filesystem, `/proc`, JNI, linker and process-environment adaptation.
5. Dynamic code/resource/native-loader integration.
6. Isolation/policy/diagnostics around permissions, storage, background work and unsupported security boundaries.

The first five are tightly coupled and required for modern broad compatibility. They are a small conceptual set but a large, open-ended implementation surface.

### B. MIRRO GAP

Mirro has useful partial package, storage, loader, component and diagnostics models. It is **ABSENT or DIAGNOSTIC_ONLY** at the decisive boundaries: real process virtualization, Binder/system-service interception, native path/linker/JNI adaptation, target UID/user authority and system-owned component lifecycle. Android remains authoritative for the exact failures observed in ChatGPT, Discord, Facebook and BA-mobil.

### C. MINI-ENGINE VERDICT

**NOT_REALISTIC_FOR_SOLO_PROJECT**

The “three primitive” plan immediately expands into version-specific hidden API hooks, transaction maintenance, ActivityThread/LoadedApk manipulation, native hooks, linker work, proxy components, OEM fixes and app-specific exceptions. Public VirtualApp maintenance evidence confirms the burden is ongoing rather than finite.

### D. PROFILE VERDICT

**USEFUL_FALLBACK**

Real profiles are the strongest local compatibility boundary and passed the most important Samsung proof, but profile creation, ownership, UX and portability are OEM/policy dependent. They are an execution backend and orchestration target, not a universally controllable Parallel Space replacement.

### E. SHORTCUT VERDICT

**PARTIAL_REUSE_ONLY**

AOSP and permissive components can inform or support selected pieces. BlackBox has an Apache-2.0 repository, but forks/dependencies/modern compatibility require a full audit. VirtualApp’s maintained compatibility surface is licensed/commercial and its public tree is stale. No safe shortcut removes the core engineering burden.

### F. ORIGINAL PRODUCT GOAL

**PIVOT**

Do not continue toward a universal in-process clone engine. Reframe Mirro as a profile-aware local orchestrator with the current container retained for a bounded supported subset.

### G. PERSONAL CHATGPT

**CONTINUE**

The personal two-session goal is achievable through supported web account switching or a real Android profile, without cloning the general Android framework or bypassing authentication controls.

### H. ONE RECOMMENDATION

**Pivot Mirro into an Android-authority orchestration layer: keep the current container as a bounded fallback, integrate only legitimate profile/OEM launch paths, and stop all work aimed at turning the host process into a universal Android virtualization engine.**
