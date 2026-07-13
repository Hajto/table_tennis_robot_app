package com.tablebot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tablebot.ble.ConnectionState
import com.tablebot.data.AppPrefs
import com.tablebot.data.TrainingStore
import com.tablebot.ui.components.StopOverlay
import com.tablebot.ui.screens.*
import com.tablebot.ui.theme.TableBotTheme
import com.tablebot.viewmodel.RobotViewModel
import com.tablebot.viewmodel.TrainingViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppPrefs.init(this)

        setContent {
            TableBotTheme {
                val navController = rememberNavController()
                val robotVm: RobotViewModel = viewModel()
                val trainingVm: TrainingViewModel = viewModel()
                val quickPlayVm: com.tablebot.viewmodel.QuickPlayDraftViewModel = viewModel()

                Box(Modifier.fillMaxSize()) {
                    val motorConfig by robotVm.motorConfigFlow.collectAsState()
                    val activeProfile by robotVm.activeProfile.collectAsState()
                    val profileIndex by robotVm.profileIndex.collectAsState()

                    NavHost(navController, startDestination = "home") {
                        composable("home") {
                            val connectionState by robotVm.connectionState.collectAsState()
                            val basicTrainings by trainingVm.basicTrainings.collectAsState()
                            val advancedTrainings by trainingVm.advancedTrainings.collectAsState()

                            // Reopen profile switcher when returning from profile editor
                            val savedState = it.savedStateHandle
                            val reopenSwitcher by savedState.getStateFlow("reopenSwitcher", false).collectAsState()
                            val scrollToProfileId by savedState.getStateFlow("scrollToProfileId", "").collectAsState()
                            LaunchedEffect(reopenSwitcher) {
                                if (reopenSwitcher) {
                                    savedState["reopenSwitcher"] = false
                                }
                            }

                            // ── Export / Import ────────────────────────────────────────────
                            val scope = rememberCoroutineScope()
                            var pendingImportBundle by remember {
                                mutableStateOf<TrainingStore.DrillExportBundle?>(null)
                            }
                            var importCollisions by remember { mutableStateOf<List<String>>(emptyList()) }
                            val importSnackbarFlow = remember { kotlinx.coroutines.flow.MutableSharedFlow<String>() }

                            val exportLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.CreateDocument("application/json")
                            ) { uri ->
                                if (uri != null) {
                                    scope.launch {
                                        val jsonText = trainingVm.exportToJson()
                                        contentResolver.openOutputStream(uri)?.use {
                                            it.write(jsonText.toByteArray())
                                        }
                                    }
                                }
                            }

                            val importLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null) {
                                    scope.launch {
                                        val jsonText = contentResolver.openInputStream(uri)
                                            ?.bufferedReader()?.use { it.readText() } ?: return@launch
                                        val bundle = runCatching {
                                            trainingVm.parseImportBundle(jsonText)
                                        }.getOrElse {
                                            importSnackbarFlow.emit("Invalid drill file")
                                            return@launch
                                        }
                                        val collisions = trainingVm.findCollisions(bundle)
                                        if (collisions.isEmpty()) {
                                            trainingVm.importBundle(bundle, emptySet())
                                            importSnackbarFlow.emit("Imported ${bundle.basic.size + bundle.advanced.size} drills")
                                        } else {
                                            pendingImportBundle = bundle
                                            importCollisions = collisions
                                        }
                                    }
                                }
                            }

                            if (pendingImportBundle != null && importCollisions.isNotEmpty()) {
                                ImportCollisionDialog(
                                    collidingNames = importCollisions,
                                    onConfirm = { overwriteNames ->
                                        trainingVm.importBundle(pendingImportBundle!!, overwriteNames)
                                        pendingImportBundle = null
                                        importCollisions = emptyList()
                                    },
                                    onDismiss = {
                                        pendingImportBundle = null
                                        importCollisions = emptyList()
                                    },
                                )
                            }

                            QuickPlayScreen(
                                draft = quickPlayVm,
                                reopenProfileSwitcher = reopenSwitcher,
                                scrollToProfileId = scrollToProfileId,
                                motorConfig = motorConfig,
                                connected = connectionState == ConnectionState.CONNECTED,
                                isPlaying = robotVm.isPlaying.collectAsState().value,
                                onTestBall = { req ->
                                    motorConfig.lookup(req.ball, req.spin, req.power, req.cell)?.let {
                                        robotVm.sendTestBall(it, req.ballTime)
                                    }
                                },
                                onPlayBasic = { robotVm.playBasicTraining(it) },
                                onPlayAdvanced = { robotVm.playAdvancedTraining(it) },
                                onStop = { robotVm.stop() },
                                connectionState = connectionState,
                                deviceName = robotVm.deviceName.collectAsState().value,
                                statusMessage = robotVm.statusMessage.collectAsState().value,
                                firmwareVersion = robotVm.firmwareVersion.collectAsState().value,
                                currentTrainingName = robotVm.currentTrainingName.collectAsState().value,
                                onScan = { robotVm.scan() },
                                onDisconnect = { robotVm.disconnect() },
                                basicTrainings = basicTrainings ?: emptyList(),
                                advancedTrainings = advancedTrainings ?: emptyList(),
                                onSaveBasic = { trainingVm.saveBasicTraining(it) },
                                onSaveAdvanced = { trainingVm.saveAdvancedTraining(it) },
                                onDeleteBasic = { trainingVm.deleteBasicTraining(it) },
                                onDeleteAdvanced = { trainingVm.deleteAdvancedTraining(it) },
                                onToggleBasicFavourite = { trainingVm.toggleBasicFavourite(it) },
                                onToggleAdvancedFavourite = { trainingVm.toggleAdvancedFavourite(it) },
                                nextBasicId = { trainingVm.nextBasicId() },
                                nextAdvancedId = { trainingVm.nextAdvancedId() },
                                onExportDrills = {
                                    exportLauncher.launch("tablebot-drills.json")
                                },
                                onImportDrills = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onCalibrate = { navController.navigate("calibration") },
                                onDebug = { navController.navigate("debug") },
                                onSettings = { navController.navigate("settings") },
                                onManual = { navController.navigate("manual") },
                                onHistory = { navController.navigate("history") },
                                activeProfileName = activeProfile?.name,
                                profileIndex = profileIndex,
                                onSwitchProfile = { robotVm.switchProfile(it) },
                                onEditProfile = {
                                    navController.navigate("editProfile/$it")
                                },
                                onCreateProfile = { name, robotType ->
                                    robotVm.createProfile(name, robotType) { profile ->
                                        navController.navigate("editProfile/${profile.id}")
                                    }
                                },
                                profileErrorFlow = robotVm.profileError,
                                importStatusFlow = importSnackbarFlow,
                            )
                        }

                        composable("debug") {
                            DebugScreen(robotVm = robotVm)
                        }

                        composable("calibration") {
                            CalibrationScreen(
                                robotVm = robotVm,
                                onBack = { navController.popBackStack() },
                                activeProfileName = activeProfile?.name,
                                seed = quickPlayVm.calibrationSeed,
                            )
                        }

                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }

                        composable("history") {
                            val historyScope = rememberCoroutineScope()
                            val sessions by produceState(emptyList<com.tablebot.data.TrainingSession>()) {
                                value = robotVm.historyStore.loadSessions()
                            }
                            HistoryScreen(
                                sessions = sessions,
                                onClearHistory = {
                                    historyScope.launch {
                                        robotVm.historyStore.clearHistory()
                                        navController.popBackStack()
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(
                            "editBasic/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.IntType }),
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getInt("id") ?: -1
                            val trainings by trainingVm.basicTrainings.collectAsState()
                            val existing = if (id >= 0) trainings?.find { it.id == id } else null
                            val connectionState by robotVm.connectionState.collectAsState()

                            BasicEditorScreen(
                                initial = existing,
                                onSave = { training ->
                                    trainingVm.saveBasicTraining(training)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() },
                                nextId = existing?.id ?: trainingVm.nextBasicId(),
                                motorConfig = motorConfig,
                                onTestBall = { req ->
                                    motorConfig.lookup(req.ball, req.spin, req.power, req.cell)?.let {
                                        robotVm.sendTestBall(it, req.ballTime)
                                    }
                                },
                                connected = connectionState == ConnectionState.CONNECTED,
                            )
                        }

                        composable(
                            "editAdvanced/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.IntType }),
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getInt("id") ?: -1
                            val trainings by trainingVm.advancedTrainings.collectAsState()
                            val existing = if (id >= 0) trainings?.find { it.id == id } else null

                            AdvancedEditorScreen(
                                initial = existing,
                                onSave = { training ->
                                    trainingVm.saveAdvancedTraining(training)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() },
                                nextId = { trainingVm.nextAdvancedId() },
                                motorConfig = motorConfig,
                            )
                        }

                        composable(
                            "editProfile/{profileId}",
                            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
                            val index by robotVm.profileIndex.collectAsState()
                            val profile = index?.profiles?.find { it.id == profileId }

                            if (profile != null) {
                                ProfileEditorScreen(
                                    profile = profile,
                                    onSave = { updated -> robotVm.updateProfile(updated) },
                                    onBack = {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle?.apply {
                                                set("reopenSwitcher", true)
                                                set("scrollToProfileId", profileId)
                                            }
                                        navController.popBackStack()
                                    },
                                    onDelete = if ((index?.profiles?.size ?: 0) > 1) {
                                        {
                                            robotVm.deleteProfile(profileId)
                                            navController.previousBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("reopenSwitcher", true)
                                            navController.popBackStack()
                                        }
                                    } else null,
                                )
                            }
                        }

                        composable("manual") {
                            ManualScreen(
                                onBack = { navController.popBackStack() },
                                onArticle = { articleId -> navController.navigate("manual/$articleId") },
                            )
                        }

                        composable(
                            "manual/{articleId}",
                            arguments = listOf(navArgument("articleId") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
                            ManualArticleScreen(
                                articleId = articleId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }

                    // Global stop overlay — visible on all screens when playing
                    val isPlaying by robotVm.isPlaying.collectAsState()
                    if (isPlaying) {
                        StopOverlay(
                            trainingName = robotVm.currentTrainingName.collectAsState().value,
                            onStop = { robotVm.stop() },
                        )
                    }

                    // Break reminder dialog
                    var showBreakReminder by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        robotVm.breakReminder.collect { showBreakReminder = true }
                    }
                    if (showBreakReminder) {
                        AlertDialog(
                            onDismissRequest = { showBreakReminder = false },
                            title = { Text("Time for a break!") },
                            text = {
                                Text(
                                    "You've been training for 30+ minutes. " +
                                    "Consider taking a 5 minute break to stay fresh and avoid injury."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { showBreakReminder = false }
                                ) { Text("OK, got it") }
                            },
                        )
                    }
                }
            }
        }
    }
}
