package com.dugcanlift.liftwear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.dugcanlift.liftkit.link.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

/**
 * The watch's end of LIFT Link: a GATT **peripheral** that advertises, accepts one phone, and
 * moves frames for [LinkSession].
 *
 * **Why the watch advertises and the phone scans** (`docs/LINK-PROTOCOL.md` has the long form):
 * the phone is the end with a screen big enough to choose between two watches and show a
 * confirmation code beside a name, and choosing needs a list, which is what scanning produces.
 * Bonding is also the central's to start, and the phone is where a system pairing dialog belongs.
 * Afterwards the phone reconnects to a remembered address with `autoConnect`, so nothing scans
 * again for the life of the pairing.
 *
 * **Nothing is readable without a bond.** Both characteristics are declared
 * `PERMISSION_*_ENCRYPTED`, so the stack refuses an unbonded central and starts pairing instead;
 * [onCharacteristicWriteRequest] refuses an unbonded device a second time in this code, because a
 * training log and a bodyweight are not something to leave to one flag being right.
 *
 * **The radio is only on while the app is.** [start] is called when the Phone screen opens, or on
 * foreground when a phone is already remembered, and [stop] when it goes away. A watch with LIFT
 * closed advertises nothing and behaves exactly as it did before this existed: standalone, logging
 * food and exporting by QR. Holding a link through a whole guided session with the screen off will
 * need a `connectedDevice` foreground service; that belongs with the guided session, not here.
 */
class PhoneLinkPeripheral(context: Context, private val store: PhoneLinkStore) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)

    sealed interface Status {
        /** Not advertising. The watch is standalone, exactly as it always was. */
        data object Idle : Status
        data object Advertising : Status
        data class Connected(val peerName: String?) : Status

        /** Show [code] and ask. The phone shows the same number. */
        data class Confirming(val code: String, val peerName: String) : Status
        data class Linked(val peerName: String) : Status

        /** Bluetooth off, permission refused, or a watch whose radio cannot advertise. */
        data class Unavailable(val reason: String) : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The most recent plan the phone pushed. The guided session reads this; v1 only proves it
     *  arrives, which is why nothing else in the app looks at it yet. */
    private val _plan = MutableStateFlow<Plan?>(null)
    val plan: StateFlow<Plan?> = _plan.asStateFlow()

    private var server: BluetoothGattServer? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var peer: BluetoothDevice? = null
    private var session: LinkSession? = null
    private var codec = LinkCodec()
    private var notificationsEnabled = false
    private var wanted = false

    /** One notification at a time: a second before `onNotificationSent` is silently dropped by
     *  the stack, which is how half a plan goes missing with nothing logged anywhere. */
    private val outbound = ArrayDeque<ByteArray>()
    private var sending = false

    // ---- lifecycle ---------------------------------------------------------------------------

    @SuppressLint("MissingPermission")   // guarded by hasPermission(), which covers API 30 too
    fun start() {
        wanted = true
        val adapter = manager?.adapter
        when {
            adapter == null -> return fail(Status.Unavailable("This watch has no Bluetooth."))
            !adapter.isEnabled -> return fail(Status.Unavailable("Turn Bluetooth on."))
            !hasPermission(connectPermission) || !hasPermission(advertisePermission) ->
                return fail(Status.Unavailable("LIFT needs Bluetooth permission."))
        }
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: return fail(Status.Unavailable("This watch cannot advertise over Bluetooth."))

        if (server == null) openServer()
        advertiser.startAdvertising(advertiseSettings(), advertiseData(), scanResponse(), advertiseCallback)
        if (_status.value is Status.Idle || _status.value is Status.Unavailable) _status.value = Status.Advertising
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        wanted = false
        val advertiser = manager?.adapter?.bluetoothLeAdvertiser
        if (hasPermission(advertisePermission)) runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        if (hasPermission(connectPermission)) runCatching { server?.close() }
        server = null
        tx = null
        peer = null
        session = null
        notificationsEnabled = false
        outbound.clear()
        sending = false
        _status.value = Status.Idle
    }

    /** The watch's own user answered the confirmation prompt. */
    fun confirm(accepted: Boolean) {
        val current = session ?: return
        if (current.state != LinkState.CONFIRMING) return
        apply(current.confirm(accepted))
    }

    /** Forget the phone and go back to standalone. Stops the radio too: nothing is waiting for it. */
    fun forget() {
        store.forget()
        stop()
    }

    // ---- the GATT server ----------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun openServer() {
        val opened = manager?.openGattServer(appContext, serverCallback) ?: return
        val service = BluetoothGattService(uuid(LinkProtocol.SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // PERMISSION_WRITE_ENCRYPTED is the rule, not a hint: an unbonded central's write is
        // refused by the stack with an authentication error, which is what makes it bond.
        val rx = BluetoothGattCharacteristic(
            uuid(LinkProtocol.RX_CHARACTERISTIC_UUID),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
        )
        val notify = BluetoothGattCharacteristic(
            uuid(LinkProtocol.TX_CHARACTERISTIC_UUID),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        )
        notify.addDescriptor(
            BluetoothGattDescriptor(
                uuid(LinkProtocol.CCCD_UUID),
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
            )
        )
        service.addCharacteristic(rx)
        service.addCharacteristic(notify)
        opened.addService(service)
        server = opened
        tx = notify
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (peer != null && peer?.address != device.address) {
                        // One phone at a time. A second central is disconnected rather than
                        // interleaved onto a channel that reassembles one message at a time.
                        runCatching { server?.cancelConnection(device) }
                        return
                    }
                    peer = device
                    codec = LinkCodec()
                    notificationsEnabled = false
                    outbound.clear()
                    sending = false
                    session = LinkSession(
                        role = LinkRole.PERIPHERAL,
                        deviceName = watchName(),
                        nonce = freshNonce(),
                        alreadyPaired = store.isPaired(device.address),
                    ).also { it.start() }
                    _status.value = Status.Connected(device.safeName())
                    // Stop advertising while connected: nothing else may join, and the radio is
                    // the watch's scarcest thing.
                    if (hasPermission(advertisePermission)) {
                        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (peer?.address != device.address) return
                    peer = null
                    session = null
                    outbound.clear()
                    sending = false
                    if (wanted) start() else _status.value = Status.Idle
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            // The declared permission already requires a bond. Checking it here as well is the
            // difference between trusting one constant and being sure: this channel carries a
            // training log and a bodyweight, and an unbonded read must not be possible.
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                respond(device, requestId, responseNeeded, BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, offset)
                return
            }
            if (characteristic.uuid != uuid(LinkProtocol.RX_CHARACTERISTIC_UUID) || preparedWrite || offset != 0) {
                respond(device, requestId, responseNeeded, BluetoothGatt.GATT_FAILURE, offset)
                return
            }
            respond(device, requestId, responseNeeded, BluetoothGatt.GATT_SUCCESS, offset)
            handle(value)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == uuid(LinkProtocol.CCCD_UUID)) {
                notificationsEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                respond(device, requestId, responseNeeded, BluetoothGatt.GATT_SUCCESS, offset)
                if (notificationsEnabled) pump()
            } else {
                respond(device, requestId, responseNeeded, BluetoothGatt.GATT_FAILURE, offset)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            codec.frameBudget = LinkProtocol.frameBudget(mtu)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            sending = false
            pump()
        }
    }

    @SuppressLint("MissingPermission")
    private fun respond(device: BluetoothDevice, requestId: Int, needed: Boolean, status: Int, offset: Int) {
        if (needed && hasPermission(connectPermission)) {
            runCatching { server?.sendResponse(device, requestId, status, offset, null) }
        }
    }

    // ---- frames in, frames out ----------------------------------------------------------------

    private fun handle(bytes: ByteArray) {
        val current = session ?: return
        when (val event = codec.accept(bytes)) {
            is CodecEvent.Incomplete -> Unit
            is CodecEvent.Complete -> apply(current.receive(event.message))
            is CodecEvent.Refused -> queue(
                LinkMessage(MessageType.ERROR, LinkPayloads.encodeError(LinkErrorMessage(event.error, event.detail)))
            )
        }
    }

    private fun apply(reaction: Reaction) {
        reaction.send.forEach(::queue)
        reaction.events.forEach { event ->
            when (event) {
                is LinkEvent.ConfirmationCode -> _status.value = Status.Confirming(event.code, event.peerName)
                is LinkEvent.Paired -> {
                    peer?.let { store.remember(it.address, event.peerName.ifBlank { it.safeName() ?: "phone" }) }
                    _status.value = Status.Linked(event.peerName)
                }
                LinkEvent.PairingRejected -> {
                    _status.value = Status.Failed("Pairing was refused.")
                    disconnectPeer()
                }
                is LinkEvent.PlanReceived -> {
                    _plan.value = event.plan
                    acknowledgePlan(event.plan)
                }
                is LinkEvent.Failed -> {
                    _status.value = Status.Failed(event.error.error.name)
                    disconnectPeer()
                }
                // Nothing on the watch sends these yet, and a phone must not be able to make the
                // watch act on one: they are the guided session's, and it does not exist here.
                is LinkEvent.Refused, is LinkEvent.Acknowledged, is LinkEvent.SetLogged,
                is LinkEvent.SessionFinished, LinkEvent.PlanRequested -> Unit
            }
        }
    }

    private fun acknowledgePlan(plan: Plan) {
        val current = session ?: return
        if (current.state != LinkState.READY) return
        // Nothing here persists a plan yet, so the honest outcome is ACCEPTED: this build read it
        // and holds it in memory for as long as the app lives. When the guided session stores
        // plans, INSERTED and IDEMPOTENT become answerable and this is where they are answered.
        queue(current.acknowledge(LinkAck(MessageType.PLAN_PUSHED, plan.planId, plan.revision, AckOutcome.ACCEPTED)))
    }

    private fun queue(message: LinkMessage) {
        codec.frames(message).forEach(outbound::addLast)
        pump()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")   // the pre-API-33 notify call, still the only one at this minSdk
    private fun pump() {
        if (sending || !notificationsEnabled) return
        val device = peer ?: return
        val characteristic = tx ?: return
        val gatt = server ?: return
        if (!hasPermission(connectPermission)) return
        val next = outbound.removeFirstOrNull() ?: return
        sending = true
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.notifyCharacteristicChanged(device, characteristic, false, next) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.value = next
                gatt.notifyCharacteristicChanged(device, characteristic, false)
            }
        }.getOrDefault(false)
        if (!ok) {
            // The stack refused the write outright, so no onNotificationSent will arrive to
            // unstick the queue. Drop the rest of this message rather than wedge the link: the
            // peer will time out and the phone can push again.
            sending = false
            outbound.clear()
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectPeer() {
        val device = peer ?: return
        if (hasPermission(connectPermission)) runCatching { server?.cancelConnection(device) }
    }

    // ---- odds and ends -------------------------------------------------------------------------

    private fun fail(status: Status) {
        _status.value = status
    }

    private fun hasPermission(permission: String?): Boolean =
        permission == null ||
            appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String? =
        if (hasPermission(connectPermission)) runCatching { name }.getOrNull() else null

    private fun watchName(): String = listOfNotNull(
        Build.MANUFACTURER?.takeIf { it.isNotBlank() },
        Build.MODEL?.takeIf { it.isNotBlank() },
    ).joinToString(" ").ifBlank { "Wear OS watch" }

    private fun freshNonce(): ByteArray = ByteArray(Hello.NONCE_BYTES).also(random::nextBytes)

    private fun advertiseSettings(): AdvertiseSettings = AdvertiseSettings.Builder()
        // Low latency while a human is waiting for a list to populate; the phone reconnects to a
        // remembered address afterwards, so this mode is only ever paid for during pairing.
        .setAdvertiseMode(if (store.isPaired) AdvertiseSettings.ADVERTISE_MODE_BALANCED else AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        .setConnectable(true)
        .setTimeout(0)
        .build()

    /** A 128-bit UUID takes 18 of the advertisement's 31 bytes, so the name goes in the scan
     *  response instead of being cut off by the stack. */
    private fun advertiseData(): AdvertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .addServiceUuid(ParcelUuid(uuid(LinkProtocol.SERVICE_UUID)))
        .build()

    private fun scanResponse(): AdvertiseData = AdvertiseData.Builder().setIncludeDeviceName(true).build()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            _status.value = Status.Unavailable(
                when (errorCode) {
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "This watch cannot advertise over Bluetooth."
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Bluetooth is busy. Try again."
                    ADVERTISE_FAILED_ALREADY_STARTED -> return
                    else -> "Bluetooth would not start advertising."
                }
            )
        }
    }

    private fun uuid(text: String): UUID = UUID.fromString(text)

    companion object {
        /** The runtime permissions this needs, empty below Android 12 where they are install-time. */
        val runtimePermissions: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyArray()
            }

        private val connectPermission: String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

        private val advertisePermission: String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_ADVERTISE else null


        private val random = SecureRandom()
    }
}
