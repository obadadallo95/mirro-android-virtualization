# Lessons Learned

## What I would do differently today

Start with an authority map before implementing a container. List every operation that must be owned by Android—package install, UID, Binder caller, ActivityManager, providers, schedulers, native linker, WebView process and GMS. Then decide whether the product can legitimately own or delegate each one.

The first proof should have been a real Android profile on several OEMs, followed by a small static APK and one native-heavy APK. That would have separated the UX question from the execution-authority question earlier.

## What looked easy but was not

- Loading an APK is easier than recreating its installed environment.
- A target Activity can be instantiated without owning its task, token, window or lifecycle.
- A virtual package name can improve Java behavior without changing Binder identity.
- Redirecting `filesDir` does not redirect native absolute paths or `/proc`.
- A missing class may belong to a loader that Mirro cannot observe.
- A GMS login screen is not evidence that GMS authentication can succeed.
- A successful first frame is not app compatibility.

## Which abstractions helped

`ApkInspector`, `MirroTargetClassLoader`, `DynamicCodeManager`, `LoaderGraph`, `VirtualStorageManager`, explicit package provenance and capability/failure categories were valuable because they made the boundary observable and testable.

The explicit distinction between guest-local, host-mediated, unsupported and identity-required behavior prevented false success claims.

## Which abstractions became diagnostic-only

The virtual Activity/task, provider, service, broadcast, PendingIntent and notification managers model useful contracts, but they do not own Android’s system objects. They should remain bounded compatibility components or test fixtures, not grow into a second system_server.

Likewise, virtual permissions/AppOps state can explain behavior and support local-only APIs, but cannot grant a platform permission or alter a Binder caller UID.

## Android identity is a vector

Identity is not one string. It includes package name, Linux UID/user, process/PID, signing certificate, installer/licensing state, Binder caller identity, AppOps/AttributionSource, accounts, device state, Play Integrity and hardware-backed keys.

Changing one field can help a target Java branch while leaving the security-relevant vector unchanged. This is why GMS and Credential Manager exposed the architecture boundary.

## Native compatibility is its own world

Native code sees library paths, linker namespaces, ABI, JNI state, process identity, `/proc`, filesystem labels, syscalls, environment variables and static process state. Java Context redirection cannot guarantee those values. Native hooks can help, but introduce ABI, Android-release, OEM and crash-maintenance costs.

## Virtualization is a maintenance program

Mature systems demonstrate that app virtualization is not a one-time implementation. Binder interfaces, hidden APIs, attribution validation, split delivery, background policies, native linkers, page sizes, OEM services and target SDK behavior change continuously. Public VirtualApp evidence shows hundreds of fixes across these areas.

The core architecture may fit on a diagram with six boxes; the compatibility surface is open-ended.

## Real profiles are technically strong but product-constrained

Android profiles solve the strongest parts of the problem because Android owns the package manager, UID, process, storage, services and native runtime. The trade-offs are profile setup, OEM APIs, badges, pause state, profile switching and lack of seamless in-process UX.

The Samsung proof is strong evidence for the architecture, not evidence of a portable public profile API.

## Universal cloning and solo development conflict

A solo developer can build a useful bounded container, a diagnostic runtime or a profile-aware launcher. A universal, no-root, no-cloud clone engine for modern social, messaging, native and GMS apps requires a continuing platform-compatibility team or a platform/OEM authority boundary.

The correct stop rule is to stop expanding the in-process engine when representative apps require per-app patches, native/Binder hooks for each Android release, or forged/security-sensitive identity.

## Practical advice for future builders

1. Define a capability taxonomy before claiming support.
2. Test package/UID/process identity, not only UI launch.
3. Keep dynamic-code and native failures separate.
4. Treat system services as authority boundaries.
5. Use deterministic fixtures for class loaders, providers, jobs, native libraries and profiles.
6. Prefer Android-owned profiles when real package semantics matter.
7. Never solve a security boundary by spoofing metadata.
8. Preserve sanitized evidence and make unsupported behavior explicit.
9. Audit license/provenance before using any virtualization engine as a base.
10. Keep the product promise narrower than the implementation ambition.
