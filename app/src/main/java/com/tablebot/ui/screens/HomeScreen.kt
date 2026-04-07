package com.tablebot.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tablebot.ble.ConnectionState
import com.tablebot.data.AppPrefs
import com.tablebot.ui.components.ConnectionBar
import com.tablebot.viewmodel.RobotViewModel
import com.tablebot.viewmodel.TrainingViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    robotVm: RobotViewModel,
    trainingVm: TrainingViewModel,
    onEditBasic: (Int?) -> Unit,     // null = new
    onEditAdvanced: (Int?) -> Unit,
    onDebug: () -> Unit = {},
    onCalibrate: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val connectionState by robotVm.connectionState.collectAsState()
    val deviceName by robotVm.deviceName.collectAsState()
    val statusMessage by robotVm.statusMessage.collectAsState()
    val isPlaying by robotVm.isPlaying.collectAsState()
    val currentTrainingName by robotVm.currentTrainingName.collectAsState()

    val basicTrainings by trainingVm.basicTrainings.collectAsState()
    val advancedTrainings by trainingVm.advancedTrainings.collectAsState()
    val searchQuery by trainingVm.searchQuery.collectAsState()

    // Permission handling
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) robotVm.scan()
    }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    fun handleScan() {
        if (permissionsGranted) {
            robotVm.scan()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("TableBot") },
                    actions = {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                        val debugMode by AppPrefs.debugMode.collectAsState()
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Calibration") },
                                onClick = { menuExpanded = false; onCalibrate() },
                            )
                            if (debugMode) {
                                DropdownMenuItem(
                                    text = { Text("Debug") },
                                    onClick = { menuExpanded = false; onDebug() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuExpanded = false; onSettings() },
                            )
                        }
                    },
                )
                ConnectionBar(
                    state = connectionState,
                    deviceName = deviceName,
                    statusMessage = statusMessage,
                    isPlaying = isPlaying,
                    currentTrainingName = currentTrainingName,
                    onScanClick = ::handleScan,
                    onDisconnectClick = { robotVm.disconnect() },
                    onStopClick = { robotVm.stop() },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (pagerState.currentPage == 0) onEditBasic(null)
                    else onEditAdvanced(null)
                }
            ) {
                Icon(Icons.Default.Add, "New training")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tab row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Basic (${basicTrainings?.size ?: "..."})") },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Advanced (${advancedTrainings?.size ?: "..."})") },
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { trainingVm.setSearchQuery(it) },
                placeholder = { Text("Search trainings...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> BasicTrainingList(
                        trainings = (basicTrainings ?: emptyList()).let { list ->
                            val q = searchQuery.lowercase()
                            if (q.isBlank()) list else list.filter { it.name.lowercase().contains(q) }
                        },
                        connected = connectionState == ConnectionState.CONNECTED,
                        isPlaying = isPlaying,
                        onPlay = { training, times, ballTime ->
                            robotVm.playBasicTraining(
                                training,
                                timesOverride = times,
                                ballTimeOverride = ballTime,
                            )
                        },
                        onStop = { robotVm.stop() },
                        onEdit = { onEditBasic(it.id) },
                        onDelete = { trainingVm.deleteBasicTraining(it.id) },
                        onToggleFavourite = { trainingVm.toggleBasicFavourite(it.id) },
                    )
                    1 -> AdvancedTrainingList(
                        trainings = (advancedTrainings ?: emptyList()).let { list ->
                            val q = searchQuery.lowercase()
                            if (q.isBlank()) list else list.filter { it.name.lowercase().contains(q) }
                        },
                        connected = connectionState == ConnectionState.CONNECTED,
                        isPlaying = isPlaying,
                        onPlay = { training, repeatNum, repeatDelay ->
                            robotVm.playAdvancedTraining(
                                training,
                                repeatNumOverride = repeatNum,
                                repeatDelayOverride = repeatDelay,
                            )
                        },
                        onStop = { robotVm.stop() },
                        onEdit = { onEditAdvanced(it.id) },
                        onDelete = { trainingVm.deleteAdvancedTraining(it.id) },
                        onToggleFavourite = { trainingVm.toggleAdvancedFavourite(it.id) },
                    )
                }
            }
        }
    }

    // Full-screen stop overlay when playing
    if (isPlaying) {
        com.tablebot.ui.components.StopOverlay(
            trainingName = currentTrainingName,
            onStop = { robotVm.stop() },
        )
    }
    } // end Box
}
