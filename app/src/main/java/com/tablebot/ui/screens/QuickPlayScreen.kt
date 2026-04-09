package com.tablebot.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tablebot.ble.ConnectionState
import androidx.compose.material.icons.filled.SwapHoriz
import com.tablebot.data.*
import com.tablebot.ui.components.ConnectionBar
import com.tablebot.ui.components.ProfileSwitcherDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlayScreen(
    // Robot control
    motorConfig: MotorConfig?,
    connected: Boolean,
    isPlaying: Boolean,
    onTestBall: (TestBallRequest) -> Unit,
    onPlayBasic: (BasicTraining) -> Unit,
    onPlayAdvanced: (AdvancedTraining) -> Unit,
    onStop: () -> Unit,
    // Connection bar
    connectionState: ConnectionState,
    deviceName: String?,
    statusMessage: String?,
    currentTrainingName: String?,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    // Training list
    basicTrainings: List<BasicTraining>,
    advancedTrainings: List<AdvancedTraining>,
    onSaveBasic: (BasicTraining) -> Unit,
    onSaveAdvanced: (AdvancedTraining) -> Unit,
    onDeleteBasic: (Int) -> Unit,
    onDeleteAdvanced: (Int) -> Unit,
    onToggleBasicFavourite: (Int) -> Unit,
    onToggleAdvancedFavourite: (Int) -> Unit,
    nextBasicId: () -> Int,
    nextAdvancedId: () -> Int,
    // Navigation
    onCalibrate: () -> Unit,
    onDebug: () -> Unit,
    onSettings: () -> Unit,
    onManual: () -> Unit,
    // Profiles
    activeProfileName: String? = null,
    profileIndex: ProfileIndex? = null,
    onSwitchProfile: (String) -> Unit = {},
    onEditProfile: (String) -> Unit = {},
    onCreateProfile: () -> Unit = {},
    reopenProfileSwitcher: Boolean = false,
    scrollToProfileId: String = "",
    profileErrorFlow: kotlinx.coroutines.flow.Flow<String>? = null,
) {
    // Mode: 0 = Basic, 1 = Advanced
    var mode by remember { mutableIntStateOf(0) }

    // Basic state
    val basicState = rememberDrillEditorState(initial = null, id = nextBasicId())
    var loadedBasicId by remember { mutableStateOf<Int?>(null) }

    // Advanced state
    val advancedState = rememberAdvancedEditorState(initial = null, id = nextAdvancedId())
    var loadedAdvancedId by remember { mutableStateOf<Int?>(null) }

    var showTrainingSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Lifecycle cleanup
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onStop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Permission handling
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) onScan()
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
        if (permissionsGranted) onScan()
        else permissionLauncher.launch(requiredPermissions)
    }

    // Test ball validation (basic mode)
    val enabledCells = remember(basicState.ball, basicState.spin, basicState.power) {
        motorConfig?.validLandareas(basicState.ball, basicState.spin, basicState.power)
    }

    // Current state helpers
    val currentName = if (mode == 0) basicState.name else advancedState.name
    val currentTags = if (mode == 0) basicState.tags else advancedState.tags
    val loadedId = if (mode == 0) loadedBasicId else loadedAdvancedId
    val currentFavourite = if (mode == 0) basicState.isFavourite else advancedState.isFavourite

    // All known tags across all trainings
    val allKnownTags = remember(basicTrainings, advancedTrainings) {
        (basicTrainings.flatMap { it.tags } + advancedTrainings.flatMap { it.tags })
            .toSortedSet()
    }

    // Original name of loaded drill (for detecting renames)
    val loadedOriginalName = remember(mode, loadedBasicId, loadedAdvancedId) {
        when {
            mode == 0 && loadedBasicId != null ->
                basicTrainings.find { it.id == loadedBasicId }?.name
            mode == 1 && loadedAdvancedId != null ->
                advancedTrainings.find { it.id == loadedAdvancedId }?.name
            else -> null
        }
    }

    // Save dialog with tag editor
    if (showSaveDialog) {
        SaveTrainingDialog(
            initialName = currentName,
            initialTags = currentTags,
            allKnownTags = allKnownTags,
            loadedOriginalName = if (loadedId != null) loadedOriginalName else null,
            onSaveOverwrite = { name, tags ->
                // Overwrite the loaded drill (rename or update)
                if (mode == 0) {
                    basicState.name = name
                    basicState.tags = tags
                    onSaveBasic(basicState.toTraining())
                } else {
                    advancedState.name = name
                    advancedState.tags = tags
                    onSaveAdvanced(advancedState.toTraining())
                }
                showSaveDialog = false
            },
            onSaveCopy = { name, tags ->
                // Save as a new drill with a new ID
                if (mode == 0) {
                    basicState.name = name
                    basicState.tags = tags
                    basicState.id = nextBasicId()
                    onSaveBasic(basicState.toTraining())
                    loadedBasicId = basicState.id
                } else {
                    advancedState.name = name
                    advancedState.tags = tags
                    advancedState.id = nextAdvancedId()
                    onSaveAdvanced(advancedState.toTraining())
                    loadedAdvancedId = advancedState.id
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    // Onboarding dialog — shown once on first launch
    val hasSeenOnboarding by AppPrefs.hasSeenOnboarding.collectAsState()
    if (!hasSeenOnboarding) {
        AlertDialog(
            onDismissRequest = { AppPrefs.setHasSeenOnboarding(true) },
            title = { Text("Welcome to TableBot") },
            text = {
                Text(
                    "Control your Joola Infinity table tennis robot \u2014 " +
                    "no internet or account needed.\n\n" +
                    "Tap the connection bar at the top to pair with your robot, " +
                    "then customize a drill and tap Play.\n\n" +
                    "For detailed help, tap the \u22EE menu and select Manual."
                )
            },
            confirmButton = {
                TextButton(onClick = { AppPrefs.setHasSeenOnboarding(true) }) {
                    Text("Got it")
                }
            },
        )
    }

    // Training list bottom sheet — shows only the type matching current tab
    if (showTrainingSheet) {
        TrainingBottomSheet(
            onDismiss = { showTrainingSheet = false },
            basicTrainings = basicTrainings,
            advancedTrainings = advancedTrainings,
            onLoadBasic = { training ->
                basicState.loadFrom(training)
                loadedBasicId = training.id
                mode = 0
                showTrainingSheet = false
            },
            onLoadAdvanced = { training ->
                advancedState.loadFrom(training)
                loadedAdvancedId = training.id
                mode = 1
                showTrainingSheet = false
            },
            onDeleteBasic = { onDeleteBasic(it.id) },
            onToggleBasicFavourite = { onToggleBasicFavourite(it.id) },
            onDeleteAdvanced = { onDeleteAdvanced(it.id) },
            onToggleAdvancedFavourite = { onToggleAdvancedFavourite(it.id) },
            connected = connected,
            isPlaying = isPlaying,
            onStop = onStop,
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(profileErrorFlow) {
        profileErrorFlow?.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    var showProfileSwitcher by remember { mutableStateOf(false) }

    LaunchedEffect(reopenProfileSwitcher) {
        if (reopenProfileSwitcher) {
            showProfileSwitcher = true
        }
    }

    if (showProfileSwitcher && profileIndex != null) {
        ProfileSwitcherDialog(
            profileIndex = profileIndex,
            initialProfileId = scrollToProfileId,
            onSelectProfile = { id ->
                onSwitchProfile(id)
                showProfileSwitcher = false
            },
            onEditProfile = { id ->
                showProfileSwitcher = false
                onEditProfile(id)
            },
            onAddProfile = {
                showProfileSwitcher = false
                onCreateProfile()
            },
            onDismiss = { showProfileSwitcher = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showProfileSwitcher = true },
                        ) {
                            Text(activeProfileName ?: "TableBot")
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Switch profile",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
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
                            DropdownMenuItem(
                                text = { Text("Manual") },
                                onClick = { menuExpanded = false; onManual() },
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
                    onDisconnectClick = onDisconnect,
                    onStopClick = onStop,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Basic / Dynamic tabs
            TabRow(selectedTabIndex = mode) {
                Tab(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    text = { Text("Basic") },
                )
                Tab(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    text = { Text("Dynamic") },
                )
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // Training name row
                TrainingNameRow(
                    name = currentName.ifBlank { "Custom" },
                    isFavourite = loadedId != null && currentFavourite == 1,
                    onNameClick = { showTrainingSheet = true },
                    onFavouriteClick = {
                        if (mode == 0 && loadedBasicId != null) {
                            basicState.isFavourite = if (basicState.isFavourite == 1) 0 else 1
                            onToggleBasicFavourite(loadedBasicId!!)
                        } else if (mode == 1 && loadedAdvancedId != null) {
                            advancedState.isFavourite = if (advancedState.isFavourite == 1) 0 else 1
                            onToggleAdvancedFavourite(loadedAdvancedId!!)
                        }
                    },
                    onSaveClick = { showSaveDialog = true },
                    showFavourite = loadedId != null,
                )

                Spacer(Modifier.height(16.dp))

                when (mode) {
                    0 -> DrillEditorContent(
                        state = basicState,
                        motorConfig = motorConfig,
                        showName = false,
                    )
                    1 -> AdvancedEditorContent(
                        state = advancedState,
                        motorConfig = motorConfig,
                        showName = false,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            // Pinned bottom bar: Test + Play
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val profileCalibrated = motorConfig?.isEmpty() != true
                    // Test button (basic mode only — fires single ball)
                    val testEnabled = mode == 0 && connected && profileCalibrated && basicState.points.isNotEmpty() &&
                        enabledCells?.let { basicState.points.first().x in it } != false
                    OutlinedButton(
                        onClick = {
                            onTestBall(
                                TestBallRequest(
                                    ball = basicState.ball,
                                    spin = basicState.spin,
                                    power = basicState.power,
                                    cell = basicState.points.first().x,
                                    ballTime = basicState.ballTime,
                                )
                            )
                        },
                        enabled = testEnabled && !isPlaying,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Test", style = MaterialTheme.typography.titleMedium)
                    }

                    // Play button
                    val playEnabled = when (mode) {
                        0 -> connected && profileCalibrated && basicState.points.isNotEmpty() && !isPlaying
                        1 -> connected && profileCalibrated && advancedState.ballList.isNotEmpty() && !isPlaying
                        else -> false
                    }
                    Button(
                        onClick = {
                            when (mode) {
                                0 -> onPlayBasic(basicState.toTraining())
                                1 -> onPlayAdvanced(advancedState.toTraining())
                            }
                        },
                        enabled = playEnabled,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// ── Training name row ───────────────────────────────────────────────

@Composable
private fun TrainingNameRow(
    name: String,
    isFavourite: Boolean,
    onNameClick: () -> Unit,
    onFavouriteClick: () -> Unit,
    onSaveClick: () -> Unit,
    showFavourite: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNameClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = "Load Training",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showFavourite) {
                IconButton(onClick = onFavouriteClick) {
                    Icon(
                        if (isFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                        "Favourite",
                        tint = if (isFavourite) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSaveClick) {
                Icon(Icons.Default.Save, "Save")
            }
        }
    }
}
