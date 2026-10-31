# NetSpeed

A tiny (~30 KB) Android app that shows your live download speed in the status bar,
like a clock, without opening the notification panel.

- Shows speed as `345.8` / `KB/s`, switching to `MB/s` above 1000 KB/s
- Adjustable position and size
- No internet permission, no ads, no tracking, no accounts
- Android 8.0+ (API 26+)

## Install

1. Download `netspeed.apk` from the [latest release](../../releases/latest).
2. Open it and allow your browser / file manager to install apps if asked.
3. Open **NetSpeed** and tap **Allow display over other apps**, then enable it for NetSpeed.
4. Use the **Position** slider to move the speed into a free gap in the status bar, and **Size** to resize it.

Do not grant the notification permission if you want to keep your notification panel clean:
Android requires a background service to have a notification, but the app creates it at the lowest
importance and it is not shown when notifications are off. (On Android 12 and older a small silent
entry may appear; long-press it and turn the "Speed meter" channel off.)

## Google Play Protect

NetSpeed asks for **no** sensitive permissions (no Accessibility, SMS, notification access, or internet),
so it is not blocked by Play Protect's fraud protection for sideloaded apps. Because it is a new app
from an independent developer, Play Protect may still show an "unknown app" prompt. Tap
**More details → Install anyway** (or **Scan app**). Nothing is wrong; Play Protect warns for any app
it has not seen before.

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` (Display over other apps) | Draws the speed text at the top of the screen |
| `FOREGROUND_SERVICE` + `..._SPECIAL_USE` | Keeps the once-per-second speed counter running |
| `RECEIVE_BOOT_COMPLETED` | Restarts the meter after a reboot |

There is deliberately **no `INTERNET` permission**, so the app cannot send data anywhere.

## Privacy

NetSpeed collects nothing. It reads the phone's total received/sent byte counters
(`TrafficStats`) once a second to compute speed, shows the result on screen, and discards it.
It does not see which apps or sites generated the traffic, and does not read your screen, contacts,
files, location or identifiers. The only thing saved on the device is three settings
(on/off, position, size). Backups are disabled.

## Build from source

Requires JDK 17 and Gradle 8.7 (or let GitHub Actions do it, see `.github/workflows/build.yml`).

```
gradle assembleRelease
```

This produces an **unsigned** `app-release-unsigned.apk`. To install your own build, sign it:

```
keytool -genkeypair -keystore my.jks -alias me -keyalg RSA -keysize 4096 -validity 10000
zipalign -p -f 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks my.jks --out netspeed.apk aligned.apk
```

Never commit your keystore. `.gitignore` already excludes `*.jks`.

## Verify a release

Official releases are signed by the maintainer's key. Check with
`apksigner verify --print-certs netspeed.apk`; the SHA-256 certificate fingerprint should be:

```
12:EE:D1:8A:00:9C:2C:2A:4D:F6:24:37:9B:CD:D5:14:1B:6D:F2:F8:C5:EF:A4:31:40:F9:95:48:44:59:A0:F0
```

## License

[MIT](LICENSE)
