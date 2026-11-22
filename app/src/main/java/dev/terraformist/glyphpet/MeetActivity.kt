package dev.terraformist.glyphpet

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

/**
 * Meet another Dotchi. Both phones open this screen; each quietly announces its pet over
 * Bluetooth and listens for others. When another pet is close by (about arm's length), the
 * two appear side by side. The first meeting with each friend each day cheers your pet up.
 *
 * Only a tiny message is shared: "this is a Dotchi", which kind it is, a number that tells
 * pets apart, and the pet's name if it has one. No accounts or location.
 *
 * The name (up to 8 letters) goes after the original 8 bytes, so older versions of the app,
 * which only read the first 8, still recognise newer ones.
 *
 * Playing together: while a game is on, the message switches to version 2, which carries the
 * game's state instead of the name (older versions simply ignore it). See [DuelPlay].
 */
class MeetActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var petView: ImageView
    private val petDots by lazy { LiveDots(petView, pitch = 20, matrixShape = false) }
    private val duelDots by lazy { LiveDots(petView, pitch = 24) }
    private var showingDuel = false
    private lateinit var infoView: TextView
    private lateinit var friendsView: TextView
    private lateinit var duelButtons: List<TextView>
    private var shownButtons: List<String> = emptyList()
    private var buttonActions: List<() -> Unit> = emptyList()

    private var bluetoothRunning = false
    private var friendCode: Int? = null
    private var friendId = 0
    private var friendSeenAt = 0L
    private var friendName = ""
    private var message = ""

    // What this phone announces.
    private var myPetId = 0
    private var myCode = 0
    private var myName = ""
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertising: ByteArray? = null
    private var lastAdvertAt = 0L

    // ---- Playing together ----
    private enum class Duel(val code: Int) { NONE(0), READY(1), PLAYING(2), DONE(3) }

    /** The friend's side of a game, as their phone last announced it. */
    private class FriendDuel(
        val state: Int, val game: Int, val session: Int, val seed: Int,
        val round: Int, val score: Int, val choice: Int, val prevChoice: Int,
    )

    private var duel = Duel.NONE
    private var duelGame: DuelGame? = null
    private var duelSession = 0
    private var duelSeed = 0
    private var play: DuelPlay? = null
    private var picking = false
    private var duelDoneAt = 0L
    private var duelResult: String? = null
    private var friendDuel: FriendDuel? = null

    private val loop = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (friendCode != null && now - friendSeenAt > LOST_AFTER_MS) {
                friendCode = null
                friendDuel = null
                if (bluetoothRunning) message = SEARCHING
            }
            duelStep(now)
            updateAdvert(now)
            render(now)
            handler.postDelayed(this, if (duel == Duel.PLAYING) 40L else 120L)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val data = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
            if (data.size < PAYLOAD_SIZE || data[0] != MAGIC_0 || data[1] != MAGIC_1) return
            if (data[2] != VERSION && data[2] != DUEL_VERSION) return
            if (data[2] == DUEL_VERSION && data.size < DUEL_SIZE) return
            val id = ((data[4].toInt() and 0xFF) shl 24) or ((data[5].toInt() and 0xFF) shl 16) or
                ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            if (id == myPetId) return
            if (result.rssi < CLOSE_RSSI) return // too far away: hold the phones closer
            fun u(i: Int) = data[i].toInt() and 0xFF
            val theirDuel = if (data[2] == DUEL_VERSION) {
                FriendDuel(
                    state = u(8), game = u(9), session = u(10), seed = (u(11) shl 8) or u(12),
                    round = u(13), score = (u(14) shl 8) or u(15), choice = u(16), prevChoice = u(17),
                )
            } else null
            val name = if (data[2] == VERSION) cleanName(String(data, PAYLOAD_SIZE, data.size - PAYLOAD_SIZE, Charsets.US_ASCII)) else ""
            handler.post {
                onFriendNearby(id, data[3].toInt(), name)
                if (id == friendId) friendDuel = theirDuel
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed $errorCode")
            message = "COULDN'T SEARCH ($errorCode). TRY AGAIN."
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meet)
        findViewById<ImageView>(R.id.title).setImageBitmap(MainActivity.dotTitle("MEET"))
        petView = findViewById(R.id.pet)
        infoView = findViewById(R.id.info)
        friendsView = findViewById(R.id.friends)
        findViewById<TextView>(R.id.back).setOnClickListener { finish() }
        duelButtons = listOf(R.id.duel_0, R.id.duel_1, R.id.duel_2, R.id.duel_3, R.id.duel_4, R.id.duel_5).map { findViewById(it) }
        duelButtons.forEachIndexed { i, b ->
            b.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                buttonActions.getOrNull(i)?.invoke()
            }
        }
        // During a game you can also tap the picture itself.
        petView.setOnTouchListener { v, e ->
            val p = play
            if (duel == Duel.PLAYING && p != null && e.actionMasked == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                p.tapPicture(e.x / v.width, System.currentTimeMillis())
            }
            duel == Duel.PLAYING
        }
        myPetId = Friends.petId(this, PetStore.get(this))
    }

    override fun onResume() {
        super.onResume()
        handler.post(loop)
        startIfReady()
    }

    override fun onPause() {
        cancelDuel() // leaving the screen ends any game
        stopBluetooth()
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun hasPermissions() = PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun startIfReady() {
        val now = System.currentTimeMillis()
        if (PetStore.get(this, now).stage(now) == Stage.EGG) {
            message = "WAIT FOR YOUR EGG TO HATCH FIRST."
            return
        }
        if (!hasPermissions()) {
            message = "ALLOW NEARBY DEVICES SO PETS CAN FIND EACH OTHER."
            requestPermissions(PERMISSIONS, PERMISSION_REQUEST)
            return
        }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            message = "THIS PHONE HAS NO BLUETOOTH."
            return
        }
        if (!adapter.isEnabled) {
            message = "TURN ON BLUETOOTH, THEN COME BACK TO THIS SCREEN."
            return
        }
        startBluetooth()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        if (hasPermissions()) startIfReady()
        else message = "MEETING NEEDS NEARBY DEVICES PERMISSION. YOU CAN ALLOW IT IN SETTINGS."
    }

    private fun startBluetooth() {
        if (bluetoothRunning) return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val adv = adapter.bluetoothLeAdvertiser
        val scanner = adapter.bluetoothLeScanner
        if (adv == null || scanner == null) {
            message = "THIS PHONE CAN'T DO BLUETOOTH LOW ENERGY."
            return
        }
        try {
            val now = System.currentTimeMillis()
            val state = PetStore.get(this, now)
            myCode = PetScene.formCode(state, now)
            myName = cleanName(state.name)
            myPetId = Friends.petId(this, state)
            advertiser = adv
            restartAdvert(currentPayload())
            lastAdvertAt = now
            val filter = ScanFilter.Builder()
                .setManufacturerData(COMPANY_ID, byteArrayOf(MAGIC_0, MAGIC_1), byteArrayOf(-1, -1))
                .build()
            scanner.startScan(
                listOf(filter),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
            bluetoothRunning = true
            message = SEARCHING
        } catch (e: SecurityException) {
            message = "MEETING NEEDS NEARBY DEVICES PERMISSION."
        }
    }

    private fun stopBluetooth() {
        if (!bluetoothRunning) return
        bluetoothRunning = false
        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        }
        advertiseCallback = null
        advertising = null
    }

    // ---- What we announce ----------------------------------------------------------------

    /** Version 1 (hello, with the name) normally; version 2 (the game's state) while playing together. */
    private fun currentPayload(): ByteArray {
        val id = myPetId
        val playing = duel != Duel.NONE
        val header = byteArrayOf(
            MAGIC_0, MAGIC_1, if (playing) DUEL_VERSION else VERSION, myCode.toByte(),
            (id ushr 24).toByte(), (id ushr 16).toByte(), (id ushr 8).toByte(), id.toByte(),
        )
        if (!playing) return header + myName.toByteArray(Charsets.US_ASCII)
        val p = play
        val score = (p?.score ?: 0).coerceIn(0, 0xFFFF)
        return header + byteArrayOf(
            duel.code.toByte(), (duelGame?.code ?: 0).toByte(), duelSession.toByte(),
            (duelSeed ushr 8).toByte(), duelSeed.toByte(),
            (p?.round ?: 0).toByte(), (score ushr 8).toByte(), score.toByte(),
            (p?.choice ?: 0).toByte(), (p?.prevChoice ?: 0).toByte(),
        )
    }

    /** Re-announce when what we're saying has changed (at most four times a second). */
    private fun updateAdvert(now: Long) {
        if (!bluetoothRunning) return
        val payload = currentPayload()
        if (advertising?.contentEquals(payload) == true || now - lastAdvertAt < ADVERT_GAP_MS) return
        restartAdvert(payload)
        lastAdvertAt = now
    }

    private fun restartAdvert(payload: ByteArray) {
        val adv = advertiser ?: return
        try {
            advertiseCallback?.let { adv.stopAdvertising(it) }
            val cb = object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    Log.w(TAG, "advertise failed $errorCode")
                    if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                        message = "COULDN'T START BLUETOOTH ($errorCode). TRY AGAIN."
                    }
                }
            }
            adv.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build(),
                AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(COMPANY_ID, payload)
                    .build(),
                cb,
            )
            advertiseCallback = cb
            advertising = payload
        } catch (e: SecurityException) {
            message = "MEETING NEEDS NEARBY DEVICES PERMISSION."
        }
    }

    // ---- Meeting -------------------------------------------------------------------------

    private fun onFriendNearby(id: Int, code: Int, nameHeard: String) {
        val now = System.currentTimeMillis()
        val isNewMeeting = friendCode == null || friendId != id
        if (friendId != id) {
            // Someone different: forget the last friend's name and any game with them.
            friendName = ""
            friendDuel = null
            cancelDuel()
        }
        if (nameHeard.isNotEmpty()) friendName = nameHeard
        friendCode = code
        friendId = id
        friendSeenAt = now
        if (!isNewMeeting) return

        // Their pet's own name if it has one, otherwise what kind it is (PIP, NOVA...).
        val name = friendName.ifEmpty { PetScene.nameForCode(code) }
        val meeting = Friends.met(this, myPetId, id, code, now, friendName)
        if (meeting.isNew || meeting.firstToday) PetStore.metFriend(this, now)
        message = when {
            meeting.isNew -> "YOU MET $name! A NEW FRIEND. YOUR PET IS DELIGHTED."
            meeting.grown && meeting.firstToday ->
                "$name IS BACK, AND HAS GROWN! YOUR PET HAS NEVER SEEN THEM LOOK LIKE THIS BEFORE."
            meeting.grown -> "$name HAS GROWN! YOUR PET HAS NEVER SEEN THEM LOOK LIKE THIS BEFORE."
            meeting.firstToday -> "$name IS BACK! YOUR PET IS HAPPY TO SEE THEM."
            else -> "$name SAYS HELLO AGAIN. (THE HAPPINESS BOOST IS ONCE A DAY.)"
        }
    }

    private fun friendLabel(): String = friendName.ifEmpty { friendCode?.let { PetScene.nameForCode(it) } ?: "YOUR FRIEND" }

    // ---- Playing together ------------------------------------------------------------------

    /** Ask the friend to play [g]. */
    private fun invite(g: DuelGame) {
        picking = false
        duel = Duel.READY
        duelGame = g
        duelSession = Random.nextInt(1, 256)
        duelSeed = Random.nextInt(0, 0x10000)
        play = null
        duelResult = null
    }

    /** Accept the friend's invitation. */
    private fun join(f: FriendDuel) {
        picking = false
        duel = Duel.READY
        duelGame = DuelGame.of(f.game)
        duelSession = f.session
        duelSeed = f.seed
        play = null
        duelResult = null
    }

    private fun cancelDuel() {
        duel = Duel.NONE
        duelGame = null
        duelSession = 0
        play = null
        picking = false
        duelResult = null
    }

    /** A new invitation from the friend (not the game we've just played). */
    private fun pendingInvite(): FriendDuel? =
        friendDuel?.takeIf { friendCode != null && it.state == Duel.READY.code && it.session != duelSession && DuelGame.of(it.game) != null }

    private fun duelStep(now: Long) {
        if (duel != Duel.NONE && friendCode == null) {
            if (duelResult == null) message = "${friendLabel()} WANDERED OFF. NO WINNER THIS TIME."
            cancelDuel()
            return
        }
        val f = friendDuel
        when (duel) {
            Duel.NONE -> {}
            Duel.READY -> {
                if (f == null) return
                // Both asked at once: the invitation from the pet with the lower number wins.
                if (f.state == Duel.READY.code && f.session != duelSession && friendId < myPetId && DuelGame.of(f.game) != null) {
                    join(f)
                    return
                }
                if (f.session == duelSession && f.state >= Duel.READY.code) {
                    duel = Duel.PLAYING
                    play = DuelPlay(duelGame ?: DuelGame.STOP_DOT, duelSeed, now + DuelPlay.COUNTDOWN_MS)
                }
            }
            Duel.PLAYING -> {
                val p = play ?: return
                val side = f?.takeIf { it.session == duelSession }?.let { FriendSide(it.round, it.score, it.choice, it.prevChoice) }
                p.tick(now, side)
                if (p.done) {
                    duel = Duel.DONE
                    duelDoneAt = now
                }
            }
            Duel.DONE -> {
                if (duelResult != null) return
                if (f != null && f.session == duelSession && f.state == Duel.DONE.code) {
                    finishDuel(f.score)
                } else if (now - duelDoneAt > DONE_WAIT_MS) {
                    message = "${friendLabel()} DIDN'T FINISH. NO WINNER THIS TIME."
                    cancelDuel()
                }
            }
        }
    }

    /** Both finished: say who won and reward this phone's pet (the friend's phone does the same for theirs). */
    private fun finishDuel(theirs: Int) {
        val g = duelGame ?: return
        val mine = play?.score ?: 0
        val who = friendLabel()
        var won: Boolean
        duelResult = when {
            g.coop -> {
                won = mine >= 3
                if (won) "IN SYNC $mine TIMES OUT OF 5! YOUR PETS ARE BEST FRIENDS."
                else "IN SYNC $mine OUT OF 5. KEEP PRACTISING TOGETHER!"
            }
            g.lowerWins -> {
                val times = "YOU %.2fS  ·  %s %.2fS".format(mine / 1000f, who, theirs / 1000f)
                won = mine <= theirs
                times + "\n" + when {
                    mine < theirs -> "YOU'RE QUICKER! YOU WIN."
                    mine > theirs -> "$who IS QUICKER. GOOD GAME!"
                    else -> "EXACTLY THE SAME! A DRAW."
                }
            }
            else -> {
                won = mine >= theirs
                when {
                    mine > theirs -> "YOU WIN, $mine TO $theirs!"
                    mine < theirs -> "$who WINS, $theirs TO $mine. GOOD GAME!"
                    else -> "A DRAW, $mine EACH!"
                }
            }
        }
        // Playing together counts as playing; winning (or a draw) cheers it up more.
        PetStore.played(this, won)
    }

    private fun playingInfo(now: Long, p: DuelPlay): String {
        val g = p.game
        if (now < p.startAt) return "${g.title}  ·  GET READY..."
        val f = friendDuel?.takeIf { it.session == duelSession }
        val who = friendLabel()
        val roundText = "ROUND ${(p.round + 1).coerceAtMost(p.rounds)} OF ${p.rounds}"
        val line = when {
            g.coop -> "$roundText  ·  IN SYNC ${p.score}"
            g.lowerWins -> "$roundText  ·  YOU %.2fS  ·  %s %.2fS".format(p.score / 1000f, who, (f?.score ?: 0) / 1000f)
            g == DuelGame.TAP_RACE -> "YOU ${p.score}  ·  $who ${f?.score ?: 0}"
            else -> "$roundText  ·  YOU ${p.score}  ·  $who ${f?.score ?: 0}"
        }
        return if (p.note.isNotEmpty()) "$line\n${p.note}" else line
    }

    // ---- Buttons and drawing -------------------------------------------------------------

    private fun setButtons(items: List<Pair<String, () -> Unit>>) {
        buttonActions = items.map { it.second }
        val labels = items.map { it.first }
        if (labels == shownButtons) return
        shownButtons = labels
        duelButtons.forEachIndexed { i, b ->
            if (i < labels.size) {
                b.text = labels[i]
                b.visibility = View.VISIBLE
            } else {
                b.visibility = View.GONE
            }
        }
    }

    private fun buttonsFor(now: Long): List<Pair<String, () -> Unit>> {
        if (friendCode == null) return emptyList()
        val invite = pendingInvite()
        val p = play
        return when {
            picking -> DuelGame.entries.map { g -> g.title to { invite(g) } } + ("CANCEL" to { picking = false })
            duel == Duel.READY -> listOf("CANCEL" to { cancelDuel() })
            duel == Duel.PLAYING -> if (p != null && now >= p.startAt && !p.done) {
                p.buttons().mapIndexed { i, label -> label to { play?.press(i, System.currentTimeMillis()); Unit } }
            } else emptyList()
            invite != null -> listOf("JOIN ${DuelGame.of(invite.game)?.title}" to { join(invite) }) +
                (if (duel == Duel.DONE) listOf("FINISH" to { cancelDuel() }) else emptyList())
            duel == Duel.DONE && duelResult == null -> emptyList()
            duel == Duel.DONE -> listOf("PLAY AGAIN" to { picking = true }, "FINISH" to { cancelDuel() })
            else -> listOf("PLAY TOGETHER" to { picking = true })
        }
    }

    private fun render(now: Long) {
        val state = PetStore.get(this, now)
        val p = play
        if (duel == Duel.PLAYING && p != null) {
            if (!showingDuel) {
                petDots.reset()
                showingDuel = true
            }
            duelDots.show(p.render(now, state))
        } else {
            if (showingDuel) {
                duelDots.reset()
                showingDuel = false
            }
            petDots.show(PetScene.renderMeeting(state, now, friendCode))
        }

        val who = friendLabel()
        val invite = pendingInvite()
        infoView.text = when {
            picking -> "PICK A GAME TO PLAY WITH $who."
            duel == Duel.READY -> "WAITING FOR $who TO JOIN ${duelGame?.title}..."
            duel == Duel.PLAYING && p != null -> playingInfo(now, p)
            duel == Duel.DONE -> duelResult ?: "WAITING FOR $who TO FINISH..."
            invite != null -> "$who WANTS TO PLAY ${DuelGame.of(invite.game)?.title}!"
            else -> message
        }
        setButtons(buttonsFor(now))

        val count = Friends.count(this)
        friendsView.text = when (count) {
            0 -> "NO FRIENDS YET"
            1 -> "1 FRIEND MET"
            else -> "$count FRIENDS MET"
        }
    }

    private companion object {
        const val TAG = "GlyphPet"
        const val SEARCHING = "LOOKING FOR OTHER DOTCHI NEARBY. HOLD THE PHONES CLOSE TOGETHER."

        // 0xFFFF is the Bluetooth company ID set aside for testing and hobby projects.
        const val COMPANY_ID = 0xFFFF
        const val MAGIC_0: Byte = 0x47 // 'G'
        const val MAGIC_1: Byte = 0x50 // 'P'
        const val VERSION: Byte = 1
        const val DUEL_VERSION: Byte = 2
        const val PAYLOAD_SIZE = 8
        const val DUEL_SIZE = 18

        /** Signal strength needed to count as "close": roughly within a metre. */
        const val CLOSE_RSSI = -75
        const val LOST_AFTER_MS = 8_000L
        const val ADVERT_GAP_MS = 250L
        const val DONE_WAIT_MS = 30_000L

        const val MAX_NAME = 8

        /** Only the letters the dot font has, up to 8 of them. */
        fun cleanName(raw: String): String =
            raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == ' ' || it == '!' }.trim().take(MAX_NAME)

        const val PERMISSION_REQUEST = 3
        val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
    }
}

/**
 * The friends met, kept on this phone. Every Dotchi has its own id (a new egg gets a new one), so
 * each pet is a friend in its own right: your new Dotchi hasn't met anyone yet, and someone's
 * new Dotchi is a new friend even if you knew their last one.
 */
object Friends {
    /** How a meeting went: a brand new friend, the first visit today, and whether they've grown since last time. */
    class Meeting(val isNew: Boolean, val firstToday: Boolean, val grown: Boolean)

    private const val PREFS = "friends"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The id other phones see for this Dotchi: this phone's number mixed with when its egg was laid. */
    fun petId(context: Context, state: PetState): Int =
        (PetState.mix(myId(context).toLong() xor state.bornAt) ushr 32).toInt()

    /** A random number for this phone. Made once. */
    private fun myId(context: Context): Int {
        val p = prefs(context)
        if (p.contains("myId")) return p.getInt("myId", 0)
        val id = Random.nextInt()
        p.edit().putInt("myId", id).apply()
        return id
    }

    fun count(context: Context): Int = prefs(context).getStringSet("ids", emptySet())?.size ?: 0

    /**
     * Record that [mine] (this phone's current Dotchi) met [theirs]. "Met before" is about these
     * two pets; the Album's count is every different Dotchi this phone's pets have met.
     */
    fun met(context: Context, mine: Int, theirs: Int, code: Int, now: Long, name: String = ""): Meeting {
        val p = prefs(context)
        val ids = p.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val pair = "${mine}_$theirs"
        val today = PetState.epochDay(now)
        val metBefore = p.contains("pairDay_$pair")
        val lastDay = p.getLong("pairDay_$pair", -1L)
        val lastCode = p.getInt("pairCode_$pair", code)
        ids += theirs.toString()
        p.edit()
            .putStringSet("ids", ids)
            .putLong("pairDay_$pair", today)
            .putInt("pairCode_$pair", code)
            .putString("name_$theirs", name)
            .apply()
        return Meeting(
            isNew = !metBefore,
            firstToday = metBefore && lastDay != today,
            grown = metBefore && lastCode != code,
        )
    }
}
