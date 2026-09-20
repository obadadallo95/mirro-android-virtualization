# Mirro

Mirro is an **Android app virtualization research project**. It explores Android app cloning, multi-account isolation, in-process containers, dynamic DEX loading, Binder/system-service boundaries, native runtime compatibility, GMS identity, and Android-owned profile execution.

> Active product development has concluded; the repository remains available as a research reference.

## Status

- Research prototype and archived active-development project.
- Not production-ready.
- Not a universal app cloner or a Parallel Space replacement.
- No compatibility guarantee for arbitrary third-party apps.
- No security-boundary bypasses are included or accepted.

The code is useful for studying Android app virtualization research, Android app cloning without root, virtual PackageManager/ActivityManager design, Binder interception boundaries, dynamic loaders, and profile-based cloning. Treat it as experimental code, not as a secure runtime for sensitive accounts or banking apps.

## Why this project exists

The original product goal was a free, local, privacy-first Android multi-account experience: independent app sessions, no ads, no cloud runtime cost, and no root. The central question was whether a normal application could host popular social, messaging, native and GMS-dependent apps inside an isolated user-space container.

## What was built

The repository contains a Kotlin/Compose Android prototype with:

- APK and split inspection, target Application bootstrap and Activity hosting.
- `MirroTargetClassLoader`, target class indexing and a dynamic loader graph.
- Virtual `Context`, package/component registry, permissions and AppOps models.
- Clone-isolated storage paths, preferences, databases and WebView process-slot handling.
- Logical Activity/task, provider/FileProvider, service, broadcast, PendingIntent and notification models.
- Capability classification and structured runtime diagnostics.
- Deterministic unit tests for loader, package, storage, identity and component contracts.
- A real Samsung profile proof showing Android-owned package/UID/process isolation.

## What actually worked

| Path | Result |
|---|---|
| In-process ChatGPT | UI reaches the login/input surface; native/GMS identity remains blocked |
| In-process Discord | Fails at native loading with missing `libkv_storage.so` |
| In-process Facebook | Application starts, then fails with `DYNAMIC_LOADER_UNSEEN` |
| In-process BA-mobil | Static/root Activity works; later GMS/Firebase identity and lifecycle boundaries fail |
| Samsung real clone profile | ChatGPT runs under a profile-derived process/data boundary; Discord reaches its normal Welcome screen without the prior native-loader failure |

The real-profile result is not a Mirro-created universal profile API. It demonstrates that Android itself can own the authority that the in-process container cannot truthfully emulate.

## Key conclusion

`Context.getPackageName()` is not Android identity. A facade does not change the Linux UID, Binder caller UID, signing certificate, AppOps attribution, system-service ownership, native linker namespace, or Play Integrity verdict.

Broad compatibility requires a deep authority boundary around:

1. Process/runtime and lifecycle ownership.
2. Binder and system-service interception or replacement.
3. Package/component/task authority.
4. Native filesystem, linker, JNI and process-environment adaptation.
5. Dynamic code/resource/loader integration.
6. Isolation policy, scheduling and diagnostics.

Mature VirtualApp/Parallel Space-style systems maintain this boundary across Android and OEM changes. Real Android profiles solve much of it because Android owns the package, UID, process, storage and services.

## Architecture evolution

```text
Stage 1  APK inspection + Application bootstrap + Activity embedding
Stage 2  Dynamic loader graph and runtime observability
Stage 3  Virtual package/component/storage/framework contracts
Stage 4  Real Samsung profile execution proof
Stage 5  Final pivot: profile-aware orchestration, bounded local container
```

### Current local container

```text
Mirro host Activity
  -> ContainerRuntime
  -> target APK/split inspection
  -> MirroTargetClassLoader + loader graph
  -> VirtualContext and local framework models
  -> target Application / Activity in Mirro process
  -> host window, task, UID and Android system services
```

### Real-profile approach

```text
Mirro launcher/orchestrator
  -> user/OEM/profile setup
  -> Android PackageManager for profile user
  -> real target package/process/UID/data root
  -> Android ActivityManager, Binder, native loader and system services
```

## Repository map

Start with [Project Overview](docs/00_PROJECT_OVERVIEW.md), then follow the [Research Index](docs/10_RESEARCH_INDEX.md).

Core reports:

- [Architecture History](docs/01_ARCHITECTURE_HISTORY.md)
- [Compatibility Matrix](docs/COMPATIBILITY_MATRIX.md)
- [Full Virtualization Architecture Audit](docs/FULL_VIRTUALIZATION_ARCHITECTURE_AUDIT.md)
- [Dynamic Code Runtime](docs/DYNAMIC_CODE_RUNTIME.md)
- [Virtual Framework Runtime](docs/VIRTUAL_FRAMEWORK_RUNTIME.md)
- [Real Profile Architecture Proof](docs/REAL_PROFILE_ARCHITECTURE_PROOF.md)
- [Final Virtualization Engine Decision](docs/FINAL_VIRTUALIZATION_ENGINE_DECISION.md)
- [Lessons Learned](docs/09_LESSONS_LEARNED.md)
- [Open Source License Review](docs/OPEN_SOURCE_LICENSE_REVIEW.md)

## Compatibility findings

The project uses evidence categories rather than a green “launch succeeded” claim. `STATIC_SUPPORTED`, `REGISTERED_DYNAMIC`, `DYNAMIC_LOADER_UNSEEN`, `NATIVE_LOAD_FAILED`, `SYSTEM_IDENTITY_BLOCKED`, and profile-mediated results describe what was actually observed.

The primary blockers were not solved by adding another package facade:

- GMS and Google login validate caller/package identity outside Mirro’s virtual metadata.
- Native libraries observe the host process and linker environment.
- Private or native-created loaders may never enter Mirro’s observable loader graph.
- Activity embedding does not replace ActivityManager, task tokens or system lifecycle.
- Background work, providers, notifications and PendingIntents remain Android-owned unless a real framework boundary exists.

## What future developers should know

- Package name is not real Android identity.
- A `Context` facade is not Binder identity.
- Retrying a class loader is not loader virtualization.
- Java storage redirection is not native process isolation.
- Activity embedding is not ActivityManager virtualization.
- GMS, Play Integrity, DRM and hardware attestation cannot be solved by spoofing guest metadata.
- A native hook layer can improve compatibility but creates an Android-version/OEM maintenance program.
- Work Profile, Dual Apps and secondary users are Android-owned execution boundaries with unavoidable setup and UX constraints.

## Ethical and security boundaries

Mirro does not bypass Play Integrity, forge signatures or UIDs, extract credentials/cookies/sessions, bypass DRM, defeat anti-tamper controls, or claim unsupported GMS identity. Do not use the prototype with sensitive accounts, financial apps or data you cannot afford to lose.

## License and contribution

Mirro-owned source and documentation are released under [Apache License 2.0](LICENSE), subject to the notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Referenced projects are not Mirro dependencies and are not relicensed by this repository.

Documentation improvements, reproducibility reports and safe compatibility research are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).
