package com.anaglych.squintboyadvance.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.anaglych.squintboyadvance.ui.settings.WatchSettingsScreen
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
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LICENSES = "licenses"

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val nodeClient = Wearable.getNodeClient(application)
    private val capabilityClient = Wearable.getCapabilityClient(application)
    private val remoteActivityHelper = RemoteActivityHelper(application)

    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    private val _connectedNodeId = MutableStateFlow<String?>(null)
    val connectedNodeId: StateFlow<String?> = _connectedNodeId.asStateFlow()

    private val _watchAppInstalled = MutableStateFlow<Boolean?>(null)
    val watchAppInstalled: StateFlow<Boolean?> = _watchAppInstalled.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val node = nodes.firstOrNull()
                _watchConnected.value = node != null
                _connectedNodeId.value = node?.id

                if (node != null) {
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
                actions = {
                    if (isRootRoute) {
                        IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                composable(ROUTE_SETTINGS) {
                    WatchSettingsScreen(
                        watchConnected = watchConnected,
                        onOpenLicenses = { navController.navigate(ROUTE_LICENSES) },
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
