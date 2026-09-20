# Real Profile Architecture Proof

**Date:** 2026-09-20
**Device:** Samsung SM-S928B, Android 16 / API 36, `arm64-v8a`
**Device ID:** omitted from the public report
**Scope:** feasibility proof only; no production code, build configuration, dependency, root, bootloader, hook, signature, UID, or Play Integrity changes.

## Decision

**Result: `GO_WITH_UX_LIMITATIONS` for the real-profile execution boundary.**

The device can run the same APK as a real Android package in both the personal profile and Samsung’s existing clone profile. Android owns the package manager, UID, process, task, storage, native loading and system-service boundary in both profiles. This directly removes the current Mirro host-process limitations.

It is **not yet a drop-in Mirro feature**. Mirro is an ordinary application with no cross-user, profile-owner, device-owner or user-management permission. The proof used ADB’s shell authority to enable and launch packages in Samsung’s already-created `DUAL_APP` profile. A consumer Mirro build cannot silently create or administer that boundary with its current manifest.

The outcome is therefore:

- **Architecture:** viable on this Samsung device through an OEM-managed real profile.
- **Native/runtime compatibility:** materially improved; Discord reached its native UI without the prior Mirro `libkv_storage.so` failure.
- **Identity:** materially improved; ChatGPT ran under a profile-derived UID rather than Mirro’s host UID.
- **ChatGPT authentication:** GMS account discovery progressed in the real profile; full account login was not completed because this would require user account setup/credentials.
- **Product UX:** profile-visible and OEM-dependent, not a seamless in-process clone.
- **Mirro-managed consumer feature:** not proven with the current app authority model.

## 1. Device capability findings

Read-only device inspection reported:

| Capability | Finding |
|---|---|
| Manufacturer/model | Samsung SM-S928B |
| Android/API | Android 16, API 36 |
| ABI | `arm64-v8a` |
| Security patch | 2026-08-05 |
| Managed profiles | `android.software.managed_users` present |
| Existing users | User 0 personal; user 95 `profile.CLONE`; user 150 `profile.PRIVATE` Secure Folder |
| User 95 | Samsung `DUAL_APP`, parent user 0, initialized, running and visible |
| User 150 | Samsung Secure Folder, profile owner `com.samsung.knox.securefolder` |
| Background profile visibility | Not supported by the device’s effective user policy (`false`) |
| Shizuku | Not installed for user 0 or user 95 |
| Mirro permissions | Internet/network, notifications and launcher shortcut only; no cross-user/profile/device-policy authority |

The user listing is the important evidence:

```text
User 0:   type=full.SYSTEM, flags=ADMIN|FULL|INITIALIZED|MAIN|PRIMARY|SYSTEM
User 95:  type=profile.CLONE, flags=DUALAPP_PROFILE|INITIALIZED|PROFILE, parentId=0
User 150:  type=profile.PRIVATE, flags=INITIALIZED|PROFILE|SECURE_FOLDER, parentId=0
```

This is an OEM-specific Samsung clone profile, not a generic Mirro-created work profile. The device has the platform managed-profile feature, but its visible clone mechanism is Samsung’s privileged implementation.

## 2. Work/managed-profile feasibility

### Generic managed profile

Android supports managed profiles as separate profile users with separate app data and profile-derived UIDs. AOSP documents the profile UID form as `100000 * userid + appid` and describes separate profile storage. [AOSP managed profiles](https://source.android.google.cn/docs/devices/admin/managed-profiles?hl=en)

Mirro cannot create a generic managed profile directly with its current authority. A normal app would need a user-mediated Device Policy Controller provisioning flow, and the resulting profile would be managed by that DPC/profile owner. The proof device already has a profile, but it is owned by Samsung’s clone/Secure Folder machinery rather than Mirro.

| Question | Result |
|---|---|
| Can Mirro initiate setup directly? | **No** with the current ordinary-app permissions. |
| Is DPC/profile-owner enrollment involved? | **Yes** for a generic managed-profile product; the OEM clone profile is already provisioned by Samsung. |
| Is user interaction required? | **Yes** for provisioning, consent, lock/profile setup and often account setup. |
| Can an existing APK be enabled? | **Yes** through shell/OEM authority; not demonstrated through Mirro’s current app authority. |
| Can Mirro launch it? | **Not currently.** Shell can launch with `--user 95`; Mirro lacks cross-user authority. |
| Can Mirro detect/manage/reset it? | It can detect normal app-visible state only within its authority; profile administration/reset requires system/OEM/DPC authority. |
| Can the same package exist independently? | **Yes**, demonstrated for ChatGPT and Discord in user 0 and user 95. |

### Samsung clone profile

Samsung’s `profile.CLONE` is the strongest result for the target product on this device. It provides the execution boundary without root or cloud infrastructure, but it is not a portable public API that Mirro can assume across OEMs. Samsung owns the provisioning UI, profile lifecycle, launcher integration, badge behavior and cross-profile restrictions.

## 3. Secondary-user feasibility

The device exposes a system user manager and has a primary user, but the available user command surface does not expose ordinary-app creation. No new full secondary user was created because doing so would change device state beyond the minimal proof and is not necessary: Samsung’s existing clone profile already tests the relevant package/UID/process boundary.

For a generic secondary user, the expected authority is system/shell/device-management level. A normal Play-distributed app cannot silently create arbitrary users or install packages into them. ADB or root can administer this on supported devices; OEM policy can remove or restrict it.

**Conclusion:** technically strong when Android/OEM exposes the user, but not a portable Mirro-controlled consumer flow.

## 4. Shizuku feasibility

Shizuku was not installed on the device and was not integrated. Its possible value is administrative, not identity virtualization. Shizuku exposes a user-authorized Binder service obtained through ADB or root; it does not make target code execute with a different kernel UID or signing identity. [Shizuku introduction](https://shizuku.rikka.app/introduction/)

| Operation | Ordinary Mirro app | Shizuku/ADB-style authority | System/root/OEM boundary |
|---|---|---|---|
| Enumerate public users/profiles | Limited and policy-dependent | Usually possible where the service exposes the query | Full |
| Create generic user/profile | No | Device/Android-version dependent; shell may be allowed, but not guaranteed | Reliable system/OEM authority |
| Provision a managed profile | No | Not equivalent to DPC provisioning; still requires user/profile-owner flow | DPC/device-owner/OEM |
| Enable existing package for user 95 | No direct current Mirro path | Yes on this device using shell `install-existing --user` | Yes |
| Install APK bytes into another user | No | Possible with package-install authority and user confirmation/policy | Yes |
| Launch Activity as another user | No direct current Mirro path | Yes using shell `am start --user` | Yes |
| Query per-user package state | Limited | Yes with shell/package authority | Yes |
| Clear/remove per-user app data | No direct current Mirro path | Possible, but destructive and user-confirmation-sensitive | Yes |
| Change target signing/UID/Binder identity | No | **No** | Kernel/platform only, and spoofing would not be legitimate |
| Produce Play Integrity or hardware attestation for a clone | No | **No** | No legitimate generic companion can manufacture it |

Shizuku could be useful in a developer/admin edition or as an optional setup helper. It would not make the current in-process container an authority-bearing Android guest.

## 5. Minimum proof performed

No production proof code was required. The smallest proof is the Android package manager and Activity manager path itself:

```text
cmd package install-existing --user 95 --full --wait com.openai.chatgpt
am start --user 95 -W -n com.openai.chatgpt/.MainActivity

cmd package install-existing --user 95 --full --wait com.discord
am start --user 95 -W -n com.discord/.main.MainDefault
```

These operations reused the already-installed APK/splits. No APK bytes were copied, repackaged, modified or re-signed. The existing Mirro container code and ChatGPT local launch path were not changed.

## 6. ChatGPT result

### Package/process proof

Before the proof:

```text
user 0:  com.openai.chatgpt uid <personal-profile-app-uid>
user 95: not installed
```

After `install-existing`:

```text
user 0:  com.openai.chatgpt uid <personal-profile-app-uid>
user 95: com.openai.chatgpt uid <clone-profile-derived-uid>
```

Android reported separate package data roots:

```text
/data/user/0/com.openai.chatgpt
/data/user/95/com.openai.chatgpt
```

The profile Activity launched as:

```text
ActivityRecord ... u95 com.openai.chatgpt/.MainActivity
ProcessRecord ... com.openai.chatgpt/u95a890
task ... A=<clone-profile-derived-uid>:com.openai.chatgpt U=95
```

The same package remained installed and separately runnable in the personal profile. This is a real profile-derived UID/process boundary, not a virtual package-name substitution.

### Authentication result

The profile instance progressed into a real profile-scoped Google authentication/account-discovery flow. The focused window was GMS’s `MinuteMaidActivity` under `u95`, and the UI displayed the profile’s account-search setup screen. No Mirro `GOOGLE_PLAY_SERVICES_CALLER_MISMATCH`, host UID substitution, or virtual Activity embedding was involved.

The proof deliberately stopped before entering credentials or completing Google account setup. Therefore:

- **Proven:** ChatGPT can launch as a real package in the clone profile and invoke GMS under that profile context.
- **Partially proven:** the known Mirro caller-identity failure is removed from the observed path.
- **Not claimed:** successful ChatGPT account login, because the profile required user account setup and no credentials were entered.

OpenAI documents that the Android app relies on a supported browser for login, with Chrome recommended. [OpenAI Android login guidance](https://help.openai.com/en/articles/8194942)

## 7. Discord result

Discord was enabled in the same Samsung clone profile:

```text
user 0:  com.discord uid <personal-profile-app-uid>
user 95: com.discord uid <clone-profile-derived-uid>
data:    /data/user/0/com.discord
         /data/user/95/com.discord
```

Android resolved and launched the real profile Activity:

```text
com.discord/.main.MainDefault
ActivityRecord ... u95 com.discord/.main.MainDefault
ProcessRecord ... com.discord/u95a376
```

The profile screenshot reached Discord’s normal Welcome screen with Register and Log In actions. The prior Mirro-container failure—`UnsatisfiedLinkError` for missing `libkv_storage.so`—did not occur in this real profile launch. The process loaded and rendered the native-backed Discord UI under Android’s normal package/process/native loader path.

This proves the profile route removes the observed native-loader boundary for this device/app version. It does not prove every Discord feature, login, notification, background, or GMS flow.

## 8. Exact UX observed

### What the device shows

- The execution target is an existing Samsung `DUAL_APP` profile, visibly distinct from user 0 at the system level.
- The proof was launched with ADB; Mirro did not provide a “Real Profile Test” button.
- Samsung’s profile is visible to Android’s user/profile machinery and has its own per-profile package state.
- The device policy reports that visible background users are not supported, so the profile is not a transparent second simultaneous Android user surface.
- ChatGPT’s login flow opened profile-scoped GMS and Chrome activities, exposing the profile’s own setup requirements.
- Discord reached a full-screen native Activity in user 95.

### What Mirro could hide

Mirro could provide a launcher that detects an already-authorized profile, identifies whether the target is enabled there, and sends the user to Android/Samsung’s profile launch path. It could display a capability explanation and return to Mirro after launch.

### What Mirro cannot hide or own

- Profile provisioning and consent screens.
- Samsung’s profile badge/launcher conventions and Work/Dual Apps settings.
- Profile pause/unpause and policy state.
- Cross-profile restrictions and account setup.
- Android-owned notifications, tasks, permissions and lifecycle.
- OEM differences: Samsung’s `profile.CLONE` is not a portable API contract.

The resulting experience is a second Android profile/clone, not a seamless Parallel Space-style window inside Mirro.

## 9. Data and session isolation

The proof verified the strongest low-level isolation signals without touching credentials:

| Isolation area | Finding |
|---|---|
| Package identity | Same package name, distinct personal/profile-derived UIDs for ChatGPT and Discord |
| Process | Separate processes such as `com.openai.chatgpt/u0a890` and `com.openai.chatgpt/u95a890` |
| App data | Separate `/data/user/0/...` and `/data/user/95/...` roots |
| Runtime permissions | Independent grants; ChatGPT personal user had grants while newly enabled profile user had separate default-denied runtime permissions |
| Account/session | Profile starts with independent app state; no account was copied or imported |
| WebView/browser | Profile-scoped Android app/browser processes were observed; complete cookie/account separation was not tested with credentials |
| APK code/update | Same installed APK/split code path is shared by users; user data and enablement are per-user. This is not two independently versioned APK copies. |
| Reset/remove | Android package manager supports per-user clear/uninstall/disable semantics; no destructive clear/remove was performed to preserve the device’s existing profile data |

This is materially stronger than Mirro’s current path redirection: Android, not Mirro, owns the storage and process boundary.

## 10. Cost, privilege and distribution

| Question | Result |
|---|---|
| Recurring infrastructure cost | **No** for the local profile route |
| Root required | **No** for the demonstrated Samsung profile |
| Computer/ADB after initial setup | **Yes for this proof path.** A consumer OEM profile may be managed through Samsung/Android UI, but Mirro has not proven direct app control. |
| Shizuku required | **No** for ADB proof; optional for administration if installed and authorized |
| Device Owner/Profile Owner required | **Yes** for generic managed-profile provisioning; not for enabling an app in Samsung’s already-created clone profile through shell authority |
| Google Play distribution | **Likely only with careful profile/container policy review; uncertain as a universal feature.** It must not promise hidden profile creation or bypass `REQUIRE_SECURE_ENV`. |
| OEM portability | **Low to medium.** The Android profile model is standard, but clone/profile creation, UX and permissions are OEM/policy-dependent. |
| Privacy | Strong OS-managed local isolation; Samsung/OEM profile services remain part of the trust boundary |

## 11. GO / NO-GO assessment

### Against the proof criteria

| Criterion | Result |
|---|---|
| Same app independently runs in personal + real profile | **Yes**, ChatGPT and Discord |
| Different profile-derived UID | **Yes**, directly observed |
| No root/cloud | **Yes**, demonstrated with existing OEM profile |
| Setup repeatable | **Yes for `install-existing`/launch on this device; not proven as ordinary-app automation** |
| Mirro can launch/manage second instance reasonably | **Not yet**; current app lacks cross-user/profile authority |
| ChatGPT login progresses beyond Mirro caller mismatch | **Partially yes**; real GMS account discovery reached, full login not tested |
| Native failure removed for second app | **Yes for Discord’s observed startup path** |
| Seamless consumer UX | **No**; Android/OEM profile UX remains visible |

### Classification

**`GO_WITH_UX_LIMITATIONS` for the architecture.** The core proof is successful: a real Android profile solves both the identity class and native-runtime class of failures without root or cloud cost.

**Not a GO for the current Mirro app as a fully self-managed profile product.** The missing piece is legitimate profile administration/launch authority and a user-understandable OEM-specific setup flow, not another in-process virtualization manager.

## 12. Recommendation for Mirro

Adopt a profile-aware hybrid boundary, in this order:

1. Keep the existing local container for apps that are already supported and for diagnostics.
2. Add a developer/experimental profile detector and launcher only after selecting an explicit authority model: OEM API, user-mediated DPC/profile-owner flow, or optional Shizuku/ADB administration.
3. Do not create a generic profile manager using hidden APIs or privileged assumptions.
4. Treat Samsung Dual Apps/Secure Folder as optional OEM adapters, not as the general Android contract.
5. Use profile mode for native, GMS/system-service and background-heavy apps where the user accepts profile UX.
6. Test ChatGPT account completion only through normal user interaction in the profile; do not import cookies, credentials or tokens.
7. Do not claim universal support until the same flow is repeated on a non-Samsung managed-profile device and a second Android/OEM version.

The proof validates the architectural pivot, but it also defines the product boundary: **Mirro can be a smart launcher/orchestrator for Android-owned clones; it cannot make a normal app silently own Android’s user/profile authority.**
