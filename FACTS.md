# FACTS

The only source of protocol facts for this project's code. Code is written from these rows alone, by writers who haven't read any other project's source. Each row: an ID (never reused), the fact, where it comes from, and whether it was confirmed on a real pad.

*   **Clean room:** whoever reads the Linux driver (K) and the capture logs writes only this file, never code. The code is written from these rows alone.
*   **Pad:** DualSense `054c:0ce6`. **Phone:** Pixel, Android 17 build `CD1A.261005.003`, kernel `6.12.69-android16-6`.
*   **As of 2026-11-19 18:55 -0500.**

## Sources

| Key | Source |
|---|---|
| K | Linux mainline `drivers/hid/hid-playstation.c` at commit `4dd10fbbeb9c`, read for byte layouts and values only |
| D | The pad's USB descriptors, listed by an app through `UsbManager` on the Pixel, 2026-11-17 |
| U | Android `UsbManager` / `UsbDeviceConnection` reference docs |
| S | Android SDK reference docs (public API) |
| A | Read-only adb captures on the Pixel, 2026-11-19: `dumpsys input`, lights, sensors, audio policy, the key layout file |
| C1, C2 | Raw input reports captured on the Pixel, 2026-11-17: run 1 (6,690 reports, 30 s, all inputs except Create/Options/Mute pressed), run 2 (5,790 reports, Create, Options and Mute pressed) |
| C3 | A Direct USB test app's log on the Pixel, 2026-11-19 10:51-10:56: every output report it sent, with the result the user saw or felt |
| C4 | An Android-API test app's log on the Pixel, 2026-11-19 11:12 |
| H | `linux/usbdevice_fs.h` in the NDK sysroot |
| O | User observation on the Pixel, the date given |

"Confirmed": **yes** = exercised on the pad and the effect observed; **not yet** = documented or listed, not exercised; **no** = tried, doesn't work.

## Identity and USB layout

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P1 | Vendor 0x054c. Product 0x0ce6 = DualSense, 0x0df2 = DualSense Edge. Match on both IDs. | K, D | yes (0x0ce6); not yet (0x0df2) |
| P2 | Interface 0 audio control, no endpoints. Interface 1 audio streaming, alt 1: OUT 0x01 isochronous, max packet 392, interval 4. Interface 2 audio streaming, alt 1: IN 0x82 isochronous, max packet 196, interval 4. Interface 3 HID: IN 0x84 interrupt and OUT 0x03 interrupt, 64 bytes each, interval 6. `UsbManager` lists 6 interface entries (1 and 2 each twice, alt 0 and alt 1). | D | yes |
| P3 | High-speed interval 4 = one packet per 1 ms. 48 kHz: OUT 4 channels × 2 bytes × 48 frames = 384 bytes per packet; IN 2 channels × 2 bytes × 48 = 192. | D, derived | yes (audio ran with 0 packet errors, C3) |
| P4 | `claimInterface(iface, true)` unbinds the kernel driver from that interface; while interface 3 is claimed Android lists no input device for the pad. `releaseInterface` then `close` gives it back: driver `usbhid` and the input devices return within 1 s, no replug. | U, O 2026-11-17, C3 | yes |
| P5 | A force-stop while interface 3 is claimed strands the pad: no driver on interface 3 and no input devices for 33 s or more, until a replug. | O 2026-11-19 | yes |
| P6 | `UsbManager.getDeviceList()` lists the pad on USB only, never over Bluetooth. | U, O 2026-11-17 | yes |
| P7 | `bulkTransfer` carries interrupt-endpoint transfers. `setInterface(UsbInterface)` selects an alternate setting; each alt is its own `UsbInterface` with the same `getId()`. `controlTransfer(requestType, request, value, index, buffer, length, timeoutMs)` sends class requests. `getFileDescriptor()` gives the usbfs fd for native ioctls. | U | yes (C3 used each) |
| P8 | A USB permission `PendingIntent` needs `FLAG_MUTABLE` (the system adds the extras). `RECEIVER_NOT_EXPORTED` needs API 33; `ContextCompat.registerReceiver` covers older. | U, lint | yes |

## Input report 0x01, 64 bytes (byte 0 is the report ID)

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P10 | Bytes 1-4: left X, left Y, right X, right Y, 0-255, centre about 0x80. | K, C1 | yes |
| P11 | Bytes 5, 6: L2, R2 analog, 0-255; both reach 255. | K, C1 | yes |
| P12 | Byte 7: a counter, +1 per report, wrapping 0xff → 0x00. 0 gaps in 12,480 consecutive reports (C1, C2), 49 wraps. | K, C1, C2 | yes |
| P13 | Byte 8 low nibble: D-pad, 0 = up, clockwise to 7 = up-left, 8 = none. Bits 4-7: Square, Cross, Circle, Triangle. | K, C1 | yes (all 9 states, all 4 buttons) |
| P14 | Byte 9: bit 0 L1, 1 R1, 2 L2 (digital), 3 R2 (digital), 4 Create, 5 Options, 6 L3, 7 R3. | K, C1, C2 | yes |
| P15 | Byte 10: bit 0 PS, bit 1 touchpad click, bit 2 Mute. | K, C1, C2 | yes |
| P16 | Bytes 16-21: gyro X, Y, Z, each int16 little-endian. Bytes 22-27: accelerometer X, Y, Z, int16 little-endian. | K | not yet: bytes vary with motion (C1), per-axis meaning, sign and scale untested |
| P17 | Touch point 0 at bytes 33-36, touch point 1 at bytes 37-40. First byte: bit 7 set = not touching, low 7 bits = contact id. X (12 bits) = second byte + (low nibble of third byte) << 8. Y (12 bits) = (high nibble of third byte) + fourth byte << 4. Range X 0-1919, Y 0-1079. | K, O 2026-11-19 | yes (one and two fingers; x 1313, y 1079 reached) |
| P18 | Byte 53: bits 0-3 battery level, bits 4-7 charging state. | K | not yet: raw values read (level 8-9, charging 2), mapping to percent and states untested |
| P19 | Byte 54: bit 0 headphones in, bit 1 mic in, bit 2 mic muted. | K, O 2026-11-19 | yes (bit 0); not yet (bits 1, 2) |
| P20 | Fixtures: one report per file, hex, from C1/C2. Two-finger touch is `touch-1`. See "Fixtures". | C1, C2 | yes |

## Output report 0x02, 63 bytes (byte 0 is the report ID)

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P30 | Byte 1 = valid flag 0, byte 2 = valid flag 1, byte 39 = valid flag 2. A field is applied only when its flag bit is set. | K, C3 | yes |
| P31 | Bytes 3, 4: motor right, motor left (0-255). Enabled by flag 0 bit 1 plus either flag 0 bit 0 (compatible vibration) or flag 2 bit 2 (vibration v2). | K, C3 | yes (both modes felt) |
| P32 | Vibration v2 when firmware update version (P51) ≥ 0x0215. | K | yes (0x0630 pad, v2 felt) |
| P33 | Byte 5: headphone volume 0-0x7f, flag 0 bit 4. 0x3f was played and heard as quiet. | C3, O | yes |
| P34 | Byte 6: speaker volume, accepted 0x3d-0x64, flag 0 bit 5. 0x64 was played. | K, C3 | yes |
| P35 | Byte 7: mic volume 0-0x40, flag 0 bit 6. | K, C3 | yes |
| P36 | Byte 8 bits 4-5: output path, flag 0 bit 7. 0 = both channels to headphones, speaker muted. 3 = right channel to the speaker, headphones muted. | K, C3 | yes |
| P37 | Byte 9: mute LED, flag 1 bit 0. 0 off, 1 on, 2 pulsing. | K, C3, O | yes |
| P38 | Byte 10: power save; bit 4 mutes the mic. Flag 1 bit 1. | K | not yet |
| P39 | Bytes 11-21: right trigger effect; bytes 22-32: left trigger effect. Enabled by flag 0 bit 2 (right) and bit 3 (left). Effect mode 0x01 = resistance: byte +1 start position, byte +2 force. All zeros = off. `01 30 c0` gives clear resistance. | C3, O | yes (resistance on both) |
| P40 | Byte 38 bits 0-2: speaker preamp gain, flag 1 bit 7. The working speaker route (P61) sets it to 2. | K, C3 | yes (as part of the speaker route) |
| P41 | Byte 42: lightbar setup; bit 1 fades the light out. Flag 2 bit 1. | K | not yet |
| P42 | Byte 44: player LEDs, bits 0-4 (LED 1 = bit 0), flag 1 bit 4. | K, C3 | yes (every single LED and combinations) |
| P43 | Bytes 45-47: lightbar red, green, blue, flag 1 bit 2. | K, C3 | yes (red, green, blue, yellow, cyan, white, off) |
| P44 | HD haptics are output channels 3-4 of the isochronous OUT stream; they play only while compatible vibration (P31) is off. | C3, O | yes |
| P45 | All-off = `golden/release-all-off.hex`: flag 0 = 0x0f, flag 1 = 0x15, flag 2 = 0x04, every value 0 (rumble 0 in both modes, both triggers off, mute LED off, player LEDs off, lightbar 0,0,0). After it and a release, the pad returned to Android normally. | C3 | yes |
| P46 | Golden output reports, each sent on 2026-11-19 with the effect seen or felt: `golden/lightbar-0000ff.hex`, `golden/lightbar-ff0000.hex`, `golden/lightbar-00ff00.hex`, `golden/lightbar-ffff00.hex`, `golden/lightbar-00ffff.hex`, `golden/lightbar-ffffff.hex`, `golden/lightbar-000000.hex`, `golden/player-leds-01.hex`, `golden/player-leds-02.hex`, `golden/player-leds-04.hex`, `golden/player-leds-08.hex`, `golden/player-leds-10.hex`, `golden/player-leds-1f.hex`, `golden/player-leds-00.hex`, `golden/mute-led-0.hex`, `golden/mute-led-1.hex`, `golden/mute-led-2.hex`, `golden/rumble-right.hex`, `golden/rumble-left.hex`, `golden/rumble-both.hex`, `golden/rumble-off.hex`, `golden/rumble-v2-both.hex`, `golden/rumble-v2-off.hex`, `golden/r2-resistance.hex`, `golden/r2-off.hex`, `golden/l2-resistance.hex`, `golden/l2-off.hex`, `golden/route-speaker.hex`, `golden/route-headphones.hex`, `golden/mic-on.hex`, `golden/release-all-off.hex`. One line of 63 hex bytes each. | C3, O | yes |

## Feature reports

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P50 | HID GET_REPORT for feature N: `controlTransfer(0xA1, 0x01, 0x0300 or N, interface 3, buf, length, timeout)`. Reply byte 0 = report ID. | USB HID spec, C3 | yes (0x20) |
| P51 | Feature 0x20, 64 bytes: bytes 1-19 ASCII build date; 24-27 hardware version, 28-31 firmware version (uint32 LE); 44-45 update version (uint16 LE). This pad: hw 0x00000413, fw 0x0110002a, update 0x0630. Fixture `feature/firmware-0x20.hex`. | K, C3 | yes |
| P52 | Feature 0x09 contains the pad's Bluetooth address. Never read it. | K | n/a |

## Audio over USB

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P60 | USB audio class 1 SET_CUR sample rate (`0x22, 0x01, 0x0100, endpoint, 3 bytes LE Hz`) returns -1 on this pad; audio runs at 48 kHz anyway. Don't send it. | C3 | yes |
| P61 | Speaker: path 3, speaker volume 0x64, flag bits as in `golden/route-speaker.hex`, then 440 Hz on channels 1-2: heard. Headphones: path 0, volume 0x3f (`golden/route-headphones.hex`): heard, quiet. Haptics: 150 Hz on channels 3-4: felt. Each 0 packet errors, result 0. | C3, O | yes |
| P62 | Mic: power on with `golden/mic-on.hex` (mic volume 0x40), claim interface 2, alt 1, read IN 0x82: 3,000 packets, 575,904 bytes, 0 errors in 3 s; peaks reach full scale at that volume. | C3, O | yes |
| P63 | usbfs iso: URB type `USBDEVFS_URB_TYPE_ISO` = 0; flag `USBDEVFS_URB_ISO_ASAP` = 0x02. `struct usbdevfs_urb` ends in `iso_frame_desc[]` of `{length, actual_length, status}`, one per packet; packet k's data starts at the sum of the requested lengths of packets 0..k-1. `USBDEVFS_SUBMITURB` queues; `USBDEVFS_REAPURBNDELAY` returns a finished URB or EAGAIN; `poll()` gives POLLOUT when one finished; `USBDEVFS_DISCARDURB` cancels, and a cancelled URB must still be reaped before its memory is freed. | H | yes (C3) |
| P64 | After audio: set the interface back to alt 0, then release it. | C3 | yes |

## Android APIs (Android mode)

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P70 | On USB, Android merges the pad's kernel devices into one `InputDevice`, sources KEYBOARD, GAMEPAD, MOUSE, TOUCHPAD, JOYSTICK, SENSOR; name contains "DualSense Wireless Controller". Find with `InputManager.getInputDeviceIds()` + vendor/product ID; hot-plug via `registerInputDeviceListener`. | A, S | yes |
| P71 | Key codes: Cross `BUTTON_A`, Circle `BUTTON_B`, Square `BUTTON_X`, Triangle `BUTTON_Y`, L1/R1 `BUTTON_L1/R1`, L2/R2 `BUTTON_L2/R2`, L3/R3 `BUTTON_THUMBL/THUMBR`, Create `BUTTON_SELECT`, Options `BUTTON_START`, PS `BUTTON_MODE` (scan 316). Each arrives at `dispatchKeyEvent`. | A, C4 | yes |
| P72 | Mute: no key event of any code; pressing it toggles the mute LED on the pad itself. | A, C4, O | no (not offered) |
| P73 | Axes: `AXIS_X/Y` left stick, `AXIS_Z/RZ` right stick (-1 to 1); `AXIS_LTRIGGER/RTRIGGER` (0 to 1); `AXIS_BRAKE/GAS` also listed; `AXIS_HAT_X/HAT_Y` D-pad (-1, 0, 1). Joystick events arrive at `onGenericMotionEvent` / `dispatchGenericMotionEvent` with `SOURCE_JOYSTICK`. `flat` is 0 on the sticks. | A, S | not yet (ranges listed; values not logged) |
| P74 | Touchpad: without pointer capture it drives a mouse pointer in screen coordinates; the click is mouse button state 0x1. Two fingers become Android's own scroll gesture. With capture, one relative pointer only (`AXIS_RELATIVE_X/Y`). | A, C4, O | yes (one finger and click); no (two fingers) |
| P75 | `InputDevice.getSensorManager()` (API 31): `TYPE_GYROSCOPE` (range 2048) and `TYPE_ACCELEROMETER` (range 4), SI units. | A, S | not yet (listed; delivery reported by O 2026-11-19, values not logged) |
| P76 | `getVibratorManager()`: two vibrators, id 0 = left, id 1 = right, amplitude control 1-255. `CombinedVibration.startParallel().addVibrator(id, createOneShot(ms, amp)).combine()`, `vibrate()`, `cancel()`. Feels weaker than Direct USB, one feel only. | S, C4, O | yes |
| P77 | `getLightsManager()`: 6 lights, type INPUT: "indicator" (RGB, the lightbar), five named "…player-1" to "…player-5" (brightness only). No mute LED. Change through `openSession()` + `requestLights`; `close()` hands them back. | A, S, C4 | yes (Bluetooth); no (USB, see P79) |
| P78 | `getBatteryState()`: `isPresent`, `getCapacity` (0-1 or NaN), `getStatus`. The battery's name contains the pad's Bluetooth address: never log or show it. | A, S | yes (Bluetooth: 85%, discharging); no (USB, see P79) |
| P79 | On USB, lights and battery fail silently: `requestLights` returns normally and nothing changes; battery capacity -0.01, status UNKNOWN. The system log shows SELinux denials for `system_server` on the pad's sysfs LED and battery files. | C4, A, O | yes |
| P80 | Mic: `AudioManager.getDevices(GET_DEVICES_INPUTS)` lists the pad as `TYPE_USB_HEADSET`, 48 kHz, 2 channels; no output device for the pad. `AudioRecord` with `setPreferredDevice` recorded from it (needs `RECORD_AUDIO`); `getRoutedDevice()` confirms. Loud input clips. | A, S, C4, O | yes |

## Toolchain (this Mac, 2026-11-19)

| ID | Fact | Source | Confirmed |
|---|---|---|---|
| P90 | Gradle 8.14.5 runs offline; AGP 8.4.2 and Kotlin 2.2.10-2.4.10 are cached; Jetpack Compose is not (download allowed by the user, 2026-11-19). | local check | yes |
| P91 | Use JDK 17 (`/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`): lint under JDK 25 crashes. | local check | yes |
| P92 | Platforms 35, 36, 37 installed, no 34. AGP 8.4.2 warns above 34: set `android.suppressUnsupportedCompileSdk=35`. | local check | yes |
| P93 | NDK 28.2.13676358 and CMake 3.22.1 installed. | local check | yes |

## Fixtures

Input reports (`pad/src/test/resources/reports/`), one 64-byte report per file, cut from C1/C2 as the first report showing that press:

| File | From |
|---|---|
| `reports/idle.hex` | C1 at 0 ms |
| `reports/square.hex` | C1 at 7273 ms |
| `reports/cross.hex` | C1 at 8251 ms |
| `reports/circle.hex` | C1 at 7960 ms |
| `reports/triangle.hex` | C1 at 7627 ms |
| `reports/l1.hex` | C1 at 6010 ms |
| `reports/r1.hex` | C1 at 6437 ms |
| `reports/l2-button.hex` | C1 at 5257 ms |
| `reports/r2-button.hex` | C1 at 6786 ms |
| `reports/create.hex` | C2 at 11331 ms |
| `reports/options.hex` | C2 at 11907 ms |
| `reports/l3.hex` | C1 at 10759 ms |
| `reports/r3.hex` | C1 at 10354 ms |
| `reports/ps.hex` | C1 at 11314 ms |
| `reports/touchpad-click.hex` | C1 at 14671 ms |
| `reports/mute.hex` | C2 at 9512 ms |
| `reports/dpad-n.hex` | C1 at 8975 ms |
| `reports/dpad-ne.hex` | C1 at 16403 ms |
| `reports/dpad-e.hex` | C1 at 9250 ms |
| `reports/dpad-se.hex` | C1 at 16483 ms |
| `reports/dpad-s.hex` | C1 at 9502 ms |
| `reports/dpad-sw.hex` | C1 at 16622 ms |
| `reports/dpad-w.hex` | C1 at 8646 ms |
| `reports/dpad-nw.hex` | C1 at 13814 ms |
| `reports/dpad-none.hex` | C1 at 0 ms |
| `reports/l2-full.hex` | C1 at 5289 ms |
| `reports/r2-full.hex` | C1 at 6807 ms |
| `reports/left-x-min.hex` | C1 at 12568 ms |
| `reports/left-x-max.hex` | C1 at 12436 ms |
| `reports/left-y-min.hex` | C1 at 11878 ms |
| `reports/left-y-max.hex` | C1 at 12357 ms |
| `reports/right-x-min.hex` | C1 at 12496 ms |
| `reports/right-x-max.hex` | C1 at 12696 ms |
| `reports/right-y-min.hex` | C1 at 12600 ms |
| `reports/right-y-max.hex` | C1 at 12744 ms |
| `reports/touch-0.hex` | C1 at 14574 ms |
| `reports/touch-1.hex` | C1 at 14710 ms |

Output reports: `pad/src/test/resources/golden/` (P46). Feature: `pad/src/test/resources/feature/firmware-0x20.hex` (P51).
