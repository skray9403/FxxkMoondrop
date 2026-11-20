# MoonEar


**MoonEar** is a standalone Android Bluetooth companion focused on **Moondrop Space Travel 2**. It uses standard Android Bluetooth APIs and does not need root, Xposed, LSPosed, or privileged system integration.

## Space Travel 2 controls


- Left and right earbud battery status through GAIA. The current device profile does not report charging-case battery.
- Active noise control: Off, Noise cancellation, Transparency and Wind, using the Space Travel 2-tested mode mapping.
- Three sound-gain levels: Low, Medium and High, using the tested device mapping.
- A 9ECA BLE parametric-EQ page that reads firmware capability, PEQ configuration and active points, with local-only preview editing.

## PEQ safety


The 9ECA client contains PEQ read and write commands, but the Space Travel 2 PEQ commit sequence has not yet been hardware-verified in this branch. The editor therefore reads the device and previews local edits, while device-side Apply remains disabled. The graph displays the raw PEQ point map; the app does not label unverified raw values as dB or claim a calibrated frequency response.

## App features


- Material 3 Expressive interface with dynamic color, light/dark theme and app-language selection.
- Optional background Bluetooth monitoring.
- Optional left/right battery and noise-control notifications.
- Android Quick Settings tile for the supported ANC modes.

## Build


```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Release builds use the signing key configured outside the repository. See DEVELOPMENT.md for the build environment and commands.

## Device mapping


See ADAPTATION.md for the tested Space Travel 2 ANC and gain mappings, Bluetooth protocols and PEQ limitations.

## License and upstream


This repository is a personal fork of bqj6666/FxxkMoondrop. The existing project license remains applicable. See LICENSE.