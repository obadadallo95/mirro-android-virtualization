# Mirro Compatibility Matrix

This matrix is a planning and test document, not a promise that the named apps will work. Results must be recorded by Android version, OEM, target APK version, ABI, installed split set, and Mirro capability flags.

## Support labels

| Label | Meaning |
|---|---|
| **S1 — Supported** | Expected to work for the tested flow without material identity or lifecycle degradation |
| **S2 — Host-mediated** | Works through Mirro/host identity or browser mediation; target may observe a difference |
| **S3 — Degraded** | Core UI/code may run, but a component, background, storage, notification, or auth feature is incomplete |
| **S4 — Diagnostic-only** | Mirro can classify the failure and collect evidence but should not claim compatibility |
| **S5 — Unsupported by design** | Requires a genuine OS identity, protected environment, privileged permission, or target opt-out |

## App taxonomy

| Type | Typical app shape | Required Mirro layers | Current expectation | Main hard limit | Representative test |
|---|---|---|---|---|---|
| A | Standard APK, ordinary resources, activities and local storage | static/split code loading, `VirtualContext`, package/component facade, Activity host, storage | **S1/S3** depending on permissions/components | host UID/AppOps, incomplete providers and background components | a small open-source notes/settings app |
| B | WebView, Custom Tabs, browser OAuth | WebView process lease/suffix, browser router, callback registry, origin policy | **S2/S3** | browser cookies/origin and Credential Manager are not clone-owned | Chrome Custom Tabs OAuth sample; WebView cookie sample |
| C | Google Play services SDKs | per-API GMS bridge, host capability policy, account/token routing | **S2/S3/S5** by API | package/cert/UID checks and GMS caller validation | Google Sign-In, Maps, Fused Location samples |
| D | Runtime DEX, custom loaders, native code, packing | loader graph, dynamic code registration, native ABI/process diagnostics | **S4 now; S1/S3 for transparent loaders later** | in-memory/packed/anti-tamper code and process identity | fixture app with `DexClassLoader`, `InMemoryDexClassLoader`, JNI |
| E | Play Feature Delivery / split code | installed split inventory, feature state, late loader graph | **S3** for installed splits; **S4** for on-demand | target delivery session and late code ownership | dynamic-feature sample with on-demand module |
| F | Long-running/foreground/background services | service registry, declared stubs, FGS adapter, process restore | **S3/S4** now | host FGS permissions, background-start rules, process death | media playback / foreground service sample |
| G | FCM messaging and push wakeup | host FCM service, token/session mapping, receiver/scheduler restore | **S3/S4** | token/app-installation identity and GMS caller | Firebase Messaging quickstart |
| H | Keystore keys, key attestation, app-bound credentials | host-owned or explicit target capability result | **S2 for host keys; S5 for target identity** | hardware-rooted package/signing facts | Android Keystore attestation sample |
| I | Play Integrity / licensing / anti-abuse | no bypass; diagnostic gate | **S5 as target identity** | verdict recognizes real package/cert/install state | Play Integrity sample in a test project |
| J | Banking, DRM, secure payment, packed anti-tamper | explicit opt-in only if vendor permits; otherwise reject | **S5** | security policy deliberately detects containers or protected APIs | test banking/DRM app only with permission and no bypass |

## Capability matrix by subsystem

| Capability | A | B | C | D | E | F | G | H | I | J |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Static APK/base DEX | S1 | S1 | S1 | S1 | S1 | S1 | S1 | S1 | S1 | S1 |
| Installed split DEX | S1 | S1 | S1 | S3 | S3 | S3 | S3 | S3 | S3 | S4 |
| Late `DexClassLoader` | S3 | S3 | S3 | S4 now / S2 later | S3 | S3 | S3 | S3 | S4 | S4 |
| `InMemoryDexClassLoader` | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 |
| Native `System.loadLibrary` | S2/S3 | S2/S3 | S3 | S4 now / S3 later | S3 | S3 | S3 | S4 | S4 | S5 |
| Target Application | S1 | S1 | S1 | S1 | S1 | S1 | S1 | S1 | S1 | S4/S5 |
| Target Activity UI | S1/S3 | S1/S2 | S1/S3 | S3/S4 | S3 | S3 | S3 | S3 | S4 | S5 |
| Nested Activity routing | S2/S3 | S2/S3 | S2/S3 | S3 | S3 | S3 | S3 | S3 | S4 | S5 |
| Local Service | S3 | S3 | S3 | S3 | S3 | S3 | S3 | S3 | S4 | S5 |
| Foreground Service | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S5 |
| ContentProvider/Resolver | S3 | S3 | S3 | S4 | S3 | S3 | S3 | S3 | S4 | S5 |
| BroadcastReceiver | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S5 |
| WorkManager/JobScheduler | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S5 |
| Alarm/PendingIntent | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S4 | S5 |
| Notifications | S3 | S3 | S3 | S3 | S3 | S3 | S3 | S3 | S4 | S5 |
| WebView clone data | S2 | S1/S2 | S2 | S2 | S2 | S2 | S2 | S2 | S4 | S5 |
| Browser OAuth callback | S2 | S2 | S2 | S2 | S2 | S2 | S2 | S2 | S4 | S5 |
| Native GMS | S3 | S3 | S4 | S4 | S4 | S4 | S4 | S5 | S5 | S5 |
| Credential Manager | S2 | S2 | S2 | S2 | S2 | S2 | S2 | S5 | S5 | S5 |
| Play Integrity | S5 as target | S5 | S5 | S5 | S5 | S5 | S5 | S5 | S5 | S5 |
| Hardware target attestation | S5 | S5 | S5 | S5 | S5 | S5 | S5 | S5 | S5 | S5 |

## Diagnostic test matrix

### Harness requirements

Every test record should include:

- device model, OEM, Android/API level, security patch and ABI;
- Mirro version/commit and capability flags;
- target APK version, signing certificate digest, base/split inventory and target SDK;
- clone ID, process slot, WebView suffix and process PID;
- loader graph before/after `Application.onCreate()`;
- component route, host/guest identity vector, and system-service path;
- result label, first failing API, exception type, log evidence and whether the failure is expected.

Do not use a green screen or “launch returned” as success. A test passes only when the target flow reaches its expected observable outcome and the identity/capability report matches the declared support label.

### Tier 0 — deterministic Mirro fixtures

| ID | Fixture | Exercises | Expected first milestone |
|---|---|---|---|
| F0-01 | Plain APK Activity/Application | root loader, Application, Activity, resources | S1 |
| F0-02 | Clone storage fixture | files, cache, DB, SharedPreferences, no-backup, code-cache | S1 for public Context paths; flag direct-path limits |
| F0-03 | Nested target Activity fixture | explicit/implicit target intents, result, new intent, back stack | S2/S3 |
| F0-04 | `DexClassLoader` fixture | packaged secondary DEX, child loader registration | `REGISTERED_DYNAMIC` or `DYNAMIC_LOADER_UNSEEN` |
| F0-05 | nested `PathClassLoader` fixture | parent-first/delegate-last ownership | correct defining loader in trace |
| F0-06 | `InMemoryDexClassLoader` fixture | no filesystem code source | metadata/unsupported classification without false success |
| F0-07 | installed split fixture | class/resource from executable split | split node and resolution evidence |
| F0-08 | late feature-state fixture | module appears after Application phase | `FEATURE_PENDING` or registered late loader |
| F0-09 | NDK/JNI fixture | `System.loadLibrary`, ABI, JNI class lookup, native path | load result and host identity evidence |
| F0-10 | provider fixture | target provider authority, query/insert, URI grant | current failure is explicit; future routing target |
| F0-11 | service fixture | start/bind/stop, restart and process death | local lifecycle result; no fake OS persistence |
| F0-12 | receiver/job/alarm fixture | scheduled callback and restore | current unsupported result; future scheduler target |
| F0-13 | WebView fixture | suffix, cookies, local storage, process reuse | one clone/process invariant |
| F0-14 | browser callback fixture | Custom Tab/OAuth registry and wrong-clone rejection | callback routed only to matching session |
| F0-15 | identity fixture | package, UID, PID, `getOpPackageName`, AppOps, Binder | host/guest vector recorded; no spoof claim |
| F0-16 | packed/guarded fixture | intentionally unavailable payload | `PACKED_UNSUPPORTED` with no bypass |

### Tier 1 — open-source/sample apps

| ID | App/sample class | What it covers | Expected use |
|---|---|---|---|
| T1-01 | F-Droid/simple notes app | ordinary UI, Room/SQLite, preferences | baseline Type A |
| T1-02 | Android WebView sample | cookies, local storage, JS bridge, WebView process setup | Type B |
| T1-03 | Custom Tabs OAuth sample | external browser, redirect and state | Type B |
| T1-04 | Credential Manager sample | caller metadata, provider selection, passkey/credential flow | Type B/H boundary |
| T1-05 | Google Sign-In sample | OAuth package/certificate configuration | Type C |
| T1-06 | Maps sample | GMS initialization, API key and package restrictions | Type C |
| T1-07 | Fused Location sample | runtime permission, AppOps, GMS location | Type C |
| T1-08 | Firebase Auth sample | browser/native auth variants | Type C |
| T1-09 | Firebase Messaging quickstart | token, receiver, process wakeup, notification | Type G |
| T1-10 | WorkManager sample | persistent worker, constraints, rescheduling | Type F |
| T1-11 | JobScheduler/Alarm sample | declared service, exact/inexact alarms, PendingIntent | Type F |
| T1-12 | dynamic-feature sample | install-on-demand and late code/resource loading | Type E |
| T1-13 | NDK sample | native library/ABI/JNI | Type D |
| T1-14 | FileProvider/SAF sample | URI authority and persisted grants | Type A/P2 |
| T1-15 | notification/action sample | channels, actions, immutable pending intents | Type F |
| T1-16 | app-widget/shortcut sample | launcher-owned components and IDs | Type F |
| T1-17 | Keystore attestation sample | hardware-backed key and package facts | Type H |
| T1-18 | Play Integrity sample | app/device/account verdict | Type I |

### Tier 2 — named product probes

These are diagnostic probes, not compatibility commitments.

| App | Primary probes | Expected investigation |
|---|---|---|
| ChatGPT | Credential Manager, browser pre-auth, Custom Tab callback, WebView/browser state, GMS caller | distinguish pre-callback caller mismatch from redirect routing |
| Facebook | late Activity class, dynamic/native loading, split inventory, login/browser flow | identify loader graph and component-resolution failure; no app-specific hack |
| Instagram | native code, GMS, browser/deep links, media | Type C/D/B boundaries |
| Telegram | native code, notifications, background service, storage | Type D/F |
| WhatsApp | native/anti-tamper, notifications, backup/account, deep links | likely Type D/J; do not bypass protections |
| Discord | WebView/browser, notifications, FCM, native/media | Type B/G/D |
| Reddit | browser auth, notifications, WebView/links | Type B/G |
| X | browser auth, notifications, links, GMS variation | Type B/G |
| Spotify | native/media, foreground service, notifications, account | Type D/F/C |
| Chrome or Firefox | browser process/model, external identity, storage | usually a boundary probe, not a normal clone target |

For commercial apps, use only user-owned/test accounts, respect terms and security controls, and stop when the app intentionally rejects container execution.

## Expected result taxonomy

| Result code | Meaning | User-facing interpretation |
|---|---|---|
| `STATIC_SUPPORTED` | all required code/components in known paths | supported for tested flow |
| `REGISTERED_DYNAMIC` | late loader observed and registered | supported with dynamic-code capability |
| `DYNAMIC_LOADER_UNSEEN` | class likely exists but no supported observation boundary | compatibility unavailable; collect evidence |
| `FEATURE_NOT_INSTALLED` | component belongs to unavailable on-demand module | install/feature capability missing |
| `NATIVE_LOAD_FAILED` | ABI/path/linker/JNI failure | native compatibility unavailable for this target/device |
| `SYSTEM_IDENTITY_BLOCKED` | GMS/Credential/permission/AppOps requires real caller identity | host-mediated or unsupported |
| `COMPONENT_NOT_DECLARED` | target expects a system-visible component not mapped to host stubs | component fabric gap |
| `PROCESS_SLOT_UNAVAILABLE` | no safe slot for WebView/native/process semantics | user must close/restart or clone concurrency is limited |
| `BACKGROUND_DEFERRED` | Android delayed/rejected work due policy/Doze/permission | not a Mirro bug unless mapping is wrong |
| `SECURE_ENV_OPT_OUT` | target declares `REQUIRE_SECURE_ENV` | reject before execution |
| `PACKED_UNSUPPORTED` | code deliberately hidden/guarded | no anti-tamper bypass |

## Sources used by the matrix

- [DexClassLoader](https://developer.android.com/reference/dalvik/system/DexClassLoader)
- [InMemoryDexClassLoader](https://developer.android.com/reference/dalvik/system/InMemoryDexClassLoader)
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)
- [WebView process configuration](https://developer.android.com/reference/androidx/webkit/ProcessGlobalConfig)
- [Android app sandbox](https://source.android.com/docs/security/app-sandbox)
- [Context/AppOps attribution](https://developer.android.com/reference/android/content/Context)
- [Credential Manager](https://developer.android.com/reference/android/credentials/CredentialManager)
- [CallingAppInfo](https://developer.android.com/reference/android/service/credentials/CallingAppInfo)
- [FCM receive messages](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- [WorkManager](https://developer.android.com/reference/androidx/work/WorkManager)
- [Foreground services](https://developer.android.com/develop/background-work/services/fgs)
- [Keystore attestation](https://developer.android.com/privacy-and-security/security-key-attestation)
- [Play Integrity](https://developer.android.com/google/play/integrity/verdicts)
- [On-device Android containers](https://support.google.com/googleplay/android-developer/answer/13609005?hl=en)
