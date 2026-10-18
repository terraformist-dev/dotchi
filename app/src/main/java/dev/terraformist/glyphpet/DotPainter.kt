package dev.terraformist.glyphpet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

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
    ): Bitmap {
        val bmp = Bitmap.createBitmap(grid.width * pitch, grid.height * pitch, Bitmap.Config.ARGB_8888)
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
