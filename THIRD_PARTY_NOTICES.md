# Third-Party Notices

This file distinguishes software actually used by Mirro from projects cited as architecture research. Mentioning a project does not mean its code is vendored or that Mirro depends on it.

## Runtime and build dependencies

The current Gradle build resolves the following upstream ecosystems. Their licenses and notices remain with the upstream projects and should be verified against the exact resolved version before redistribution of a binary:

| Dependency family | Use | Upstream license commonly published |
|---|---|---|
| Android SDK, AndroidX, Jetpack Compose, Room, Activity and Lifecycle | Android platform, UI and runtime libraries | Apache-2.0 |
| Kotlin and Kotlin Coroutines | Language/runtime/concurrency | Apache-2.0 |
| JUnit and AndroidX test libraries | Tests | Eclipse Public License 1.0 for JUnit; Apache-2.0 for AndroidX components |
| Robolectric | JVM Android test environment | MIT |
| Gradle wrapper | Build tooling | Apache-2.0 |

The build does not currently depend on Retrofit, OkHttp, Moshi, Coil, CameraX,
Firebase, Google Identity/Credentials, Maps secrets, or Accompanist. Those
unused product-era catalog entries were removed rather than treated as runtime
dependencies.

Mirro does not vendor VirtualApp, VirtualXposed, BlackBox, DroidPlugin, Shizuku or any target APK. Those projects are research references only.

## Referenced architecture projects

- [VirtualApp](https://github.com/asLody/VirtualApp): architecture and maintenance reference. Do not treat the public tree as a blanket license for the maintained commercial branch.
- [VirtualXposed](https://github.com/android-hacker/VirtualXposed): VirtualApp/Xposed architecture reference; no code is copied here.
- [BlackBox](https://github.com/FBlackBox/BlackBox): architecture reference. Some repositories/forks publish Apache-2.0 licenses, but provenance and bundled dependencies must be audited before reuse.
- [DroidPlugin](https://droidpluginteam.github.io/DroidPlugin/): historical plugin virtualization reference; no code is copied here.
- [Shizuku](https://shizuku.rikka.app/): optional administration concept reference; not integrated.
- [Android Open Source Project](https://source.android.com/): platform documentation and source references; no AOSP source is vendored by this notice.

## Target applications and device evidence

ChatGPT, Discord, Facebook and BA-mobil are external applications used only as sanitized compatibility probes. Their code, credentials, cookies, content and APKs are not included in this repository.

## Provenance policy

Any future copied code must include its original copyright and license, be listed here, and pass a provenance review before merge. A link to a repository is not a license grant for unrelated forks or commercial branches.
