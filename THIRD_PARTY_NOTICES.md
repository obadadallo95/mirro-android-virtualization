# Third-Party Notices

This file distinguishes software actually used by Mirro from projects cited as architecture research. Mentioning a project does not mean its code is vendored or that Mirro depends on it.

## Runtime and build dependencies

The Gradle catalogs in `gradle/libs.versions.toml` reference the following upstream ecosystems. Their licenses and notices remain with the upstream projects and should be verified against the exact resolved version before redistribution of a binary:

| Dependency family | Use | Upstream license commonly published |
|---|---|---|
| Android SDK, AndroidX, Jetpack Compose, Room, Activity, Lifecycle, DataStore, CameraX | Android platform and UI/runtime libraries | Apache-2.0 |
| Kotlin and Kotlin Coroutines | Language/runtime/concurrency | Apache-2.0 |
| JUnit and AndroidX test libraries | Tests | Eclipse Public License 1.0 for JUnit; Apache-2.0 for AndroidX components |
| Robolectric | JVM Android test environment | MIT |
| Roborazzi | Screenshot testing | Apache-2.0; verify the resolved artifact notice |
| OkHttp, Retrofit, Moshi, Coil | Networking, JSON and image support | Apache-2.0 |
| Google Play services, Firebase, Google Identity/Credentials | Optional platform/service integrations referenced by the build | Google/Apache-2.0 notices vary by artifact; retain upstream notices |
| Gradle wrapper | Build tooling | Apache-2.0 |

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
