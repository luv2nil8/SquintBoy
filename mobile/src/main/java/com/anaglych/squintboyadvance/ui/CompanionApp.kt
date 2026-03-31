package com.anaglych.squintboyadvance.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.anaglych.squintboyadvance.MobileBillingManager
import com.anaglych.squintboyadvance.PurchaseRequestSignal
import com.anaglych.squintboyadvance.ReviewRequestSignal
import com.anaglych.squintboyadvance.WatchPongSignal
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.ui.components.OverlayCard
import com.anaglych.squintboyadvance.ui.theme.Crimson
import com.anaglych.squintboyadvance.ui.roms.RomManagementScreen
import com.anaglych.squintboyadvance.ui.roms.RomsTab
import com.anaglych.squintboyadvance.ui.roms.WatchRomListViewModel
import com.anaglych.squintboyadvance.ui.theme.DarkNavy
import com.anaglych.squintboyadvance.ui.theme.GbGreen
import com.anaglych.squintboyadvance.ui.settings.LicensesScreen
import com.google.android.gms.wearable.Wearable
import com.anaglych.squintboyadvance.BuildConfig
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.play.core.review.ReviewInfo

private const val ROUTE_ROMS = "roms"
private const val ROUTE_LICENSES = "licenses"

// ── State machine ────────────────────────────────────────────────────────────

enum class WatchConnectionState {
    /** Initial check in progress. */
    CHECKING,
    /** No paired/connected watch found. First-time user. */
    NO_WATCH,
    /** Watch connected but app not installed (ping timed out). */
    WATCH_NO_APP,
    /** Watch + app confirmed reachable via ping/pong. */
    CONNECTED,
    /** Was previously connected, but watch is now unreachable. */
    DISCONNECTED,
}

// ── ViewModel ────────────────────────────────────────────────────────────────

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ConnectionVM"
        private const val PING_TIMEOUT_MS = 5000L
        private const val INSTALL_POLL_INTERVAL_MS = 10_000L
    }

    private val nodeClient = Wearable.getNodeClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val remoteActivityHelper = RemoteActivityHelper(application)
    private val prefs = application.getSharedPreferences("connection", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(WatchConnectionState.CHECKING)
    val state: StateFlow<WatchConnectionState> = _state.asStateFlow()

    private val _connectedNodeId = MutableStateFlow<String?>(null)
    val connectedNodeId: StateFlow<String?> = _connectedNodeId.asStateFlow()

    /** True if a watch app has ever responded to a ping in any session. */
    private val _hasEverConnected = MutableStateFlow(prefs.getBoolean("has_ever_connected", false))
    val hasEverConnected: StateFlow<Boolean> = _hasEverConnected.asStateFlow()

    /** True while polling for the watch app after "Install on Watch" was tapped. */
    private val _isPollingForInstall = MutableStateFlow(false)
    val isPollingForInstall: StateFlow<Boolean> = _isPollingForInstall.asStateFlow()

    // Ping/pong synchronization — set by WatchPongSignal collector
    private var pongReceived = false
    private val pongLock = Object()

    private var installPollJob: Job? = null

    /** Convenience: true when the watch app is confirmed reachable. */
    val isReady: Boolean get() = _state.value == WatchConnectionState.CONNECTED

    init {
        // Collect pong signals from MobileListenerService
        viewModelScope.launch {
            WatchPongSignal.pongs.collect {
                synchronized(pongLock) {
                    pongReceived = true
                    (pongLock as Object).notifyAll()
                }
            }
        }
        refresh()
    }

    override fun onCleared() {
        installPollJob?.cancel()
        super.onCleared()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = WatchConnectionState.CHECKING
            try {
                val nodes = withContext(Dispatchers.IO) {
                    nodeClient.connectedNodes.await()
                }
                val node = nodes.firstOrNull()
                _connectedNodeId.value = node?.id

                if (node == null) {
                    _state.value = if (_hasEverConnected.value) {
                        WatchConnectionState.DISCONNECTED
                    } else {
                        WatchConnectionState.NO_WATCH
                    }
                    return@launch
                }

                // Watch node exists — ping the app to confirm it's installed & running
                val appResponded = pingWatch(node.id)
                if (appResponded) {
                    if (!_hasEverConnected.value) {
                        _hasEverConnected.value = true
                        prefs.edit().putBoolean("has_ever_connected", true).apply()
                    }
                    _state.value = WatchConnectionState.CONNECTED
                } else {
                    _state.value = WatchConnectionState.WATCH_NO_APP
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed", e)
                _connectedNodeId.value = null
                _state.value = if (_hasEverConnected.value) {
                    WatchConnectionState.DISCONNECTED
                } else {
                    WatchConnectionState.NO_WATCH
                }
            }
        }
    }

    /**
     * Sends a ping to the watch and waits up to [PING_TIMEOUT_MS] for a pong.
     * Returns true if pong was received.
     */
    private suspend fun pingWatch(nodeId: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(pongLock) { pongReceived = false }
        try {
            messageClient.sendMessage(
                nodeId,
                WearMessageConstants.PATH_WATCH_PING,
                byteArrayOf(),
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send ping", e)
            return@withContext false
        }

        // Wait for pong with timeout
        val deadline = System.currentTimeMillis() + PING_TIMEOUT_MS
        synchronized(pongLock) {
            while (!pongReceived) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                (pongLock as Object).wait(remaining)
            }
            pongReceived
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

    /** Opens the Play Store on the watch and starts polling until the app responds. */
    fun installOnWatch() {
        openWatchPlayStore()
        startInstallPolling()
    }

    fun startInstallPolling() {
        if (installPollJob?.isActive == true) return
        _isPollingForInstall.value = true
        installPollJob = viewModelScope.launch {
            while (true) {
                delay(INSTALL_POLL_INTERVAL_MS)
                val nodeId = try {
                    withContext(Dispatchers.IO) {
                        nodeClient.connectedNodes.await()
                    }.firstOrNull()?.id
                } catch (_: Exception) { null }

                if (nodeId == null) continue

                _connectedNodeId.value = nodeId
                if (pingWatch(nodeId)) {
                    if (!_hasEverConnected.value) {
                        _hasEverConnected.value = true
                        prefs.edit().putBoolean("has_ever_connected", true).apply()
                    }
                    _state.value = WatchConnectionState.CONNECTED
                    _isPollingForInstall.value = false
                    return@launch
                }
            }
        }
    }

    fun stopInstallPolling() {
        installPollJob?.cancel()
        installPollJob = null
        _isPollingForInstall.value = false
    }

}

// ── Companion app UI ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionApp(
    connectionViewModel: ConnectionViewModel = viewModel(),
    watchRomListViewModel: WatchRomListViewModel = viewModel(),
    transferViewModel: RomTransferViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val hasEverConnected by connectionViewModel.hasEverConnected.collectAsStateWithLifecycle()
    val isPolling by connectionViewModel.isPollingForInstall.collectAsStateWithLifecycle()

    val watchConnected = connectionState == WatchConnectionState.CONNECTED
    val isRootRoute = currentRoute == ROUTE_ROMS
    val context = androidx.compose.ui.platform.LocalContext.current
    val billingManager = MobileBillingManager.getInstance(context.applicationContext)
    val isPro by billingManager.isPro.collectAsStateWithLifecycle()

    // Upgrade overlay
    var showUpgradeOverlay by remember { mutableStateOf(false) }
    val doUpgrade: () -> Unit = { showUpgradeOverlay = true }

    // Watch can request purchase via Wearable message
    LaunchedEffect(Unit) {
        PurchaseRequestSignal.requests.collect {
            showUpgradeOverlay = true
            PurchaseRequestSignal.consume()
        }
    }

    // Auto-dismiss upgrade overlay when purchase completes
    LaunchedEffect(isPro) {
        if (isPro) showUpgradeOverlay = false
    }

    val shouldRequestReview by transferViewModel.shouldRequestReview.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    LaunchedEffect(shouldRequestReview) {
        if (shouldRequestReview && activity != null) {
            try {
                val manager = if (BuildConfig.DEBUG) FakeReviewManager(activity) else ReviewManagerFactory.create(activity)
                val reviewInfo: ReviewInfo = manager.requestReviewFlow().await()
                manager.launchReviewFlow(activity, reviewInfo).await()
            } catch (_: Exception) { }
            transferViewModel.onReviewHandled()
        }
    }
    LaunchedEffect(Unit) {
        ReviewRequestSignal.requests.collect {
            try {
                if (activity != null) {
                    // Wait for the activity window to be fully attached and focused —
                    // launchReviewFlow silently no-ops if called before the window is ready.
                    delay(800)
                    val manager = if (BuildConfig.DEBUG) FakeReviewManager(activity) else ReviewManagerFactory.create(activity)
                    val reviewInfo: ReviewInfo = manager.requestReviewFlow().await()
                    manager.launchReviewFlow(activity, reviewInfo).await()
                }
            } catch (_: Exception) { }
            ReviewRequestSignal.consume()
        }
    }

    // Banner is shown for WATCH_NO_APP (always) or NO_WATCH (first-time users only)
    val showBanner = when (connectionState) {
        WatchConnectionState.WATCH_NO_APP -> true
        WatchConnectionState.NO_WATCH -> !hasEverConnected
        else -> false
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                actions = {
                    if (BuildConfig.DEBUG && isRootRoute) {
                        TextButton(onClick = { billingManager.debugSetPro(!isPro) }) {
                            Text(
                                if (isPro) "PRO" else "FREE",
                                color = if (isPro) Color(0xFF9BBC0F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
            ConnectionStatusBar(
                connectionState = connectionState,
                onRefresh = { connectionViewModel.refresh() },
            )
            if (showBanner) {
                WatchAppInstallBanner(
                    connectionState = connectionState,
                    isPolling = isPolling,
                    onInstall = { connectionViewModel.installOnWatch() },
                    onRefresh = { connectionViewModel.refresh() },
                    onRetry = {
                        connectionViewModel.stopInstallPolling()
                        connectionViewModel.installOnWatch()
                    },
                )
            }

            // Upgrade prompt — shown when connected to watch + demo tier
            if (watchConnected && !isPro && isRootRoute) {
                UpgradeOnWatchBanner(
                    onUpgrade = doUpgrade,
                )
            }

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
                        isPro = isPro,
                        onUpgrade = doUpgrade,
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
                        onUpgrade = doUpgrade,
                    )
                }
            }
        }
    }

    // ── Upgrade overlay with perks + purchase ─────────────────────────
    if (showUpgradeOverlay) {
        OverlayCard(
            onDismiss = { showUpgradeOverlay = false },
            icon = Icons.Default.Lock,
            iconTint = GreenPrimary,
            title = "Squint Boy Pro",
            subtitle = "Unlock everything",
        ) {
            // Feature list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UpgradeFeatureRow("Unlimited ROMs")
                UpgradeFeatureRow("Unlimited session time")
                UpgradeFeatureRow("Save states")
                UpgradeFeatureRow("Fast forward")
                UpgradeFeatureRow("Custom scaling")
                UpgradeFeatureRow("All 24 color palettes")
                UpgradeFeatureRow("Save backups & exports")
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    (context as? Activity)?.let { activity ->
                        billingManager.launchPurchase(activity)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPrimary,
                    contentColor = DarkNavy,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Upgrade", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    } // Box
}

// ── Status bar ───────────────────────────────────────────────────────────────

@Composable
fun ConnectionStatusBar(
    connectionState: WatchConnectionState,
    onRefresh: () -> Unit,
) {
    val isConnected = connectionState == WatchConnectionState.CONNECTED
    val isChecking = connectionState == WatchConnectionState.CHECKING
    val tint = if (isConnected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant
    val label = when (connectionState) {
        WatchConnectionState.CHECKING -> "Watch: Checking..."
        WatchConnectionState.CONNECTED -> "Watch: Connected"
        WatchConnectionState.WATCH_NO_APP -> "Watch: App Not Found"
        WatchConnectionState.NO_WATCH -> "Watch: Disconnected"
        WatchConnectionState.DISCONNECTED -> "Watch: Disconnected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Watch,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
        if (isConnected || isChecking) {
            Spacer(Modifier.width(6.dp))
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh, enabled = !isChecking) {
            Text("Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Install banner ───────────────────────────────────────────────────────────

private val GreenPrimary = GbGreen

@Composable
fun WatchAppInstallBanner(
    connectionState: WatchConnectionState,
    isPolling: Boolean,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
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
            // Fixed-size icon area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp),
            ) {
                if (isPolling) {
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
                            if (connectionState == WatchConnectionState.NO_WATCH) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                GreenPrimary.copy(alpha = 0.15f)
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Watch,
                        contentDescription = null,
                        tint = if (connectionState == WatchConnectionState.NO_WATCH) {
                            Color.White.copy(alpha = 0.5f)
                        } else {
                            GreenPrimary
                        },
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            when {
                connectionState == WatchConnectionState.NO_WATCH -> {
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
                }
                isPolling -> {
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
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Try Again", color = Color.White)
                    }
                }
                else -> {
                    // WATCH_NO_APP, not polling yet
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
                        onClick = onInstall,
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
                }
            }
        }
    }
}

// ── Upgrade on Watch banner ─────────────────────────────────────────────────

@Composable
fun UpgradeOnWatchBanner(onUpgrade: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GreenPrimary.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Free Version",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GreenPrimary,
                )
                Text(
                    "Upgrade for full access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPrimary,
                    contentColor = DarkNavy,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Upgrade", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun UpgradeFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = GreenPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
