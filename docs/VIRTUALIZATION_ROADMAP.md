# Mirro Virtualization Roadmap

This roadmap converts the architecture audit into staged work. It deliberately avoids a single rewrite and keeps the product honest about what a normal, Play-distributed user-space app can and cannot virtualize.

## Operating principles

1. **Measure before widening the boundary.** Every compatibility change must produce evidence about the defining class loader, host/guest identity vector, component route, system-service path and first failing API.
2. **Prefer public APIs and explicit adapters.** Hidden APIs may be isolated behind version-gated compatibility modules, but they cannot be the product's only foundation.
3. **Never spoof a security boundary.** No fake UID, signing certificate, Play Integrity verdict, hardware attestation, privileged permission, GMS caller, or anti-tamper result.
4. **Separate guest semantics from host capabilities.** A target may see a virtual package name in guest code while the framework still sees Mirro. The result must be `SUPPORTED`, `HOST_MEDIATED`, `DEGRADED`, or `UNSUPPORTED_WITH_REASON`.
5. **Treat dynamic code as a graph.** A startup DEX path and a retry loop are not a runtime loader architecture.
6. **Make background behavior durable.** In-memory clone state is not enough for process death, reboot, jobs, alarms, push, or notifications.
7. **Keep compatibility scoped.** A fixed process pool and manifest-declared host stubs are safer than pretending Android can dynamically create unlimited virtual components/processes.
8. **Policy is an input to architecture.** Honor `REQUIRE_SECURE_ENV`, avoid remote executable code delivery, request only justified package visibility, and disclose sensitive data handling.

## Dependency order

```text
Phase 0  evidence + contracts
    |
Phase 1  dynamic code/native observability
    |
Phase 2  identity/package/permission/storage consistency
    |
Phase 3  component/task/provider fabric
    |
Phase 4  fixed process pool + restoration
    |
Phase 5  explicit Binder/system adapters + GMS capability layer
    |
Phase 6  background, notifications, jobs, alarms, FCM
    |
Phase 7  compatibility hardening, policy and release qualification
```

Phase 0 and Phase 1 should happen before trying to make ChatGPT, Facebook, or another individual app “work.” Phase 2 must define identity semantics before broad GMS or Binder work. Phases 3–6 can proceed in slices, but each slice must use the same capability contract.

## Phase 0 — Evidence, contracts, and policy gate

### Objective

Create the measurement surface that prevents one-off fixes from silently extending host fallbacks or claiming unsupported identity.

### Work

- Define `RuntimeSession`, `CapabilityDecision`, `HostGuestIdentityVector`, `ComponentRoute`, `LoaderNode`, and structured failure codes.
- Record base/split APKs, target SDK, ABI, installed feature state, secure-environment opt-out, loader graph, native load events, component resolution, system-service calls and process slot.
- Add a capability manifest/report for every target launch.
- Define host/guest/unsupported field policy for `PackageInfo`, `ApplicationInfo`, `ComponentInfo`, `getPackageUid`, signing data, installer, permissions and process name.
- Add explicit privacy/policy review gates: `REQUIRE_SECURE_ENV`, package visibility, target APK provenance, executable code source, target data handling and consent.
- Build a test runner that stores results by device/API/OEM and target version.

### Compatibility gain

No immediate app compatibility gain; prevents false positives and identifies the highest-value gaps.

### Effort

Medium, mostly data model, diagnostics and fixtures.

### Risks

- Overbuilding telemetry before the contracts are used.
- Accidentally logging credentials, tokens or sensitive target data.

### Tests

F0-01, F0-02, F0-15, F0-16 from [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md), plus ChatGPT and Facebook launch traces with secrets redacted.

### Exit criteria

- Every launch ends in a capability result and first-failure category.
- Logs prove the difference between guest package string, host package, UID, PID, AppOps package, and Binder caller.
- No target credentials, cookies or token values are persisted in diagnostics.
- Targets declaring `REQUIRE_SECURE_ENV` are rejected before code execution.

## Phase 1 — Dynamic code and native runtime plane

### Current implementation status (2026-09-20)

The first Phase 0/1 slice is implemented in the repository: `RuntimeSession` and
`HostGuestIdentityVector` are attached to existing container diagnostics;
`LoadedApkRuntime` owns a `DynamicCodeManager`; loader nodes, split metadata,
resolution records, capability categories, and native inventory/load events are
structured; and Activity preflight/hosting consumes the graph. Focused unit tests
pass. Device validation on a real API 36 arm64 device reached ChatGPT and produced
the expected structured Facebook `DYNAMIC_LOADER_UNSEEN` result; the available
lightweight baseline did not qualify because it failed at the host lifecycle/GMS
identity boundary. A real dynamic-feature/native/in-memory fixture remains
outstanding, so this does not change the roadmap's compatibility claims.

### Phase 2/Core Phase 3 implementation status (2026-09-20)

The first general virtual-framework foundation is now implemented: target package/component
metadata is owned by `VirtualPackageRegistry`; permissions and AppOps have explicit clone-local
state; storage paths have a single `VirtualStorageManager`; and logical Activity/task, intent,
provider/FileProvider, service, broadcast, PendingIntent, and notification contracts are exposed
through composable runtime services. The new contract suite passes alongside the existing runtime
tests. This is a foundation slice, not a claim of full Android framework emulation; GMS/Binder
identity remains intentionally deferred.

### Objective

Replace the single startup class-loader assumption with a measured guest loader graph and native-load capability.

### Work

- Add `DynamicCodeManager` behind `DexRuntimeLoader` and `MirroTargetClassLoader`.
- Represent root, split, child, feature and in-memory loader nodes with parent/delegation policy and code-source metadata.
- Register known installed splits before Application bootstrap.
- Register target-created loaders through supported boundaries; make “unseen” a first-class result.
- Resolve component classes through the graph, not just the initial `TargetClassIndex`.
- Track class owner, source APK/DEX, loader node and resolution attempt.
- Track native ABI, search paths, `System.loadLibrary`/`dlopen` outcomes and `UnsatisfiedLinkError` separately from class errors.
- Enforce one clone per native/WebView process slot.
- Do not introduce production-wide hidden hooks or anti-tamper bypasses in this phase.

### Compatibility gain

High for transparent multi-DEX, secondary-Dex, split and ordinary child-loader apps; diagnostic gain for in-memory, native-packed and guarded apps.

### Effort

High. This is the first foundational runtime change.

### Risks

- Loader delegation can create class identity conflicts.
- Instrumentation at loader/native boundaries is Android-version and Play-sensitive.
- Loading untrusted or remote executable code creates policy risk.
- Native libraries are process-global and can conflict across clones.

### Tests

F0-04 through F0-09; dynamic-feature sample; NDK sample; Facebook as a probe, not a target-specific fix.

### Exit criteria

- A class loaded by a registered late loader is resolvable by component routing.
- An unseen loader is reported as `DYNAMIC_LOADER_UNSEEN`, not retried indefinitely or reported as success.
- Installed split code and late feature state are distinguishable.
- Native failures identify ABI, path, linker/JNI or identity cause.
- No test changes production behavior for target apps whose code is already statically available.

## Phase 2 — Identity, package, permission, AppOps, and storage contract

### Objective

Make all guest-facing metadata internally consistent while explicitly preserving the host identity where the Android framework verifies it.

### Work

- Replace broad `VirtualPackageManager` fallback with a virtual package registry and field policy.
- Model logical clone ID separately from host package, UID, process, signing and installer.
- Implement `VirtualPermissionManager` with per-clone requested/granted/denied/host-mediated states.
- Add `VirtualAppOpsManager` policy and attribution tags; return host denial truthfully.
- Refactor `VirtualContext` so `getOpPackageName`, `AttributionSource`-related operations and host identity windows are explicit per API, not hidden global state.
- Make SharedPreferences use the clone storage manager and distinguish credential-protected/device-protected roots.
- Define direct path, native path, external storage, MediaStore, SAF and FileProvider policy.
- Expose capability results when target code asks for genuine UID/signature/installer/Play identity.

### Compatibility gain

Medium-high for ordinary apps, SDK initialization, permission checks, storage and package queries; low for identity-protected GMS.

### Effort

High; many APIs and field-level tests.

### Risks

- A misleading virtual value can cause data leaks or security failures.
- Host permission grants may be broader than one clone's logical grant.
- Storage migration can lose existing clone data if paths change.

### Tests

F0-02, F0-10, F0-15; FileProvider/SAF, permission, AppOps and package-query samples; ChatGPT identity trace.

### Exit criteria

- Same clone receives stable answers across Context, PackageManager, storage and permission APIs.
- Different clones cannot read each other's Mirro storage through supported guest APIs.
- Host UID/signature/installer answers are never mislabeled as target identity.
- Credential/GMS failures are classified as `SYSTEM_IDENTITY_BLOCKED` instead of appearing as random exceptions.

## Phase 3 — Component, task, provider, and intent fabric

### Objective

Move routing/state out of `TargetActivityHost` and create a coherent virtual component model.

### Work

- Implement `VirtualActivityManager` and `VirtualActivityTaskManager` for target intent resolution, task/affinity/flags, results, new intents and saved state.
- Keep `TargetActivityHost` as a UI adapter that consumes virtual routes.
- Add `VirtualContentManager` with target authority registry, local provider dispatch, URI translation, clone-scoped grants and host-provider rules.
- Split `VirtualServiceManager` into local guest lifecycle and host-stub lifecycle/foreground state.
- Add receiver registry and host dispatch path for runtime/manifest receivers.
- Define `PendingIntent` descriptors with clone/session identity and immutable action routing.
- Add notification facade with clone channels/IDs and action routing.
- Predeclare only the host stub components required by the supported feature set; document the bound.

### Compatibility gain

High for apps that depend on providers, nested components, services, notifications, links and results.

### Effort

Very high. This is the largest framework-plane phase.

### Risks

- Task/window behavior diverges across Android/OEM versions.
- URI/pending-intent mistakes can become cross-clone data or action leaks.
- Manifest stubs increase policy and background-execution obligations.

### Tests

F0-03, F0-10 through F0-15; FileProvider/SAF, notifications, widgets/shortcuts, Custom Tabs and deep-link samples.

### Exit criteria

- Target component routing is consistent across Activity, Service, Provider and Receiver descriptors.
- A provider URI from one clone cannot resolve to another clone's local provider.
- Pending intent actions return to the correct clone/session.
- Process death during a component callback produces a persisted, diagnosable result.

## Phase 4 — Fixed process pool and restoration

### Objective

Make process-scoped WebView/native/runtime state predictable and restore clones after process death without pretending to create unlimited processes.

### Work

- Declare a bounded pool such as `:container0...:containerN`, sized from product memory testing.
- Replace `SingleCloneProcessSlotStrategy` with a lease-based `ProcessPoolManager`.
- Persist clone-to-slot lease, WebView suffix, loader metadata, component registry checkpoint and last foreground state.
- Add clean teardown, crash release, stale lease recovery and process-death diagnostics.
- Keep a separate host scheduler/router process or host service for wakeups; do not run arbitrary guest UI there.
- Define clone activation/eviction policy and user-visible concurrency limits.

### Compatibility gain

Medium for multi-clone concurrency, native/WebView isolation and process-death recovery; necessary for background work.

### Effort

High, with device/OEM performance work.

### Risks

- Memory pressure and process churn.
- WebView suffix cannot be changed after initialization.
- Slot recovery can accidentally revive the wrong clone if lease identity is weak.

### Tests

F0-13, process-kill/relaunch scenarios, two or more clones of the same and different apps, WebView/native fixtures.

### Exit criteria

- At most one clone owns a process slot at a time.
- WebView suffix is stable for the slot lifetime.
- A killed process restores the correct clone state or returns a safe reset result.
- No clone's pending action is delivered to another clone after slot reuse.

## Phase 5 — Explicit Binder/system adapters and GMS capability layer

### Objective

Add only the high-value framework adapters justified by trace data and expose a stable capability contract for identity-sensitive Google APIs.

### Work

- Add typed adapters for selected Package, Activity/Task, Content, Notification, Alarm, Job, Account and GMS APIs.
- Keep Binder proxying explicit and version-gated; never assume one ServiceManager hook covers all Android releases.
- Implement `GmsBridge` capability results: `HOST_NATIVE`, `BROWSER_MEDIATED`, `TARGET_METADATA_ONLY`, `UNSUPPORTED_IDENTITY_REQUIRED`.
- Implement host-mediated Credential Manager flow only with user disclosure and no forged `CallingAppInfo`.
- Test Google Sign-In, Firebase Auth, Maps, Location, Drive and Billing separately.
- Do not implement Play Integrity or hardware-attestation spoofing.

### Compatibility gain

Medium for ordinary GMS SDKs; targeted rather than universal.

### Effort

Very high per API family; ongoing maintenance.

### Risks

- Google APIs and services update independently.
- Caller mismatch can occur in native Binder paths even when Java facade tests pass.
- Policy and backend terms may disallow host substitution.

### Tests

T1-04 through T1-08; ChatGPT auth; package/certificate mismatch diagnostics; no secret logging.

### Exit criteria

- Each supported GMS flow identifies which package/cert/UID/account is used.
- Host-mediated results are disclosed and do not claim target identity.
- Unsupported target-identity flows fail before destructive or ambiguous state changes.

## Phase 6 — Background, notifications, jobs, alarms, and FCM

### Objective

Restore supported clones after process death/reboot under Android's actual scheduling and user-permission rules.

### Work

- Persist guest job/alarm/receiver registrations in a clone registry.
- Map supported guest jobs to Mirro-owned WorkManager/JobScheduler entries with host constraints.
- Add host receiver/stub dispatch for boot, package/user, connectivity, alarms and FCM where legally and technically supported.
- Implement FGS adapter with declared host type, permission, user-visible notification and launch policy.
- Map notification channels/action intents to clone descriptors.
- Define FCM mode: host token, per-clone logical routing, or unsupported; do not imply an independent target FCM installation without evidence.
- Test Doze, force-stop, reboot, notification permission denial, battery restrictions and process kill.

### Compatibility gain

High for messaging/media/sync apps when host mediation is acceptable; low for identity-bound push.

### Effort

High.

### Risks

- Background policy changes and OEM restrictions.
- User-visible notifications can be misleading if clone attribution is unclear.
- Wakeups can become data collection outside user expectation.

### Tests

T1-09 through T1-16; Telegram/Discord/Spotify probes; force-stop/reboot/Doze test suite.

### Exit criteria

- Every restored callback is tied to a clone and persisted registration.
- Android declines/deferred work is represented accurately.
- FGS and notification behavior follows current target/host permission policy.

## Phase 7 — Compatibility hardening and Play qualification

### Objective

Turn the measured capability set into a releasable product contract.

### Work

- Run the full device/API/OEM matrix and pre-launch reports.
- Audit non-SDK usage and remove or isolate unsupported paths.
- Validate `REQUIRE_SECURE_ENV` handling for every loaded target.
- Review package visibility and remove broad queries unless core functionality clearly qualifies.
- Complete privacy disclosure, consent, data retention/deletion and Data Safety review.
- Publish support classes and failure codes rather than a universal compatibility claim.
- Re-run license/IP review for any new dependency or hook technology.

### Compatibility gain

Reliability and trust more than new app classes.

### Effort

Continuous.

### Risks

- Play review may classify a design choice differently from internal analysis.
- OEM behavior can invalidate hidden framework assumptions.

### Tests

All matrix tiers, Play pre-launch, policy test cases, license scans and privacy/security review.

### Exit criteria

- Release build has no unreviewed executable-code download path.
- Target opt-outs are enforced.
- Every advertised class has reproducible support evidence.
- All known hard limits are user-visible and documented.

## What not to do in the roadmap

- Do not add a Facebook-specific Activity name or loader delay.
- Do not set `getOpPackageName()` to a guest package in an attempt to fool AppOps/GMS.
- Do not change `Process.myUid()`, Binder caller identity, package signatures or Play Integrity responses.
- Do not globally hook hidden framework internals as the first milestone.
- Do not load executable code from a remote server to improve compatibility.
- Do not copy VirtualApp, BlackBox, DroidPlugin or VirtualXposed code into Mirro without a separate legal and technical approval.
- Do not claim that browser callback routing equals credential or package identity virtualization.

## Immediate next prompt

The exact prompt for the first implementation milestone is also recorded in the main audit. Use this version as the execution contract:

> Implement only the first Mirro virtualization milestone: guest runtime observability and a generalized dynamic-code contract. Do not modify AndroidManifest.xml, build files, Play/GMS identity behavior, production Binder caches, hidden-API exemptions, anti-tamper behavior, or competitor-specific code. Preserve unrelated user changes.
>
> Add a `RuntimeSession` diagnostics model and a `DynamicCodeManager` abstraction behind the existing `DexRuntimeLoader`/`MirroTargetClassLoader`. Track the root loader, installed executable split paths, registered child loaders, parent/delegation policy, DEX/code-source metadata, native library paths, ABI, and component class-resolution attempts. Make loader ownership and failure categories explicit; a retry of the same loader must not be reported as dynamic discovery.
>
> Add fixture-oriented tests for packaged `DexClassLoader`, nested `PathClassLoader`, `InMemoryDexClassLoader` metadata/unsupported behavior, installed split code, `System.loadLibrary`/ABI diagnostics, and a deliberately unavailable/packed case. If production code cannot observe a loader through a supported boundary, record `DYNAMIC_LOADER_UNSEEN` and explain the limitation rather than using hidden global hooks. Keep the current Activity hosting and auth routing behavior unchanged. Run the focused unit tests and report exactly which evidence is available for the Facebook-style missing Activity case.

## Decision checkpoints

After each phase, answer these questions before continuing:

1. Did the change improve a class of apps or only one app?
2. Did it introduce a new host identity fallback or merely make one less visible?
3. Can process death, slot reuse and clone separation be tested deterministically?
4. Does the feature require a real UID, signature, user/profile, system permission or attestation?
5. Does the feature load or execute code from a source outside the allowed Play distribution model?
6. Does the target's `REQUIRE_SECURE_ENV` choice or terms of service rule it out?
7. Is the necessary behavior available through public APIs, or is a hidden hook being treated as a permanent product dependency?

## References

- [On-device Android containers and `REQUIRE_SECURE_ENV`](https://support.google.com/googleplay/android-developer/answer/13609005?hl=en)
- [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en-NZ)
- [Non-SDK interface restrictions](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)
- [Processes and threads](https://developer.android.com/guide/components/processes-and-threads)
- [Process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
- [Service manifest element](https://developer.android.com/guide/topics/manifest/service-element)
- [WebView process configuration](https://developer.android.com/reference/androidx/webkit/ProcessGlobalConfig)
- [WorkManager](https://developer.android.com/reference/androidx/work/WorkManager)
- [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler)
- [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
- [FCM receive messages](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- [AVF](https://source.android.com/docs/core/virtualization)
- [Microdroid](https://source.android.com/docs/core/virtualization/microdroid)
