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

    /** The section that gets the Glyph nudges on/off switch underneath it. */
    const val NUDGES_HEADING = "GLYPH NUDGES"

    val sections = listOf(
        Section(
            "WHAT IS THIS?",
            "Glyph Pet is a tiny creature that lives on your phone. It gets hungry, bored and messy, " +
                "and it grows up over a few days. How it turns out depends on how well you look after " +
                "it.\n\nIt is the same pet in three places: this app, the home-screen widget, and the " +
                "Glyph Matrix lights on the back of your phone. Looking after it in one place counts " +
                "everywhere.",
        ),
        Section(
            "GETTING STARTED",
            "Your pet starts as an egg. After 3 minutes it hatches into a baby, and you can start " +
                "looking after it. Tapping the buttons while it's still an egg just makes the egg wobble.",
        ),
        Section(
            "YOUR PET'S NAME",
            "Once your pet hatches, the app asks you to name it (up to 8 letters or numbers). The " +
                "name is shown in dots at the top of the pet screen.\n\nTap the name at any time to " +
                "change it. The name only appears in the app, not on the widget or the Glyph Matrix.",
        ),
        Section(
            "THE BUTTONS",
            "FEED fills its tummy.\n\nPLAY opens a mini-game. Playing makes it happier, and winning " +
                "makes it much happier.\n\nCLEAN tidies up its mess. It needs the toilet every few hours, " +
                "and the mess shows up as little piles next to it.\n\nMEDS only appears when your pet is " +
                "ill, in place of PLAY. Ill pets can't play.\n\nUnder the buttons: GUIDE (this page), " +
                "ALBUM (your keepsakes) and MEET (visit a friend's pet).",
        ),
        Section(
            "READING THE SIGNS",
            "The five dots under each button show how it's doing: more dots is better.\n\nRed means " +
                "it needs something now. A red label or red dots on a button tell you which. On the pet " +
                "screen and the Glyph Matrix, a red icon blinks above its head.\n\nA happy pet bounces. " +
                "An unhappy one droops and frowns.",
        ),
        Section(
            "GETTING ILL",
            "If mess is left lying around or it goes hungry for too long, it falls ill. An ill pet " +
                "looks dim, shivers, and gets unhappy faster.\n\nTap MEDS to make it better. It won't " +
                "enjoy the taste, but it'll be fine.",
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
                "napping its needs build up more slowly.\n\nWhen you plug your phone in to charge, it " +
                "naps on the charger, shown by a little lightning bolt. Charging naps slowly cheer it up. " +
                "It wakes when you unplug. Plugging in always starts a charging nap, even if you've only " +
                "just woken it.\n\nFeeding, cleaning or playing with it wakes it for a minute. Shaking it " +
                "wakes it for about 15 minutes, even on the charger.\n\nThe light sensor is on the screen " +
                "side of the phone, so it can't see the light while the phone is face-down. If it's " +
                "napping and the phone is face-down, give it a shake to wake it.\n\nThe " +
                "widget only refreshes about every half hour, so a nap can take a little while to show " +
                "there.",
        ),
        Section(
            "DAYCARE",
            "Busy day? Tap SEND TO DAYCARE near the bottom of the pet screen. For up to 8 hours its " +
                "needs barely change, it won't make a mess or fall ill, and a little house shows next to " +
                "it.\n\nTap the daycare line again to pick it up early. Daycare can be used once a day.",
        ),
        Section(
            "WALKS",
            "Carry your phone and your pet comes for a walk. Every 1,000 steps cheers it up a little " +
                "and makes it a bit hungry, so keep an eye on FEED.\n\nWalk 5,000 steps in a day for a " +
                "treat: a big boost of happiness and a little celebration.\n\nWhile you're walking and " +
                "it's on screen, it trots along as the ground slides past. Today's steps are shown under " +
                "its name on the pet screen.\n\nSteps come from your phone's own step counter, so they " +
                "only count while the phone is with you, and they won't exactly match a watch or fitness " +
                "band. Vigorous shaking (or the tilt games) can count a few steps too.\n\nWalks use your " +
                "phone's step counter. The app asks for permission the first time you open it. If you " +
                "said no, you can turn on Physical activity for Glyph Pet in your phone's Settings, under " +
                "Apps.",
        ),
        Section(
            "WEATHER",
            "Your pet sees the weather where you are. A faint sun, moon, cloud or fog shows in the " +
                "top corner, and rain or snow falls gently behind it. The weather and temperature are " +
                "shown under its name on the pet screen.\n\nWeather changes how it feels. On rainy days " +
                "it's stuck indoors and gets restless, but playing with it counts for half as much again. " +
                "Snow delights it. On hot days (27°C and up) it gets hungrier.\n\nWeather uses your " +
                "approximate location (to about a kilometre), sent to a free weather service. It updates " +
                "when you open the app (at most every 45 minutes), and the widget and Glyph Matrix keep " +
                "showing it for up to 6 hours. If you said no to location, you can turn it on for Glyph " +
                "Pet in your phone's Settings, under Apps.",
        ),
        Section(
            "SPECIAL DAYS",
            "Your pet dresses up for special days, everywhere it appears:\n\n10 June is its birthday: " +
                "a party hat and confetti.\n\nLunar New Year (worked out each year from the Chinese " +
                "calendar, for three days): a red lantern.\n\n31 October: a pumpkin hat for " +
                "Halloween.\n\n24 to 26 December: a Santa hat.\n\n31 December and 1 January: a party hat " +
                "and fireworks.",
        ),
        Section(
            "GROWING UP",
            "It is a baby for its first day, a child until day 3, and then an adult.\n\nEach time it " +
                "grows up, it turns into one of several kinds: two kinds of child and three kinds of " +
                "adult. Which one depends on how often its needs were left unmet while it was awake. Keep " +
                "it fed, happy, clean and healthy for the best results.\n\nThe next time you see it after " +
                "it hatches or grows up, it flickers among sparkles and reveals its new form. Its kind is " +
                "shown at the top of the pet screen.",
        ),
        Section(
            "ADVENTURES AND GENERATIONS",
            "After two weeks as an adult, your pet sets off on an adventure. The next time you look, " +
                "it waves goodbye and walks off, leaving an egg behind: the next generation.\n\nIt goes " +
                "into your Album, and the new egg starts afresh (you'll be asked to name it once it " +
                "hatches). The Album shows when your current pet will set off.",
        ),
        Section(
            "ALBUM",
            "Tap ALBUM to see your keepsakes: every form you've raised (faint outlines are ones you " +
                "haven't raised yet), every pet that has gone on its adventure, how many friends you've " +
                "met, and your walking records.\n\nTry raising all six forms. Different care leads to " +
                "different ones.",
        ),
        Section(
            "MINI-GAMES",
            "Tap PLAY to pick a game. Each game has 5 rounds. Get 3 or more right to win.\n\nLEFT OR " +
                "RIGHT: guess which way your pet will look.\n\nHIGHER OR LOWER: your pet shows a number. " +
                "Guess whether the next number will be higher or lower.",
        ),
        Section(
            "MEETING OTHER PETS",
            "Got a friend with Glyph Pet? Both open the app, tap MEET, and hold your phones close " +
                "together (within about a metre).\n\nWhen the pets find each other they appear side by " +
                "side and wave, with hearts between them. Meeting a friend cheers your pet up a lot, but " +
                "only the first time each day with each friend. The screen shows how many friends your " +
                "pet has met.\n\nMeeting uses Bluetooth, so turn it on, and allow Nearby devices when " +
                "asked. Only a tiny anonymous message is shared: that it's a Glyph Pet, which kind, and a " +
                "random number to tell friends apart. No names, accounts or location. Your pet can't meet " +
                "anyone while it's still an egg.",
        ),
        Section(
            "HOME-SCREEN WIDGET",
            "To add it: long-press an empty spot on your home screen, tap Widgets, find Glyph Pet and " +
                "drag it out.\n\nThe widget has the same buttons as the app. PLAY opens the mini-games. " +
                "Tap the pet to open the app.",
        ),
        Section(
            "GLYPH MATRIX",
            "To add it: open your Glyph settings, go to Glyph Toys and add Glyph Pet. Then tap the " +
                "button on the back of the phone until your pet appears.\n\nLong-press the back button to " +
                "choose an action. Each long-press moves to the next one: Feed, Play (or Meds if it's " +
                "ill), Clean, then nothing. The chosen action shows as a red icon. Wait 2 seconds and it " +
                "happens. Long-press until nothing is shown to cancel. While it's still an egg, a " +
                "long-press just makes it wobble.\n\nYou can also shake the phone to play, or to wake " +
                "it.\n\nEach time you pick Glyph Pet, or turn the phone face-down while it's awake, it " +
                "waves hello. It only waves when it's awake, not while it's asleep or napping (in the dark or on the charger).\n\nYour pet animates for a " +
                "minute after you touch the back button. If you leave it on the always-on Glyph, it wakes " +
                "for a little bounce once a minute.",
        ),
        Section(
            "GLYPH GAMES",
            "Choosing Play on the Glyph Matrix starts a 25-second tilt game. The two games take " +
                "turns. Hold the phone with the lights facing you:\n\nCATCH: tilt left and right to move " +
                "your pet under the falling red treats. Catch 6 to win.\n\nROLL: tilt the phone to roll " +
                "the red ball into your pet's open mouth. Score 3 to win.\n\nYour score shows at the end, " +
                "and winning makes it much happier. Long-press the back button to stop a game early. (A " +
                "quick tap of the back button switches to another Glyph Toy, so the games use tilting " +
                "instead.)",
        ),
        Section(
            "GLYPH NUDGES",
            "Want a gentle reminder? Switch on Glyph nudges below. About every half hour, if your pet " +
                "needs something and your phone is lying face-down, the icon of what it needs blinks on " +
                "the Glyph Matrix for a few seconds. No sound and no notification.\n\nNudges stay quiet " +
                "at night, at daycare, while Glyph Pet is already showing on the back, and never come " +
                "more than once an hour.",
        ),
        Section(
            "STARTING OVER",
            "At the bottom of the pet screen, START OVER gives you a new egg. Your current pet is " +
                "gone for good (it doesn't go into the Album), so only use it if you mean it.",
        ),
    )
}
