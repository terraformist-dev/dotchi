package dev.terraformist.glyphpet

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Little games played on the Glyph Matrix by tilting the phone (the back button's quick tap
 * switches toys, so it can't be used for games). Looking at the back of the phone:
 * tilt left and right to move; tilt the top towards or away from you to roll.
 *
 * Each lasts [DURATION_MS], then shows the score for a moment before it counts.
 */
sealed class GlyphGame(val startedAt: Long) {

    var score = 0
        protected set
    abstract val winScore: Int

    /** Advance one frame. [tiltX]/[tiltY] are accelerometer readings in m/s². */
    abstract fun step(now: Long, tiltX: Float, tiltY: Float, state: PetState)

    abstract fun draw(g: DotGrid, state: PetState, now: Long)

    fun playing(now: Long) = now - startedAt < DURATION_MS
    fun finished(now: Long) = now - startedAt >= DURATION_MS + SCORE_SHOW_MS
    val won get() = score >= winScore

    /** The end screen: the score above the pet, happy or glum. */
    fun drawResult(g: DotGrid, state: PetState, now: Long) {
        PetScene.drawPetAt(g, state, now, 12, 19, if (won) Mood.PLAYING else Mood.SAD, now)
        val text = score.toString()
        g.text(text, (25 - DotFont.width(text)) / 2, 2, red = won)
        for (x in 3..21 step 2) g.set(x, 20, 40)
    }

    companion object {
        const val DURATION_MS = 25_000L
        const val SCORE_SHOW_MS = 2_500L
    }
}

/** Catch: tilt to move your pet under the falling treats. */
class CatchGame(startedAt: Long) : GlyphGame(startedAt) {
    override val winScore = 6

    private var petX = 12f
    private var treatX = Random.nextInt(5, 20)
    private var treatY = 1f
    private var munchUntil = 0L

    override fun step(now: Long, tiltX: Float, tiltY: Float, state: PetState) {
        val half = PetScene.bodyWidth(state, now) / 2
        petX = (petX + tiltX * 0.12f).coerceIn((2 + half).toFloat(), (22 - half).toFloat())
        treatY += 0.22f + score * 0.02f
        val petTop = 19 - PetScene.bodyHeight(state, now) + 1
        if (treatY >= petTop) {
            if (abs(treatX - petX) <= half + 0.5f) {
                score++
                munchUntil = now + 400
            }
            treatX = Random.nextInt(5, 20)
            treatY = 1f
        }
    }

    override fun draw(g: DotGrid, state: PetState, now: Long) {
        for (x in 3..21 step 2) g.set(x, 20, 40)
        PetScene.drawPetAt(g, state, now, petX.toInt(), 19, if (now < munchUntil) Mood.EATING else Mood.HAPPY, now)
        val y = treatY.toInt()
        g.set(treatX, y, 255, red = true)
        g.set(treatX + 1, y, 255, red = true)
        g.set(treatX, y + 1, 255, red = true)
        g.set(treatX + 1, y + 1, 255, red = true)
    }
}

/** Roll: tilt to roll the ball into your pet's open mouth. */
class RollGame(startedAt: Long) : GlyphGame(startedAt) {
    override val winScore = 3

    private var bx = 12f
    private var by = 4f
    private var vx = 0f
    private var vy = 0f
    private var munchUntil = 0L

    override fun step(now: Long, tiltX: Float, tiltY: Float, state: PetState) {
        vx = (vx + tiltX * 0.012f) * 0.94f
        vy = (vy + tiltY * 0.012f) * 0.94f
        bx += vx
        by += vy
        // Keep the ball inside the round matrix, bouncing softly off the edge.
        val dx = bx - 12f
        val dy = by - 12f
        val dist = sqrt(dx * dx + dy * dy)
        val limit = 10.5f
        if (dist > limit) {
            bx = 12f + dx / dist * limit
            by = 12f + dy / dist * limit
            vx *= -0.4f
            vy *= -0.4f
        }
        val (mx, my) = mouth(state, now)
        val ddx = bx - mx
        val ddy = by - my
        if (ddx * ddx + ddy * ddy < 2.5f) {
            score++
            munchUntil = now + 500
            bx = Random.nextInt(6, 19).toFloat()
            by = 3f
            vx = 0f
            vy = 0f
        }
    }

    /** Roughly where the pet's mouth is when it stands in the middle. */
    private fun mouth(state: PetState, now: Long): Pair<Float, Float> {
        val top = 19 - PetScene.bodyHeight(state, now) + 1
        return 12f to (top + PetScene.bodyHeight(state, now) * 0.55f)
    }

    override fun draw(g: DotGrid, state: PetState, now: Long) {
        for (x in 3..21 step 2) g.set(x, 20, 40)
        PetScene.drawPetAt(g, state, now, 12, 19, Mood.EATING, if (now < munchUntil) now else 0L)
        val x = bx.toInt()
        val y = by.toInt()
        g.set(x, y, 255, red = true)
        g.set(x + 1, y, 255, red = true)
        g.set(x, y + 1, 255, red = true)
        g.set(x + 1, y + 1, 255, red = true)
    }
}
