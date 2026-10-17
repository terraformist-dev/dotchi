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
    ): DotGrid {
        val g = DotGrid(SIZE, SIZE)
        val mood = moodOverride ?: state.mood(now)
        val stage = state.stage(now)

        drawGround(g)

        if (stage == Stage.EGG) {
            drawEgg(g, state, now, anim, animate)
        } else {
            drawPoops(g, state.poops, anim, animate)
            drawPet(g, Art.body(stage, state.form(now)), mood, anim, animate, look)
        }

        when {
            topText != null -> {
                val x = (SIZE - DotFont.width(topText)) / 2
                g.text(topText, x, 1)
            }
            hud -> drawTop(g, state, mood, anim, animate, selected, selectProgress)
        }

        // Anything outside the real matrix shape stays dark.
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (!GlyphShape.contains(x, y)) g.set(x, y, 0)
        }
        return g
    }

    private fun drawGround(g: DotGrid) {
        for (x in 3..21 step 2) g.set(x, GROUND_Y, 40)
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

    private fun drawPet(g: DotGrid, body: Body, mood: Mood, anim: Long, animate: Boolean, look: Int) {
        val sprite = body.sprite

        val lift = if (!animate) 0 else when (mood) {
            Mood.PLAYING -> if ((anim / 180) % 2 == 0L) 2 else 0
            Mood.HAPPY, Mood.OK, Mood.CLEANING -> ((anim / 500) % 2).toInt()
            Mood.EATING -> ((anim / 250) % 2).toInt()
            Mood.SAD -> if ((anim / 1400) % 2 == 0L) 0 else 1
            else -> 0
        }
        // An ill pet shivers from side to side instead of bouncing.
        val shiver = if (animate && mood == Mood.SICK) (if ((anim / 300) % 2 == 0L) 0 else 1) else 0
        val left = CENTER_X - sprite.width / 2 + shiver
        val top = FEET_Y - sprite.height + 1 - lift
        val level = when (mood) {
            Mood.SLEEPING -> 150
            Mood.SICK -> 120
            else -> 255
        }
        g.stamp(sprite, left, top, level)

        // Eyes
        val ey = top + body.eyeY
        val blink = animate && (anim % 3400) < 160
        val eyesClosed = mood == Mood.SLEEPING || mood == Mood.PLAYING || mood == Mood.SICK ||
            mood == Mood.MEDICATED || blink
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

        // Mouth
        val mx = left + body.mouthX + look
        val my = top + body.mouthY
        when (mood) {
            Mood.HAPPY, Mood.PLAYING, Mood.CLEANING -> smile(g, mx, my)
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
            Mood.CLEANING -> {
                val flip = animate && (anim / 300) % 2 == 0L
                if (flip) { g.stamp(Art.SPARK, 4, 6); g.stamp(Art.SPARK, 18, 9) }
                else { g.stamp(Art.SPARK, 18, 5); g.stamp(Art.SPARK, 4, 10) }
            }
            Mood.SLEEPING -> {
                val rise = if (animate && (anim / 900) % 2 == 0L) 1 else 0
                g.stamp(Art.ZED, 16, 3 - rise, level = 170)
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
