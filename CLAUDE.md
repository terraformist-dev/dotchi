# Dotchi (repo terraformist-dev/dotchi, formerly glyph-pet): notes for Claude

Owner has no coding experience and works only from a phone. Explain changes in plain English,
never ask them to run commands, and always finish by pushing to `main` and confirming the
GitHub Actions build went green (the build publishes `dotchi.apk` as the latest release).

The app is called **Dotchi** (renamed from Glyph Pet in build 12). Code names (package
`dev.terraformist.glyphpet`, class names) stay as they are. The GitHub repo was renamed to `terraformist-dev/dotchi`.

## The Guide must stay current

The in-app Guide (`app/src/main/java/dev/terraformist/glyphpet/Guide.kt`) is written for someone
who has never had a virtual pet. The owner rewrote it in his own words in build 12: keep his
voice and wording, and change only what a feature change requires. **Any change that affects how a person looks after or interacts
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
- Hello wave (toy picked / flipped face-down) only when the pet is awake: not egg, not `isAsleep` (night or nap). It never wakes the pet.
- Widget refresh: non-wakeup repeating alarm every 60s (`PetWidgetProvider.keepTicking`), so it ticks while the phone is awake and fires on screen-on; plus the 30-minute `updatePeriodMillis`.
- Softer animations (build 17, owner picked from a design canvas): keep the crisp Tamagotchi feel, no
  sub-dot blurring of the pet. Bodies are shaded (`PetScene.shading`, brighter top-left, dimmer edges) with
  half-bright cheeks at mouthX±4. Rain/snow at three depths, rain tails + splashes, snow lying on row 21.
  Need icons and Glyph nudges pulse like a heartbeat (`heartbeat`, 1.8s) with a faint ripple; red dots can
  now dim (`DotPainter`). Ill = blotchy shimmer (`illShimmer`); medicine = bright wave down the body
  (`MEDICINE_WAVE_MS`) then sparkles. `glowAt` (feed only while hungry, i.e. food < LOW; game win; friend) -> rings + flickering heart
  for `GLOW_MS` after the reaction. Egg speckles/crack glow faster as it nears hatching; hatching flashes.
  Glyph Toy opens out from a centre dot when picked (`powerOn`). A switch-off animation was tried in build 17
  and removed in build 18 at the owner's request (it disrupted cycling through Glyph Toys): don't re-add it.
  The play heart flickers. Dotchi's and the egg's own movements are unchanged on purpose.
- Touching Dotchi in the app (build 20, cosmetic only, never changes needs): `MainActivity` touch listener ->
  `PetScene.render(touch = Touch(kind, elapsed, lean))`. One tap BOOP (wide eyes, "!", hop); 3 taps in 1s
  TICKLE; hold 450ms SNUGGLE (lean to finger side, eyes closed, flickering heart); asleep STIR (one Z, stays
  asleep); ill WEAK_SMILE; egg: 3 quick taps KNOCK (two jolts from inside, "KNOCK KNOCK!").
- Walks (build 21): every 1,000 steps only makes it hungrier (`FOOD_PER_1000_STEPS`); happiness comes only
  from the 5,000-step daily goal (`STEP_GOAL_JOY`). Owner's choice: walking shouldn't replace playing.
- Too much of a good thing (build 14): feeding at food >= 90 = overeating (no food gain, -5 joy, next mess in 20 min and bigger via `extraMess`, STUFFED reaction). An over-play cooldown (played out, head shake) was removed in build 18 at the owner's request: it got in the way when shakes happen naturally while carrying the phone. Don't re-add it.
