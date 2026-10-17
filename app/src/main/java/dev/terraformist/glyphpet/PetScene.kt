package dev.terraformist.glyphpet

/**
 * The pet's look at each stage/form, plus where its face goes.
 * Every body leaves at least one lit row between the bottom of the eyes and the top of the mouth.
 */
private class Body(
    val sprite: Sprite,
    val eyeLeftX: Int,
    val eyeRightX: Int,
    val eyeY: Int,
    val eyeWidth: Int,
    val mouthX: Int,
    val mouthY: Int,
)

private object Art {
    val EGG = Sprite(
        "..###..",
        ".##+##.",
        ".#####.",
        "##+####",
        "#####+#",
        "#######",
        "#+#####",
        ".###+#.",
        "..###..",
    )

    val BABY = Body(
        Sprite(
            "..#####..",
            ".#######.",
            "#########",
            "#########",
            "#########",
            "#########",
            "#########",
            ".#######.",
            "..##.##..",
        ),
        eyeLeftX = 2, eyeRightX = 6, eyeY = 2, eyeWidth = 1, mouthX = 4, mouthY = 5,
    )

    // Child, well looked after: perky ears.
    val PIP = Body(
        Sprite(
            ".#.......#.",
            ".##.....##.",
            ".#########.",
            "###########",
            "###########",
            "###########",
            "###########",
            "###########",
            ".#########.",
            "..#######..",
            "..##...##..",
        ),
        eyeLeftX = 3, eyeRightX = 7, eyeY = 4, eyeWidth = 1, mouthX = 5, mouthY = 7,
    )

    // Child, often neglected: a lumpy blob with stubby feet.
    val GRUB = Body(
        Sprite(
            "...#####...",
            "..#######..",
            ".#########.",
            ".#########.",
            "###########",
            "###########",
            "###########",
            "###########",
            "###########",
            ".##.....##.",
        ),
        eyeLeftX = 3, eyeRightX = 7, eyeY = 3, eyeWidth = 1, mouthX = 5, mouthY = 6,
    )

    // Adult, excellent care: antenna and long legs.
    val NOVA = Body(
        Sprite(
            "......#......",
            ".....###.....",
            "..#########..",
            ".###########.",
            "#############",
            "#############",
            "#############",
            "#############",
            "#############",
            "#############",
            ".###########.",
            "..##.....##..",
            "..##.....##..",
        ),
        eyeLeftX = 3, eyeRightX = 8, eyeY = 5, eyeWidth = 2, mouthX = 6, mouthY = 8,
    )

    // Adult, decent care: round, with a sprout on top.
    val BUD = Body(
        Sprite(
            ".......##....",
            "......##.....",
            "......#......",
            "...#######...",
            ".###########.",
            "#############",
            "#############",
            "#############",
            "#############",
            "#############",
            ".###########.",
            "...#######...",
            "...##...##...",
        ),
        eyeLeftX = 3, eyeRightX = 8, eyeY = 5, eyeWidth = 2, mouthX = 6, mouthY = 8,
    )

    // Adult, poor care: a wide, low, drippy blob.
    val GLOOP = Body(
        Sprite(
            "....#####....",
            "..#########..",
            ".###########.",
            "#############",
            "#############",
            "#############",
            "#############",
            "#############",
            ".###########.",
            "#.#.#...#.#.#",
        ),
        eyeLeftX = 3, eyeRightX = 8, eyeY = 3, eyeWidth = 2, mouthX = 6, mouthY = 6,
    )

    val FOOD = Sprite(
        "...#.",
        ".###.",
        "#####",
        "#####",
        ".###.",
    )
    val PLAY = Sprite(
        ".###.",
        "##.##",
        "#.#.#",
        "##.##",
        ".###.",
    )
    val CLEAN = Sprite(
        "..#..",
        "..#..",
        "##.##",
        "..#..",
        "..#..",
    )
    val PILL = Sprite(
        "...##",
        "..###",
        ".###.",
        "###..",
        "##...",
    )
    val HEART = Sprite(
        ".#.#.",
        "#####",
        ".###.",
        "..#..",
    )
    val ZED = Sprite(
        "####",
        "..#.",
        ".#..",
        "####",
    )
    val POOP = Sprite(
        ".#.",
        ".##",
        "###",
    )
    val BOLT = Sprite(
        "..#",
        ".#.",
        "###",
        ".#.",
        "#..",
    )
    val SUN = Sprite(
        "#.#.#",
        ".###.",
        "#####",
        ".###.",
        "#.#.#",
    )
    val MOON = Sprite(
        ".###",
        "##..",
        "#...",
        "##..",
        ".###",
    )
    val CLOUD = Sprite(
        "..##..",
        ".####.",
        "######",
    )
    val FOG = Sprite(
        "#####",
        ".....",
        ".####",
        ".....",
        "####.",
    )
    val HAND = Sprite(
        "##",
        "##",
    )
    val SPARK = Sprite(
        ".#.",
        "###",
        ".#.",
    )

    fun icon(action: Action) = when (action) {
        Action.FEED -> FOOD
        Action.PLAY -> PLAY
        Action.CLEAN -> CLEAN
        Action.MEDICINE -> PILL
    }

    fun body(stage: Stage, form: Form?): Body = when (form) {
        Form.PIP -> PIP
        Form.GRUB -> GRUB
        Form.NOVA -> NOVA
        Form.BUD -> BUD
        Form.GLOOP -> GLOOP
        null -> if (stage == Stage.ADULT) NOVA else if (stage == Stage.CHILD) PIP else BABY
    }
}

/**
 * Draws the whole 25x25 scene. Used by the Glyph Matrix, the widget and the app,
 * so the pet looks identical everywhere.
 */
object PetScene {

    private const val SIZE = GlyphShape.SIZE
    private const val GROUND_Y = 20
    private const val FEET_Y = 19
    private const val CENTER_X = 12


    /**
     * @param animate false for a still picture.
     * @param selected the action picked with the Glyph Button but not done yet.
     * @param selectProgress 0..1, how far through the "wait to confirm" countdown.
     * @param anim the clock used for animation (bounce, blink). Defaults to [now]; the widget
     *   passes made-up times to pre-draw a short looping animation.
     * @param look -1 = looking left, 0 = ahead, 1 = right (mini-games).
     * @param moodOverride show this mood instead of the real one (mini-game reactions).
     * @param topText text shown above the pet instead of the usual icons (mini-games).
     * @param hud false hides the icons above the pet.
     * @param hello 0..1 progress of the hello wave, or negative for none.
     * @param weather local weather to show (falling rain/snow, a little sky icon), if known.
     */
    fun render(
        state: PetState,
        now: Long,
        animate: Boolean,
        selected: Action? = null,
        selectProgress: Float = 0f,
        anim: Long = now,
        look: Int = 0,
        moodOverride: Mood? = null,
        topText: String? = null,
        hud: Boolean = true,
        hello: Float = -1f,
        weather: WeatherNow? = null,
    ): DotGrid {
        val g = DotGrid(SIZE, SIZE)
        val greeting = hello >= 0f && hello <= 1f
        val mood = when {
            moodOverride != null -> moodOverride
            greeting -> Mood.HAPPY
            else -> state.mood(now)
        }
        val stage = state.stage(now)

        // Weather goes in first, so the pet stands in front of the rain.
        if (weather != null) drawWeather(g, weather, anim, animate)

        if (stage == Stage.EGG) {
            drawEgg(g, state, now, anim, animate)
        } else {
            drawPoops(g, state.poops, anim, animate)
            val waking = if (mood == Mood.WAKING) (now - state.wokenAt).toFloat() / PetState.WAKING_ANIM_MS else -1f
            drawPet(g, Art.body(stage, state.form(now)), mood, anim, animate, look, greeting, waking)
        }

        // Walking: the ground slides past underneath.
        val scroll = if (animate && mood == Mood.WALKING) ((anim / 200) % 2).toInt() else 0
        drawGround(g, scroll)

        when {
            topText != null -> {
                val x = (SIZE - DotFont.width(topText)) / 2
                g.text(topText, x, 1)
            }
            hud -> {
                drawTop(g, state, mood, anim, animate, selected, selectProgress)
                if (weather != null && selected == null) drawSkyIcon(g, weather)
            }
        }

        // Anything outside the real matrix shape stays dark.
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (!GlyphShape.contains(x, y)) g.set(x, y, 0)
        }
        return g
    }

    // ---- Meeting another Glyph Pet ----------------------------------------------------

    /** A one-byte code for which pet this is, sent to other phones when meeting. */
    fun formCode(state: PetState, now: Long): Int = when (state.stage(now)) {
        Stage.EGG -> 0
        Stage.BABY -> 1
        else -> when (state.form(now)) {
            Form.PIP -> 2
            Form.GRUB -> 3
            Form.NOVA -> 4
            Form.BUD -> 5
            Form.GLOOP -> 6
            null -> 1
        }
    }

    fun nameForCode(code: Int): String = when (code) {
        0 -> "EGG"
        2 -> Form.PIP.label
        3 -> Form.GRUB.label
        4 -> Form.NOVA.label
        5 -> Form.BUD.label
        6 -> Form.GLOOP.label
        else -> "BABY"
    }

    private fun bodyForCode(code: Int): Body = when (code) {
        2 -> Art.PIP
        3 -> Art.GRUB
        4 -> Art.NOVA
        5 -> Art.BUD
        6 -> Art.GLOOP
        else -> Art.BABY
    }

    const val MEET_WIDTH = 33
    const val MEET_HEIGHT = 21

    /**
     * Two pets side by side: yours on the left facing right, your friend on the right facing
     * left, with hearts between them. Without a friend ([friendCode] null), yours looks around.
     */
    fun renderMeeting(state: PetState, now: Long, friendCode: Int?, anim: Long = now): DotGrid {
        val g = DotGrid(MEET_WIDTH, MEET_HEIGHT)
        val feet = MEET_HEIGHT - 2
        for (x in 1 until MEET_WIDTH step 2) g.set(x, feet + 1, 40)

        val mine = Art.body(state.stage(now), state.form(now))
        if (friendCode == null) {
            // Searching: in the middle, glancing left and right.
            val look = if ((anim / 900) % 2 == 0L) -1 else 1
            drawPet(g, mine, Mood.OK, anim, animate = true, look = look, waving = false, waking = -1f,
                centerX = MEET_WIDTH / 2, feetY = feet)
            // "..." searching dots above it.
            val lit = ((anim / 400) % 4).toInt()
            for (i in 0 until 3) g.set(MEET_WIDTH / 2 - 2 + i * 2, 1, if (i < lit) 200 else 40)
            return g
        }

        drawPet(g, mine, Mood.HAPPY, anim, animate = true, look = 1, waving = false, waking = -1f,
            centerX = 8, feetY = feet)
        drawPet(g, bodyForCode(friendCode), Mood.HAPPY, anim + 250, animate = true, look = -1,
            waving = true, waking = -1f, centerX = MEET_WIDTH - 9, feetY = feet)
        // Hearts drifting up between them.
        val rise = ((anim / 300) % 4).toInt()
        g.stamp(Art.HEART, MEET_WIDTH / 2 - 2, 4 - rise.coerceAtMost(3), red = true)
        return g
    }

    // ---- Shared bits ------------------------------------------------------------------

    private fun drawGround(g: DotGrid, scroll: Int = 0) {
        for (x in 0 until SIZE) g.set(x, GROUND_Y, 0)
        for (x in (3 + scroll)..21 step 2) g.set(x, GROUND_Y, 40)
    }

    /** Falling rain or snow, faint, behind everything. */
    private fun drawWeather(g: DotGrid, w: WeatherNow, anim: Long, animate: Boolean) {
        val t = if (animate) anim else 0L
        when (w.sky) {
            Sky.RAIN, Sky.STORM -> {
                val columns = if (w.sky == Sky.STORM) intArrayOf(3, 5, 7, 9, 11, 13, 15, 17, 19, 21)
                else intArrayOf(4, 8, 12, 16, 20)
                columns.forEachIndexed { i, x ->
                    val y = ((t / 90 + i * 7) % 19).toInt()
                    g.set(x, y, 60)
                    g.set(x, y - 1, 25)
                }
            }
            Sky.SNOW -> {
                val columns = intArrayOf(4, 9, 14, 19, 6, 17)
                columns.forEachIndexed { i, x ->
                    val y = ((t / 350 + i * 5) % 19).toInt()
                    val drift = ((t / 700 + i) % 2).toInt()
                    g.set(x + drift, y, 90)
                }
            }
            else -> {}
        }
    }

    /** A small, faint sky icon in the top-left: sun, moon, cloud or fog. */
    private fun drawSkyIcon(g: DotGrid, w: WeatherNow) {
        val icon = when (w.sky) {
            Sky.CLEAR -> if (w.isDay) Art.SUN else Art.MOON
            Sky.FOG -> Art.FOG
            else -> Art.CLOUD
        }
        g.stamp(icon, 4, 4, level = 90)
    }

    private fun drawEgg(g: DotGrid, state: PetState, now: Long, anim: Long, animate: Boolean) {
        val egg = Art.EGG
        val age = now - state.bornAt
        val nearlyHatched = age > PetState.EGG_MS * 6 / 10
        val wobble = if (animate && nearlyHatched) intArrayOf(0, 1, 0, -1)[((anim / 200) % 4).toInt()] else 0
        val left = CENTER_X - egg.width / 2 + wobble
        val top = FEET_Y - egg.height + 1
        g.stamp(egg, left, top)
        if (age > PetState.EGG_MS / 2) {
            // A crack across the middle.
            g.carve(left + 1, top + 4)
            g.carve(left + 2, top + 5)
            g.carve(left + 3, top + 4)
            g.carve(left + 4, top + 5)
            g.carve(left + 5, top + 4)
        }
    }

    private fun drawPoops(g: DotGrid, count: Int, anim: Long, animate: Boolean) {
        val spots = listOf(2 to 17, 20 to 17, 2 to 13)
        for (i in 0 until minOf(count, spots.size)) {
            val (x, y) = spots[i]
            g.stamp(Art.POOP, x, y, level = 170)
            // A little wisp of smell.
            val wisp = !animate || ((anim / 400) + i) % 2 == 0L
            if (wisp) g.set(x + 1, y - 2, 70)
        }
    }

    private fun drawPet(
        g: DotGrid,
        body: Body,
        mood: Mood,
        anim: Long,
        animate: Boolean,
        look: Int,
        waving: Boolean,
        waking: Float,
        centerX: Int = CENTER_X,
        feetY: Int = FEET_Y,
    ) {
        val sprite = body.sprite
        // Waking up: first a sleepy wriggle, then a jump with wide eyes.
        val wakingWriggle = waking in 0f..0.3f
        val wakingJump = waking > 0.3f

        val lift = if (!animate) 0 else when (mood) {
            Mood.PLAYING -> if ((anim / 180) % 2 == 0L) 2 else 0
            Mood.HAPPY, Mood.OK, Mood.CLEANING -> ((anim / 500) % 2).toInt()
            Mood.EATING -> ((anim / 250) % 2).toInt()
            Mood.SAD -> if ((anim / 1400) % 2 == 0L) 0 else 1
            Mood.WALKING -> ((anim / 250) % 2).toInt()
            Mood.WAKING -> if (wakingJump) intArrayOf(3, 2, 1, 0, 0, 0)[(((waking - 0.3f) / 0.7f) * 6).toInt().coerceIn(0, 5)] else 0
            else -> 0
        }
        // An ill pet shivers from side to side instead of bouncing.
        val shiver = if (animate && mood == Mood.SICK) (if ((anim / 300) % 2 == 0L) 0 else 1) else 0
        // Playing: dash from side to side as well as hopping.
        val dash = if (animate && mood == Mood.PLAYING) intArrayOf(0, 2, 3, 2, 0, -2, -3, -2)[((anim / 150) % 8).toInt()] else 0
        val wriggle = if (animate && wakingWriggle) (if ((anim / 90) % 2 == 0L) -1 else 1) else 0
        val left = centerX - sprite.width / 2 + shiver + dash + wriggle
        val top = feetY - sprite.height + 1 - lift
        val level = when {
            mood == Mood.SLEEPING || wakingWriggle -> 150
            mood == Mood.SICK -> 120
            else -> 255
        }
        g.stamp(sprite, left, top, level)

        // Eyes
        val ey = top + body.eyeY
        val blink = animate && (anim % 3400) < 160
        val eyesClosed = mood == Mood.SLEEPING || mood == Mood.PLAYING || mood == Mood.SICK ||
            mood == Mood.MEDICATED || wakingWriggle || (blink && mood != Mood.WAKING)
        for (ex in listOf(left + body.eyeLeftX + look, left + body.eyeRightX + look)) {
            for (dx in 0 until body.eyeWidth) {
                if (eyesClosed) {
                    g.carve(ex + dx, ey + 1)
                } else {
                    g.carve(ex + dx, ey)
                    g.carve(ex + dx, ey + 1)
                }
            }
        }

        // Hello: a little hand waving beside it.
        if (waving) {
            val up = (anim / 220) % 2 == 0L
            g.stamp(Art.HAND, left + sprite.width + 1, top + if (up) 1 else 3)
        }

        // Mouth
        val mx = left + body.mouthX + look
        val my = top + body.mouthY
        when (mood) {
            Mood.HAPPY, Mood.PLAYING, Mood.CLEANING, Mood.WALKING -> smile(g, mx, my)
            Mood.WAKING -> if (wakingWriggle) flat(g, mx, my) else openMouth(g, mx, my)
            Mood.SAD, Mood.SICK -> frown(g, mx, my)
            Mood.EATING -> if (!animate || (anim / 250) % 2 == 0L) openMouth(g, mx, my) else flat(g, mx, my)
            Mood.SLEEPING -> g.carve(mx, my + 1)
            else -> flat(g, mx, my)
        }
    }

    private fun smile(g: DotGrid, x: Int, y: Int) {
        g.carve(x - 2, y); g.carve(x + 2, y)
        g.carve(x - 1, y + 1); g.carve(x, y + 1); g.carve(x + 1, y + 1)
    }

    private fun frown(g: DotGrid, x: Int, y: Int) {
        g.carve(x - 1, y); g.carve(x, y); g.carve(x + 1, y)
        g.carve(x - 2, y + 1); g.carve(x + 2, y + 1)
    }

    private fun flat(g: DotGrid, x: Int, y: Int) {
        g.carve(x - 1, y + 1); g.carve(x, y + 1); g.carve(x + 1, y + 1)
    }

    private fun openMouth(g: DotGrid, x: Int, y: Int) {
        for (dx in -1..1) { g.carve(x + dx, y); g.carve(x + dx, y + 1) }
    }

    /** The space above the pet: menu choice, reactions, sleep Zs, or a call for attention. */
    private fun drawTop(
        g: DotGrid,
        state: PetState,
        mood: Mood,
        anim: Long,
        animate: Boolean,
        selected: Action?,
        selectProgress: Float,
    ) {
        val iconX = 10
        val iconY = 1

        if (selected != null) {
            g.stamp(Art.icon(selected), iconX, iconY, red = true)
            // Three dots that run out while waiting to confirm.
            val remaining = 3 - (selectProgress.coerceIn(0f, 1f) * 3).toInt()
            for (i in 0 until remaining) g.set(10 + i * 2, 0, 200)
            return
        }

        when (mood) {
            Mood.PLAYING -> g.stamp(Art.HEART, iconX, iconY + 1, red = true)
            Mood.EATING -> g.stamp(Art.FOOD, iconX, iconY, level = 120)
            Mood.MEDICATED -> g.stamp(Art.PILL, iconX, iconY, level = 120)
            Mood.WAKING -> g.text("!", 11, iconY)
            Mood.CLEANING -> {
                val flip = animate && (anim / 300) % 2 == 0L
                if (flip) { g.stamp(Art.SPARK, 4, 6); g.stamp(Art.SPARK, 18, 9) }
                else { g.stamp(Art.SPARK, 18, 5); g.stamp(Art.SPARK, 4, 10) }
            }
            Mood.SLEEPING -> {
                val rise = if (animate && (anim / 900) % 2 == 0L) 1 else 0
                if (state.nap == Nap.CHARGING) {
                    // Napping on the charger: a little lightning bolt instead of Zs.
                    g.stamp(Art.BOLT, 17, 3 - rise, level = 200)
                } else {
                    g.stamp(Art.ZED, 16, 3 - rise, level = 170)
                }
            }
            Mood.SAD, Mood.SICK -> {
                val need = state.mostNeeded()
                if (need != null) {
                    val on = !animate || (anim / 600) % 2 == 0L
                    if (on) g.stamp(Art.icon(need), iconX, iconY, red = true)
                }
            }
            else -> {}
        }
    }
}
