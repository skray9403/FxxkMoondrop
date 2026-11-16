# MoonEar development


## Build environment


| Item | Requirement |
|---|---|
| JDK | 21 |
| Gradle wrapper | Repository-pinned wrapper |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | AGP built-in Kotlin support |
| Android SDK | compileSdk 37 |
| Minimum Android | API 32 (Android 12L) |
| Target Android | API 37 |

## Build and test


```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Release build (requires app2.keystore and its password):

```bash
./gradlew assembleRelease -PfxxkKeypass=<signing-password>
```

The release signing key is not stored in this repository. The CI workflow uses repository secrets when configured and otherwise builds a debug APK.

## Source layout


- app/: Android Gradle module, manifest, resources and JVM tests.
- src/com/fxxkmoondrop/secret/: application source, referenced by the Gradle source sets.
- docs/BACKGROUND_MONITORING.md: background-service behaviour and verification.
- ADAPTATION.md: Space Travel 2 protocol notes and tested device mappings.

## Engineering boundaries


- Keep device-specific mappings in AncProfileLib; do not infer device command values from UI order.
- Preserve the shared GATT operation queue when adding 9ECA or GAIA commands.
- Read the current device state after writes whenever the protocol provides a response.
- Keep PEQ Apply disabled until the Space Travel 2 write/commit/read-back sequence is hardware-verified.
- Do not add privileged shell commands, root checks, Xposed/LSPosed hooks or cross-process GMS/Settings injection to this app.