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
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.util.UUID

private val HID_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805F9B34FB"))
private val SCAN_GREEN  = Color(0xFF9BBC0F)
private val PAIRED_BLUE = Color(0xFF6A5ACD)

private fun isLikelyController(device: BluetoothDevice): Boolean {
    val cls = device.bluetoothClass ?: return true
    return cls.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL ||
           cls.majorDeviceClass == BluetoothClass.Device.Major.UNCATEGORIZED
}

private fun deviceName(device: BluetoothDevice): String =
    try { device.name?.takeIf { it.isNotBlank() } } catch (_: SecurityException) { null }
        ?: device.address

private fun hasPermissions(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_COARSE_LOCATION)

private enum class DeviceStatus { FOUND, PAIRING, BONDED }

@Composable
fun ControllerPairScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val adapter = remember {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    var permsGranted by remember { mutableStateOf(hasPermissions(context)) }
    var isScanning  by remember { mutableStateOf(false) }
    var discovered  by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var bonded      by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var pairingAddr by remember { mutableStateOf<String?>(null) }
    var justPairedAddr by remember { mutableStateOf<String?>(null) }

    fun refreshBonded() {
        bonded = try {
            adapter?.bondedDevices
                ?.filter { isLikelyController(it) }
                ?.sortedBy { deviceName(it) }
                ?: emptyList()
        } catch (_: SecurityException) { emptyList() }
    }

    // BLE scan callback — must be the same instance for stopScan
    val leScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                if (discovered.none { it.address == device.address })
                    discovered = discovered + device
            }
        }
    }

    fun stopScanning() {
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) { }
        try { adapter?.bluetoothLeScanner?.stopScan(leScanCallback) } catch (_: Exception) { }
        isScanning = false
    }

    fun startScanning() {
        if (adapter == null || !permsGranted) return
        discovered = emptyList()
        pairingAddr = null
        justPairedAddr = null
        isScanning = true
        try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
        try { adapter.startDiscovery() } catch (_: SecurityException) { isScanning = false; return }

        // BLE scan filtered to HID service UUID (covers BLE-mode controllers)
        try {
            val scanner = adapter.bluetoothLeScanner ?: return
            val filter   = ScanFilter.Builder().setServiceUuid(HID_UUID).build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(listOf(filter), settings, leScanCallback)
        } catch (_: Exception) { }
    }

    fun pairDevice(device: BluetoothDevice) {
        stopScanning()
        pairingAddr = device.address
        try { device.createBond() } catch (_: SecurityException) { pairingAddr = null }
    }

    // Register classic BT discovery + bond state receiver
    DisposableEffect(context) {
        refreshBonded()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && isLikelyController(device) &&
                            discovered.none { it.address == device.address }) {
                            discovered = discovered + device
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanning = false
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        when (state) {
                            BluetoothDevice.BOND_BONDED -> {
                                justPairedAddr = device?.address
                                pairingAddr    = null
                                refreshBonded()
                            }
                            BluetoothDevice.BOND_NONE -> {
                                pairingAddr = null
                                refreshBonded()
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            stopScanning()
            context.unregisterReceiver(receiver)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permsGranted = results.values.all { it }
        if (permsGranted) { refreshBonded(); startScanning() }
    }

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isScanning) SCAN_GREEN else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Find Controller", style = MaterialTheme.typography.title3, color = Color.White)
                }
            }

            if (!permsGranted) {
                item {
                    Text(
                        "Bluetooth permission needed",
                        style = MaterialTheme.typography.body2,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                item {
                    Button(
                        onClick = { permLauncher.launch(requiredPermissions()) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = SCAN_GREEN.copy(alpha = 0.85f)),
                    ) { Text("Allow", fontSize = 12.sp) }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(
                            onClick = { if (isScanning) stopScanning() else startScanning() },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (isScanning) Color.White.copy(alpha = 0.10f)
                                    else SCAN_GREEN.copy(alpha = 0.85f)
                            ),
                            label = { Text(if (isScanning) "Stop" else "Scan", fontSize = 11.sp) },
                            icon = {
                                if (isScanning) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                            },
                        )
                        Chip(
                            onClick = onDismiss,
                            colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.08f)),
                            label = { Text("Done", fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) },
                        )
                    }
                }

                if (bonded.isNotEmpty()) {
                    item {
                        Text(
                            "Paired",
                            style = MaterialTheme.typography.caption2,
                            color = PAIRED_BLUE,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(bonded) { device ->
                        DeviceRow(
                            name = deviceName(device),
                            address = device.address,
                            status = DeviceStatus.BONDED,
                            onClick = {},
                        )
                    }
                }

                val undiscoveredNew = discovered.filter { d -> bonded.none { it.address == d.address } }
                if (undiscoveredNew.isNotEmpty()) {
                    item {
                        Text(
                            "Discovered",
                            style = MaterialTheme.typography.caption2,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(undiscoveredNew) { device ->
                        DeviceRow(
                            name = deviceName(device),
                            address = device.address,
                            status = when (device.address) {
                                justPairedAddr -> DeviceStatus.BONDED
                                pairingAddr    -> DeviceStatus.PAIRING
                                else           -> DeviceStatus.FOUND
                            },
                            onClick = {
                                if (device.address != pairingAddr && device.address != justPairedAddr)
                                    pairDevice(device)
                            },
                        )
                    }
                }

                if (!isScanning && undiscoveredNew.isEmpty() && bonded.isEmpty()) {
                    item {
                        Text(
                            "Put your controller in pairing mode, then tap Scan.",
                            style = MaterialTheme.typography.body2,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    address: String,
    status: DeviceStatus,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = when (status) {
                DeviceStatus.BONDED  -> PAIRED_BLUE.copy(alpha = 0.25f)
                DeviceStatus.PAIRING -> Color.White.copy(alpha = 0.12f)
                DeviceStatus.FOUND   -> Color.White.copy(alpha = 0.08f)
            }
        ),
        label = {
            Column {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (status) {
                        DeviceStatus.BONDED  -> "Paired"
                        DeviceStatus.PAIRING -> "Pairing…"
                        DeviceStatus.FOUND   -> address
                    },
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        },
        secondaryLabel = null,
        icon = {
            when (status) {
                DeviceStatus.BONDED  -> Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = PAIRED_BLUE)
                DeviceStatus.PAIRING -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                DeviceStatus.FOUND   -> Icon(Icons.Default.Bluetooth, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
