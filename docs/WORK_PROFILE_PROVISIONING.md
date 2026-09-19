# Android Managed Profile (Work Profile) Provisioning Architecture

This document specifies the technical design, Android APIs, and lifecycle states for **Milestone 2A: Real Work Profile Provisioning** in Mirro (`app.mirro.android`).

---

## 1. Overview & Isolation Principle

Mirro leverages Android's native **Managed Profile / Work Profile** subsystem (`android.app.admin.DevicePolicyManager`, `android.os.UserManager`) to create an authentic hardware-isolated and kernel-isolated sandbox for duplicate application instances.

### Why Work Profiles?
- **Genuine Process & Data Isolation**: Android assigns a unique Linux User ID (e.g. `u10`, `u11`) to the managed profile. Application data directories (`/data/user/10/<pkg>/`), internal SQLite databases, and Android Keystore master keys are cryptographically separated by the Linux kernel and Android ART runtime.
- **Native Google Play Services**: Managed profiles have first-class support for Google Play Services, Push Notifications (FCM), and native WebView instances.
- **Zero Performance Degradation**: Apps inside a work profile run directly on Android's native runtime without bytecode instrumentation, emulation overhead, or hooked IPC proxies.

---

## 2. Platform APIs & Components

### 2.1 `MirroDeviceAdminReceiver`
- **Location**: `app.mirro.android.domain.engine.workprofile.MirroDeviceAdminReceiver`
- **Manifest Declaration**:
  ```xml
  <receiver
      android:name=".domain.engine.workprofile.MirroDeviceAdminReceiver"
      android:permission="android.permission.BIND_DEVICE_ADMIN"
      android:exported="true">
      <meta-data
          android:name="android.app.device_admin"
          android:resource="@xml/device_admin_mirro" />
      <intent-filter>
          <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
          <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
      </intent-filter>
  </receiver>
  ```
- **Lifecycle Callbacks**:
  - `onProfileProvisioningComplete(context, intent)`: Triggered inside the newly created managed profile once Android finishes provisioning. Mirro establishes itself as the Profile Owner:
    1. Sets profile display name via `dpm.setProfileName(adminComponent, "Mirro")`.
    2. Enables the profile via `dpm.setProfileEnabled(adminComponent)` so that apps and launchers can discover and interact with it.
    3. Records the local runtime state flag.

### 2.2 `ProfileProvisioningManager`
- **Location**: `app.mirro.android.domain.engine.workprofile.ProfileProvisioningManager`
- **Key Responsibilities**:
  1. Inspects `PackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)`.
  2. Queries `DevicePolicyManager.isProvisioningAllowed(ACTION_PROVISION_MANAGED_PROFILE)`.
  3. Evaluates `DevicePolicyManager.isProfileOwnerApp(packageName)` and `UserManager.userProfiles`.
  4. Generates the official system provisioning intent:
     ```kotlin
     Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
         putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
         putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
     }
     ```
  5. Maps activity results (`RESULT_OK`, `RESULT_CANCELED`) to typed `ProvisioningStatus`.

---

## 3. Provisioning State Machine

```
              +---------------------------+
              | Device Support Inspection |
              +---------------------------+
                            |
           +----------------+----------------+
           |                                 |
   [Not Supported]                      [Supported]
           |                                 |
           v                                 v
   +---------------+             +-----------------------+
   | NOT_SUPPORTED |             | Existing Profile Check|
   +---------------+             +-----------------------+
                                             |
                         +-------------------+-------------------+
                         |                                       |
                 [Mirro is Owner]                     [No Mirro Profile]
                         |                                       |
                         v                                       v
                  +------------+                     +----------------------+
                  |   ACTIVE   |                     | isProvisioningAllowed|
                  +------------+                     +----------------------+
                                                                 |
                                             +-------------------+-------------------+
                                             |                                       |
                                         [Allowed]                               [Blocked]
                                             |                                       |
                                             v                                       v
                                      +-------------+                         +--------------+
                                      |  AVAILABLE  |                         |   CONFLICT   |
                                      +-------------+                         +--------------+
                                             |
                                  [User Taps Setup]
                                             |
                                             v
                                      +--------------+
                                      | PROVISIONING |
                                      +--------------+
                                             |
                             +---------------+---------------+
                             |                               |
                        [RESULT_OK]                   [RESULT_CANCELED]
                             |                               |
                             v                               v
                       +------------+                 +--------------+
                       |   ACTIVE   |                 |    FAILED    |
                       +------------+                 +--------------+
```

---

## 4. Platform Constraints & Guarantees

1. **One Work Profile Constraint**:
   On standard consumer Android ROMs (Pixel, Samsung One UI, Xiaomi HyperOS, Motorola), Android enforces a hard limit of **one managed profile** per device. If a device is already enrolled in an enterprise MDM (e.g. Microsoft Intune, Google Workspace MDM), Mirro detects the conflict and reports `ProvisioningStatus.Conflict` without corrupting system policy.

2. **Zero Fake State**:
   Mirro never marks provisioning as complete unless Android's `DevicePolicyManager.isProfileOwnerApp()` or `onProfileProvisioningComplete` confirms Profile Owner authority.

3. **Separation of Concerns**:
   - **Milestone 2A**: Foundation provisioning and Profile Owner establishment.
   - **Milestone 2B**: Package installation into the managed profile (`PackageInstaller` session / intent).
   - **Milestone 2C**: Cross-profile execution, badging synchronization, and real ChatGPT multi-account testing.
