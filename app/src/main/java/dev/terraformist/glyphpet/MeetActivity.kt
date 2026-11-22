package dev.terraformist.glyphpet

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
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
import android.widget.ImageView
import android.widget.TextView
import kotlin.random.Random

/**
 * Meet another Dotchi. Both phones open this screen; each quietly announces its pet over
 * Bluetooth and listens for others. When another pet is close by (about arm's length), the
 * two appear side by side. The first meeting with each friend each day cheers your pet up.
 *
 * Only a tiny message is shared: "this is a Dotchi", which kind it is, a random number that
 * tells friends apart, and the pet's name if it has one. No accounts or location.
 *
 * The name (up to 8 letters) goes after the original 8 bytes, so older versions of the app,
 * which only read the first 8, still recognise newer ones.
 */
class MeetActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var petView: ImageView
    private val petDots by lazy { LiveDots(petView, pitch = 20, matrixShape = false) }
    private lateinit var infoView: TextView
    private lateinit var friendsView: TextView

    private var bluetoothRunning = false
    private var friendCode: Int? = null
    private var friendId = 0
    private var friendSeenAt = 0L
    private var message = ""

    private val loop = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (friendCode != null && now - friendSeenAt > LOST_AFTER_MS) {
                friendCode = null
                if (bluetoothRunning) message = SEARCHING
            }
            render(now)
            handler.postDelayed(this, 120L)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "advertise failed $errorCode")
            message = "COULDN'T START BLUETOOTH ($errorCode). TRY AGAIN."
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val data = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
            if (data.size < PAYLOAD_SIZE || data[0] != MAGIC_0 || data[1] != MAGIC_1 || data[2] != VERSION) return
            val id = ((data[4].toInt() and 0xFF) shl 24) or ((data[5].toInt() and 0xFF) shl 16) or
                ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            if (id == Friends.petId(this@MeetActivity, PetStore.get(this@MeetActivity))) return
            if (result.rssi < CLOSE_RSSI) return // too far away: hold the phones closer
            val name = cleanName(String(data, PAYLOAD_SIZE, data.size - PAYLOAD_SIZE, Charsets.US_ASCII))
            handler.post { onFriendNearby(id, data[3].toInt(), name) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed $errorCode")
            message = "COULDN'T SEARCH ($errorCode). TRY AGAIN."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meet)
        findViewById<ImageView>(R.id.title).setImageBitmap(MainActivity.dotTitle("MEET"))
        petView = findViewById(R.id.pet)
        infoView = findViewById(R.id.info)
        friendsView = findViewById(R.id.friends)
        findViewById<TextView>(R.id.back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(loop)
        startIfReady()
    }

    override fun onPause() {
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
        val advertiser = adapter.bluetoothLeAdvertiser
        val scanner = adapter.bluetoothLeScanner
        if (advertiser == null || scanner == null) {
            message = "THIS PHONE CAN'T DO BLUETOOTH LOW ENERGY."
            return
        }
        try {
            val now = System.currentTimeMillis()
            val code = PetScene.formCode(PetStore.get(this, now), now)
            val id = Friends.petId(this, PetStore.get(this, now))
            val payload = byteArrayOf(
                MAGIC_0, MAGIC_1, VERSION, code.toByte(),
                (id ushr 24).toByte(), (id ushr 16).toByte(), (id ushr 8).toByte(), id.toByte(),
            ) + cleanName(PetStore.get(this, now).name).toByteArray(Charsets.US_ASCII)
            advertiser.startAdvertising(
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
                advertiseCallback,
            )
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
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        }
    }

    private fun onFriendNearby(id: Int, code: Int, friendName: String) {
        val now = System.currentTimeMillis()
        val isNewMeeting = friendCode == null || friendId != id
        friendCode = code
        friendId = id
        friendSeenAt = now
        if (!isNewMeeting) return

        // Their pet's own name if it has one, otherwise what kind it is (PIP, NOVA...).
        val name = friendName.ifEmpty { PetScene.nameForCode(code) }
        val meeting = Friends.met(this, Friends.petId(this, PetStore.get(this, now)), id, code, now, friendName)
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

    private fun render(now: Long) {
        val state = PetStore.get(this, now)
        val grid = PetScene.renderMeeting(state, now, friendCode)
        petDots.show(grid)
        infoView.text = message
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
        const val PAYLOAD_SIZE = 8

        /** Signal strength needed to count as "close": roughly within a metre. */
        const val CLOSE_RSSI = -75
        const val LOST_AFTER_MS = 8_000L

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
