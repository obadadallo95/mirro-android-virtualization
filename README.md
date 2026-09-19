# Mirro

**Mirro** is a modern, privacy-first Android application cloning and multi-account platform built natively with Kotlin, Jetpack Compose, and Material 3 / Material You.

> **Tagline**: *Same apps. More possibilities.*  
> **Application ID**: `app.mirro.android`  
> **Namespace**: `app.mirro.android`

---

## 💎 Brand Identity & Visual Design System

### 1. Conceptual Foundation
The visual identity of **Mirro** is built on the convergence of three foundational ideas:
1. **The Mirror / Reflection**: An optical plane across which an entity is replicated with absolute geometric fidelity.
2. **The Letter M**: A natural typographic silhouette formed by twin grounded monoliths, peaked apexes, and descending diagonal chamfers.
3. **Duplication with Separation**: Two equal, autonomous spaces operating side by side without data entanglement or leakage.

```
       [Primary Space]              [Reflected Space]
       Royal Cobalt #1E40AF         Electric Azure #38BDF8
             |                            |
             v                            v
          +-----+                      +-----+
          |  /\ |                      | /\  |
          | /  \|                      |/  \ |
          |/    |                      |    \|
          +-----+                      +-----+
             |                            |
             +--------[ 4dp Optical ]-----+
                      [ Mirror Axis ]
                      [ Separation  ]
```

### 2. The Reflective Color Spectrum
- **Mirro Cobalt Primary (`#1E40AF`)**: Grounded foundation, represents the original host instance, stability, and trust.
- **Electric Reflection (`#38BDF8`)**: Radiant luminous cyan, represents the duplicate space, energy, and clarity.
- **Cyan Mirror Accent (`#0284C7`)**: Interactive highlights, buttons, and state indicators.
- **Obsidian Dark Canvas (`#090D16`)**: Deep, calm background providing high contrast for the reflective mark.
- **Midnight Navy Surface (`#0F172A`)**: Raised card surfaces and elevated containers.
- **Cool Slate (`#64748B`)**: Structural dividers, subtle optical frames, and secondary metadata.
- **Ice Mist Canvas (`#F8FAFC`)**: Light mode canvas, soft on eyes, crisp off-white.

### 3. Android Adaptive Launcher Icon & Splash Screen
- **Safe Zone**: Foreground mark centered strictly within the central 66dp of the 108dp canvas (`ic_launcher_foreground.xml`).
- **Background**: Multi-stop radial and linear gradient on obsidian navy (`ic_launcher_background.xml`).
- **Splash Screen**: Fully compliant with Android 12+ SplashScreen API (`ic_mirro_splash_logo.xml` and `values-v31/themes.xml`), featuring specular emblem padding to avoid circular viewport clipping.

---

## 🎯 Acceptance Criteria: Primary Real-World Use Case

**Target**: Multi-account usage for **ChatGPT** (`com.openai.chatgpt`).
1. Primary personal account operating on the host device.
2. Isolated instance operating with independent tokens, local cache, and user sessions.

### Technical Viability & Compatibility Evidence:
- `com.openai.chatgpt` utilizes a standard single-user user-space architecture (no `sharedUserId`, no custom system daemon bindings).
- Target SDK is current (Android 14+ / API 34+).
- In accordance with our core engineering principles, compatibility is categorized as **UNKNOWN / Pending Runtime Test** until a live isolated execution engine successfully validates sandbox launch on real devices.

---

## 🏛️ Clean Architecture & Package Structure

Mirro strictly decouples user interface components from local persistence and the container runtime:

```
app.mirro.android
├── data
│   ├── local
│   │   ├── dao             # CloneInstanceDao (Room Flow queries)
│   │   ├── entity          # CloneInstanceEntity (SQLite table)
│   │   └── AppDatabase     # Room database instance ("mirro_database")
│   └── repository          # InstalledAppRepository, CloneInstanceRepository,
│                           # StorageRepository, ShortcutRepository, SettingsRepository
├── domain
│   ├── analyzer            # CompatibilityAnalyzer (PackageInfo evaluation)
│   ├── engine              # CloneEngine interface, EngineAvailability, EngineExecutionResult
│   │   └── container       # ContainerCloneEngine (User-space virtual container)
│   └── model               # InstalledApp, CloneInstance, CompatibilityStatus, StorageMetrics
└── ui
    ├── components          # AppIconWithBadge, CompatibilityChip, SearchField
    ├── container           # ContainerHostActivity (Dedicated container runner)
    ├── details             # CloneDetailsScreen & ViewModel
    ├── home                # HomeScreen & ViewModel (Grid/List, live search, stats)
    ├── navigation          # Type-safe Screen routes
    ├── picker              # AppPickerScreen & ViewModel (app discovery)
    ├── settings            # SettingsScreen & ContainerDiagnosticsScreen
    ├── setup               # CloneSetupScreen & ViewModel (custom naming, badging preview)
    ├── theme               # Material 3 Dynamic Color, Light/Dark palettes, Typography
    └── trampoline          # MirroLaunchTrampolineActivity (Home-screen shortcut launcher)
```

---

## ⚙️ Runtime Engine: Mirro Container

Mirro uses an isolated user-space sandbox architecture (`ContainerCloneEngine`):

1. **APK & DEX Inspection (`ApkInspector`)**: Reads APK descriptors, split APK paths, native ABIs, and component declarations.
2. **Filesystem Partitioning (`VirtualFileSystem`)**: Generates private sandboxes under `files/virtual/<clone_id>/` for data, databases, shared_prefs, cache, and code_cache.
3. **Context Redirection (`VirtualContext`)**: Intercepts and redirects storage operations to the clone's dedicated directory.
4. **Multi-Process WebView Isolation**: Assigns unique suffixes (`WebView.setDataDirectorySuffix("mirro_<clone_id>")`) to isolate cookies and browser sessions.
5. **Shortcut Trampoline (`MirroLaunchTrampolineActivity`)**: Transparent trampoline that routes pinned home-screen shortcuts into the container runtime.
6. **Developer Mode**: Advanced diagnostic inspectors and sandbox tools are cleanly gated in Settings behind a Developer Mode toggle.

---

## 🔍 Compatibility System

Mirro never claims universal compatibility based on static metadata alone. Applications are analyzed directly against their declared package attributes:

| Status | Definition | Example Scenarios |
| :--- | :--- | :--- |
| **SUPPORTED** | Confirmed compatible with user-space isolation. | Verified applications following live engine tests. |
| **LIMITED** | Identified external push notifications (FCM) or hardware keystore dependencies. | Apps requiring strict hardware attestation. |
| **PROTECTED** | System apps or shared Linux UIDs that cannot be isolated. | Carrier apps, Settings, Device Admin apps (`android:sharedUserId`). |


---

## 📊 Storage Metrics Policy

- Application APK size is measured directly from the filesystem (`ApplicationInfo.sourceDir`).
- Isolated sandbox data and cache sizes are returned as `null` (Unavailable) or marked as estimated when isolated execution has not yet run.
- Fabricated numbers (e.g. 42MB, 12MB) are strictly prohibited.

---

## 🌐 Internationalization & RTL

- Full native localization for **English** (`values/strings.xml`) and **Arabic** (`values-ar/strings.xml`).
- Arabic includes correct Right-to-Left (RTL) mirroring across all screens and components.
