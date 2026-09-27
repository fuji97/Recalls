# Cover image assets

`cover.mjs` composites the README header image from **real emulator captures**, not hand-drawn
UI. Only the background blobs, hero text, and device bezel are generated; everything inside the
phone frame and the notification card is a verbatim screenshot.

## `assets/`

- `app-light.png`, `app-dark.png` — full `adb exec-out screencap -p` captures (1440×3120,
  Pixel 7 Pro / `Pixel_7_Pro` AVD, Android 16) of `RecallListScreen` with real synced data
  (EU Safety Gate + Italian operator/ministry recalls), real thumbnails, and genuine
  new-item badges. Dark capture has `cmd uimode night yes` set — colors are real
  `dynamicDarkColorScheme` Material You output, not simulated tokens.
- `notification.png` — cropped (`ffmpeg -vf crop=1322:463:58:770`) from a real notification
  shade capture showing the app's actual "New recall in Italy" notification, triggered via
  `RecallSyncWorker` after deleting a synced row and clearing the source's HTTP etag on-device
  (same technique as the sqlite3 trick in `docs/MANUAL_TESTING.md`, done
  through `run-as` + Python's `sqlite3` module since the system image has no `sqlite3` CLI).

## Known limitation

Only a **light-theme** notification capture exists. Repeated attempts to reproduce it in dark
mode reliably reached `RecallRepository.sync()` and set `isNew = 1` on a fresh item (confirmed
via direct DB inspection and `WM-WorkerWrapper: Worker result SUCCESS` in logcat), but
`RecallNotifier.notifyNew()` never produced a visible `NotificationRecord` in
`dumpsys notification` on that run — while the exact same code path worked on the first
(light-mode) attempt. A plain `adb shell cmd notification post` confirmed the OS itself was not
suppressing notifications at the time. This looks like an app- or emulator-specific flake in
`RecallNotifier`/`RecallSyncWorker`, not a cover-generation issue — worth a dedicated
investigation, out of scope here. `notification.png` (light) is reused as-is for the dark cover
variant rather than fabricating a dark version.
