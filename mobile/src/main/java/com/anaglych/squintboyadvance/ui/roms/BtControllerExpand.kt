package com.anaglych.squintboyadvance.ui.roms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import android.provider.Settings
import android.view.InputDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.GamepadMapping
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ── Constants ─────────────────────────────────────────────────────────────────

private val HID_UUID = UUID.fromString("00001812-0000-1000-8000-00805F9B34FB")
internal val BT_PRIMARY_GREEN = Color(0xFF9BBC0F)
internal val BT_CRIMSON = Color(0xFFEC1358)

private val REBIND_ORDER = listOf(
    ButtonId.A, ButtonId.B, ButtonId.SELECT, ButtonId.START,
    ButtonId.DPAD_RIGHT, ButtonId.DPAD_LEFT, ButtonId.DPAD_UP, ButtonId.DPAD_DOWN,
    ButtonId.R, ButtonId.L,
)

private enum class ControllerBtTab { CONTROLLER, BINDINGS }
private enum class ScanState { IDLE, SCANNING }

private fun btButtonLabel(button: ButtonId): String = when (button) {
    ButtonId.A -> "A"
    ButtonId.B -> "B"
    ButtonId.START -> "St"
    ButtonId.SELECT -> "Sel"
    ButtonId.DPAD_UP -> "↑"
    ButtonId.DPAD_DOWN -> "↓"
    ButtonId.DPAD_LEFT -> "←"
    ButtonId.DPAD_RIGHT -> "→"
    ButtonId.L -> "L"
    ButtonId.R -> "R"
}

private fun btButtonLongLabel(button: ButtonId): String = when (button) {
    ButtonId.A -> "A"
    ButtonId.B -> "B"
    ButtonId.START -> "Start"
    ButtonId.SELECT -> "Select"
    ButtonId.DPAD_UP -> "D-Pad Up"
    ButtonId.DPAD_DOWN -> "D-Pad Down"
    ButtonId.DPAD_LEFT -> "D-Pad Left"
    ButtonId.DPAD_RIGHT -> "D-Pad Right"
    ButtonId.L -> "L"
    ButtonId.R -> "R"
}

// ── Main composable ───────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BtControllerExpand(
    gamepadEnabled: Boolean,
    onToggleGamepad: () -> Unit,
    gamepadMapping: GamepadMapping,
    onUpdateMapping: (GamepadMapping) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(ControllerBtTab.CONTROLLER) }
    var scanState by remember { mutableStateOf(ScanState.IDLE) }
    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    var bondingDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connectedGamepad by remember { mutableStateOf<InputDevice?>(null) }
    var pressedGbaButtons by remember { mutableStateOf<Set<ButtonId>>(emptySet()) }
    var isRebinding by remember { mutableStateOf(false) }
    var rebindStep by remember { mutableStateOf(0) }
    var pendingMapping by remember(gamepadMapping) { mutableStateOf(gamepadMapping) }
    var hasPermission by remember { mutableStateOf(false) }

    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    // Recheck permission each time the panel opens
    LaunchedEffect(expanded) {
        if (expanded) hasPermission = context.hasBtPermissions()
    }

    // Scan timeout: auto-stop after 30 s
    LaunchedEffect(scanState) {
        if (scanState == ScanState.SCANNING) {
            delay(30_000L)
            stopBtScan(bluetoothAdapter, leScanCallback = null)
            scanState = ScanState.IDLE
        }
    }

    // Stable BLE callback — captures discoveredDevices list directly
    val leScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device
                if (discoveredDevices.none { it.address == dev.address }) {
                    scope.launch { discoveredDevices.add(dev) }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
    }

    // Classic BT discovery + bonding broadcast receiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val dev = deviceFromIntent(intent) ?: return
                        if (discoveredDevices.none { it.address == dev.address }) {
                            discoveredDevices.add(dev)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (scanState == ScanState.SCANNING) scanState = ScanState.IDLE
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val dev = deviceFromIntent(intent) ?: return
                        val state = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.BOND_NONE,
                        )
                        if (dev.address == bondingDevice?.address) {
                            if (state == BluetoothDevice.BOND_BONDED ||
                                state == BluetoothDevice.BOND_NONE
                            ) bondingDevice = null
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // Give InputManager a moment to register the device
                        scope.launch {
                            delay(600L)
                            connectedGamepad = findConnectedGamepad(context)
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        connectedGamepad = findConnectedGamepad(context)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // InputManager listener — track connected gamepads
    DisposableEffect(Unit) {
        connectedGamepad = findConnectedGamepad(context)
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) {
                connectedGamepad = findConnectedGamepad(context)
            }
            override fun onInputDeviceRemoved(id: Int) {
                connectedGamepad = findConnectedGamepad(context)
            }
            override fun onInputDeviceChanged(id: Int) {}
        }
        im.registerInputDeviceListener(listener, null)
        onDispose { im.unregisterInputDeviceListener(listener) }
    }

    // Fall back to Controller tab when no gamepad is connected
    LaunchedEffect(connectedGamepad) {
        if (connectedGamepad == null && activeTab == ControllerBtTab.BINDINGS) {
            activeTab = ControllerBtTab.CONTROLLER
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Toggle / expand button ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (gamepadEnabled) BT_PRIMARY_GREEN
                    else Color.Transparent,
                )
                .then(
                    if (!gamepadEnabled) Modifier.border(
                        1.dp,
                        BT_CRIMSON.copy(alpha = 0.45f),
                        RoundedCornerShape(8.dp),
                    ) else Modifier,
                )
                .combinedClickable(
                    onClick = onToggleGamepad,
                    onLongClick = { expanded = !expanded },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (gamepadEnabled) Color.White else BT_CRIMSON,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Bluetooth Controller",
                style = MaterialTheme.typography.bodyMedium,
                color = if (gamepadEnabled) Color.White else BT_CRIMSON,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (gamepadEnabled) Color.White.copy(alpha = 0.7f)
                    else BT_CRIMSON.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }

        // ── Expand panel ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                if (!hasPermission) {
                    PermissionPrompt { permissionLauncher.launch(btPermissions()) }
                } else {
                    BtTabSelector(active = activeTab, onSelect = { activeTab = it })
                    Spacer(Modifier.height(10.dp))

                    when (activeTab) {
                        ControllerBtTab.CONTROLLER -> ControllerSection(
                            connectedGamepad = connectedGamepad,
                            scanState = scanState,
                            discoveredDevices = discoveredDevices,
                            bondingDevice = bondingDevice,
                            onScanStart = {
                                discoveredDevices.clear()
                                scanState = ScanState.SCANNING
                                startBtScan(bluetoothAdapter, leScanCallback)
                            },
                            onRefresh = {
                                discoveredDevices.clear()
                                scanState = ScanState.SCANNING
                                startBtScan(bluetoothAdapter, leScanCallback)
                            },
                            onStopScan = {
                                stopBtScan(bluetoothAdapter, leScanCallback)
                                scanState = ScanState.IDLE
                            },
                            onPair = { device ->
                                bondingDevice = device
                                runCatching { device.createBond() }
                            },
                            onDisconnect = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )

                        ControllerBtTab.BINDINGS -> BindingsSection(
                            connectedGamepad = connectedGamepad,
                            gamepadMapping = pendingMapping,
                            pressedGbaButtons = pressedGbaButtons,
                            onPressedChanged = { pressedGbaButtons = it },
                            isRebinding = isRebinding,
                            rebindStep = rebindStep,
                            onRebindStart = {
                                pendingMapping = gamepadMapping
                                rebindStep = 0
                                isRebinding = true
                            },
                            onRebindCancel = {
                                isRebinding = false
                                pendingMapping = gamepadMapping
                            },
                            onRebindKeyPress = { keyCode ->
                                val updated = pendingMapping.withButton(REBIND_ORDER[rebindStep], keyCode)
                                pendingMapping = updated
                                val next = rebindStep + 1
                                if (next >= REBIND_ORDER.size) {
                                    isRebinding = false
                                    onUpdateMapping(updated)
                                } else {
                                    rebindStep = next
                                }
                            },
                            onResetMapping = {
                                val defaults = GamepadMapping()
                                pendingMapping = defaults
                                onUpdateMapping(defaults)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Tab selector ──────────────────────────────────────────────────────────────

@Composable
private fun BtTabSelector(active: ControllerBtTab, onSelect: (ControllerBtTab) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        val halfW = maxWidth / 2
        val pillX by animateDpAsState(
            targetValue = if (active == ControllerBtTab.CONTROLLER) 0.dp else halfW,
            animationSpec = tween(200),
            label = "btTab",
        )

        // Moving green pill
        Box(
            modifier = Modifier
                .offset(x = pillX)
                .width(halfW)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BT_PRIMARY_GREEN),
        )

        // Labels on top (so they're above the pill)
        Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clickable { onSelect(ControllerBtTab.CONTROLLER) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Controller",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active == ControllerBtTab.CONTROLLER) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clickable { onSelect(ControllerBtTab.BINDINGS) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Bindings",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active == ControllerBtTab.BINDINGS) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ── Controller tab ────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun ControllerSection(
    connectedGamepad: InputDevice?,
    scanState: ScanState,
    discoveredDevices: List<BluetoothDevice>,
    bondingDevice: BluetoothDevice?,
    onScanStart: () -> Unit,
    onRefresh: () -> Unit,
    onStopScan: () -> Unit,
    onPair: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (connectedGamepad != null) {
            // ── Connected ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BT_PRIMARY_GREEN.copy(alpha = 0.12f))
                    .border(1.dp, BT_PRIMARY_GREEN.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = BT_PRIMARY_GREEN,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    connectedGamepad.name ?: "Controller",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BT_CRIMSON),
                    border = BorderStroke(1.dp, BT_CRIMSON.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Disconnect", fontSize = 12.sp)
                }
            }
        } else {
            // ── Not connected: scan controls ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = if (scanState == ScanState.SCANNING) onStopScan else onScanStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scanState == ScanState.SCANNING)
                            MaterialTheme.colorScheme.surfaceVariant
                        else BT_PRIMARY_GREEN,
                        contentColor = if (scanState == ScanState.SCANNING)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    if (scanState == ScanState.SCANNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Searching…", fontSize = 13.sp)
                    } else {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Search", fontSize = 13.sp)
                    }
                }
                if (scanState == ScanState.IDLE) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh scan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Discovered devices
            if (discoveredDevices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Discovered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                discoveredDevices.forEach { device ->
                    DeviceRow(
                        device = device,
                        isBonding = bondingDevice?.address == device.address,
                        onPair = { onPair(device) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            } else if (scanState == ScanState.IDLE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No controllers found. Press Search to scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(
    device: BluetoothDevice,
    isBonding: Boolean,
    onPair: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            runCatching { device.name }.getOrNull() ?: device.address,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        when {
            isBonding -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = BT_PRIMARY_GREEN,
            )
            runCatching { device.bondState }.getOrNull() == BluetoothDevice.BOND_BONDED ->
                Text("Paired", fontSize = 11.sp, color = BT_PRIMARY_GREEN)
            else -> OutlinedButton(
                onClick = onPair,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text("Pair", fontSize = 11.sp)
            }
        }
    }
}

// ── Bindings tab ──────────────────────────────────────────────────────────────

@Composable
private fun BindingsSection(
    connectedGamepad: InputDevice?,
    gamepadMapping: GamepadMapping,
    pressedGbaButtons: Set<ButtonId>,
    onPressedChanged: (Set<ButtonId>) -> Unit,
    isRebinding: Boolean,
    rebindStep: Int,
    onRebindStart: () -> Unit,
    onRebindCancel: () -> Unit,
    onRebindKeyPress: (Int) -> Unit,
    onResetMapping: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(connectedGamepad, isRebinding) {
        if (connectedGamepad != null) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (connectedGamepad == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Pair a Bluetooth controller first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Key capture surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val kev = event.nativeKeyEvent
                        val isGamepad =
                            (kev.source and InputDevice.SOURCE_GAMEPAD) != 0 ||
                            (kev.source and InputDevice.SOURCE_JOYSTICK) != 0
                        if (!isGamepad) return@onPreviewKeyEvent false
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (isRebinding) {
                                    onRebindKeyPress(kev.keyCode)
                                } else {
                                    val btn = gamepadMapping.fromKeyCode(kev.keyCode)
                                    if (btn != null) onPressedChanged(pressedGbaButtons + btn)
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                if (!isRebinding) {
                                    val btn = gamepadMapping.fromKeyCode(kev.keyCode)
                                    if (btn != null) onPressedChanged(pressedGbaButtons - btn)
                                }
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                if (isRebinding) {
                    RebindWheel(step = rebindStep)
                } else {
                    GbaButtonGrid(pressedGbaButtons = pressedGbaButtons)
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(8.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRebinding) {
                    Button(
                        onClick = onRebindCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = BT_CRIMSON),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onRebindStart,
                        colors = ButtonDefaults.buttonColors(containerColor = BT_PRIMARY_GREEN),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Rebind All", fontSize = 13.sp)
                    }
                    IconButton(
                        onClick = onResetMapping,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset to defaults",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── GBA button visualizer ─────────────────────────────────────────────────────

@Composable
private fun GbaButtonGrid(pressedGbaButtons: Set<ButtonId>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Shoulder row
        Row(modifier = Modifier.fillMaxWidth()) {
            GbaButtonPip(ButtonId.L, pressedGbaButtons)
            Spacer(Modifier.weight(1f))
            GbaButtonPip(ButtonId.R, pressedGbaButtons)
        }

        // DPad + face buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // DPad
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GbaButtonPip(ButtonId.DPAD_UP, pressedGbaButtons)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GbaButtonPip(ButtonId.DPAD_LEFT, pressedGbaButtons)
                    Spacer(Modifier.width(30.dp))
                    GbaButtonPip(ButtonId.DPAD_RIGHT, pressedGbaButtons)
                }
                GbaButtonPip(ButtonId.DPAD_DOWN, pressedGbaButtons)
            }

            Spacer(Modifier.weight(1f))

            // Face buttons (B upper-left, A lower-right)
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GbaButtonPip(ButtonId.B, pressedGbaButtons)
                    Spacer(Modifier.width(4.dp))
                    Spacer(Modifier.size(36.dp)) // offset for A below
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(36.dp))
                    Spacer(Modifier.width(4.dp))
                    GbaButtonPip(ButtonId.A, pressedGbaButtons)
                }
            }
        }

        // Select / Start row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            GbaButtonPip(ButtonId.SELECT, pressedGbaButtons)
            Spacer(Modifier.width(20.dp))
            GbaButtonPip(ButtonId.START, pressedGbaButtons)
        }
    }
}

@Composable
private fun GbaButtonPip(button: ButtonId, pressed: Set<ButtonId>) {
    val active = button in pressed
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active) BT_PRIMARY_GREEN
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            )
            .border(
                1.dp,
                if (active) BT_PRIMARY_GREEN
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            btButtonLabel(button),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (active) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}

// ── Rebind wheel ──────────────────────────────────────────────────────────────

@Composable
private fun RebindWheel(step: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${step + 1} / ${REBIND_ORDER.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )

        // Conveyor belt — animate whole row on step change
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally { -it / 2 } + fadeOut(tween(140)))
            },
            label = "rebindStep",
        ) { s ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Prev (faded)
                val prev = if (s > 0) REBIND_ORDER[s - 1] else null
                if (prev != null) {
                    WheelItem(prev, scale = 0.62f, alpha = 0.28f, highlighted = false)
                } else {
                    Spacer(Modifier.size(40.dp))
                }
                Spacer(Modifier.width(10.dp))
                // Current (full)
                WheelItem(REBIND_ORDER[s], scale = 1f, alpha = 1f, highlighted = true)
                Spacer(Modifier.width(10.dp))
                // Next (faded)
                val next = if (s < REBIND_ORDER.size - 1) REBIND_ORDER[s + 1] else null
                if (next != null) {
                    WheelItem(next, scale = 0.62f, alpha = 0.28f, highlighted = false)
                } else {
                    Spacer(Modifier.size(40.dp))
                }
            }
        }

        Text(
            "Press controller button for:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Text(
            btButtonLongLabel(REBIND_ORDER[step]),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WheelItem(button: ButtonId, scale: Float, alpha: Float, highlighted: Boolean) {
    val size = (64 * scale).dp
    val corner = (8 * scale).dp
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .clip(RoundedCornerShape(corner))
            .background(
                if (highlighted) BT_PRIMARY_GREEN.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )
            .border(
                (1.5f * scale).dp,
                if (highlighted) BT_PRIMARY_GREEN
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(corner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            btButtonLabel(button),
            fontWeight = FontWeight.Bold,
            fontSize = (18 * scale).sp,
            color = if (highlighted) BT_PRIMARY_GREEN
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Permission prompt ─────────────────────────────────────────────────────────

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Bluetooth permission is required to scan for controllers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = BT_PRIMARY_GREEN),
        ) {
            Text("Grant Permission")
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
private fun startBtScan(adapter: BluetoothAdapter?, leScanCallback: ScanCallback) {
    adapter ?: return
    if (adapter.isDiscovering) adapter.cancelDiscovery()
    adapter.startDiscovery()
    runCatching {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HID_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter.bluetoothLeScanner?.startScan(listOf(filter), settings, leScanCallback)
    }
}

@SuppressLint("MissingPermission")
private fun stopBtScan(adapter: BluetoothAdapter?, leScanCallback: ScanCallback?) {
    adapter ?: return
    if (adapter.isDiscovering) runCatching { adapter.cancelDiscovery() }
    leScanCallback?.let { runCatching { adapter.bluetoothLeScanner?.stopScan(it) } }
}

private fun findConnectedGamepad(context: Context): InputDevice? {
    val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    for (deviceId in im.inputDeviceIds) {
        val dev: InputDevice = im.getInputDevice(deviceId) ?: continue
        val src = dev.sources
        if ((src and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (src and InputDevice.SOURCE_JOYSTICK) != 0) return dev
    }
    return null
}

private fun Context.hasBtPermissions(): Boolean {
    val perms = btPermissions()
    return perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}

private fun btPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun deviceFromIntent(intent: Intent): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
