package dev.terraformist.glyphpet

/** The pet's look at each stage, plus where its face goes. */
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
            ".#######.",
            "..##.##..",
        ),
        eyeLeftX = 2, eyeRightX = 6, eyeY = 2, eyeWidth = 1, mouthX = 4, mouthY = 4,
    )

    val CHILD = Body(
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
        eyeLeftX = 3, eyeRightX = 7, eyeY = 4, eyeWidth = 1, mouthX = 5, mouthY = 6,
    )

    val ADULT = Body(
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
    }
}

/**
 * Draws the whole 25x25 scene. Used by both the Glyph Matrix and the widget,
 * so the pet looks identical in both places.
 */
object PetScene {

    private const val SIZE = GlyphShape.SIZE
    private const val GROUND_Y = 20
    private const val FEET_Y = 19
    private const val CENTER_X = 12

    /**
     * @param animate false for a still picture (widget, always-on display).
     * @param selected the action picked with the Glyph Button but not done yet.
     * @param selectProgress 0..1, how far through the "wait to confirm" countdown.
     */
    fun render(
        state: PetState,
        now: Long,
        animate: Boolean,
        selected: Action? = null,
        selectProgress: Float = 0f,
    ): DotGrid {
        val g = DotGrid(SIZE, SIZE)
        val mood = state.mood(now)
        val stage = state.stage(now)

        drawGround(g)

        if (stage == Stage.EGG) {
            drawEgg(g, state, now, animate)
        } else {
            drawPoops(g, state.poops, now, animate)
            drawPet(g, stage, mood, now, animate)
        }

        drawTop(g, state, mood, now, animate, selected, selectProgress)

        // Anything outside the real matrix shape stays dark.
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (!GlyphShape.contains(x, y)) g.set(x, y, 0)
        }
        return g
    }

    private fun drawGround(g: DotGrid) {
        for (x in 3..21 step 2) g.set(x, GROUND_Y, 40)
    }

    private fun drawEgg(g: DotGrid, state: PetState, now: Long, animate: Boolean) {
        val egg = Art.EGG
        val age = now - state.bornAt
        val nearlyHatched = age > PetState.EGG_MS * 6 / 10
        val wobble = if (animate && nearlyHatched) intArrayOf(0, 1, 0, -1)[((now / 200) % 4).toInt()] else 0
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

    private fun drawPoops(g: DotGrid, count: Int, now: Long, animate: Boolean) {
        val spots = listOf(2 to 17, 20 to 17, 2 to 13)
        for (i in 0 until minOf(count, spots.size)) {
            val (x, y) = spots[i]
            g.stamp(Art.POOP, x, y, level = 170)
            // A little wisp of smell.
            val wisp = !animate || ((now / 400) + i) % 2 == 0L
            if (wisp) g.set(x + 1, y - 2, 70)
        }
    }

    private fun drawPet(g: DotGrid, stage: Stage, mood: Mood, now: Long, animate: Boolean) {
        val body = when (stage) {
            Stage.BABY -> Art.BABY
            Stage.CHILD -> Art.CHILD
            else -> Art.ADULT
        }
        val sprite = body.sprite

        val lift = if (!animate) 0 else when (mood) {
            Mood.PLAYING -> if ((now / 180) % 2 == 0L) 2 else 0
            Mood.HAPPY, Mood.OK, Mood.CLEANING -> ((now / 500) % 2).toInt()
            Mood.EATING -> ((now / 250) % 2).toInt()
            Mood.SAD -> if ((now / 1400) % 2 == 0L) 0 else 1
            else -> 0
        }
        val left = CENTER_X - sprite.width / 2
        val top = FEET_Y - sprite.height + 1 - lift
        val level = if (mood == Mood.SLEEPING) 150 else 255
        g.stamp(sprite, left, top, level)

        // Eyes
        val ey = top + body.eyeY
        val blink = animate && (now % 3400) < 160
        val eyesClosed = mood == Mood.SLEEPING || mood == Mood.PLAYING || blink
        for (ex in listOf(left + body.eyeLeftX, left + body.eyeRightX)) {
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
        val mx = left + body.mouthX
        val my = top + body.mouthY
        when (mood) {
            Mood.HAPPY, Mood.PLAYING, Mood.CLEANING -> smile(g, mx, my)
            Mood.SAD -> frown(g, mx, my)
            Mood.EATING -> if (!animate || (now / 250) % 2 == 0L) openMouth(g, mx, my) else flat(g, mx, my)
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
        now: Long,
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
            Mood.CLEANING -> {
                val flip = animate && (now / 300) % 2 == 0L
                if (flip) { g.stamp(Art.SPARK, 4, 6); g.stamp(Art.SPARK, 18, 9) }
                else { g.stamp(Art.SPARK, 18, 5); g.stamp(Art.SPARK, 4, 10) }
            }
            Mood.SLEEPING -> {
                val rise = if (animate && (now / 900) % 2 == 0L) 1 else 0
                g.stamp(Art.ZED, 16, 3 - rise, level = 170)
            }
            Mood.EGG -> {}
            else -> {
                val need = state.mostNeeded()
                if (need != null && mood == Mood.SAD) {
                    val on = !animate || (now / 600) % 2 == 0L
                    if (on) g.stamp(Art.icon(need), iconX, iconY, red = true)
                }
            }
        }
    }
}
