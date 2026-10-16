**The beta test version is only released for testing
in [TG Channel’s associated groups](https://t.me/LuckyTool)**

**中文更新日志：[更新日志](../changelog)**

Entries for versions before `1.3.5_beta` are currently only available in the
[Chinese changelog](../changelog).

### 1.3.5_beta

#### Added

- Force enable the Feiniu Cloud NAS option
- Custom system message region presets
- Disable malicious application intercept C16.1
- Disable accessibility warning dialog C16.1
- Remove Control Center carriers C16.1(400)
- Unlock GpsJoyStick Pro — unlocks the Pro features of Fake GPS Joystick (com.theappninjas.fakegpsjoystick)
- Remove Control Center settings button C16.1
- Disable audio focus
- Remove Breeno Next root detection C17 — bypasses the Root device restriction on the recruitment page
- Hide the status bar in panoramic AOD C16+ (@ShellWen) — only hides the status bar while the panoramic AOD is showing; the lock screen and normal screen are unaffected; restart System UI to take effect
- Optimize the application installation UI (@蛮吉)
- Custom notification side spacing C12+ — portrait and landscape can be configured separately
- Custom action key event interception (ringtone cycle) — adds “no-op” and “ringtone cycle” interception modes to custom action keys
- Manual scope hot reload — reload the scope process directly after changing a switch, without rebooting
- Expand the update list by default in the software store
- Miuix UI style — switch between the Material and Miuix appearances in Settings
- Cold-start animation — logo entrance and app-name fade-in
- Custom status bar icon order
- Force enable the Xiaobu keyboard liquid glass blur — ignores the system blur switch, the app whitelist, the built-in theme and the folding state; the blur strength is adjustable
- App permission page — checks the notification, all files access, app list and install app permissions, each granted by a button on its row; a cold start opens it until all four are granted (it only checks: no automatic request and no automatic jump to the system settings page), with Root status, root source, Xposed status and framework version pinned on top, and it offers no skip entry
- Custom status bar network speed refresh interval — off by default; adjustable from 0.1 s to 5 s in 0.1 s steps
- MEMC configuration Activity list supports searching and shows activity names

#### Optimized

- Clarified the descriptions of the “app clone limit” options: “How many apps can be cloned” and “The number of clones a single app can have”

#### Fixed

- Fixed “Remove app clone badge” not taking effect
- Fixed some selectors and switches not responding to taps
- Fixed the launcher crashing with sinking icons when the maximum number of columns is used C16
- Fixed crashes caused by scope optimizations C17 (@蛮吉)
- Fixed the wireless charging wattage display on the lock screen C17
- Fixed “Remove recent task list clear button” not taking effect C17
- Fixed “Remove the lock icon at the top of the lock screen” not taking effect C17
- Fixed data backup and restore for set values and legacy string formats; unwritable entries are skipped and the result now reports how many entries succeeded and failed
- Fixed donate data loading by switching to the jsDelivr mirror
- Fixed repeated permission requests and repeated jumps to the system settings page after returning from it — a cold start no longer requests permissions or jumps to the system settings page at all, the permission check only queries, and every grant starts from a button inside the page
- Fixed “Custom desktop layout row/column count” not taking effect C17

#### Changed

- “Auto app archiving” is now disabled by default; it can be enabled in the settings when needed

#### Removed

- Removed “Notification Center two-side alignment” (adapted for C17 in this version, then removed)
- Removed the secure payment scope; the related restrictions can be turned off in the system secure payment settings

#### Adapted

- Enable Wi-Fi details to display the gateway C16.1
- Remove the Control Center clock C16.1(400)
- Disable automatic switching to the last task v16.6.17(8a73764)
- Remove the countdown for adding a virus app to the Phone Manager whitelist v17.3.10(c24156e)
- Migrated to libxposed API 102
- Custom fingerprint icon replacement C16.1
- Remove the personal hotspot power consumption notification C17 — no longer shows “turning on the personal hotspot for a long time increases power consumption and heat”
- Force recent task memory usage display C17
- Remove the widget add-request whitelist C17
- Long press a recent task icon to open app details C17
- Force Docker background blur C17
- Desktop page indicator component sliding C17
- Custom launcher icon size C17
- Remove the app clone badge C17
- Notification Center two-side alignment C17
- Control Center clock seconds display C17
- Status bar battery notification C17
- Enable Opex local installation C17
- Scan an NFC tag to click the button automatically C17
- 5G switch tile C17
- Force refresh rate C17 — conflicts with other dynamic refresh rate processes such as dfps
- Wi-Fi address information shows the gateway C17
- Remove the word limit for saving and renaming files v17.9.16(064a5da)
- Lock screen clock “red 1” style C17
- Remove the Notification Center clock C17
- Remove the rotate-screen button in the lower-left corner C17
- Remove the accessibility warning dialog C17
- Unlock the background task lock limit C17
- Remove the image save watermark of the Pictorial lock screen v40.10.15.2(4e15fb0)
- Enable the Smart Sidebar to keep running in the background C17 — an older version of the Smart Sidebar may be required
- Remove the battery restriction plugin C17
