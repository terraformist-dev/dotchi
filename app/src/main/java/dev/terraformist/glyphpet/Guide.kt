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
            "SHAKE TO PLAY",
            "Give your phone a good shake for a quick game of chase: your pet dashes about and " +
                "gets a bit happier. This works with the app open, and on the Glyph Matrix while your " +
                "pet is awake there (for a minute after you touch the back button).\n\n" +
                "An ill pet or an egg won't play.",
        ),
        Section(
            "SLEEP AND NAPS",
            "It sleeps from 11pm to 7am and needs much less looking after overnight. " +
                "You'll see little Zs above it.\n\n" +
                "It also naps during the day:\n\n" +
                "When it's dark, like a dark room or your pocket, it dozes off. When the lights come " +
                "back on, it wakes up. While napping its needs build up more slowly.\n\n" +
                "When your phone is charging, it naps on the charger, shown by a little lightning bolt. " +
                "Charging naps slowly cheer it up. It wakes when you unplug.\n\n" +
                "Feeding, cleaning or playing with it wakes it for a minute.\n\n" +
                "Your phone notices the light and the charger whenever the app is open, the Glyph " +
                "Matrix pet is awake, or the widget refreshes (about every half hour). So a nap can " +
                "take a little while to show on the widget.",
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
            "To add it: open your Glyph settings, go to Glyph Toys and add Glyph Pet. " +
                "Then tap the button on the back of the phone until your pet appears.\n\n" +
                "Long-press the back button to choose an action. Each long-press moves to the next one: " +
                "Feed, Play (or Meds if it's ill), Clean, then nothing. The chosen action shows as a red " +
                "icon. Wait 2 seconds and it happens. Long-press until nothing is shown to cancel.\n\n" +
                "Play on the Glyph Matrix is a quick play, not a game. You can also shake the phone " +
                "to play.\n\n" +
                "Each time you pick Glyph Pet, or turn the phone face-down while it's awake, it peeks " +
                "up from below and waves hello. Saying hello wakes it from a dark-room nap, because " +
                "the light sensor is on the screen side and can't see the light when the phone is " +
                "face-down.\n\n" +
                "Your pet animates for a minute after you touch the back button. If you leave it on the " +
                "always-on Glyph, it wakes for a little bounce once a minute.",
        ),
        Section(
            "STARTING OVER",
            "At the bottom of the pet screen, START OVER gives you a new egg. Your current pet is " +
                "gone for good, so only use it if you mean it.",
        ),
    )
}
