package dev.terraformist.glyphpet

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * Mini-games, opened from Play in the app or on the widget.
 *
 *  - LEFT OR RIGHT: guess which way the pet will look.
 *  - HIGHER OR LOWER: a number 1-9 is shown; guess if the next one is higher or lower.
 *
 * Five rounds each; three or more right is a win. Winning makes the pet much happier
 * than losing, but playing at all helps.
 */
class GameActivity : Activity() {

    private enum class Game(val title: String) {
        LEFT_RIGHT("LEFT OR RIGHT"),
        HIGH_LOW("HIGHER OR LOWER"),
    }

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var titleView: ImageView
    private lateinit var infoView: TextView
    private lateinit var petView: ImageView
    private val petDots by lazy { LiveDots(petView, pitch = 24) }
    private lateinit var choiceA: TextView
    private lateinit var choiceB: TextView

    private var game: Game? = null
    private var round = 0
    private var score = 0
    private var waiting = false
    private var finished = false

    // What the pet shows during a round.
    private var look = 0
    private var reaction: Mood? = null
    private var number = 0

    private val loop = object : Runnable {
        override fun run() {
            renderPet()
            handler.postDelayed(this, 120L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = PetStore.get(this)
        val now = System.currentTimeMillis()
        val reason = when {
            state.stage(now) == Stage.EGG -> "Wait for the egg to hatch first."
            state.sick -> "Dotchi is ill. Give it medicine first."
            else -> null
        }
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_game)
        titleView = findViewById(R.id.title)
        infoView = findViewById(R.id.info)
        petView = findViewById(R.id.pet)
        choiceA = findViewById(R.id.choice_a)
        choiceB = findViewById(R.id.choice_b)
        findViewById<TextView>(R.id.back).setOnClickListener { finish() }

        choiceA.setOnClickListener { onChoice(first = true) }
        choiceB.setOnClickListener { onChoice(first = false) }

        showMenu()
    }

    override fun onResume() {
        super.onResume()
        if (::petView.isInitialized) handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun showMenu() {
        game = null
        finished = false
        waiting = false
        reaction = null
        look = 0
        titleView.setImageBitmap(MainActivity.dotTitle("PLAY"))
        infoView.text = "PICK A GAME"
        choiceA.text = Game.LEFT_RIGHT.title
        choiceB.text = Game.HIGH_LOW.title
    }

    private fun start(g: Game) {
        game = g
        round = 1
        score = 0
        finished = false
        titleView.setImageBitmap(MainActivity.dotTitle(g.title))
        when (g) {
            Game.LEFT_RIGHT -> { choiceA.text = "LEFT"; choiceB.text = "RIGHT" }
            Game.HIGH_LOW -> { choiceA.text = "HIGHER"; choiceB.text = "LOWER" }
        }
        nextRound()
    }

    private fun nextRound() {
        waiting = false
        reaction = null
        look = 0
        if (game == Game.HIGH_LOW) number = Random.nextInt(2, 9) // 2..8, so both answers are possible
        infoView.text = "ROUND $round OF $ROUNDS  ·  SCORE $score"
    }

    private fun onChoice(first: Boolean) {
        if (waiting) return
        val g = game
        when {
            finished -> if (first) start(g ?: Game.LEFT_RIGHT) else finish()
            g == null -> start(if (first) Game.LEFT_RIGHT else Game.HIGH_LOW)
            else -> guess(g, first)
        }
    }

    private fun guess(g: Game, first: Boolean) {
        waiting = true
        val correct = when (g) {
            Game.LEFT_RIGHT -> {
                look = if (Random.nextBoolean()) -1 else 1
                (first && look == -1) || (!first && look == 1)
            }
            Game.HIGH_LOW -> {
                var next = Random.nextInt(1, 10)
                while (next == number) next = Random.nextInt(1, 10)
                val higher = next > number
                number = next
                (first && higher) || (!first && !higher)
            }
        }
        if (correct) score++
        reaction = if (correct) Mood.HAPPY else Mood.SAD
        infoView.text = (if (correct) "YES!" else "NOPE") + "  ·  SCORE $score"

        handler.postDelayed({
            if (round < ROUNDS) {
                round++
                nextRound()
            } else {
                endGame()
            }
        }, 1300L)
    }

    private fun endGame() {
        finished = true
        waiting = false
        look = 0
        val won = score >= WIN_SCORE
        PetStore.played(this, won)
        reaction = null // show the pet's real (now playful) mood
        infoView.text = if (won) "YOU WON  ·  $score OF $ROUNDS" else "$score OF $ROUNDS  ·  NICE TRY"
        choiceA.text = "PLAY AGAIN"
        choiceB.text = "DONE"
    }

    private fun renderPet() {
        val now = System.currentTimeMillis()
        val state = PetStore.get(this, now)
        val inRound = game != null && !finished
        val grid = PetScene.render(
            state,
            now,
            animate = true,
            look = look,
            moodOverride = if (inRound) reaction ?: Mood.OK else null,
            topText = if (inRound && game == Game.HIGH_LOW) number.toString() else null,
            hud = !inRound,
        )
        petDots.show(grid)
    }

    private companion object {
        const val ROUNDS = 5
        const val WIN_SCORE = 3
    }
}
