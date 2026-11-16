## MoonEar standalone Space Travel 2 refactor (unreleased)


- Reworked the main device page around Space Travel 2 battery, tested ANC modes and three-level gain.
- Replaced the old SPP-based equalizer entry with a 9ECA BLE PEQ reader and local draft preview.
- Removed the Xposed/LSPosed, root inspection, GMS popup and system Settings injection paths.
- Kept normal Bluetooth scanning, optional background monitoring, notifications and the Quick Settings ANC tile.
- PEQ device-side Apply remains locked pending Space Travel 2 hardware verification of the write/commit/read-back sequence.
## 3.4.1 (401)

> 3.4.0 的两处体验修补。

### 修复
- 悬浮导航栏收起后，左右滑动切页会主动重新显示底栏（此前切了页却看不到任何导航反馈）
- 主题设置里的「界面缩放」滑块补上标题与当前值（此前只有一句「当前 100%」，看不出是哪一项）

