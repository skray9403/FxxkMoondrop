# MoonEar Compose UI and Live Edit

The Space Travel 2 Overview is hosted in Jetpack Compose. Bluetooth discovery, GAIA GATT transport, ANC commands, gain mapping, battery telemetry and the 9ECA service detection remain in the existing Kotlin backend.

## Open the project

1. Check out `feature/compose-overview-live-edit`.
2. Open the repository root (the folder containing `settings.gradle.kts`) in Android Studio.
3. Let Gradle sync complete.
4. Connect the Pixel 8a with USB debugging enabled, or pair it through Wireless debugging.
5. Select the `app` run configuration and the Pixel 8a, then click Run once.

## Enable Live Edit

1. Open **File → Settings → Editor → Live Edit**.
2. Enable **Live Edit**.
3. Choose **Manual on Save** for a predictable Antigravity/Android Studio workflow, or **Automatic** when editing directly in Android Studio.
4. Keep the app running on the Pixel.

Live Edit applies supported changes to existing composable function bodies. It is not a no-compile mechanism: Android Studio incrementally compiles changed Kotlin code and sends the update to the running app.

## Antigravity editing

Keep Antigravity and Android Studio pointed at the same checkout. Save edits from Antigravity, then check Android Studio's Live Edit status indicator. If it does not update or shows **Out of Date**, inspect the IDE error and use **Run** again.

The Live Edit integration is IDE-driven. External file edits are not guaranteed to trigger Live Edit in every Android Studio version, so verify this on the first edit. If Antigravity's disk changes are not picked up, edit/save the changed composable in Android Studio or use a normal incremental Run.

## Live Edit limitations

- Function body changes are the reliable fast path.
- Renaming composables, changing signatures, adding/removing functions, changing class fields or hierarchy, and build configuration changes require a normal build/redeploy.
- The first edit to a file can reset that app's UI state.
- Live Edit is for debug builds and is not a substitute for a clean release build.
- Do not use Apply Changes at the same time as Live Edit; restart the app with Run if the Live Edit session becomes stale.

## Migration boundary

This first migration slice converts the Overview screen. The Activity navigation shell, drawer, Settings, About, theme settings and equalizer editor remain on their existing Android View/Fragment implementations for now. They are intentionally not mixed into the Bluetooth backend migration.
