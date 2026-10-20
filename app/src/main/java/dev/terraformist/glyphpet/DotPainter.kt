package dev.terraformist.glyphpet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.ImageView

/** Paints a [DotGrid] as round dots, Nothing-style: white on black, one red accent, faint unlit dots. */
object DotPainter {

    const val RED = 0xFFD71921.toInt()
    private const val GHOST = 0xFF1F1F1F.toInt()

    /**
     * @param pitch pixels between dot centres.
     * @param shape if given, only dots inside it are drawn (ghost dots included), like the Glyph Matrix.
     */
    fun paint(
        grid: DotGrid,
        pitch: Int,
        shape: ((Int, Int) -> Boolean)? = null,
        ghosts: Boolean = true,
        reuse: Bitmap? = null,
    ): Bitmap {
        val w = grid.width * pitch
        val h = grid.height * pitch
        // Paint over an existing picture of the same size if we're given one (saves making a new one each frame).
        val bmp = if (reuse != null && reuse.isMutable && reuse.width == w && reuse.height == h) {
            reuse.also { it.eraseColor(Color.TRANSPARENT) }
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = pitch * 0.40f

        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                if (shape != null && !shape(x, y)) continue
                val level = grid.level(x, y)
                val color = when {
                    level <= 0 -> if (ghosts) GHOST else continue
                    // Red dims towards the ghost grey too, so red things can fade and pulse.
                    grid.isRed(x, y) -> if (level >= 255) RED else Color.rgb(
                        0x1F + (0xD7 - 0x1F) * level / 255,
                        0x1F + (0x19 - 0x1F) * level / 255,
                        0x1F + (0x21 - 0x1F) * level / 255,
                    )
                    else -> {
                        // Blend from the ghost grey up to white by brightness.
                        val v = 0x1F + ((0xFF - 0x1F) * level / 255)
                        Color.rgb(v, v, v)
                    }
                }
                paint.color = color
                canvas.drawCircle(x * pitch + pitch / 2f, y * pitch + pitch / 2f, radius, paint)
            }
        }
        return bmp
    }

    fun paintPet(grid: DotGrid, pitch: Int = 16): Bitmap =
        paint(grid, pitch, shape = { x, y -> GlyphShape.contains(x, y) })
}

/**
 * A dot picture shown on screen that keeps one bitmap and only repaints it when the dots
 * actually change. Looks exactly the same as painting a fresh picture every frame, but
 * does far less work.
 */
class LiveDots(private val view: ImageView, private val pitch: Int, private val matrixShape: Boolean = true) {
    private var bitmap: Bitmap? = null
    private var levels: IntArray? = null
    private var accent: BooleanArray? = null

    fun show(grid: DotGrid) {
        if (levels?.contentEquals(grid.levels) == true && accent?.contentEquals(grid.accent) == true) return
        val old = bitmap
        val shape: ((Int, Int) -> Boolean)? = if (matrixShape) { x, y -> GlyphShape.contains(x, y) } else null
        val painted = DotPainter.paint(grid, pitch, shape = shape, reuse = old)
        if (painted !== old) {
            bitmap = painted
            view.setImageBitmap(painted)
        } else {
            view.invalidate()
        }
        levels = grid.levels.copyOf()
        accent = grid.accent.copyOf()
    }
}
