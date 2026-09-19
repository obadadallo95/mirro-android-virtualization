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

Mirro strictly decouples user interface components from cloning runtime engines and package discovery:

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
│   ├── analyzer            # CompatibilityAnalyzer (Evidence-based PackageInfo evaluation)
│   ├── engine              # CloneEngine interface, EngineAvailability, EngineExecutionResult
│   │   ├── blueprint       # BlueprintCloneEngine (Milestone 1 foundation engine)
│   │   ├── workprofile     # WorkProfileCloneEngine (Android Enterprise DevicePolicyManager)
│   │   └── container       # ContainerCloneEngine (User-space virtual container)
│   └── model               # InstalledApp, CloneInstance, CloneConfig, CompatibilityStatus, StorageMetrics
└── ui
    ├── components          # AppIconWithBadge, CompatibilityChip, SearchField, HonestEngineNotice
    ├── details             # CloneDetailsScreen & ViewModel
    ├── home                # HomeScreen & ViewModel (Grid/List, live filters, stats)
    ├── navigation          # Type-safe Screen routes
    ├── picker              # AppPickerScreen & ViewModel (discovery, filtering)
    ├── settings            # SettingsScreen & ArchitectureDocsScreen
    ├── setup               # CloneSetupScreen & ViewModel (live badging preview, engine selection)
    └── theme               # Material 3 Dynamic Color, Light/Dark palettes, Typography
```

---

## ⚙️ Isolation Engine Strategies

### 1. Strategy A: Android Work Profile (`WorkProfileCloneEngine`)
- **Mechanism**: Utilizes Android Enterprise APIs (`DevicePolicyManager`, `LauncherApps`) to create an OS-level managed work profile.
- **Benefits**:
  - 100% genuine OS-level hardware cryptographic isolation.
  - Native Google Play Services and push notification compatibility.
  - Zero performance overhead (runs native ART process without translation).
- **Constraints**:
  - Requires Profile Owner provisioning.
  - Device manufacturer limit of 1 active work profile on standard consumer ROMs.

### 2. Strategy B: Virtualized Container (`ContainerCloneEngine`)
- **Mechanism**: User-space virtual container that loads host APK DEX code via custom `DexClassLoader`, intercepts system service IPCs using dynamic `Binder` proxies for `ActivityManager` and `PackageManager`, and redirects path access to Mirro's private app directory (`/data/user/0/app.mirro.android/clones/<id>/`).
- **Benefits**:
  - Unlimited concurrent clone instances.
  - Does not require device administrator privileges.
- **Constraints**:
  - Strict Android 14+ private data directory hardening (`Context.createPackageContext` restrictions).
  - Dynamic code loading security validations.

### 3. Architecture Blueprint Staging (`BlueprintCloneEngine`)
- Foundation engine active in Milestone 1.
- Persists clone profiles, custom names, color badges, symbols, and launcher shortcuts in local Room SQLite storage.
- Real-time compatibility analysis against host `PackageManager`.
- **Honest Engineering Guarantee**: Does NOT fake process virtualization. When launch is requested in staging, it launches the base host application while explicitly informing the user that isolated sandboxing is pending implementation.

---

## 🔍 Compatibility System

Mirro never claims universal compatibility based on static metadata alone. Applications are analyzed directly against their declared package attributes:

| Status | Definition | Example Scenarios |
| :--- | :--- | :--- |
| **SUPPORTED** | Confirmed compatible via runtime isolation execution. | Verified applications following live engine tests. |
| **LIMITED** | Identified external push notifications (FCM) or hardware keystore dependencies. | WhatsApp, Telegram, Signal (Keystore bound to single master key). |
| **PROTECTED** | System apps or shared Linux UIDs that cannot be isolated. | Carrier apps, Settings, Device Admin apps (`android:sharedUserId`). |
| **UNKNOWN** | Standard applications requiring runtime validation. | ChatGPT, Note apps, Browsers, and newly discovered packages. |

---

## 📊 Storage Metrics Policy

- Application APK size is measured directly from the filesystem (`ApplicationInfo.sourceDir`).
- Isolated sandbox data and cache sizes are returned as `null` (Unavailable) or marked as estimated when isolated execution has not yet run.
- Fabricated numbers (e.g. 42MB, 12MB) are strictly prohibited.

---

## 🌐 Internationalization & RTL

- Full native localization for **English** (`values/strings.xml`) and **Arabic** (`values-ar/strings.xml`).
- Arabic includes correct Right-to-Left (RTL) mirroring across all screens and components.
