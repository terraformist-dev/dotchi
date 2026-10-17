package dev.terraformist.glyphpet

/** A little picture drawn as text: '#' = fully lit, '+' = half lit, anything else = off. */
class Sprite(vararg rows: String) {
    private val rows: List<String> = rows.toList()
    val width: Int = this.rows.maxOf { it.length }
    val height: Int = this.rows.size

    fun at(x: Int, y: Int): Char = rows.getOrNull(y)?.getOrNull(x) ?: '.'
}

/**
 * A grid of dots, each with a brightness 0..255 and an optional red "accent".
 * The pet scene is drawn into one of these, then shown either on the Glyph
 * Matrix (brightness only) or painted as dots for the widget (with red).
 */
class DotGrid(val width: Int, val height: Int) {

    val levels = IntArray(width * height)
    val accent = BooleanArray(width * height)

    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun set(x: Int, y: Int, level: Int, red: Boolean = false) {
        if (!inBounds(x, y)) return
        val i = y * width + x
        levels[i] = level.coerceIn(0, 255)
        accent[i] = red && level > 0
    }

    fun level(x: Int, y: Int): Int = if (inBounds(x, y)) levels[y * width + x] else 0

    fun isRed(x: Int, y: Int): Boolean = inBounds(x, y) && accent[y * width + x]

    /** Turn a dot off (used to cut eyes and mouths out of a lit body). */
    fun carve(x: Int, y: Int) = set(x, y, 0)

    fun stamp(sprite: Sprite, left: Int, top: Int, level: Int = 255, red: Boolean = false) {
        for (y in 0 until sprite.height) {
            for (x in 0 until sprite.width) {
                when (sprite.at(x, y)) {
                    '#' -> set(left + x, top + y, level, red)
                    '+' -> set(left + x, top + y, level / 2, red)
                }
            }
        }
    }

    /** Draw text in the 3x5 dot font. Returns the width used. */
    fun text(s: String, left: Int, top: Int, level: Int = 255, red: Boolean = false): Int {
        var x = left
        for (ch in s.uppercase()) {
            DotFont.glyph(ch)?.let { stamp(it, x, top, level, red) }
            x += DotFont.ADVANCE
        }
        return x - left
    }

    fun fill(level: Int) {
        levels.fill(level.coerceIn(0, 255))
        accent.fill(false)
    }
}

/** The exact shape of the Phone (3) Glyph Matrix: 25 rows, each narrower toward the top and bottom. */
object GlyphShape {
    const val SIZE = 25

    // How many dots each row is indented on the left (and right). From Nothing's 23112 spec sheet.
    private val INDENT = intArrayOf(9, 7, 5, 4, 3, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 4, 5, 7, 9)

    fun contains(x: Int, y: Int): Boolean {
        if (y !in 0 until SIZE || x !in 0 until SIZE) return false
        val indent = INDENT[y]
        return x >= indent && x < SIZE - indent
    }
}

/** A tiny 3x5 dot-matrix font, in the spirit of Nothing's dotted type. */
object DotFont {
    const val ADVANCE = 4
    const val HEIGHT = 5

    fun width(s: String): Int = if (s.isEmpty()) 0 else s.length * ADVANCE - 1

    fun glyph(ch: Char): Sprite? = GLYPHS[ch]

    private fun g(vararg rows: String) = Sprite(*rows)

    private val GLYPHS: Map<Char, Sprite> = mapOf(
        'A' to g(".#.", "#.#", "###", "#.#", "#.#"),
        'B' to g("##.", "#.#", "##.", "#.#", "##."),
        'C' to g(".##", "#..", "#..", "#..", ".##"),
        'D' to g("##.", "#.#", "#.#", "#.#", "##."),
        'E' to g("###", "#..", "##.", "#..", "###"),
        'F' to g("###", "#..", "##.", "#..", "#.."),
        'G' to g(".##", "#..", "#.#", "#.#", ".##"),
        'H' to g("#.#", "#.#", "###", "#.#", "#.#"),
        'I' to g("###", ".#.", ".#.", ".#.", "###"),
        'J' to g("..#", "..#", "..#", "#.#", ".#."),
        'K' to g("#.#", "#.#", "##.", "#.#", "#.#"),
        'L' to g("#..", "#..", "#..", "#..", "###"),
        'M' to g("#.#", "###", "###", "#.#", "#.#"),
        'N' to g("##.", "#.#", "#.#", "#.#", "#.#"),
        'O' to g(".#.", "#.#", "#.#", "#.#", ".#."),
        'P' to g("##.", "#.#", "##.", "#..", "#.."),
        'Q' to g(".#.", "#.#", "#.#", "##.", ".##"),
        'R' to g("##.", "#.#", "##.", "#.#", "#.#"),
        'S' to g(".##", "#..", ".#.", "..#", "##."),
        'T' to g("###", ".#.", ".#.", ".#.", ".#."),
        'U' to g("#.#", "#.#", "#.#", "#.#", "###"),
        'V' to g("#.#", "#.#", "#.#", "#.#", ".#."),
        'W' to g("#.#", "#.#", "###", "###", "#.#"),
        'X' to g("#.#", "#.#", ".#.", "#.#", "#.#"),
        'Y' to g("#.#", "#.#", ".#.", ".#.", ".#."),
        'Z' to g("###", "..#", ".#.", "#..", "###"),
        '0' to g("###", "#.#", "#.#", "#.#", "###"),
        '1' to g(".#.", "##.", ".#.", ".#.", "###"),
        '2' to g("##.", "..#", ".#.", "#..", "###"),
        '3' to g("##.", "..#", ".#.", "..#", "##."),
        '4' to g("#.#", "#.#", "###", "..#", "..#"),
        '5' to g("###", "#..", "##.", "..#", "##."),
        '6' to g(".##", "#..", "###", "#.#", "###"),
        '7' to g("###", "..#", ".#.", ".#.", ".#."),
        '8' to g("###", "#.#", "###", "#.#", "###"),
        '9' to g("###", "#.#", "###", "..#", "##."),
        '!' to g(".#.", ".#.", ".#.", "...", ".#."),
        ' ' to g("...", "...", "...", "...", "..."),
    )
}
