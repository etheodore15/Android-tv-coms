# FamilyTV Link

A private, family-only Android app pair (no accounts, no Play Store — sideloaded):

- **Phone app** (`phone` flavor): send short messages to the living-room TV; receive quick replies as notifications.
- **TV app** (`tv` flavor): a background service pops a message overlay on top of whatever is playing, with D-pad-selectable canned replies (**OK · 5 mins · Coming · No**, plus Dismiss).

Transport is **Supabase Realtime Broadcast** (WebSocket pub/sub) — channel `to-tv` for phone→TV, `to-phone` for TV→phone. Messages are ephemeral; nothing is stored anywhere.

## Link code (security & pairing)

Because the APKs sit on a public URL, the anon key inside them can't be the only gate. Devices pair with a **link code**:

1. On first launch the TV generates a random 8-character code (secure RNG, no confusable characters) and displays it on its settings screen, e.g. `K7F3-P2M9`.
2. On each phone, enter that code (case and dashes don't matter) — the phone is now linked.
3. Extra TVs: press "Enter a code instead" on the new TV and type the first TV's code.

The code is never compiled into the APK. From it the apps derive (PBKDF2, 120k iterations):

- **secret channel names** — `to-tv-<hmac>` instead of `to-tv`, so an outsider holding the APK can't find the family's channels; and
- an **AES-256-GCM key** that encrypts every message, so traffic can be neither read nor forged without the code. Payloads that don't decrypt are dropped silently.

Messaging is inactive until a device is linked. If an APK (or the code) ever leaves the family, press **Generate new code** on the TV and re-enter it on the phones — instant lockout of the old code, no rebuild needed. Rotating the Supabase anon key remains available as a second lever.

## Build

```
./gradlew assemblePhoneDebug assembleTvDebug
```

APKs land in `app/build/outputs/apk/phone/debug/` and `app/build/outputs/apk/tv/debug/`.

> Before building for real use, fill in your Supabase credentials — see below.

## Easy install via GitHub Pages (recommended)

A GitHub Actions workflow ([.github/workflows/pages.yml](.github/workflows/pages.yml)) builds both APKs with your Supabase credentials and publishes them behind a simple install page.

**One-time setup:**

1. In the repo: **Settings → Secrets and variables → Actions**, add two repository secrets: `SUPABASE_URL` and `SUPABASE_ANON_KEY` (from Supabase **Settings → API**).
2. **Settings → Pages**, set Source to **GitHub Actions**.
3. Push to the default branch (or run the workflow from the Actions tab). Note: GitHub Pages on a private repo requires a paid GitHub plan; on a public repo the page and APKs are world-readable — the anon key gates the Supabase project and can be rotated from the dashboard if needed.

**Then, on each device**, open `https://<owner>.github.io/<repo>/`:

- **Phone:** tap the download button, allow "unknown sources", install.
- **TV:** install the free "Downloader" app from the TV's Play Store, enter the `tv.apk` address shown on the page, install. (Or use adb — the page shows the command.)

Builds are signed with the committed keystore in `signing/`, so every new build installs as an in-place update — no uninstall needed. The keystore only proves update continuity; repo access is the real gate.

**In-app updates:** after the first install, devices update themselves. Each app checks `version.json` on the install page when opened and shows an "Update to X" button on its settings screen; one tap downloads the APK and opens the system installer (a one-time "install unknown apps" grant for FamilyTV Link is required on each device). Publishing an update is just pushing to the repo — CI rebuilds, bumps the page, and every device offers the update on next open.

## Manual setup & install runbook

### Supabase (once, ~5 min)

1. Create a free project at supabase.com (any region; Sydney `ap-southeast-2` for lowest latency).
2. Copy the Project URL and anon public key from **Settings → API** into
   [`app/src/main/java/family/tvlink/core/Config.kt`](app/src/main/java/family/tvlink/core/Config.kt)
   (`SUPABASE_URL` and `SUPABASE_ANON_KEY`).
3. Nothing else — no tables, no auth config. Realtime Broadcast works out of the box.
4. Rebuild both APKs.

### TV (once)

1. TV Settings → System → About → click "Android TV OS build" 7× to enable Developer options; enable **USB debugging** (network debugging on Google TV).
2. On dev machine: `adb connect <TV_IP>:5555`
3. `adb install app/build/outputs/apk/tv/debug/app-tv-debug.apk`
4. Launch once from the launcher, grant overlay permission when prompted, press **Test overlay** to verify.

### Phone (once)

1. Copy `app/build/outputs/apk/phone/debug/app-phone-debug.apk` to the phone (or `adb install`), allow install from unknown sources.
2. Open, set sender name, allow notifications, accept the battery-optimisation exemption.

## Multiple TVs

Install the TV APK on each TV, then:

1. Add each TV's name to `TV_NAMES` in `Config.kt` (e.g. `listOf("Living room", "Bedroom")`) and rebuild/reinstall both APKs.
2. On each TV's settings screen, set its **TV name** to exactly one of those entries (case-insensitive).
3. The phone then shows a target selector — **All TVs** or a specific TV — above the preset buttons. The selector is hidden while `TV_NAMES` has a single entry, and messages with no target (including sends from older builds) appear on every TV.

Replies are already distinguished by the TV's name in the notification.

## Editing family settings

Everything family-editable lives in one file, `Config.kt`: Supabase credentials, channel names, TV names, canned replies, preset messages, overlay timeout, default sender names.

## Notes

- The anon key gates the private project; rotate it from the Supabase dashboard if an APK ever leaves the family.
- Both services auto-restart on boot and reconnect with exponential backoff (max 60 s) after WiFi drops.
- If the TV lacks overlay permission, messages fall back to high-priority notifications instead of failing silently.
