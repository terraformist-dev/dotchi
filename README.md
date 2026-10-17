# Dotchi

Dotchi is a Tamagotchi-style pet for the Nothing Phone (3). It lives in an app, a home-screen widget and the Nothing Phone 3 glyph matrix.

## Getting the app onto your phone

1. On your phone, open **Releases → Latest** on this repo's GitHub page.
2. Tap **dotchi.apk** to download it.
3. Open the download and tap **Install** (allow your browser to install apps the first time).

New builds install over the old one.

## Using it

Everything is explained in the app: tap **GUIDE**.

## How it works

| File | What it does |
|---|---|
| `PetState.kt` | The Dotchi's stats and rules: hunger, happiness, mess, illness, sleep, growing up into different forms. No Android code. |
| `PetStore.kt` | Saves the Dotchi in all states. |
| `PetScene.kt` | Draws the 25×25 scene (pet, mess, icons). Shared by the widget and the Glyph Matrix. |
| `DotGrid.kt` | The dot grid, the exact Glyph Matrix shape, and a 3×5 dot font. |
| `Senses.kt` | Shake, face-down, light, charging and step counting (for play, waking, hello, naps and walks). |
| `Weather.kt` | Local weather from Open-Meteo using approximate location. |
| `DotPainter.kt` | Paints a dot grid as round dots for the widget and app (white, black, Nothing red). |
| `PetWidgetProvider.kt` | The home screen widget. |
| `GlyphPetToyService.kt` | The Glyph Toy. |
| `MainActivity.kt` | The main Dotchi screen. |
| `GameActivity.kt` | Minigames: Left or Right, Higher or Lower. |
| `Status.kt` | Short status words and meters shown on the pet screen. |
| `MeetActivity.kt` | Meeting other Dotchi over Bluetooth, and the friends list. |
| `AlbumActivity.kt` / `Album.kt` | The Album: forms found, past generations, friends, walking records. |
| `Season.kt` | Special days (birthday 10 June, Lunar New Year, Halloween, Christmas, New Year). |
| `GlyphGames.kt` | Tilt games on the Glyph Matrix: Catch and Roll. |
| `Nudges.kt` | Optional Glyph nudges when the Dotchi needs something. |
| `Guide.kt` / `GuideActivity.kt` | The in-app Guide. Update it whenever you adjust the code. |

The Dotchi never runs in the background. Whenever anything shows it, `PetState.advanceTo()` works out what happened since it was last looked at.

The Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-2.0.aar`) is from
[Nothing-Developer-Programme/GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) (MIT).
