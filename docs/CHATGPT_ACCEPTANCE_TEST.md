# Acceptance Test Procedure: ChatGPT Dual-Account Isolation in Mirro Space

## Objective
Validate that Mirro successfully launches and isolates a secondary instance of `com.openai.chatgpt` inside Android's native managed work profile (**Mirro Space**), ensuring independent sessions, storage, credentials, and background processes from the personal profile instance.

---

## Prerequisites
1. Physical or emulated Android device running Android 9.0+ (API 28+).
2. Primary personal profile with `com.openai.chatgpt` installed from Google Play Store.
3. Two independent OpenAI accounts (**Account A** for personal profile, **Account B** for Mirro Space).
4. Device supports Managed Profiles / Multi-User.

---

## 20-Step Manual Acceptance Test Protocol

### Phase 1: Mirro Space Provisioning
1. Launch **Mirro** on the primary personal profile.
2. In the top hero section ("Create Mirro Space"), tap **Create Mirro Space**.
3. Observe Android OS standard managed profile setup dialog displaying profile ownership terms.
4. Agree and complete the system setup wizard.
5. Return to Mirro and verify the hero card updates to **Mirro Space Active** with the **Profile Owner** badge.

### Phase 2: Dual App Discovery & Profile Sync
6. In Mirro, tap the floating action button (**+**) to open the App Selection catalog.
7. Locate **ChatGPT** (`com.openai.chatgpt`) in the list.
8. If marked with *Install Required*, tap **Enable in Mirro Space** (or open the Work Profile Google Play Store) to make the package available inside the managed profile.
9. Verify that ChatGPT status transitions to **Ready to create second instance** / **Verified in Profile**.
10. Tap on ChatGPT to open the Clone Setup screen.

### Phase 3: Clone Configuration
11. Set the custom clone name (e.g., `ChatGPT Work` or `ChatGPT (Mirro)`).
12. Customize the badge color and symbol (e.g., Purple badge with `W` or `2`).
13. Select **Mirro Space (Work Profile)** as the isolation engine.
14. Tap **Create Instance**. Verify instance registration completes and appears on the Mirro Home screen.

### Phase 4: Runtime Launch & Authentication Isolation
15. Open personal profile **ChatGPT** from the device launcher. Sign in with **Account A**. Verify active session.
16. Open **Mirro**, select `ChatGPT (Mirro)`, and tap **Launch Instance**.
17. Verify the system launches ChatGPT inside the managed profile (indicated by the Android work briefcase badge on the app icon and window header).
18. Sign in with **Account B** inside the Mirro Space ChatGPT instance.
19. Open device recent apps overview. Verify two distinct task windows exist: one for Personal ChatGPT and one for Work Profile ChatGPT.
20. Switch between both apps and send separate prompts on each. Verify chats, user credentials, settings, and local tokens remain 100% isolated and never leak across profile boundaries.

---

## Technical Verification & API Assertions

| Assertion | Expected System Behavior | Verification API |
| :--- | :--- | :--- |
| **UserHandle Resolution** | Target user handle is distinct from `Process.myUserHandle()`. | `UserManager.getUserForSerialNumber(userSerialNumber)` |
| **Launch Bridge** | Launch target triggers work profile UID without personal fallback. | `LauncherApps.startMainActivity(component, workUserHandle, null, null)` |
| **Storage Sandboxing** | Data directories exist in `/data/user/10/com.openai.chatgpt` instead of `/data/user/0`. | Linux UID separation (`u10_aXXX` vs `u0_aXXX`) |
| **Desktop Shortcuts** | Pinned shortcut resolves via `MirroLaunchTrampolineActivity` directly into the work profile. | `ShortcutManager.requestPinShortcut` |
| **Quiet Mode Handling** | When Work Profile is paused via Quick Settings, launch yields `PROFILE_PAUSED` with unpause prompt. | `UserManager.isQuietModeEnabled(targetUser)` |
| **Compatibility Evidence** | Status updates to `VERIFIED_WORK_PROFILE` only after successful runtime execution. | `CompatibilityAnalyzer.analyze(..., isRuntimeLaunchVerified = true)` |
