package dev.terraformist.glyphpet

/**
 * The in-app Guide, written for someone who has never had a virtual pet.
 *
 * KEEP THIS UP TO DATE: whenever a change affects how someone looks after the pet
 * (new actions, new rules, new places it lives, changed controls), update the text here
 * in the same change.
 */
object Guide {

    class Section(val heading: String, val body: String)

    val sections = listOf(
        Section(
            "WHAT IS THIS?",
            "Glyph Pet is a tiny creature that lives on your phone. It gets hungry, bored and messy, " +
                "and it grows up over a few days. How it turns out depends on how well you look after it.\n\n" +
                "It is the same pet in three places: this app, the home-screen widget, and the Glyph Matrix " +
                "lights on the back of your phone. Looking after it in one place counts everywhere.",
        ),
        Section(
            "GETTING STARTED",
            "Your pet starts as an egg. After 3 minutes it hatches into a baby, and you can start looking after it.",
        ),
        Section(
            "THE BUTTONS",
            "FEED fills its tummy.\n\n" +
                "PLAY opens a mini-game. Playing makes it happier, and winning makes it much happier.\n\n" +
                "CLEAN tidies up its mess. It needs the toilet every few hours, and the mess shows up as " +
                "little piles next to it.\n\n" +
                "MEDS only appears when your pet is ill, in place of PLAY. Ill pets can't play.",
        ),
        Section(
            "READING THE SIGNS",
            "The five dots under each button show how it's doing: more dots is better.\n\n" +
                "Red means it needs something now. A red label or red dots on a button tell you which. " +
                "On the pet screen and the Glyph Matrix, a red icon blinks above its head.\n\n" +
                "A happy pet bounces. An unhappy one droops and frowns.",
        ),
        Section(
            "GETTING ILL",
            "If mess is left lying around or it goes hungry for too long, it falls ill. " +
                "An ill pet looks dim, shivers, and gets unhappy faster.\n\n" +
                "Tap MEDS to make it better. It won't enjoy the taste, but it'll be fine.",
        ),
        Section(
            "SHAKING",
            "Give your phone a good shake (three firm shakes in about a second).\n\nIf your pet is " +
                "awake, it's a quick game of chase: it dashes about and gets a bit happier.\n\nIf it's " +
                "asleep or napping, a shake wakes it up instead. It wriggles, jumps up with a \"!\" and " +
                "stays awake for about 15 minutes. Waking it doesn't count as playing, so it doesn't fill " +
                "the PLAY meter.\n\nShaking works with the app open, and on the Glyph Matrix while your " +
                "pet is awake there (for a minute after you pick it, touch the back button, turn the " +
                "screen on or pick the phone up).\n\nAn ill pet or an egg won't play.",
        ),
        Section(
            "SLEEP AND NAPS",
            "It sleeps from 11pm to 7am and needs much less looking after overnight. You'll see " +
                "little Zs above it.\n\nIt also naps during the day:\n\nWhen it's dark, like a dark room " +
                "or your pocket, it dozes off. When the lights come back on, it wakes up by itself. While " +
                "napping its needs build up more slowly.\n\nWhen your phone is charging, it naps on the " +
                "charger, shown by a little lightning bolt. Charging naps slowly cheer it up. It wakes " +
                "when you unplug.\n\nFeeding, cleaning or playing with it wakes it for a minute. Shaking " +
                "it wakes it for about 15 minutes.\n\nThe light sensor is on the screen side of the " +
                "phone, so it can't see the light while the phone is face-down. If it's napping and the " +
                "phone is face-down, shake it or flip it over and back to wake it.\n\nThe widget only " +
                "refreshes about every half hour, so a nap can take a little while to show there.",
        ),
        Section(
            "WALKS",
            "Carry your phone and your pet comes for a walk. Every 1,000 steps cheers it up a little " +
                "and makes it a bit hungry, so keep an eye on FEED.\n\nWalk 5,000 steps in a day for a " +
                "treat: a big boost of happiness and a little celebration.\n\nWhile you're walking and " +
                "it's on screen, it trots along as the ground slides past. Today's steps are shown under " +
                "its name on the pet screen.\n\nWalks use your phone's step counter. The app asks for " +
                "permission the first time you open it. If you said no, you can turn on Physical activity " +
                "for Glyph Pet in your phone's Settings, under Apps.",
        ),
        Section(
            "WEATHER",
            "Your pet sees the weather where you are. A faint sun, moon, cloud or fog shows in the " +
                "top corner, and rain or snow falls gently behind it. The weather and temperature are " +
                "shown under its name on the pet screen.\n\nWeather uses your approximate location (to " +
                "about a kilometre), sent to a free weather service, and updates about every 45 minutes. " +
                "If you said no to location, you can turn it on for Glyph Pet in your phone's Settings, " +
                "under Apps.",
        ),
        Section(
            "GROWING UP",
            "It is a baby for its first day, a child until day 3, and then an adult.\n\n" +
                "Each time it grows up, it turns into one of several kinds: two kinds of child " +
                "and three kinds of adult. Which one depends on how often its needs were left unmet " +
                "while it was awake. Keep it fed, happy, clean and healthy for the best results. " +
                "Its kind is shown at the top of the pet screen.",
        ),
        Section(
            "MINI-GAMES",
            "Tap PLAY to pick a game. Each game has 5 rounds. Get 3 or more right to win.\n\n" +
                "LEFT OR RIGHT: guess which way your pet will look.\n\n" +
                "HIGHER OR LOWER: your pet shows a number. Guess whether the next number will be " +
                "higher or lower.",
        ),
        Section(
            "HOME-SCREEN WIDGET",
            "To add it: long-press an empty spot on your home screen, tap Widgets, find Glyph Pet " +
                "and drag it out.\n\n" +
                "The widget has the same buttons as the app. PLAY opens the mini-games. " +
                "Tap the pet to open the app.",
        ),
        Section(
            "GLYPH MATRIX",
            "To add it: open your Glyph settings, go to Glyph Toys and add Glyph Pet. Then tap the " +
                "button on the back of the phone until your pet appears.\n\nLong-press the back button to " +
                "choose an action. Each long-press moves to the next one: Feed, Play (or Meds if it's " +
                "ill), Clean, then nothing. The chosen action shows as a red icon. Wait 2 seconds and it " +
                "happens. Long-press until nothing is shown to cancel.\n\nPlay on the Glyph Matrix is a " +
                "quick play, not a game. You can also shake the phone to play, or to wake it.\n\nEach " +
                "time you pick Glyph Pet, or turn the phone face-down while it's awake, it waves hello. " +
                "Saying hello also wakes it from a dark-room nap.\n\nYour pet animates for a minute after " +
                "you touch the back button. If you leave it on the always-on Glyph, it wakes for a little " +
                "bounce once a minute.",
        ),
        Section(
            "STARTING OVER",
            "At the bottom of the pet screen, START OVER gives you a new egg. Your current pet is " +
                "gone for good, so only use it if you mean it.",
        ),
    )
}
