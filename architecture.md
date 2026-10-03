# RootFix: Autonomous Magisk & Root Management Platform

## 1. Project Vision & Purpose
**RootFix** is an open-source, automated Android root utility app designed to simplify root management for everyday users and enthusiasts. Instead of requiring manual terminal commands, flashing zips in recovery, or manually tracking and pasting device fingerprints into config files, RootFix runs autonomously with superuser privileges (`su`) to:
1. **Manage Magisk Modules**: Discover, inspect, install, enable, disable, and clean up Magisk modules via standard Magisk CLI interfaces.
2. **Autonomous Play Integrity Fix (PIF) Management**: Automatically fetch, validate, and hot-update device fingerprint profiles (`pif.prop` / `pif.json`) to keep Play Integrity attestation passing without manual intervention.
3. **Background Sync & Automation**: Run non-intrusive periodic background workers (`WorkManager`) and IPC Root Services (`libsu`) to verify module status and apply configuration updates smoothly.
4. **Safety & Bootloop Guard**: Validate module payloads, verify JSON/prop schemas before applying, maintain backup copies of working configs, and avoid destructive reboot cycles.

---

## 2. Target Environment & Hardware Baseline
The initial development and target validation environment is based on:
- **Device**: Samsung Galaxy J7 Duo (`SM-J720F` / `j7duolte`)
- **OS**: Android 10 (Q) — API Level 29 (Build `samsung/j7duoltedd/j7duolte:10/QP1A.190711.020/J720FDDS7CUL1`)
- **SELinux**: Enforcing
- **Root Provider**: Magisk v30.7 (`30.7:MAGISK:R`), Superuser binary at `/sbin/su`
- **Active Modules**:
  - `playintegrityfix` (`v4.7-1-inject-s`)
  - `zygisk-detach` (`v1.24.0`)
  - `zygisk_shamiko` (`v1.2.5`)
- **Active Configuration Path**: `/data/adb/pif.prop` (also supports `/data/adb/pif.json` and module-specific configs)

---

## 3. Technology Stack & Tooling
- **Platform**: Android Native (Minimum SDK: 26 (Android 8.0), Target SDK: 34 (Android 14))
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose + Material 3
- **Root Engine**: `com.github.topjohnwu.libsu:core` + `service` (official Magisk-compatible root IPC and shell library)
- **Background Scheduling**: `androidx.work:work-runtime-ktx` (WorkManager)
- **Networking & Serialization**: `io.ktor` or `okhttp3` + `kotlinx.serialization` for configuration retrieval and JSON/schema validation
- **Build System**: Gradle 8.x with Kotlin DSL (`build.gradle.kts`)
- **Java Runtime**: JDK 17 (OpenJDK 17.0.20.1)

---

## 4. System Architecture

```
+---------------------------------------------------------------------------------+
|                                 RootFix App                                     |
+---------------------------------------------------------------------------------+
| [Jetpack Compose UI]                                                           |
|  - Dashboard: Root status, Magisk version, Active PIF profile, Integrity state  |
|  - Module Manager: List installed modules, Toggle switch, Install ZIP           |
|  - PIF Manager: Current fingerprint view, manual refresh, auto-update toggle    |
|  - Settings: Update interval, Kill-GMS switch, Bootloop protection rules        |
+---------------------------------------------------------------------------------+
                                      |
| [Domain & Repository Layer]                                                     |
|  - MagiskRepository: Query /data/adb/modules, exec `magisk --install-module`   |
|  - PifRepository: Parse and write /data/adb/pif.prop & pif.json, reload GMS    |
|  - SyncManager: Schedules periodic sync via WorkManager                        |
+---------------------------------------------------------------------------------+
                                      |
| [Root Execution Layer - libsu]                                                  |
|  - RootSession / RootService: Persistent AIDL/Binder IPC with UID 0             |
|  - Command Pipeline: Atomic file operations (cp temp -> chmod -> mv), restorecon|
+---------------------------------------------------------------------------------+
                                      | UID 0 (root shell)
+---------------------------------------------------------------------------------+
|                                Android OS                                       |
|  - /data/adb/modules/          - /data/adb/pif.prop / pif.json                  |
|  - /data/adb/magisk.db         - GMS Unstable process (com.google.android.gms)  |
+---------------------------------------------------------------------------------+
```

---

## 5. Core Subsystems

### 5.1 Root Management Abstraction
Direct `Runtime.getRuntime().exec("su")` is prone to deadlocks and process leaks. RootFix utilizes `libsu`:
- Shell lifecycle managed by `com.topjohnwu.superuser.Shell`.
- Pre-checks `Shell.isAppGrantedRoot()` before running privileged tasks.
- Uses `Shell.cmd(...)` for synchronous batch operations.
- Uses `RootService` for background tasks requiring isolated root IPC.

### 5.2 Magisk Modules Engine
Interacts with the local Magisk installation:
- **Querying**: Reads `/data/adb/modules/*/module.prop` to parse `id`, `name`, `version`, `versionCode`, `author`, `description`, `updateJson`.
- **Status check**: Checks for the existence of `disable` or `remove` marker files inside `/data/adb/modules/<id>/`.
- **State toggle**:
  - Disable: `touch /data/adb/modules/<id>/disable`
  - Enable: `rm -f /data/adb/modules/<id>/disable`
  - Uninstall: `touch /data/adb/modules/<id>/remove`
- **Installation**: Executes `magisk --install-module "<path_to_zip>"` and streams installation output.

### 5.3 Autonomous PIF (Play Integrity Fix) Engine & AutoPIF Integration
Play Integrity attestation requires valid device fingerprints. When fingerprints are revoked, devices fail device integrity.
The PIF Engine:
- **Reads Active Profile**: Supports `/data/adb/pif.prop` (Key-Value format) with automatic fallback and schema parsing.
- **Enforces All 7 Spoof Flags**: Every profile saved or fetched is strictly required to enable all 7 spoofs:
  1. `spoofBuild=true` (spoof android.os.Build fields)
  2. `spoofProps=true` (spoof system properties)
  3. `spoofProvider=true` (spoof GMS service provider)
  4. `spoofSignature=true` (spoof build signature)
  5. `spoofVendingBuild=true` (spoof Play Store vending build)
  6. `spoofVendingSdk=true` (spoof Play Store vending SDK)
  7. `DEBUG=true` (enable debug telemetry for PIF module)
- **AutoPIF Canary Engine**: Directly integrates `/data/adb/modules/playintegrityfix/autopif.sh`:
  - Dynamically discovers all Pixel Canary devices via `autopif.sh --list` (`Pixel 6a` up to `Pixel 11 Pro Fold`).
  - Fetches latest Canary release candidates from Google FlashStation (`https://content-flashstation-pa.googleapis.com/v1/builds?product=$PRODUCT&key=$FLASH_KEY`).
  - Extracts Canary build ID, release candidate name, and Pixel security bulletin date.
  - Automatically updates `/data/adb/pif.prop` and enforces all 7 required spoof flags.
- **Atomic Replacement**:
  1. Writes to temporary file: `/data/adb/pif.prop.tmp`
  2. Creates backup copy: `/data/adb/pif.prop.bak`
  3. Sets permission: `chmod 644 /data/adb/pif.prop.tmp`
  4. Atomically replaces: `mv -f /data/adb/pif.prop.tmp /data/adb/pif.prop`
  5. Restores context: `restorecon /data/adb/pif.prop`
- **GMS Refresh**: Terminates `com.google.android.gms.unstable` and `com.android.vending` so Google Play Services reloads the new fingerprint without requiring a device reboot:
  ```bash
  pkill -9 -f com.google.android.gms.unstable 2>/dev/null
  pkill -9 -f com.android.vending 2>/dev/null
  am force-stop com.google.android.gms 2>/dev/null
  ```

### 5.4 Background Scheduling (`WorkManager`)
- Schedules periodic checks (e.g., every 6, 12, or 24 hours).
- Only triggers when network is connected and battery is not low.
- In case of fingerprint expiration or user request, performs the fetch -> validate -> atomic write -> GMS restart sequence autonomously.

### 5.5 Google Services & Attestation Reset Subsystem
Managed by `GoogleServicesRepository`:
- **Target Components**:
  - `com.google.android.gms` (Google Play Services)
  - `com.android.vending` (Google Play Store)
  - `com.google.android.gsf` (Google Services Framework)
- **Safe Cache Wipe**: Clears temporary runtime caches (`/data/data/<pkg>/cache/*`, `/data/data/<pkg>/code_cache/*`, and `/data/user_de/0/<pkg>/cache/*`) and kills the unstable attestation process (`com.google.android.gms.unstable`) without touching user accounts or contactless cards.
- **Deep Data Wipe**: Issues root-level `pm clear <package>` on selected packages to completely purge corrupt or banned attestation states and force fresh token generation.
- **Safety & Warning Safeguards**:
  - Prominent amber warning banner displayed directly in UI.
  - Interactive chip selection for granular targeting.
  - Two-step confirmation modal with alert sign before deep data wipes.

---

## 6. Project Implementation Structure
```
root-fix/
├── architecture.md               # System architectural documentation (this file)
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Gradle project settings
├── gradle/
│   └── wrapper/                  # Gradle wrapper (Gradle 9.7.1 / JDK 17)
├── app/
│   ├── build.gradle.kts          # App dependencies: Compose, Material3, libsu 5.2.2, WorkManager, OkHttp
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/rootfix/app/
│           │   ├── RootFixApp.kt               # App entrypoint, libsu initialization
│           │   ├── data/
│           │   │   ├── model/
│           │   │   │   ├── RootStatus.kt       # Device root and Magisk version info
│           │   │   │   ├── MagiskModule.kt     # Magisk module metadata & toggle states
│           │   │   │   └── PifProfile.kt       # PIF / Fingerprint configuration model
│           │   │   └── repository/
│           │   │       ├── RootExecutor.kt     # Core libsu runner & atomic file I/O
│           │   │       ├── MagiskRepository.kt # Module inspection & management
│           │   │       └── PifRepository.kt    # PIF reader, writer, remote fetcher, GMS reloader
│           │   ├── service/
│           │   │   └── PifSyncWorker.kt        # WorkManager autonomous background worker
│           │   └── ui/
│           │       ├── MainActivity.kt         # Jetpack Compose navigation host
│           │       ├── dashboard/              # Status overview & fast action controls
│           │       ├── modules/                # Module listing & toggle controls
│           │       └── pif/                    # PIF fingerprint viewer, preset selector, and editor
│           └── res/
```

---

## 7. Device Integration Verification Status
- **Test Device**: Samsung Galaxy J7 Duo (`SM-J720F` / `j7duolte`), Android 10 (API 29)
- **App UID**: `10310`
- **Magisk Root Policy**: Pre-granted in `/data/adb/magisk.db` (`policy=2`, UID 10310)
- **Magisk Version**: 30.7 (`30.7:MAGISK:R`)
- **Active Modules Verified**:
  - `playintegrityfix` (`v4.7-1-inject-s`)
  - `zygisk-detach` (`v1.24.0`)
  - `zygisk_shamiko` (`v1.2.5`)
- **Active Fingerprint Config**: `/data/adb/pif.prop`

---

## 8. Safety, Security & Bootloop Mitigation Rules
1. **Never perform destructive reboots**: The app must never automatically force reboot without user knowledge.
2. **Backup configs**: Before overwriting `/data/adb/pif.prop` or `/data/adb/pif.json`, create `/data/adb/pif.prop.bak`.
3. **Atomic writes only**: Never use direct output redirection `> /data/adb/...` which can leave a truncated file on power cut or process termination.
4. **SELinux context hygiene**: Always run `restorecon` on modified files.
5. **Payload validation**: Ensure ZIP files passed to `magisk --install-module` contain a valid `module.prop` and `META-INF/com/google/android/update-binary`.

---

## 9. Verification & Agent Developer Guide

### 9.1 On-Device Verification Results
- **Dashboard Screen**: Displays verified root privilege status (UID 0), Magisk 30.7 details, SELinux enforcing state, active PIF fingerprint, and autonomous sync state. Includes glassmorphic design (`RootFixGlassCard`).
- **Google Services Reset & Cache Manager**: Integrated on Dashboard with prominent warning banner, target package selection chips, safe cache clearance, and full data wipe protected by a confirmation modal. Verified live on device for `com.google.android.gms`, `com.android.vending`, and `com.google.android.gsf`.
- **PIF Autopilot Screen & AutoPIF Engine**:
  - Dynamically parses `autopif.sh --list` and populates the device selector (`Pixel 6a` through `Pixel 11 Pro Fold`).
  - Executes `autopif.sh` under root to fetch live Google FlashStation Canary releases.
  - Automatically enforces all 7 required spoof flags (`spoofBuild=true`, `spoofProps=true`, `spoofProvider=true`, `spoofSignature=true`, `spoofVendingBuild=true`, `spoofVendingSdk=true`, `DEBUG=true`).
  - Visual status verified: All 7 spoof badges render active (emerald green) in the UI.
  - Verified on `/data/adb/pif.prop`: Atomic write confirmed with automatic backup creation (`pif.prop.bak`) and GMS/Vending reload without reboot.
- **Magisk Modules Screen**: Accurately queries and displays all installed Magisk modules (`playintegrityfix`, `zygisk_shamiko`, `zygisk-detach`), with interactive toggle controls (`disable` marker management) and uninstall marking (`remove` marker management).

### 9.2 Instructions for Future Agents
When modifying or extending RootFix:
1. **Root Operations**: Always route root commands through `RootExecutor` rather than raw `Runtime.exec`. `RootExecutor` handles `libsu` shell lifecycle and output streaming.
2. **Configuration Writes**: Always use `RootExecutor.writeFileAtomically()`. It guarantees atomic file swapping, permission setting (`chmod 644`), and SELinux relabeling (`restorecon`).
3. **Build & Test**:
   ```bash
   # Build debug APK
   ./gradlew assembleDebug

   # Deploy to connected test device
   adb install -r app/build/outputs/apk/debug/app-debug.apk

   # Restart app
   adb shell am force-stop com.rootfix.app
   adb shell am start -n com.rootfix.app/.ui.MainActivity
   ```
4. **Tracking Changes**: Maintain architectural updates in this document whenever introducing new repositories, workers, or module handlers.

