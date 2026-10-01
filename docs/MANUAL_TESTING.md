# Manual testing

Run from the repo root in cmd, with a JDK on PATH.
1. `gradlew.bat :app:testDebugUnitTest`: the 3 test classes pass.
2. `gradlew.bat :app:assembleDebug` succeeds.
3. Start an Android emulator (API 33 or newer), then:
   - `adb wait-for-device`
   - `adb install -r app\build\outputs\apk\debug\app-debug.apk`
   - `adb shell am start -n it.federicorapetti.recalls/.MainActivity`

   Allow the notification permission prompt.
4. **First sync, list:**
   - The list fills within about 30 s, with no notifications (baseline).
   - The newest EU card matches the first `reference` returned by the command below, which POSTs the search body from [`DATA_SOURCES.md`](DATA_SOURCES.md) after it is saved to `sg.json`: `curl -s -X POST -H "Content-Type: application/json" --data @sg.json https://ec.europa.eu/safety-gate-alerts/public/api/search`.
   - Italian cards include the first title of `curl -s -A "<Chrome UA>" https://www.salute.gov.it/new/rss/RSS_avvisi_richiami_osa.xml`.
   - Thumbnails load for EU cards.
5. **Filters and search:**
   - The toolbar "Italy" toggle shows only IT cards. "Unread only" shows none right after the baseline.
   - Typing `mais` shows the popcorn-maize recalls.
   - The toolbar hides on scroll down.
   - Pull-to-refresh shows the expressive loading indicator.
6. **Detail:**
   - An EU card shows the photo carousel, risk description and measures. "Open official page" opens the Safety Gate page.
   - An IT operator card's "Recall notice (PDF)" opens the PDF in the browser.
   - A Ministry warning (if one is inside 90 days) shows its image.
   - Switching the device language to Italian and relaunching shows Italian UI strings.
7. **Periodic job:** `adb shell dumpsys jobscheduler | findstr /i "it.federicorapetti.recalls"` lists a periodic job at a 6 h interval. After switching the interval to 12 h in Settings, the period changes.
8. **New-recall notifications** (within 1 h of the last sync, so that opening the app does not auto-refresh first):
   - Run:
     - `adb shell am force-stop it.federicorapetti.recalls`
     - `adb shell "run-as it.federicorapetti.recalls sqlite3 databases/recalls.db \"DELETE FROM recalls WHERE id IN (SELECT id FROM recalls WHERE source='SAFETY_GATE' ORDER BY publishedAt DESC LIMIT 3); DELETE FROM recalls WHERE id IN (SELECT id FROM recalls WHERE source='IT_OPERATOR' ORDER BY publishedAt DESC LIMIT 2); UPDATE source_state SET etag=NULL;\""`
   - Open the app, go to Settings, tap "Check now".
   - Expected: two notifications — "3 new EU recalls" and "2 new recalls in Italy" (InboxStyle titles). Check with `adb shell dumpsys notification --noredact | findstr /i "android.title"`.
   - Tapping the EU one opens the list filtered to EU, with unread dots on those 3 cards.
   - Turning the EU switch off and repeating produces only the Italy notification.
   - If `sqlite3` is missing on the image: run `adb shell "run-as it.federicorapetti.recalls cat databases/recalls.db" > recalls.db` (after force-stop; also delete `-wal`/`-shm` via `run-as rm`), edit it with Python's `sqlite3`, `adb push recalls.db /data/local/tmp/`, then `adb shell "run-as it.federicorapetti.recalls cp /data/local/tmp/recalls.db databases/recalls.db"`.
9. **Icon:** the launcher shows the white badge-alert glyph on red. With themed icons enabled (long-press the home screen, Wallpaper & style, Themed icons), the monochrome version renders.
