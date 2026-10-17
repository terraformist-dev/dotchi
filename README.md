# Glyph Pet

A Tamagotchi-style pet for the Nothing Phone (3) that lives in two places at once:

- **Home-screen widget**: the pet on a dot grid, with Feed / Play / Clean buttons.
- **Glyph Toy**: the same pet on the 25×25 Glyph Matrix on the back of the phone.

Both read and write the same saved pet, so feeding it in one place shows up in the other.

## Getting the app onto your phone

Every push to `main` builds the app in the cloud (GitHub Actions) and publishes it as a release.

1. On your phone, open **Releases → Latest** on this repo's GitHub page.
2. Tap **glyph-pet.apk** to download it.
3. Open the download and tap **Install** (allow your browser to install apps the first time).

New builds install over the old one, and the pet is kept.

## Using it

Everything is explained in the app: tap **GUIDE** on the pet screen.

## How it works

| File | What it does |
|---|---|
| `PetState.kt` | The pet's stats and rules: hunger, happiness, mess, illness, sleep, growing up into different forms. No Android code. |
| `PetStore.kt` | Saves the pet; the one place the widget, toy and app all go through. |
| `PetScene.kt` | Draws the 25×25 scene (pet, mess, icons). Shared by the widget and the Glyph Matrix. |
| `DotGrid.kt` | The dot grid, the exact Glyph Matrix shape, and a 3×5 dot font. |
| `Senses.kt` | Shake, face-down, light, charging and step counting (for play, waking, hello, naps and walks). |
| `Weather.kt` | Local weather from Open-Meteo using approximate location. |
| `DotPainter.kt` | Paints a dot grid as round dots for the widget and app (white, black, Nothing red). |
| `PetWidgetProvider.kt` | The home-screen widget. |
| `GlyphPetToyService.kt` | The Glyph Toy. |
| `MainActivity.kt` | The main pet screen. |
| `GameActivity.kt` | Mini-games: Left or Right, Higher or Lower. |
| `Guide.kt` / `GuideActivity.kt` | The in-app Guide. Update it whenever how you care for the pet changes. |

The pet never runs in the background. Whenever anything shows it, `PetState.advanceTo()` works out what happened since it was last looked at.

The Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-2.0.aar`) is from
[Nothing-Developer-Programme/GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) (MIT).
