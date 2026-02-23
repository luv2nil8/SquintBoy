package com.example.squintboyadvance.ui

import android.app.Application
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
import com.example.squintboyadvance.shared.model.SystemType
import com.example.squintboyadvance.ui.roms.RomManagementScreen
import com.example.squintboyadvance.ui.roms.RomsTab
import com.example.squintboyadvance.ui.roms.WatchRomListViewModel
import com.example.squintboyadvance.ui.settings.LicensesScreen
import com.example.squintboyadvance.ui.settings.WatchSettingsScreen
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val ROUTE_ROMS = "roms"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LICENSES = "licenses"

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val nodeClient = Wearable.getNodeClient(application)

    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    private val _connectedNodeId = MutableStateFlow<String?>(null)
    val connectedNodeId: StateFlow<String?> = _connectedNodeId.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val node = nodes.firstOrNull()
                _watchConnected.value = node != null
                _connectedNodeId.value = node?.id
            } catch (_: Exception) {
                _watchConnected.value = false
                _connectedNodeId.value = null
            }
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
            ConnectionStatusBar(watchConnected) { connectionViewModel.refresh() }

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
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(connected: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Watch,
            contentDescription = null,
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (connected) "Watch: Connected" else "Watch: Disconnected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (connected) {
            Spacer(Modifier.width(6.dp))
            Text("\u25CF", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Text("Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
}
