package dev.terraformist.glyphpet

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val headY: Int = 0, // first full row of the head (hats sit on it)
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
        eyeLeftX = 3, eyeRightX = 7, eyeY = 4, eyeWidth = 1, mouthX = 5, mouthY = 7, headY = 2,
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
        eyeLeftX = 3, eyeRightX = 8, eyeY = 5, eyeWidth = 2, mouthX = 6, mouthY = 8, headY = 2,
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
        eyeLeftX = 3, eyeRightX = 8, eyeY = 5, eyeWidth = 2, mouthX = 6, mouthY = 8, headY = 3,
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
    // Seasonal hats and decorations. '#' is drawn in red, '+' in white.
    val PARTY_HAT = Sprite(
        "..+..",
        "..#..",
        ".###.",
        "#####",
    )
    val PUMPKIN_HAT = Sprite(
        "..+..",
        ".###.",
        "#####",
    )
    val SANTA_HAT = Sprite(
        "....+",
        "..##.",
        ".###.",
        "+++++",
    )
    val LANTERN = Sprite(
        ".+.",
        "###",
        "###",
        "###",
        ".+.",
    )
    val HOUSE = Sprite(
        "..#..",
        ".###.",
        "#####",
        "#.#.#",
        "#.###",
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

    fun hat(season: Season?): Sprite? = when (season) {
        Season.BIRTHDAY, Season.NEW_YEAR -> PARTY_HAT
        Season.HALLOWEEN -> PUMPKIN_HAT
        Season.CHRISTMAS -> SANTA_HAT
        else -> null
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
     * @param season a special day to dress up for (hats, confetti, lanterns).
     * @param poke the egg was just tapped: give it a wobble.
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
        season: Season? = Season.on(now),
        poke: Boolean = false,
    ): DotGrid {
        if (state.isFarewell(now)) return mask(renderFarewell(state, now, anim, season))
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
            drawEgg(g, state, now, anim, animate, poke)
        } else {
            drawPoops(g, state.poops, anim, animate)
            val waking = if (mood == Mood.WAKING) (now - state.wokenAt).toFloat() / PetState.WAKING_ANIM_MS else -1f
            val growing = if (mood == Mood.GROWING) (now - state.cutsceneAt).toFloat() / PetState.GROW_MS else -1f
            val medicine = if (mood == Mood.MEDICATED) (now - state.lastActionAt).toFloat() / PetState.MEDICINE_WAVE_MS else -1f
            val body = Art.body(stage, state.form(now))
            // After a good feed, a win or a friend: faint rings ripple out around it, and a heart floats up.
            val glow = state.glowProgress(now)
            if (animate && glow >= 0f && moodOverride == null && mood in GLOW_MOODS) {
                val shape = DotGrid(SIZE, SIZE)
                drawPet(shape, body, mood, anim, animate, look, greeting, waking, season = season)
                drawGlow(g, shape, anim)
            }
            drawPet(
                g, body, mood, anim, animate, look, greeting, waking,
                season = season, growing = growing, medicine = medicine,
            )
            // Hatching: the egg bursts into light, which fades out from the middle.
            if (stage == Stage.BABY && growing in 0f..HATCH_FLASH) drawHatchFlash(g, growing / HATCH_FLASH)
        }

        // Walking: the ground slides past underneath.
        val scroll = if (animate && mood == Mood.WALKING) ((anim / 200) % 2).toInt() else 0
        drawGround(g, scroll)
        if (weather?.sky == Sky.SNOW) drawSnowCover(g)

        when {
            topText != null -> {
                val x = (SIZE - DotFont.width(topText)) / 2
                g.text(topText, x, 1)
            }
            hud -> {
                drawTop(g, state, mood, anim, animate, selected, selectProgress, now)
                if (selected == null) {
                    if (weather != null) drawSkyIcon(g, weather)
                    if (season != null && stage != Stage.EGG) drawSeasonExtras(g, season, anim, animate)
                }
            }
        }
        return mask(g)
    }

    // ---- Switching the Glyph Matrix on and off (like an old TV) -------------------------

    /** Switching on: a dot appears in the middle, then the picture opens out from it. [p] is 0..1. */
    fun powerOn(scene: DotGrid, p: Float): DotGrid {
        val out = DotGrid(SIZE, SIZE)
        if (p < 0.3f) {
            centreDot(out, ease(p / 0.3f))
        } else {
            val r = ease((p - 0.3f) / 0.7f) * 18f
            copyWithin(scene, out, r)
            if (r < 2f) centreDot(out, 1f)
        }
        return mask(out)
    }

    /** Switching off: the picture shrinks into one glowing dot, which then fades. [p] is 0..1. */
    fun powerOff(scene: DotGrid, p: Float): DotGrid {
        val out = DotGrid(SIZE, SIZE)
        if (p < 0.65f) {
            val q = p / 0.65f
            copyWithin(scene, out, 17f * (1f - ease(q)))
            if (q > 0.5f) centreDot(out, ease((q - 0.5f) / 0.5f))
        } else {
            centreDot(out, 1f - (p - 0.65f) / 0.35f)
        }
        return mask(out)
    }

    private fun copyWithin(from: DotGrid, to: DotGrid, r: Float) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (hypot((x - 12).toFloat(), (y - 12).toFloat()) <= r) to.set(x, y, from.level(x, y), from.isRed(x, y))
        }
    }

    private fun centreDot(g: DotGrid, strength: Float) {
        val v = (255 * strength.coerceIn(0f, 1f)).roundToInt()
        g.set(12, 12, max(g.level(12, 12), v))
        for ((x, y) in listOf(11 to 12, 13 to 12, 12 to 11, 12 to 13)) g.set(x, y, max(g.level(x, y), v * 3 / 10))
    }

    /** Anything outside the real matrix shape stays dark. */
    private fun mask(g: DotGrid): DotGrid {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (!GlyphShape.contains(x, y)) g.set(x, y, 0)
        }
        return g
    }

    /**
     * Goodbye: the old adult waves, then walks off to the right on its adventure,
     * and the egg it left behind fades in.
     */
    private fun renderFarewell(state: PetState, now: Long, anim: Long, season: Season?): DotGrid {
        val g = DotGrid(SIZE, SIZE)
        val p = ((now - state.cutsceneAt).toFloat() / PetState.FAREWELL_MS).coerceIn(0f, 1f)
        val walk = ((p - 0.35f) / 0.5f).coerceIn(0f, 1f)
        val body = Art.body(Stage.ADULT, state.farewellForm ?: Form.GLOOP)
        drawPet(
            g, body, if (walk > 0f) Mood.WALKING else Mood.HAPPY, anim, animate = true,
            look = if (walk > 0f) 1 else 0, waving = walk == 0f, waking = -1f,
            centerX = CENTER_X + (walk * 20).toInt(), season = season,
        )
        if (p > 0.65f) {
            val level = (((p - 0.65f) / 0.25f).coerceIn(0f, 1f) * 255).toInt().coerceAtLeast(40)
            g.stamp(Art.EGG, CENTER_X - Art.EGG.width / 2, FEET_Y - Art.EGG.height + 1, level)
        }
        drawGround(g, if (walk > 0f && walk < 1f) ((anim / 200) % 2).toInt() else 0)
        return g
    }

    // ---- Public helpers for the Glyph games and the Album -------------------------------

    /** Draw this pet anywhere on a grid (for the Glyph games). */
    fun drawPetAt(g: DotGrid, state: PetState, now: Long, centerX: Int, feetY: Int, mood: Mood, anim: Long, look: Int = 0) {
        drawPet(g, Art.body(state.stage(now), state.form(now)), mood, anim, animate = true, look = look,
            waving = false, waking = -1f, centerX = centerX, feetY = feetY)
    }

    /** Width of this pet's body in dots. */
    fun bodyWidth(state: PetState, now: Long): Int = Art.body(state.stage(now), state.form(now)).sprite.width

    fun bodyHeight(state: PetState, now: Long): Int = Art.body(state.stage(now), state.form(now)).sprite.height

    /**
     * A big, simple frame for a Glyph nudge: the icon of what the pet needs, three times size,
     * pulsing like a heartbeat with a faint ripple. [t] is milliseconds since the nudge began.
     */
    fun nudgeFrame(need: Action, t: Long): DotGrid {
        val g = DotGrid(SIZE, SIZE)
        val icon = Art.icon(need)
        val level = (30 + 225 * heartbeat(t)).roundToInt()
        for (y in 0 until icon.height) for (x in 0 until icon.width) {
            if (icon.at(x, y) == '#') {
                for (dy in 0 until 3) for (dx in 0 until 3) g.set(5 + x * 3 + dx, 5 + y * 3 + dy, level)
            }
        }
        ripple(g, t, 12f, 12f, 8.5f, 4f, 0..24)
        return mask(g)
    }

    /** A small picture of a form for the Album: drawn properly if [known], otherwise a faint outline. */
    fun portrait(code: Int, known: Boolean): DotGrid {
        val g = DotGrid(15, 15)
        val body = Art.body(if (code <= 1) Stage.BABY else Stage.CHILD, PetState.formOfCode(code))
        if (known) {
            drawPet(g, body, Mood.HAPPY, 0L, animate = false, look = 0, waving = false, waking = -1f,
                centerX = 7, feetY = 13)
        } else {
            g.stamp(body.sprite, 7 - body.sprite.width / 2, 13 - body.sprite.height + 1, level = 35)
        }
        for (x in 1 until 15 step 2) g.set(x, 14, 30)
        return g
    }

    // ---- Meeting another Dotchi -------------------------------------------------------

    /** A one-byte code for which pet this is, sent to other phones when meeting. */
    fun formCode(state: PetState, now: Long): Int = state.formCode(now)

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

    /**
     * Falling rain or snow, behind everything. Drops and flakes come at three depths: far ones
     * are dim and slow, near ones bright and quick. Raindrops have fading tails and splash.
     */
    private fun drawWeather(g: DotGrid, w: WeatherNow, anim: Long, animate: Boolean) {
        val t = if (animate) anim else 0L
        when (w.sky) {
            Sky.RAIN, Sky.STORM -> {
                val drops = if (w.sky == Sky.STORM) RAIN + STORM_EXTRA else RAIN
                for (d in drops) {
                    val lv = RAIN_LEVEL[d.depth]
                    val y = ((t / RAIN_STEP_MS[d.depth] + d.offset) % 23).toInt() - 2
                    if (y < GROUND_Y) g.set(d.x, y, lv)
                    g.set(d.x, y - 1, lv * 45 / 100)
                    g.set(d.x, y - 2, lv * 18 / 100)
                    if (d.depth > 0 && y == GROUND_Y) {
                        g.set(d.x - 1, GROUND_Y - 1, lv / 2)
                        g.set(d.x + 1, GROUND_Y - 1, lv / 2)
                    }
                }
            }
            Sky.SNOW -> {
                for (d in SNOW) {
                    val step = SNOW_STEP_MS[d.depth]
                    val y = ((t / step + d.offset) % 22).toInt() - 1
                    val sway = SWAY[((t / (step * 2) + d.offset) % SWAY.size).toInt()] * (if (d.depth == 0) 0 else 1)
                    if (y < GROUND_Y) g.set(d.x + sway, y, SNOW_LEVEL[d.depth])
                }
            }
            else -> {}
        }
    }

    /** Snow lying on the ground. */
    private fun drawSnowCover(g: DotGrid) {
        for (x in 0 until SIZE) {
            if (GlyphShape.contains(x, GROUND_Y + 1)) g.set(x, GROUND_Y + 1, if (x % 3 == 0) 60 else 35)
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

    private fun drawEgg(g: DotGrid, state: PetState, now: Long, anim: Long, animate: Boolean, poke: Boolean) {
        val egg = Art.EGG
        val age = now - state.bornAt
        val nearlyHatched = age > PetState.EGG_MS * 6 / 10
        val wobble = if (animate && (nearlyHatched || poke)) intArrayOf(0, 1, 0, -1)[((anim / 200) % 4).toInt()] else 0
        val left = CENTER_X - egg.width / 2 + wobble
        val top = FEET_Y - egg.height + 1
        // It glows from inside, pulsing faster as hatching gets closer.
        val ready = (age.toFloat() / PetState.EGG_MS).coerceIn(0f, 1f)
        val hz = 0.5f + 0.5f * (ready * 5).toInt().coerceIn(0, 4)
        val glow = if (animate) 0.5f + 0.5f * sin(2.0 * Math.PI * hz * (anim % 60_000L) / 1000.0).toFloat() else 0.5f
        for (y in 0 until egg.height) for (x in 0 until egg.width) {
            when (egg.at(x, y)) {
                '#' -> g.set(left + x, top + y, 170)
                '+' -> g.set(left + x, top + y, (60 + 195 * glow * (0.35f + 0.65f * ready)).roundToInt())
            }
        }
        if (age > PetState.EGG_MS / 2) {
            // A crack across the middle, with the light shining through it.
            val light = (110 + 145 * glow).roundToInt()
            for ((x, y) in listOf(1 to 4, 2 to 5, 3 to 4, 4 to 5, 5 to 4)) g.set(left + x, top + y, light)
        }
    }

    /** The flash as the egg hatches. [p] is 0..1. */
    private fun drawHatchFlash(g: DotGrid, p: Float) {
        val fade = 1f - ease(p)
        val reach = 5f + 16f * p
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val d = hypot((x - CENTER_X).toFloat(), (y - 15).toFloat())
            val v = (255 * fade * (1.25f - d / reach).coerceIn(0f, 1f)).roundToInt()
            if (v > g.level(x, y)) g.set(x, y, v)
        }
    }

    /** Faint rings rippling out from around the pet, and a flickering heart floating up behind it. */
    private fun drawGlow(g: DotGrid, shape: DotGrid, anim: Long) {
        val lit = ArrayList<Int>()
        for (i in shape.levels.indices) if (shape.levels[i] > 0) lit.add(i)
        if (lit.isEmpty()) return
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (shape.level(x, y) > 0 || g.level(x, y) > 0 || !GlyphShape.contains(x, y)) continue
            var best = 99f
            for (i in lit) {
                val d = hypot((x - i % SIZE).toFloat(), (y - i / SIZE).toFloat())
                if (d < best) best = d
            }
            if (best > 7f) continue
            val wave = 0.5f + 0.5f * kotlin.math.cos(2.0 * Math.PI * (best / 2.8 - (anim % 13_000L) / 1300.0)).toFloat()
            val v = (52 * wave * exp(-(best - 1f) / 2.3f)).roundToInt()
            if (v > 0) g.set(x, y, v)
        }
        val hp = (anim % 2000L) / 2000f
        val flickerOn = (anim / 90) % 6 != 4L
        if (hp in 0.05f..0.95f && flickerOn) g.stamp(Art.HEART, 16 + (hp * 1.5f).toInt(), 8 - (hp * 5f).toInt(), red = true)
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
        season: Season? = null,
        growing: Float = -1f,
        medicine: Float = -1f,
    ) {
        val sprite = body.sprite
        // Medicine: a bright wave runs down through it, then it's well again.
        val wave = mood == Mood.MEDICATED && medicine in 0f..1f
        // Growing up: flickers in and out among sparkles, then settles.
        val flicker = growing in 0f..0.6f && animate && (anim / 110) % 2 == 0L
        // Waking up: first a sleepy wriggle, then a jump with wide eyes.
        val wakingWriggle = waking in 0f..0.3f
        val wakingJump = waking > 0.3f

        val lift = if (!animate) 0 else when (mood) {
            Mood.PLAYING -> if ((anim / 180) % 2 == 0L) 2 else 0
            Mood.HAPPY, Mood.OK, Mood.CLEANING -> ((anim / 500) % 2).toInt()
            Mood.EATING -> ((anim / 250) % 2).toInt()
            Mood.SAD -> if ((anim / 1400) % 2 == 0L) 0 else 1
            Mood.WALKING -> ((anim / 250) % 2).toInt()
            Mood.STUFFED -> if ((anim / 400) % 2 == 0L) 0 else 1
            Mood.WAKING -> if (wakingJump) intArrayOf(3, 2, 1, 0, 0, 0)[(((waking - 0.3f) / 0.7f) * 6).toInt().coerceIn(0, 5)] else 0
            else -> 0
        }
        // An ill pet shivers from side to side instead of bouncing.
        val shiver = if (animate && mood == Mood.SICK) (if ((anim / 300) % 2 == 0L) 0 else 1) else 0
        // Playing: dash from side to side as well as hopping.
        val dash = if (animate && mood == Mood.PLAYING) intArrayOf(0, 2, 3, 2, 0, -2, -3, -2)[((anim / 150) % 8).toInt()] else 0
        val wriggle = if (animate && wakingWriggle) (if ((anim / 90) % 2 == 0L) -1 else 1) else 0
        // Turning down a game: shakes its head (eyes and mouth swing side to side).
        val headShake = if (mood == Mood.REFUSING) (if (!animate || (anim / 200) % 2 == 0L) -1 else 1) else 0
        val left = centerX - sprite.width / 2 + shiver + dash + wriggle
        val top = feetY - sprite.height + 1 - lift
        val level = when {
            flicker -> 60
            mood == Mood.SLEEPING || wakingWriggle -> 150
            else -> 255
        }
        val front = if (wave) -1.5f + medicine * (sprite.height + 3) else 0f
        if (wave && animate) {
            // The wave's edge glows faintly across the whole matrix.
            val bandY = top + front.roundToInt()
            for (x in 0 until g.width) if (g.level(x, bandY) == 0) g.set(x, bandY, 45)
        }
        val t = if (animate) anim else 0L
        for (y in 0 until sprite.height) for (x in 0 until sprite.width) {
            if (sprite.at(x, y) != '#') continue
            // Ill: a slow, blotchy shimmer instead of an even glow.
            val ill = !flicker && (mood == Mood.SICK || (wave && y > front))
            val base = if (ill) (120 + 50 * illShimmer(x, y, t)).roundToInt() else level
            val shade = if (flicker) 1f else shading(sprite, x, y)
            g.set(left + x, top + y, (base * shade).roundToInt())
        }
        // A little blush on the cheeks.
        if (!flicker) {
            for (cx in listOf(body.mouthX - 4, body.mouthX + 4)) {
                if (sprite.at(cx, body.mouthY) == '#') {
                    g.set(left + cx, top + body.mouthY, g.level(left + cx, top + body.mouthY) / 2)
                }
            }
        }

        // Eyes
        val ey = top + body.eyeY
        val blink = animate && (anim % 3400) < 160
        val eyesClosed = mood == Mood.SLEEPING || mood == Mood.PLAYING || mood == Mood.SICK ||
            wave || mood == Mood.STUFFED || wakingWriggle || (blink && mood != Mood.WAKING)
        val turn = look + headShake
        for (ex in listOf(left + body.eyeLeftX + turn, left + body.eyeRightX + turn)) {
            for (dx in 0 until body.eyeWidth) {
                if (eyesClosed) {
                    g.carve(ex + dx, ey + 1)
                } else {
                    g.carve(ex + dx, ey)
                    g.carve(ex + dx, ey + 1)
                }
            }
        }

        // Special days: a hat sitting on its head.
        Art.hat(season)?.let { hat ->
            val hx = centerX + (left - (centerX - sprite.width / 2)) - hat.width / 2
            val hy = top + body.headY - hat.height
            for (y in 0 until hat.height) for (x in 0 until hat.width) {
                when (hat.at(x, y)) {
                    '#' -> g.set(hx + x, hy + y, 255, red = true)
                    '+' -> g.set(hx + x, hy + y, 255)
                }
            }
        }

        // Hello: a little hand waving beside it.
        if (waving) {
            val up = (anim / 220) % 2 == 0L
            g.stamp(Art.HAND, left + sprite.width + 1, top + if (up) 1 else 3)
        }

        // Mouth
        val mx = left + body.mouthX + turn
        val my = top + body.mouthY
        when (mood) {
            Mood.HAPPY, Mood.PLAYING, Mood.CLEANING, Mood.WALKING, Mood.GROWING -> smile(g, mx, my)
            Mood.MEDICATED -> if (wave) flat(g, mx, my) else smile(g, mx, my)
            Mood.WAKING -> if (wakingWriggle) flat(g, mx, my) else openMouth(g, mx, my)
            Mood.SAD, Mood.SICK, Mood.REFUSING -> frown(g, mx, my)
            Mood.STUFFED -> flat(g, mx, my)
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
        now: Long,
    ) {
        val iconX = 10
        val iconY = 1

        // At daycare: a little house in the corner.
        if (selected == null && state.isAtDaycare(now) && mood != Mood.SLEEPING) {
            g.stamp(Art.HOUSE, 17, 4, level = 110)
        }

        if (selected != null) {
            g.stamp(Art.icon(selected), iconX, iconY, red = true)
            // Three dots that run out while waiting to confirm.
            val remaining = 3 - (selectProgress.coerceIn(0f, 1f) * 3).toInt()
            for (i in 0 until remaining) g.set(10 + i * 2, 0, 200)
            return
        }

        when (mood) {
            // The heart flickers, Tamagotchi-style.
            Mood.PLAYING -> if (!animate || (anim / 90) % 7 != 2L && (anim / 90) % 7 != 5L) {
                g.stamp(Art.HEART, iconX, iconY + 1, red = true)
            }
            Mood.EATING -> g.stamp(Art.FOOD, iconX, iconY, level = 120)
            Mood.MEDICATED -> {
                val p = (now - state.lastActionAt).toFloat() / PetState.MEDICINE_WAVE_MS
                if (p < 1f) {
                    g.stamp(Art.PILL, iconX, iconY, level = (120 * (1f - p.coerceIn(0f, 1f))).roundToInt())
                } else {
                    // Well again: a few sparkles that fade away.
                    val fade = (1f - (p - 1f) / 1.5f).coerceIn(0f, 1f)
                    val rise = ((p - 1f) * 1.5f).toInt()
                    for ((x, y) in listOf(3 to 7, 19 to 8, 17 to 3)) g.stamp(Art.SPARK, x, y - rise, level = (220 * fade).roundToInt())
                }
            }
            Mood.STUFFED -> g.stamp(Art.FOOD, iconX, iconY, level = 70)
            Mood.REFUSING -> g.stamp(Art.PLAY, iconX, iconY, level = 70)
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
            Mood.GROWING -> {
                // Sparkles circling round it.
                val spots = listOf(4 to 7, 19 to 6, 3 to 13, 20 to 12, 7 to 3, 16 to 3)
                val phase = ((anim / 150) % spots.size).toInt()
                for (i in spots.indices) {
                    if ((i + phase) % 2 == 0) g.stamp(Art.SPARK, spots[i].first, spots[i].second)
                }
            }
            Mood.SAD, Mood.SICK -> {
                val need = state.mostNeeded()
                if (need != null) {
                    // Pulses like a heartbeat (two beats, then a rest), with a faint ripple.
                    val beat = if (animate) heartbeat(anim) else 1f
                    g.stamp(Art.icon(need), iconX, iconY, level = (40 + 215 * beat).roundToInt(), red = true)
                    if (animate) ripple(g, anim, iconX + 2f, iconY + 2f, 3f, 4.5f, 0..7)
                }
            }
            else -> {}
        }
    }

    /** Extra touches on special days (hats are drawn with the pet). */
    private fun drawSeasonExtras(g: DotGrid, season: Season, anim: Long, animate: Boolean) {
        val t = if (animate) anim else 0L
        when (season) {
            Season.BIRTHDAY -> {
                // Confetti drifting down.
                val columns = intArrayOf(3, 6, 18, 21, 9, 15)
                columns.forEachIndexed { i, x ->
                    val y = ((t / 220 + i * 4) % 17).toInt() + 1
                    g.set(x, y, 200, red = i % 2 == 0)
                }
            }
            Season.NEW_YEAR -> {
                // Two little fireworks bursting in turn.
                val burst = ((t / 400) % 4).toInt()
                val (cx, cy) = if ((t / 1600) % 2 == 0L) 5 to 6 else 19 to 5
                val r = burst.coerceAtMost(2)
                for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to 1, -1 to 1, 1 to -1)) {
                    g.set(cx + dx * r, cy + dy * r, if (burst == 3) 60 else 220, red = burst == 1)
                }
            }
            Season.CHINESE_NEW_YEAR -> {
                // A red lantern swaying on the right, and a few sparks.
                val sway = if ((t / 700) % 2 == 0L) 0 else 1
                g.set(19 + sway, 3, 120)
                for (y in 0 until Art.LANTERN.height) for (x in 0 until Art.LANTERN.width) {
                    when (Art.LANTERN.at(x, y)) {
                        '#' -> g.set(18 + sway + x, 4 + y, 255, red = true)
                        '+' -> g.set(18 + sway + x, 4 + y, 200)
                    }
                }
                val spark = ((t / 300) % 3).toInt()
                g.set(5 + spark, 5 - spark, 200, red = true)
            }
            else -> {}
        }
    }

    // ---- Little helpers for the softer animations -----------------------------------------

    private val GLOW_MOODS = setOf(Mood.HAPPY, Mood.OK, Mood.SAD, Mood.WALKING, Mood.EATING, Mood.PLAYING, Mood.CLEANING)
    private const val HATCH_FLASH = 0.3f

    private class Drop(val x: Int, val depth: Int, val offset: Int)

    // depth 0 = far (dim, slow), 1 = middle, 2 = near (bright, quick)
    private val RAIN = listOf(
        Drop(3, 2, 0), Drop(6, 0, 5), Drop(8, 1, 11), Drop(11, 2, 3),
        Drop(14, 0, 8), Drop(16, 1, 15), Drop(19, 2, 6), Drop(21, 0, 13),
    )
    private val STORM_EXTRA = listOf(Drop(4, 1, 9), Drop(9, 2, 17), Drop(13, 1, 2), Drop(18, 0, 19), Drop(20, 1, 4))
    private val RAIN_LEVEL = intArrayOf(35, 70, 115)
    private val RAIN_STEP_MS = longArrayOf(140L, 95L, 60L)
    private val SNOW = listOf(
        Drop(3, 0, 2), Drop(7, 0, 14), Drop(12, 0, 8), Drop(17, 0, 19), Drop(21, 0, 5),
        Drop(5, 1, 10), Drop(10, 1, 1), Drop(15, 1, 16), Drop(20, 1, 7),
        Drop(8, 2, 4), Drop(14, 2, 12), Drop(19, 2, 18),
    )
    private val SNOW_LEVEL = intArrayOf(30, 70, 140)
    private val SNOW_STEP_MS = longArrayOf(650L, 420L, 260L)
    private val SWAY = intArrayOf(0, 1, 1, 0, -1, -1)

    private fun ease(p: Float): Float {
        val q = p.coerceIn(0f, 1f)
        return q * q * (3 - 2 * q)
    }

    /** 0..1: two quick beats, then a rest, every 1.8 seconds. */
    private fun heartbeat(t: Long): Float {
        val c = (t % 1800L).toFloat()
        fun bump(m: Float, w: Float): Float { val d = (c - m) / w; return exp(-d * d) }
        return (bump(150f, 130f) + 0.75f * bump(520f, 150f)).coerceIn(0f, 1f)
    }

    /** A faint ring spreading out from (cx, cy) after each heartbeat, only on unlit dots in [rows]. */
    private fun ripple(g: DotGrid, t: Long, cx: Float, cy: Float, from: Float, spread: Float, rows: IntRange) {
        val c = t % 1800L
        if (c < 150L) return
        val k = (c - 150L) / 1650f
        val r = from + k * spread
        for (y in rows) for (x in 0 until g.width) {
            if (g.level(x, y) != 0 || !GlyphShape.contains(x, y)) continue
            val near = 1f - abs(hypot(x - cx, y - cy) - r) / 0.8f
            if (near > 0f) g.set(x, y, (40 * near * (1f - k)).roundToInt())
        }
    }

    /** -1..1, drifting slowly across the body: how an ill pet's glow goes blotchy. */
    private fun illShimmer(x: Int, y: Int, t: Long): Float {
        val s = (t % 6_930_000L).toDouble()
        return ((sin(x * 1.7 + s / 700) + sin(y * 2.3 - s / 900) + sin((x + y) * 1.1 + s / 500) +
            sin((x - y) * 0.9 - s / 1100)) / 4).toFloat()
    }

    /** Soft shading: a little brighter towards the top left, a little dimmer at the edges, so it looks round. */
    private fun shading(sprite: Sprite, x: Int, y: Int): Float {
        val hx = sprite.width * 0.32f
        val hy = sprite.height * 0.32f
        val reach = max(sprite.width, sprite.height) * 0.82f
        var v = 1f - 0.5f * ((hypot(x - hx, y - hy) - 1f) / reach).coerceIn(0f, 1f)
        val edge = sprite.at(x - 1, y) != '#' || sprite.at(x + 1, y) != '#' ||
            sprite.at(x, y - 1) != '#' || sprite.at(x, y + 1) != '#'
        if (edge) v *= 0.75f
        return v
    }
}
