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
 * Meet another Glyph Pet. Both phones open this screen; each quietly announces its pet over
 * Bluetooth and listens for others. When another pet is close by (about arm's length), the
 * two appear side by side. The first meeting with each friend each day cheers your pet up.
 *
 * Only a tiny anonymous message is shared: "this is a Glyph Pet", which kind it is, and a
 * random number that tells friends apart. No names, accounts or location.
 */
class MeetActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var petView: ImageView
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
            if (id == Friends.myId(this@MeetActivity)) return
            if (result.rssi < CLOSE_RSSI) return // too far away: hold the phones closer
            handler.post { onFriendNearby(id, data[3].toInt()) }
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
            val id = Friends.myId(this)
            val payload = byteArrayOf(
                MAGIC_0, MAGIC_1, VERSION, code.toByte(),
                (id ushr 24).toByte(), (id ushr 16).toByte(), (id ushr 8).toByte(), id.toByte(),
            )
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

    private fun onFriendNearby(id: Int, code: Int) {
        val now = System.currentTimeMillis()
        val isNewMeeting = friendCode == null || friendId != id
        friendCode = code
        friendId = id
        friendSeenAt = now
        if (!isNewMeeting) return

        val name = PetScene.nameForCode(code)
        message = when (Friends.met(this, id, code, now)) {
            Friends.Meeting.NEW_FRIEND -> {
                PetStore.metFriend(this, now)
                "YOU MET $name! A NEW FRIEND. YOUR PET IS DELIGHTED."
            }
            Friends.Meeting.FIRST_TODAY -> {
                PetStore.metFriend(this, now)
                "$name IS BACK! YOUR PET IS HAPPY TO SEE THEM."
            }
            Friends.Meeting.AGAIN_TODAY -> "$name SAYS HELLO AGAIN. (THE HAPPINESS BOOST IS ONCE A DAY.)"
        }
    }

    private fun render(now: Long) {
        val state = PetStore.get(this, now)
        val grid = PetScene.renderMeeting(state, now, friendCode)
        petView.setImageBitmap(DotPainter.paint(grid, pitch = 20))
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
        const val SEARCHING = "LOOKING FOR GLYPH PETS NEARBY. HOLD THE PHONES CLOSE TOGETHER."

        // 0xFFFF is the Bluetooth company ID set aside for testing and hobby projects.
        const val COMPANY_ID = 0xFFFF
        const val MAGIC_0: Byte = 0x47 // 'G'
        const val MAGIC_1: Byte = 0x50 // 'P'
        const val VERSION: Byte = 1
        const val PAYLOAD_SIZE = 8

        /** Signal strength needed to count as "close": roughly within a metre. */
        const val CLOSE_RSSI = -75
        const val LOST_AFTER_MS = 8_000L

        const val PERMISSION_REQUEST = 3
        val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
    }
}

/** The friends this pet has met, kept on this phone. */
object Friends {
    enum class Meeting { NEW_FRIEND, FIRST_TODAY, AGAIN_TODAY }

    private const val PREFS = "friends"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A random number identifying this phone's pet to others. Made once. */
    fun myId(context: Context): Int {
        val p = prefs(context)
        if (p.contains("myId")) return p.getInt("myId", 0)
        val id = Random.nextInt()
        p.edit().putInt("myId", id).apply()
        return id
    }

    fun count(context: Context): Int = prefs(context).getStringSet("ids", emptySet())?.size ?: 0

    fun met(context: Context, id: Int, code: Int, now: Long): Meeting {
        val p = prefs(context)
        val ids = p.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = id.toString()
        val today = PetState.epochDay(now)
        val lastDay = p.getLong("day_$key", -1L)
        val result = when {
            key !in ids -> Meeting.NEW_FRIEND
            lastDay != today -> Meeting.FIRST_TODAY
            else -> Meeting.AGAIN_TODAY
        }
        ids += key
        p.edit()
            .putStringSet("ids", ids)
            .putLong("day_$key", today)
            .putInt("code_$key", code)
            .apply()
        return result
    }
}
