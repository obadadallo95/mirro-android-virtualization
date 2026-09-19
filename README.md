# Mirro

**Mirro** is a modern, privacy-first Android application cloning and multi-account platform built natively with Kotlin, Jetpack Compose, and Material 3 / Material You.

> **Tagline**: *One app. Two spaces.*

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

### 2. Design Iterations & Evolution
- **Direction A: The Planar Portal (Chosen Hero Direction)**:
  Two symmetrical monolithic portal facets mirrored across a vertical optical meridian. Solves the core challenge: instantly reads as the letter **M**, instantly reads as reflection/mirroring, and maintains high-contrast legibility even at 16dp and 24dp notification badge sizes.
- **Direction B: The Folded Glass Ribbon (Evaluated & Discarded)**:
  Continuous isometric loop. Rejected because the continuous connection metaphorically contradicted data isolation (implying linked accounts rather than independent sandboxes).
- **Direction C: The Sliced Letterform (Evaluated & Discarded)**:
  A single standard M cut with a vertical slit. Rejected because it evoked a broken or fractured letter rather than two thriving, whole environments.

### 3. The Reflective Color Spectrum
- **Mirro Cobalt Primary (`#1E40AF`)**: Grounded foundation, represents the original host instance, stability, and trust.
- **Electric Reflection (`#38BDF8`)**: Radiant luminous cyan, represents the duplicate space, energy, and clarity.
- **Cyan Mirror Accent (`#0284C7`)**: Interactive highlights, buttons, and state indicators.
- **Obsidian Dark Canvas (`#090D16`)**: Deep, calm background providing high contrast for the reflective mark.
- **Midnight Navy Surface (`#0F172A`)**: Raised card surfaces and elevated containers.
- **Cool Slate (`#64748B`)**: Structural dividers, subtle optical frames, and secondary metadata.
- **Ice Mist Canvas (`#F8FAFC`)**: Light mode canvas, soft on eyes, crisp off-white.

### 4. Typography Hierarchy
- **Primary Type Family**: Neo-Grotesque Sans-Serif (Android system Roboto Flex / Inter / Plus Jakarta Sans)
- **Wordmark**: 28sp / Bold / Tracking -0.02em ("Mirro" — the symmetric double 'r' reinforces the duplication metaphor)
- **Primary Tagline**: 14sp / Regular / Tracking +0.01em ("One app. Two spaces.")
- **Technical Monospace Labels**: 11sp / SemiBold / Tracking +0.05em (Engine indicators, storage metrics)

### 5. Android Adaptive Launcher Icon
- **Safe Zone**: Foreground mark centered strictly within 66dp of the 108dp canvas (`ic_launcher_foreground.xml`).
- **Background**: Multi-stop radial and linear gradient on obsidian navy (`ic_launcher_background.xml`).
- **Splash Screen**: Compliant with Android 12+ SplashScreen API, featuring centered specular emblem on obsidian canvas (`ic_mirro_splash_logo.xml`).

---

## 🎯 Product Philosophy

Unlike traditional cloning tools (e.g. Parallel Space, Dual Space), Mirro is designed with a strictly transparent and user-respecting philosophy:

- **100% Free Forever**: No paywalls, artificial clone limits, or locked features.
- **Zero Advertisements**: Clean, calm, consumer-grade Android experience without ad SDKs.
- **Zero Subscriptions**: No recurring fees or premium tiers.
- **Zero Tracking**: No telemetry, analytics, or behavioral surveillance.
- **No Mandatory Accounts**: Completely functional on-device without cloud logins.
- **Privacy-First & On-Device**: Data stays confined to the local Android sandbox.
- **Never Fake Functionality**: If low-level virtualization hooks or profile owners are under development, Mirro explicitly exposes their status rather than fabricating dummy sandboxes.

---

## 🧪 Primary Acceptance Test: ChatGPT (`com.openai.chatgpt`)

The primary target acceptance test is enabling independent accounts for the official **ChatGPT** Android application:
1. Primary personal account operating on the host device.
2. Isolated work/research instance operating with independent tokens, local cache, and user sessions.

### Technical Viability Analysis:
- `com.openai.chatgpt` utilizes a standard user-space architecture (no `sharedUserId`, no custom system daemon bindings).
- Target SDK is current (Android 14+).
- Session tokens are stored in private SharedPreferences and encrypted database storage.
- Evaluates as **SUPPORTED** under profile isolation and **SUPPORTED** under user-space container redirection.

---

## 🏛️ Clean Architecture & Package Structure

AppTwin strictly decouples user interface components from cloning runtime engines and package discovery:

```
com.example
├── data
│   ├── local
│   │   ├── dao             # CloneInstanceDao (Room Flow queries)
│   │   ├── entity          # CloneInstanceEntity (SQLite table)
│   │   └── AppDatabase     # Room database instance
│   └── repository          # InstalledAppRepository, CloneInstanceRepository,
│                           # StorageRepository, ShortcutRepository, SettingsRepository
├── domain
│   ├── analyzer            # CompatibilityAnalyzer (real Android PackageInfo evaluation)
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
- **Mechanism**: User-space virtual container that loads host APK DEX code via custom `DexClassLoader`, intercepts system service IPCs using dynamic `Binder` proxies for `ActivityManager` and `PackageManager`, and redirects path access to AppTwin's private app directory (`/data/user/0/com.aistudio.apptwin.../clones/<id>/`).
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

---

## 🔍 Compatibility System

AppTwin never claims universal compatibility. Applications are analyzed directly against their declared package attributes:

| Status | Definition | Example Scenarios |
| :--- | :--- | :--- |
| **SUPPORTED** | Standard single-user architecture, no shared UID. | ChatGPT, Note apps, Browsers, Standalone productivity apps. |
| **LIMITED** | Requires push notifications (FCM) or hardware keystore. | WhatsApp, Telegram, Signal (Keystore bound to single master key). |
| **PROTECTED** | System apps or shared Linux UIDs. | Carrier apps, Settings, Device Admin apps (`android:sharedUserId`). |
| **UNKNOWN** | Non-standard packages requiring dynamic validation. | Obfuscated or proprietary non-standard bundles. |

---

## 🌐 Internationalization & RTL

- Full native localization for **English** (`values/strings.xml`) and **Arabic** (`values-ar/strings.xml`).
- Arabic includes correct Right-to-Left (RTL) mirroring across all screens and components.
