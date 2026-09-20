# Mirro Project Overview

## Project status

Mirro is a research prototype. Active product development has concluded; the repository is preserved as a public Android virtualization research reference. It is not production-ready and makes no universal app-cloning claim.

## Original goal

The project started from a practical question: can a normal Android user create a second independent instance of popular apps—social, messaging, native and GMS-dependent—without root, cloud execution, advertisements or loss of local privacy?

The intended experience was a local Parallel Space alternative: select an installed app, create an isolated instance, and run separate data, accounts, cookies, preferences and notifications from one host application.

## Constraints

- ordinary third-party Android application;
- no root or bootloader/system-image changes;
- no security-boundary spoofing;
- no Play Integrity, DRM, signature, UID or credential bypass;
- no per-app patches as a compatibility strategy;
- local execution and privacy-first storage;
- reproducible evidence instead of launch-screen claims.

## What the repository contains

The implementation is a Kotlin/Compose Android host with a user-space container runtime. Its research surfaces include APK/split inspection, Application bootstrap, Activity hosting, target-first class loading, dynamic loader evidence, virtual Context/package/component models, clone storage, provider/service/broadcast/task contracts and capability diagnostics.

The tests are primarily deterministic unit tests. Real-device probes were used to distinguish UI reachability from actual compatibility.

## Major discoveries

1. A package-name override is not Android identity. Linux UID, Binder caller identity, signing records, AppOps attribution, GMS and system services remain host-owned.
2. A loader retry is not a dynamic loader boundary. Modern apps create child, in-memory, split, feature and native-origin loaders that may not be observable.
3. Java storage redirection does not control native absolute paths, `/proc`, linker namespaces, JNI state or native process behavior.
4. Activity embedding can demonstrate UI compatibility but does not replace ActivityManager, task tokens, system lifecycle or background scheduling.
5. A real Android profile changes the equation: Android itself owns package installation, profile UID, process, data root, native loader and system services.
6. Broad user-space virtualization is a continuing Android/OEM compatibility program, not a small collection of independent managers.

## Final decision

The deep Parallel Space-style engine was judged `NOT_REALISTIC_FOR_SOLO_PROJECT`. The project should pivot to profile-aware orchestration while retaining the current container as a bounded research/runtime subset. The Samsung real-profile proof was classified `GO_WITH_UX_LIMITATIONS`: technically strong, but OEM/profile setup and UX remain visible and not universally controllable by an ordinary app.

## Intended audience

This repository is for Android developers, mobile platform engineers, security researchers, reverse engineers and anyone studying Android app virtualization, Android app cloning without root, Binder/system-service virtualization, dynamic DEX loading, native compatibility, GMS identity boundaries, Work Profiles, Dual Apps and Android runtime authority.

## Reading order

1. [Architecture History](01_ARCHITECTURE_HISTORY.md)
2. [Compatibility Matrix](COMPATIBILITY_MATRIX.md)
3. [Full Architecture Audit](FULL_VIRTUALIZATION_ARCHITECTURE_AUDIT.md)
4. [Dynamic Code Runtime](DYNAMIC_CODE_RUNTIME.md)
5. [Virtual Framework Runtime](VIRTUAL_FRAMEWORK_RUNTIME.md)
6. [Real Profile Proof](REAL_PROFILE_ARCHITECTURE_PROOF.md)
7. [Final Engine Decision](FINAL_VIRTUALIZATION_ENGINE_DECISION.md)
8. [Lessons Learned](09_LESSONS_LEARNED.md)
