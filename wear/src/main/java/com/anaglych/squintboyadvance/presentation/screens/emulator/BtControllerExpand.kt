package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.ParcelUuid
import android.view.InputDevice
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh // used in BindingsTabContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.shared.model.ButtonId
import kotlinx.coroutines.delay
import java.util.UUID

private val BT_GREEN   = Color(0xFF9BBC0F)
private val BT_CRIMSON = Color(0xFFEC1358)
private val HID_UUID   = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805F9B34FB"))

internal fun btBtnLabel(btn: ButtonId) = when (btn) {
    ButtonId.A          -> "A"
    ButtonId.B          -> "B"
    ButtonId.START      -> "St"
    ButtonId.SELECT     -> "Se"
    ButtonId.DPAD_UP    -> "↑"
    ButtonId.DPAD_DOWN  -> "↓"
    ButtonId.DPAD_LEFT  -> "←"
    ButtonId.DPAD_RIGHT -> "→"
    ButtonId.L          -> "L"
    ButtonId.R          -> "R"
}

private fun isLikelyController(device: BluetoothDevice): Boolean {
    val cls = device.bluetoothClass ?: return true
    val dc  = cls.deviceClass
    // Exclude definite non-controllers: keyboards, mice, and combo devices
    if (dc == BluetoothClass.Device.PERIPHERAL_KEYBOARD ||
        dc == BluetoothClass.Device.PERIPHERAL_POINTING ||
        dc == BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING) return false
    return cls.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL ||
           cls.majorDeviceClass == BluetoothClass.Device.Major.UNCATEGORIZED
}

private fun btDeviceName(device: BluetoothDevice): String =
    try { device.name?.takeIf { it.isNotBlank() } } catch (_: SecurityException) { null }
        ?: device.address

private fun hasBtPerms(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun findConnectedGamepadName(im: InputManager, adapter: BluetoothAdapter? = null): String? {
    val bondedByName = if (adapter != null) {
        try { adapter.bondedDevices.associateBy { it.name ?: "" } } catch (_: SecurityException) { emptyMap() }
    } else emptyMap()

    for (id in im.inputDeviceIds) {
        val dev: InputDevice = im.getInputDevice(id) ?: continue
        if (dev.isVirtual || !dev.isExternal) continue
        val src = dev.sources
        if ((src and InputDevice.SOURCE_GAMEPAD) == 0 && (src and InputDevice.SOURCE_JOYSTICK) == 0) continue
        // Cross-check BT class — exclude audio devices (headphones, speakers) that
        // spuriously report gamepad/joystick sources via their media-control HID profile.
        val name = dev.name?.takeIf { it.isNotBlank() } ?: "Controller"
        val btDev = bondedByName[name]
        if (btDev != null) {
            val majorClass = btDev.bluetoothClass?.majorDeviceClass
            if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) continue
        }
        return name
    }
    return null
}

/**
 * Expand content for the BT Pad hex button in PauseOverlay.
 * Manages its own BT scan state. Receives live button state and recording
 * state from the ViewModel via the parent.
 */
@Composable
fun BtPadExpandContent(
    liveGamepadButtons: Set<ButtonId>,
    recordingState: EmulatorViewModel.RecordingState,
    onStartRecordAll: () -> Unit,
    onSkipRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val context = LocalContext.current
    val im      = remember { context.getSystemService(InputManager::class.java) }
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val permsOk = remember { hasBtPerms(context) }

    var activeTab        by remember { mutableStateOf(0) }
    var connectedName    by remember { mutableStateOf(findConnectedGamepadName(im, adapter)) }
    var connectedAddress by remember { mutableStateOf<String?>(null) }
    var isScanning       by remember { mutableStateOf(false) }
    var discovered       by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var pairingAddr      by remember { mutableStateOf<String?>(null) }
    var bondedOrphans    by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectingAddr   by remember { mutableStateOf<String?>(null) }

    fun resolveConnectedAddress(name: String?): String? {
        if (name == null) return null
        return try {
            adapter?.bondedDevices?.firstOrNull { dev ->
                try { dev.name == name } catch (_: SecurityException) { false }
            }?.address
        } catch (_: SecurityException) { null }
    }

    fun refreshBondedOrphans(currentConnectedAddr: String?) {
        val bonded = try { adapter?.bondedDevices } catch (_: SecurityException) { null }
        bondedOrphans = bonded
            ?.filter { dev -> dev.address != currentConnectedAddr && isLikelyController(dev) }
            ?: emptyList()
    }

    fun forgetDevice(address: String) {
        val bonded = try { adapter?.bondedDevices } catch (_: SecurityException) { null } ?: return
        bonded.firstOrNull { it.address == address }?.let { dev ->
            try { dev.javaClass.getMethod("removeBond").invoke(dev) } catch (_: Exception) {}
        }
        bondedOrphans = bondedOrphans.filter { it.address != address }
    }

    DisposableEffect(Unit) {
        val addr = resolveConnectedAddress(connectedName)
        connectedAddress = addr
        refreshBondedOrphans(addr)
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) {
                connectedName = findConnectedGamepadName(im, adapter)
                val a = resolveConnectedAddress(connectedName)
                connectedAddress = a
                refreshBondedOrphans(a)
            }
            override fun onInputDeviceRemoved(id: Int) {
                connectedName = findConnectedGamepadName(im, adapter)
                val a = resolveConnectedAddress(connectedName)
                connectedAddress = a
                refreshBondedOrphans(a)
            }
            override fun onInputDeviceChanged(id: Int) {
                connectedName = findConnectedGamepadName(im, adapter)
                val a = resolveConnectedAddress(connectedName)
                connectedAddress = a
                refreshBondedOrphans(a)
            }
        }
        im.registerInputDeviceListener(listener, null)
        onDispose { im.unregisterInputDeviceListener(listener) }
    }

    val leScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val dev = result.device ?: return
                // BLE result always replaces a Classic-discovered entry for the same address.
                // This ensures createBond() bonds via BLE transport for dual-mode controllers
                // (Xbox, PS4 etc.), which lets the system's HOGP stack handle HID automatically.
                discovered = discovered.filter { it.address != dev.address } + dev
            }
        }
    }

    fun stopScan() {
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) { }
        try { adapter?.bluetoothLeScanner?.stopScan(leScanCallback) } catch (_: Exception) { }
        isScanning = false
    }

    fun startScan() {
        if (adapter == null || !permsOk) return
        discovered = emptyList(); pairingAddr = null; isScanning = true
        try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
        try { adapter.startDiscovery() } catch (_: SecurityException) { isScanning = false; return }
        try {
            val scanner  = adapter.bluetoothLeScanner ?: return
            val filter   = ScanFilter.Builder().setServiceUuid(HID_UUID).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(listOf(filter), settings, leScanCallback)
        } catch (_: Exception) { }
    }

    fun pairDevice(device: BluetoothDevice) {
        stopScan(); pairingAddr = device.address
        try { device.createBond() } catch (_: SecurityException) { pairingAddr = null }
    }

    fun forgetController() {
        val address = connectedAddress ?: return
        forgetDevice(address)
        discovered = emptyList()
    }

    fun connectOrphan(device: BluetoothDevice) {
        if (connectingAddr == device.address) return
        connectingAddr = device.address
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        // Probe the HID SDP UUID via RFCOMM to establish an ACL link.
        // The connect() itself fails (HID isn't RFCOMM), but the ACL handshake
        // signals the controller that its bonded host is reachable.
        Thread {
            try {
                val socket = device.createInsecureRfcommSocketToServiceRecord(
                    java.util.UUID.fromString("00001124-0000-1000-8000-00805F9B34FB")
                )
                try { socket.connect() } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
            } catch (_: Exception) {}
            mainHandler.post { connectingAddr = null }
        }.start()
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val dev: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (dev != null && isLikelyController(dev) && discovered.none { it.address == dev.address })
                            discovered = discovered + dev
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        @Suppress("DEPRECATION")
                        val dev: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        // Nothing to do on BOND_BONDED — let the system HID host auto-connect.
                        // Previously we called getProfileProxy + reflection here, but it always
                        // failed (BLUETOOTH_PRIVILEGED) and the bind/release cycle appeared to
                        // interfere with the system HID host accepting future reconnections.
                        if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                            pairingAddr = null
                            refreshBondedOrphans(connectedAddress)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { stopScan(); context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(isScanning) {
        if (isScanning) { delay(30_000L); stopScan() }
    }

    LaunchedEffect(activeTab) {
        if (activeTab != 1) onStopRecording()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Sliding tab selector
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            val pillX by animateDpAsState(
                if (activeTab == 0) 0.dp else maxWidth / 2,
                label = "btPill",
            )
            Box(
                modifier = Modifier
                    .width(maxWidth / 2)
                    .fillMaxHeight()
                    .offset(x = pillX)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BT_GREEN.copy(alpha = 0.85f)),
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                listOf("Controller", "Bindings").forEachIndexed { i, label ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { activeTab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }

        if (activeTab == 0) {
            ControllerTabContent(
                connectedName = connectedName,
                bondedOrphans = bondedOrphans,
                connectingAddr = connectingAddr,
                isScanning = isScanning,
                discovered = discovered,
                pairingAddr = pairingAddr,
                permsOk = permsOk,
                onStartScan = ::startScan,
                onStopScan = ::stopScan,
                onPair = ::pairDevice,
                onDisconnect = ::forgetController,
                onForgetDevice = ::forgetDevice,
                onConnectOrphan = ::connectOrphan,
            )
        } else {
            BindingsTabContent(
                connectedName = connectedName,
                liveGamepadButtons = liveGamepadButtons,
                recordingState = recordingState,
                onStartRecordAll = onStartRecordAll,
                onSkipRecording = onSkipRecording,
                onStopRecording = onStopRecording,
                onResetDefaults = onResetDefaults,
            )
        }
    }
}

@Composable
private fun ControllerTabContent(
    connectedName: String?,
    bondedOrphans: List<BluetoothDevice>,
    connectingAddr: String?,
    isScanning: Boolean,
    discovered: List<BluetoothDevice>,
    pairingAddr: String?,
    permsOk: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPair: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onForgetDevice: (String) -> Unit,
    onConnectOrphan: (BluetoothDevice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (connectedName != null) {
            BondedDeviceRow(name = connectedName, tint = BT_GREEN, onForget = onDisconnect)
        }


        // Bonded but not active as InputDevice — tap to attempt reconnect
        bondedOrphans.forEach { dev ->
            BondedDeviceRow(
                name = btDeviceName(dev),
                tint = Color.White.copy(alpha = 0.35f),
                isConnecting = connectingAddr == dev.address,
                onConnect = { onConnectOrphan(dev) },
                onForget = { onForgetDevice(dev.address) },
            )
        }

        if (!permsOk) {
            Text("Bluetooth permission required", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
        } else {
            if (isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onStopScan() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Searching…", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BT_GREEN.copy(alpha = 0.85f))
                        .clickable { onStartScan() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Search", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White)
                }
            }

            discovered.forEach { device ->
                val isPairing = device.address == pairingAddr
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = !isPairing) { onPair(device) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isPairing) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
                    else Icon(Icons.Default.Bluetooth, null, Modifier.size(10.dp), tint = Color.White.copy(alpha = 0.4f))
                    Text(
                        btDeviceName(device), style = MaterialTheme.typography.caption2, fontSize = 10.sp,
                        color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (isPairing) Text("Pairing…", style = MaterialTheme.typography.caption2, fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun BondedDeviceRow(
    name: String,
    tint: Color,
    isConnecting: Boolean = false,
    onConnect: (() -> Unit)? = null,
    onForget: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.12f))
            .then(if (onConnect != null && !isConnecting) Modifier.clickable { onConnect() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isConnecting)
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
        else
            Icon(Icons.Default.Bluetooth, null, Modifier.size(12.dp), tint = tint)
        Text(
            if (isConnecting) "Connecting…" else name,
            style = MaterialTheme.typography.caption2, fontSize = 10.sp,
            color = Color.White, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (!isConnecting) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { onForget() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Forget", style = MaterialTheme.typography.caption2, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun BindingsTabContent(
    connectedName: String?,
    liveGamepadButtons: Set<ButtonId>,
    recordingState: EmulatorViewModel.RecordingState,
    onStartRecordAll: () -> Unit,
    onSkipRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val isRecording = recordingState is EmulatorViewModel.RecordingState.Recording

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isRecording) {
            val st = recordingState as EmulatorViewModel.RecordingState.Recording
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${st.stepIndex + 1} / ${st.totalSteps}", style = MaterialTheme.typography.caption2, fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
                Text("Press for:", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                Text(btBtnLabel(st.targetButton), style = MaterialTheme.typography.title3, color = Color.White, fontSize = 20.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onSkipRecording() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Skip", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)) }
            }
        } else if (connectedName == null) {
            Text("Pair a controller first.", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
        } else {
            GbaButtonGrid(pressedButtons = liveGamepadButtons)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier.weight(1f).height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BT_CRIMSON.copy(alpha = 0.85f))
                        .clickable { onStopRecording() },
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White) }
            } else {
                Box(
                    modifier = Modifier.weight(1f).height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onStartRecordAll() },
                    contentAlignment = Alignment.Center,
                ) { Text("Rebind All", style = MaterialTheme.typography.caption2, fontSize = 10.sp, color = Color.White) }
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onResetDefaults() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Refresh, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.5f)) }
            }
        }
    }
}

@Composable
private fun GbaButtonGrid(pressedButtons: Set<ButtonId>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Pip(ButtonId.L,      "L",  pressedButtons)
                Pip(ButtonId.B,      "B",  pressedButtons)
                Pip(ButtonId.SELECT, "Se", pressedButtons)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.size(22.dp)); Pip(ButtonId.DPAD_UP, "↑", pressedButtons); Box(Modifier.size(22.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Pip(ButtonId.DPAD_LEFT, "←", pressedButtons); Box(Modifier.size(22.dp)); Pip(ButtonId.DPAD_RIGHT, "→", pressedButtons)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.size(22.dp)); Pip(ButtonId.DPAD_DOWN, "↓", pressedButtons); Box(Modifier.size(22.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Pip(ButtonId.R,     "R",  pressedButtons)
                Pip(ButtonId.A,     "A",  pressedButtons)
                Pip(ButtonId.START, "St", pressedButtons)
            }
        }
    }
}

@Composable
private fun Pip(button: ButtonId, label: String, pressed: Set<ButtonId>) {
    val isPressed = button in pressed
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isPressed) BT_GREEN.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.caption2, fontSize = 8.sp, color = Color.White)
    }
}
