package dev.terraformist.glyphpet

/**
 * The in-app Guide. Written by the owner in his own words (build 12): keep his voice, and change
 * only what a feature change requires.
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
            "WHAT IS DOTCHI?",
            "Dotchi is a tiny creature that lives on your phone. It gets hungry, bored and sometimes " +
                "makes a mess. Please take care of it.\n\nIt grows quickly! Dotchi will grow based on how " +
                "you look after it.\n\nDotchi is comfortable living in the app, or on a widget. It is " +
                "especially comfortable in the Nothing Phone 3, where it also lives happily on the back " +
                "of your phone.",
        ),
        Section(
            "GETTING STARTED",
            "Your Dotchi starts as an egg. After 3 minutes it hatches into a baby. Give it a name by " +
                "tapping DOTCHI at the top of the screen (you can choose up to 8 letters or numbers). Tap " +
                "the name at any time to change it.",
        ),
        Section(
            "THE BUTTONS",
            "FEED fills its tummy.\n\nPLAY opens up some minigames. Playing makes it happier, and " +
                "winning makes it much happier.\n\nCLEAN tidies up its mess. It needs the toilet every " +
                "few hours.\n\nUnder the buttons: GUIDE (this page), ALBUM (your keepsakes) and MEET " +
                "(visit a friend’s pet).",
        ),
        Section(
            "READING THE SIGNS",
            "The dots under each button show how it’s doing. The more dots the better.\n\nIf it's " +
                "red, it needs something now.",
        ),
        Section(
            "GETTING ILL",
            "If mess is left lying around or it goes hungry for too long, it falls ill. An ill Dotchi " +
                "gets unhappy faster, and doesn't want to play.\n\nTap MEDS to give it some medicine. It " +
                "won’t enjoy the taste, but it’ll be fine.",
        ),
        Section(
            "SLEEP AND NAPS",
            "Your Dotchi has very regular sleep patterns. It sleeps from 11pm to 7am and conserves " +
                "its energy.\n\nIt also naps during the day:\n\nWhen it’s dark, like a dark room or your " +
                "pocket, it dozes off. When the lights come back on, it wakes up. Its needs build up " +
                "slower while napping.\n\nWhen you plug your phone in to charge, it naps on the charger. " +
                "Charging naps slowly cheer it up, and is jolted awake when you unplug.\n\nFeeding, " +
                "cleaning or playing with it wakes it for just a minute before it lulls itself back to " +
                "sleep.",
        ),
        Section(
            "SHAKING",
            "Give your phone a good shake (three firm shakes in about a second).\n\nIf your Dotchi is " +
                "awake, it enjoys a quick game of chase.\n\nIf it’s asleep or napping, a shake wakes it " +
                "up instead. If it's taking a charger nap, it stays awake for about 15 minutes before " +
                "snuggling back up to the charge.",
        ),
        Section(
            "DAYCARE",
            "Busy day? Tap SEND TO DAYCARE near the bottom of the screen. For up to 8 hours its needs " +
                "barely change.\n\nTap the daycare line again to pick it up early. Daycare can be used " +
                "once a day.",
        ),
        Section(
            "WALKS",
            "Your Dotchi comes with you on walks. Every 1,000 steps cheers it up a little and makes " +
                "it a bit hungry, so keep an eye on FEED.\n\nWalk 5,000 steps in a day for a big boost of " +
                "happiness.\n\nThe app asks for permission the first time you open it. If you said no, " +
                "you can turn on physical activity for Dotchi in your phone’s settings, under Apps.",
        ),
        Section(
            "WEATHER",
            "Your Dotchi sees the weather where you are. You can see it in the top corner.\n\nWeather " +
                "changes how it feels. On rainy days it’s stuck indoors and gets restless, but playing " +
                "with it counts for more. Dotchi loves snow. On hot days it gets hungrier.\n\nThe app " +
                "asks for you to share your location when you open it. If you said no to location, you " +
                "can turn it on in your phone’s settings, under Apps.",
        ),
        Section(
            "SPECIAL DAYS",
            "Dotchi all share the same birthday: 10 June. There are other holidays it celebrates " +
                "through the year. Keep an eye out!",
        ),
        Section(
            "GROWING UP",
            "Dotchi is a baby for the first day. Then it turns into a child, and then an adult.\n\nIt " +
                "is very sensitive to how it's treated and will grow differently according to how you " +
                "treat it. Keep it fed, happy, clean and healthy for the best results.",
        ),
        Section(
            "ADVENTURES AND GENERATIONS",
            "After two weeks as an adult, your pet sets off on an adventure. It leaves you a gift: an " +
                "egg. The next generation.\n\nYour adult Dotchi is remembered in your Album, and you " +
                "start afresh with your new Dotchi.",
        ),
        Section(
            "ALBUM",
            "Tap ALBUM to remember your keepsakes. It includes every form you’ve raised, every Dotchi " +
                "that has gone on its adventure, how many friends you’ve met, and your walking " +
                "records.\n\nTry raising all six forms. Different types of care lead to different " +
                "outcomes.",
        ),
        Section(
            "MINIGAMES",
            "Tap PLAY to pick a game. Each game has 5 rounds. Get 3 or more right to win.\n\nLEFT OR " +
                "RIGHT: guess which way your pet will look.\n\nHIGHER OR LOWER: your pet shows a number. " +
                "Guess whether the next number will be higher or lower.",
        ),
        Section(
            "MEETING OTHER PETS",
            "Got a friend with a Dotchi? Meet up with them!\n\nYou both open the app, tap MEET, and " +
                "hold your phones close together.\n\nMeeting a friend cheers your Dotchi up a lot, but " +
                "only the first time each day with each friend.\n\nMeeting uses Bluetooth, so turn it on " +
                "and allow nearby devices when asked. Only a tiny anonymous message is shared with the " +
                "other phone to show that it's a unique Dotchi. Nothing else is shared.",
        ),
        Section(
            "HOME SCREEN WIDGET",
            "To add a widget, long-press an empty spot on your home screen, tap Widgets, find Dotchi " +
                "and drag it out.",
        ),
        Section(
            "GLYPH MATRIX",
            "To add Dotchi to your Nothing Phone 3 glyph matrix, open your Glyph settings, go to " +
                "Glyph Toys and add Dotchi. Then tap the button on the back of the phone until your " +
                "Dotchi appears.\n\nLong-press the back button to choose an action. Each long-press moves " +
                "to the next one: Feed, Play (or Meds if it’s ill), Clean, then nothing. Wait 2 seconds " +
                "and the selected action will happen. To cancel an action, long-press until nothing is " +
                "shown.",
        ),
        Section(
            "GLYPH GAMES",
            "Choosing Play on the Glyph Matrix starts a 25-second tilt game. The two games take " +
                "turns. Hold the phone with the lights facing you:\n\nCATCH: tilt left and right to move " +
                "your Dotchi under the falling red treats. Catch 6 to win.\n\nROLL: tilt the phone to " +
                "roll the ball into your Dotchi's open mouth. Score 3 to win.\n\nYour score shows at the " +
                "end, and winning makes it much happier. Long-press the back button to stop a game early.",
        ),
        Section(
            "GLYPH NUDGES",
            "Want a gentle reminder of your Dotchi's needs? Switch on Glyph nudges below. When your " +
                "Dotchi needs something and your phone is lying face-down, what it needs blinks on the " +
                "Glyph Matrix for a few seconds. If it's happy, you won't see anything, and you'll never " +
                "get more than one nudge an hour.\n\nNudges " +
                "stay quiet at night, at daycare, and while Dotchi is already showing on the back of your " +
                "phone.",
        ),
        Section(
            "STARTING OVER",
            "At the bottom of the screen, START OVER gives you a new egg. Your current Dotchi will be " +
                "gone for good (it doesn’t go into the Album), so only use it if you mean it.",
        ),
    )
}
