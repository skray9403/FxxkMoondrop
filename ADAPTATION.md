# Space Travel 2 adaptation notes


This branch focuses on **Moondrop Space Travel 2**.

## Device and protocols


| Item | Verified project value |
|---|---|
| Device | Space Travel 2 / 太空漫游2 |
| SoC | Bluetrum BT8932F |
| ANC and battery | GAIA V3 over BLE |
| Parametric EQ | 9ECA BleSourceSwitch over BLE |
| Battery telemetry | Left and right earbuds; no charging-case value in the current GAIA path |

## ANC mapping


The device profile in AncProfileLib has been hardware-tested with different SET and GET mappings.

| UI mode | UI value | SET device value | GET device value |
|---|---:|---:|---:|
| Off | 0 | 1 | 0 |
| Noise cancellation | 1 | 2 | 1 |
| Transparency | 2 | 4 | 2 |
| Wind | 3 | 3 | 3 |

Do not replace this with the nominal UI order. The SET mapping is deliberately not identical to GET.

## Sound gain


The three UI levels map to device values as follows:


| UI level | Device value |
|---|---:|
| Low | 2 |
| Medium | 1 |
| High | 0 |

## 9ECA PEQ boundary


The 9ECA protocol implementation supports firmware capability reads, PEQ configuration reads, PEQ point reads and SET/commit commands. The wire representation contains the point index, frequency, signed raw gain, raw Q and filter ID. The pre-gain response is documented by the protocol layer in 0.01 dB units.

This branch does not yet contain a real-device-verified Space Travel 2 PEQ write/commit/read-back sequence. As a safety measure, the editor allows only local draft edits and keeps Apply disabled. Raw point gain and Q are shown as raw values until their units are confirmed for this model.

## Standalone app scope


The user-facing app is limited to the Space Travel 2 device page, its tested ANC and gain controls, battery and notification/background settings, theme/language, and the 9ECA PEQ reader/editor. Root, Xposed/LSPosed, GMS Fast Pair injection and Android Settings injection are not part of this branch.