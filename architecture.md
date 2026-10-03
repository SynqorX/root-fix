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

### 5.3 Autonomous PIF (Play Integrity Fix) Engine
Play Integrity attestation requires valid device fingerprints. When fingerprints are revoked, devices fail device integrity.
The PIF Engine:
- **Reads Active Profile**: Supports both `/data/adb/pif.prop` (Key-Value format) and `/data/adb/pif.json` (JSON format).
- **Fetches Verified Profiles**: Connects to configurable, reliable remote endpoints or GitHub repositories providing community-tested fingerprints.
- **Safety Validation**: Verifies schema (`FINGERPRINT`, `MANUFACTURER`, `MODEL`, `SECURITY_PATCH`) before write.
- **Atomic Replacement**:
  1. Writes to temporary file: `/data/adb/pif.prop.tmp`
  2. Sets permission: `chmod 644 /data/adb/pif.prop.tmp`
  3. Atomically replaces: `mv -f /data/adb/pif.prop.tmp /data/adb/pif.prop`
  4. Restores context: `restorecon /data/adb/pif.prop`
- **GMS Refresh**: Kills `com.google.android.gms.unstable` so Google Play Services re-reads the updated fingerprint without requiring a full device reboot:
  ```bash
  killall -9 com.google.android.gms.unstable 2>/dev/null || pkill -f com.google.android.gms.unstable
  ```

### 5.4 Background Scheduling (`WorkManager`)
- Schedules periodic checks (e.g., every 6, 12, or 24 hours).
- Only triggers when network is connected and battery is not low.
- In case of fingerprint expiration or user request, performs the fetch -> validate -> atomic write -> GMS restart sequence autonomously.

---

## 6. Directory Structure (To be scaffolded)
```
root-fix/
├── architecture.md               # System architectural documentation (this file)
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Gradle project settings
├── gradle/
│   └── wrapper/                  # Gradle wrapper files
├── app/
│   ├── build.gradle.kts          # Application module configuration
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/rootfix/app/
│           │   ├── RootFixApp.kt               # Application entrypoint & libsu init
│           │   ├── data/
│           │   │   ├── model/                  # Data models (Module, PifProfile, etc.)
│           │   │   └── repository/             # MagiskRepo, PifRepo, RootExecutor
│           │   ├── service/
│           │   │   ├── RootIpcService.kt       # libsu RootService for background IPC
│           │   │   └── PifSyncWorker.kt        # WorkManager periodic worker
│           │   └── ui/
│           │       ├── MainActivity.kt         # Jetpack Compose host
│           │       ├── theme/                  # Material 3 theme & dynamic color
│           │       ├── dashboard/              # Status & quick action screens
│           │       ├── modules/                # Module list & management screens
│           │       └── pif/                    # PIF fingerprint inspection & update UI
│           └── res/                            # Android XML resources, icons, strings
```

---

## 7. Safety, Security & Bootloop Mitigation Rules
1. **Never perform destructive reboots**: The app must never automatically force reboot without user knowledge.
2. **Backup configs**: Before overwriting `/data/adb/pif.prop` or `/data/adb/pif.json`, create `/data/adb/pif.prop.bak`.
3. **Atomic writes only**: Never use direct output redirection `> /data/adb/...` which can leave a truncated file on power cut or process termination.
4. **SELinux context hygiene**: Always run `restorecon` on modified files.
5. **Payload validation**: Ensure ZIP files passed to `magisk --install-module` contain a valid `module.prop` and `META-INF/com/google/android/update-binary`.
