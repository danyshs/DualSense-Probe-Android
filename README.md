# DualSense-Probe-Android

A tester for the PlayStation 5 DualSense controller on Android, in the spirit of Windows' `joy.cpl`. It
shows every input live, fires every output by hand, and logs what Android's APIs actually do, so you can
see which features work on your phone and which need a custom driver.

Work in progress. Tested on a Pixel 11 Pro XL running Android 17 (build `CD1A.261005.003`).

## Known Android issue: lightbar, player LEDs and battery do nothing over USB

With the DualSense plugged in by USB, Android's own lights and battery APIs fail silently:

- `LightsManager.Session.requestLights()` returns normally, and the lightbar and player LEDs don't change.
- `InputDevice.getBatteryState()` reports capacity -0.01 (NaN-like) and status UNKNOWN.

Over Bluetooth, the same app making the same calls lights the lightbar and all five player LEDs, and
reads the battery (85%, discharging). Talking to the controller directly over USB (claiming its HID interface) lights every LED,
so the controller and its USB link are fine.

The cause is an SELinux denial. `system_server` (InputReader / EventHub) is refused access to the
controller's LED and battery files in sysfs, which on USB carry the generic `sysfs` label:

```
E EventHub: Can not write to file, error: Permission denied
E EventHub: Failed to read sysfs battery info: Permission denied
avc: denied { read } for comm="InputReader" name="max_brightness" dev="sysfs"
     scontext=u:r:system_server:s0 tcontext=u:object_r:sysfs:s0 tclass=file permissive=0 bug=b/458240870
avc: denied { read } for comm="binder:1771_1F" name="brightness" dev="sysfs"
     scontext=u:r:system_server:s0 tcontext=u:object_r:sysfs:s0 tclass=file permissive=0 bug=b/458240870
```

The denials carry the tag `bug=b/458240870`. That tag isn't specific to this problem: the phone's
`/vendor/etc/selinux/selinux_denial_metadata` stamps it on every `system_server` denial against a
plain `sysfs` file (and on several other `system_server` denials), so it reads as a catch-all, not a
report that the controller's lights are known to fail.
`dumpsys input` shows the effect: over USB every raw light reads brightness -1; over Bluetooth the same
lights read real values.

Reproduce:

1. Plug a DualSense into the phone by USB.
2. From any app, open a lights session on the controller's `InputDevice` and request a colour for the
   light named `indicator`.
3. `adb logcat -b all | grep -E "EventHub|avc.*system_server.*sysfs"` shows the errors above, and the
   lightbar stays as it was.
4. Pair the same controller over Bluetooth and repeat: the lightbar changes.

## Licence

MIT.
