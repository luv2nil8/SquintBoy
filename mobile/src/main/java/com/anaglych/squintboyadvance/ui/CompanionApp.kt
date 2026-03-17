package com.anaglych.squintboyadvance.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.ui.roms.RomManagementScreen
import com.anaglych.squintboyadvance.ui.roms.RomsTab
import com.anaglych.squintboyadvance.ui.roms.WatchRomListViewModel
import com.anaglych.squintboyadvance.ui.settings.LicensesScreen
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val ROUTE_ROMS = "roms"
private const val ROUTE_LICENSES = "licenses"

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val nodeClient = Wearable.getNodeClient(application)
    private val capabilityClient = Wearable.getCapabilityClient(application)
    private val remoteActivityHelper = RemoteActivityHelper(application)
    private val prefs = application.getSharedPreferences("connection", android.content.Context.MODE_PRIVATE)

    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    private val _connectedNodeId = MutableStateFlow<String?>(null)
    val connectedNodeId: StateFlow<String?> = _connectedNodeId.asStateFlow()

    private val _watchAppInstalled = MutableStateFlow<Boolean?>(null)
    val watchAppInstalled: StateFlow<Boolean?> = _watchAppInstalled.asStateFlow()

    /** True once connectivity check has completed at least once this session. */
    private val _hasChecked = MutableStateFlow(false)
    val hasChecked: StateFlow<Boolean> = _hasChecked.asStateFlow()

    /** True if a watch has ever successfully connected in any session. */
    private val _hasEverConnected = MutableStateFlow(prefs.getBoolean("has_ever_connected", false))
    val hasEverConnected: StateFlow<Boolean> = _hasEverConnected.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val node = nodes.firstOrNull()
                _watchConnected.value = node != null
                _connectedNodeId.value = node?.id

                if (node != null) {
                    // Record that a watch has connected at least once
                    if (!_hasEverConnected.value) {
                        _hasEverConnected.value = true
                        prefs.edit().putBoolean("has_ever_connected", true).apply()
                    }
                    val capInfo = capabilityClient.getCapability(
                        WearMessageConstants.CAPABILITY_WATCH_APP,
                        CapabilityClient.FILTER_ALL,
                    ).await()
                    _watchAppInstalled.value = capInfo.nodes.isNotEmpty()
                } else {
                    _watchAppInstalled.value = null
                }
            } catch (_: Exception) {
                _watchConnected.value = false
                _connectedNodeId.value = null
                _watchAppInstalled.value = null
            } finally {
                _hasChecked.value = true
            }
        }
    }

    fun openWatchPlayStore() {
        val nodeId = _connectedNodeId.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    remoteActivityHelper.startRemoteActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse(WearMessageConstants.PLAY_STORE_URI))
                            .addCategory(Intent.CATEGORY_BROWSABLE),
                        nodeId,
                    ).get()
                }
            } catch (_: Exception) { /* best-effort */ }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionApp(
    connectionViewModel: ConnectionViewModel = viewModel(),
    watchRomListViewModel: WatchRomListViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val watchConnected by connectionViewModel.watchConnected.collectAsStateWithLifecycle()
    val watchAppInstalled by connectionViewModel.watchAppInstalled.collectAsStateWithLifecycle()
    val hasChecked by connectionViewModel.hasChecked.collectAsStateWithLifecycle()
    val hasEverConnected by connectionViewModel.hasEverConnected.collectAsStateWithLifecycle()

    val isRootRoute = currentRoute == ROUTE_ROMS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Squint Boy Advance") },
                navigationIcon = {
                    if (!isRootRoute) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val needsInstall = watchConnected && watchAppInstalled == false
            val noWatch = !watchConnected
            var bannerDismissed by rememberSaveable { mutableStateOf(false) }

            // Show banner automatically only after check completes, when there's
            // an issue, and only if a watch has never connected before (first-time setup).
            // Once dismissed, stays hidden for the session.
            val showBanner = hasChecked && !bannerDismissed &&
                !hasEverConnected && (noWatch || needsInstall)

            if (showBanner) {
                WatchAppInstallBanner(
                    noWatchDetected = noWatch && !needsInstall,
                    onInstall = { connectionViewModel.openWatchPlayStore() },
                    onRefresh = { connectionViewModel.refresh() },
                    onDismiss = { bannerDismissed = true },
                )
            }
            ConnectionStatusBar(
                connected = watchConnected,
                watchAppInstalled = watchAppInstalled,
                onRefresh = { connectionViewModel.refresh() },
                onInstall = { connectionViewModel.openWatchPlayStore() },
            )

            NavHost(
                navController = navController,
                startDestination = ROUTE_ROMS,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(ROUTE_ROMS) {
                    RomsTab(
                        watchConnected = watchConnected,
                        watchRomListViewModel = watchRomListViewModel,
                        onRomSelected = { entry ->
                            navController.navigate(
                                "rom_management/${Uri.encode(entry.romId)}/${entry.systemType.name}"
                            )
                        },
                    )
                }
                composable(ROUTE_LICENSES) {
                    LicensesScreen()
                }
                composable(
                    route = "rom_management/{romId}/{systemType}",
                    arguments = listOf(
                        navArgument("romId") { type = NavType.StringType },
                        navArgument("systemType") { type = NavType.StringType },
                    ),
                ) { backStack ->
                    val romId = Uri.decode(backStack.arguments?.getString("romId") ?: "")
                    val systemType = runCatching {
                        SystemType.valueOf(backStack.arguments?.getString("systemType") ?: "GB")
                    }.getOrDefault(SystemType.GB)
                    RomManagementScreen(
                        romId = romId,
                        systemType = systemType,
                        watchConnected = watchConnected,
                        onRomDeleted = {
                            watchRomListViewModel.removeRomLocally(romId)
                            navController.popBackStack()
                        },
                        onRenamed = { newName ->
                            watchRomListViewModel.setDisplayName(romId, newName)
                        },
                        onOpenLicenses = { navController.navigate(ROUTE_LICENSES) },
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(
    connected: Boolean,
    watchAppInstalled: Boolean?,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
) {
    val needsInstall = connected && watchAppInstalled == false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (needsInstall) Icons.Default.Download else Icons.Default.Watch,
            contentDescription = null,
            tint = if (connected && !needsInstall) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                needsInstall -> "Watch: App Not Found"
                connected -> "Watch: Connected"
                else -> "Watch: Disconnected"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (connected && !needsInstall) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (connected && !needsInstall) {
            Spacer(Modifier.width(6.dp))
            Text("\u25CF", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        if (needsInstall) {
            TextButton(onClick = onInstall) {
                Text("Install", style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = onRefresh) {
            Text("Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val GreenPrimary = Color(0xFF9BBC0F)
private val DarkNavy = Color(0xFF16213E)

@Composable
fun WatchAppInstallBanner(
    noWatchDetected: Boolean,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sentToWatch by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkNavy),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Fixed-size icon area so layout doesn't shift between states
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp),
            ) {
                if (sentToWatch) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val ringScale by pulse.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "ringScale",
                    )
                    val ringAlpha by pulse.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "ringAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                            }
                            .border(2.dp, GreenPrimary.copy(alpha = ringAlpha), CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (noWatchDetected) Color.White.copy(alpha = 0.08f)
                            else GreenPrimary.copy(alpha = 0.15f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Watch,
                        contentDescription = null,
                        tint = if (noWatchDetected) Color.White.copy(alpha = 0.5f) else GreenPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            when {
                noWatchDetected -> {
                    // ── No watch paired/reachable ──
                    Text(
                        "No Watch Detected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Squint Boy Advance requires a Wear OS smartwatch. Pair a watch with your phone, then come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Refresh", color = Color.White)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Dismiss",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                sentToWatch -> {
                    // ── "Check your watch" after install tap ──
                    Text(
                        "Check Your Watch",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "The Play Store should be opening on your watch. Follow the prompts to install.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { sentToWatch = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Try Again", color = Color.White)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Dismiss",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                else -> {
                    // ── Install prompt ──
                    Text(
                        "Watch App Not Installed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Install Squint Boy on your watch to transfer ROMs, sync settings, and play games.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onInstall()
                            sentToWatch = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenPrimary,
                            contentColor = DarkNavy,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Install on Watch",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Dismiss",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
