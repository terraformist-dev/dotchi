# Glyph Pet: notes for Claude

Owner has no coding experience and works only from a phone. Explain changes in plain English,
never ask them to run commands, and always finish by pushing to `main` and confirming the
GitHub Actions build went green (the build publishes `glyph-pet.apk` as the latest release).

## The Guide must stay current

The in-app Guide (`app/src/main/java/dev/terraformist/glyphpet/Guide.kt`) is written for someone
who has never had a virtual pet. **Any change that affects how a person looks after or interacts
with the pet (actions, rules, timings, controls, screens, widget, Glyph Toy) must update the Guide
in the same commit.** Keep the main screen uncluttered: instructions belong in the Guide, not on
the pet screen.

## Project

Android app (Kotlin, no AndroidX, no Compose), package `dev.terraformist.glyphpet`,
minSdk 34, target Phone (3) on Nothing OS 5 / Android 17. Builds only in GitHub Actions
(`.github/workflows/build.yml`); the sandbox cannot reach Google's Maven or Gradle servers.

The debug build is signed with `app/glyphpet.keystore` (committed on purpose) so updates
install over the old app and keep the saved pet. Don't change the key or applicationId.

## Glyph Matrix SDK 2.0 — verified against the .aar, don't guess

- `GlyphMatrixManager.getInstance(ctx)`, `init(Callback)`, `register(Glyph.DEVICE_23112)`,
  `setMatrixFrame(IntArray)`, `turnOff()`, `unInit()`. In an app (not a toy) use `setAppMatrixFrame`.
- Raw frames are 625 ints (25×25, row-major), **0..4095** per LED.
- `GlyphToy` constants: `MSG_GLYPH_TOY`, `MSG_GLYPH_TOY_DATA`, `EVENT_CHANGE` (long press),
  `EVENT_AOD` (once a minute when AOD toy), `EVENT_ACTION_DOWN`, `EVENT_ACTION_UP`.
- Matrix shape: row indents `9,7,5,4,3,2,2,1,1,0,0,0,0,0,0,0,1,1,2,2,3,4,5,7,9` (see `GlyphShape`).
- Toy service only lives while selected; return `Messenger.binder` from `onBind`.

## Design

Nothing look: black, white dots, one red accent (`#D71921`), faint unlit "ghost" dots,
3×5 dot font (`DotFont`). The same `PetScene` renders the app, widget and matrix.
Owner wants screens calm and uncluttered: show things only when they're relevant
(e.g. MEDS replaces PLAY only while ill). Mini-games are app-only for now; Glyph Play is a quick play.
Every body sprite keeps a lit row between the bottom of the eyes and the top of the mouth.

## Sensors, permissions, network

Walks use `TYPE_STEP_COUNTER` (ACTIVITY_RECOGNITION); weather uses coarse location + Open-Meteo
(no key). Both are asked for once on first app open; everything must still work if refused.
The light sensor faces the screen side, so light is ignored while the phone is face-down.
The Glyph Toy can't run in the background between frames: anything time-based must be lazily
computed in `PetState.advanceTo`, and waking the matrix relies on screen-on, charger broadcasts,
significant-motion, a wake-up light sensor if present, or a short light check every 2 minutes.
Lock screen: tried in build 7 (keyguard widget + notification card) and removed at the owner's
request; don't re-add it. `MainActivity.removeOldLockScreenCard` cleans up after build 7. Meet (`MeetActivity`): BLE advertise + scan, manufacturer data 0xFFFF with payload
`'G','P',version,formCode,id(4 bytes)`; RSSI > -75 counts as close; boost once per friend per day.

## Life cycle, cutscenes, extras (build 9)

- Pet name (`PetState.name`) is shown **only in the app** (dot title), never on widget or Glyph.
- Adults leave after `ADULT_MS` (14 days): `advanceTo` swaps in a new-generation egg and sets
  `farewellPending`/`albumPending`. One-off animations (`Cutscene.GROW`, `Cutscene.FAREWELL`) only
  start from `PetStore.get(lookingNow = true)` (app and Glyph Toy, not the widget).
- `Album` keeps forms seen, past generations and step records; `Friends` keeps friends.
- `Season`: 10 June birthday (party hat), Lunar New Year (computed with `android.icu.util.ChineseCalendar`,
  3 days, lantern), Halloween (pumpkin), 24-26 Dec (Santa), 31 Dec-1 Jan (party hat, fireworks).
- Weather affects needs via `Climate` (rain: restless but play x1.5; snow: happier; >=27°C: hungrier).
- Daycare: once a day, up to 8 hours, needs at 25%, no mess/illness/neglect.
- Glyph Play starts a tilt game (`GlyphGames.kt`: Catch, Roll), alternating; button taps can't be used
  (a quick tap switches toys). Tilt assumes the matrix is viewed from the back with x to the right.
- Glyph nudges (`Nudges`): opt-in switch inside the Guide; half-hourly inexact alarm to
  `PetWidgetProvider` (action NUDGE); blinks the need icon via `setAppMatrixFrame` when face-down.
