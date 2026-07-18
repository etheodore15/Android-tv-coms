# FamilyTV Link

A private, family-only Android app pair (no accounts, no Play Store — sideloaded):

- **Phone app** (`phone` flavor): send short messages to the living-room TV; receive quick replies as notifications.
- **TV app** (`tv` flavor): a background service pops a message overlay on top of whatever is playing, with D-pad-selectable canned replies (**OK · 5 mins · Coming · No**, plus Dismiss).

Transport is **Supabase Realtime Broadcast** (WebSocket pub/sub) — channel `to-tv` for phone→TV, `to-phone` for TV→phone. Messages are ephemeral; nothing is stored anywhere.

## Build

```
./gradlew assemblePhoneDebug assembleTvDebug
```

APKs land in `app/build/outputs/apk/phone/debug/` and `app/build/outputs/apk/tv/debug/`.

> Before building for real use, fill in your Supabase credentials — see below.

## Setup & install runbook

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
