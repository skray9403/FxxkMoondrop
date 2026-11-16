# MoonEar architecture

MoonEar is a standalone Android companion focused on Moondrop Space Travel 2. It runs as a normal application and communicates with the earbuds through Android Bluetooth APIs. It does not inject into Google Play services or Android Settings and does not require root, Xposed or LSPosed.

## Runtime layout


MainActivity
├── OverviewFragment
│   ├── GaiaBleClient: GAIA V3 over BLE
│   ├── AncBridge: Space Travel 2 ANC mode mapping
│   ├── DeviceControlBridge: three-level gain mapping
│   └── BatteryStore: left/right battery telemetry
├── SettingsFragment
│   ├── ThemeSettingsActivity
│   ├── DeviceNotif
│   └── KeepAlive / HeadsetDetectService
└── AboutFragment

EqEditorActivity
└── BleSourceSwitchClient: 9ECA BLE PEQ capability/config/point reads

## Bluetooth control


- GAIA V3 BLE is used for Space Travel 2 connection state, battery telemetry, ANC and gain.
- 9ECA BLE shares the GATT session for PEQ capability and parameter reads.
- HeadsetGate finds a connected Bluetooth device using Android A2DP/HEADSET profile proxies. GaiaBleClient performs the app's own BLE scan and GATT session.
- HeadsetDetectService optionally monitors connection changes in the background. KeepAlive controls this behaviour and the user-approved battery-optimisation exemption.
- DeviceNotif, NotifActionReceiver and AncTileService expose the same confirmed ANC state in notifications and the Android Quick Settings tile.

## Space Travel 2 mappings


| Feature | UI values | Device mapping |
|---|---|---|
| ANC | Off, Noise cancellation, Transparency, Wind | SET: [1, 2, 4, 3]; GET: [0, 1, 2, 3] |
| Sound gain | Low, Medium, High | [2, 1, 0] |
| Battery | Left, Right | Charging-case level is not reported by the current GAIA path |

## PEQ safety boundary


The 9ECA client understands PEQ firmware capability, configuration and point messages. The editor reads the firmware flags, config and active points, and supports local preview edits. The Space Travel 2 PEQ commit action is not yet hardware-verified in this branch, so device-side Apply remains disabled. Do not enable it until SET-point, pre-gain, COMMIT and read-back are validated on a real Space Travel 2 unit.

The graph currently displays the raw PEQ point map. The protocol response exposes raw gain and Q values; the UI does not label them as dB or claim to calculate a calibrated acoustic response until those field scales and filter semantics are confirmed.

## UI and preferences


The app uses the existing Android View-based Material 3 / Material 3 Expressive components and theme palette. OverviewFragment, SettingsFragment, AboutFragment and ThemeSettingsActivity are the only user-facing pages. Settings are limited to theme, app language, battery/noise-control notifications and background monitoring.