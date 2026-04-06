package com.tablebot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tablebot.ui.screens.*
import com.tablebot.ui.theme.TableBotTheme
import com.tablebot.viewmodel.RobotViewModel
import com.tablebot.viewmodel.TrainingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TableBotTheme {
                val navController = rememberNavController()
                val robotVm: RobotViewModel = viewModel()
                val trainingVm: TrainingViewModel = viewModel()

                NavHost(navController, startDestination = "home") {
                    composable("debug") {
                        DebugScreen(robotVm = robotVm)
                    }

                    composable("home") {
                        HomeScreen(
                            robotVm = robotVm,
                            trainingVm = trainingVm,
                            onEditBasic = { id ->
                                if (id != null) navController.navigate("editBasic/$id")
                                else navController.navigate("editBasic/-1")
                            },
                            onEditAdvanced = { id ->
                                if (id != null) navController.navigate("editAdvanced/$id")
                                else navController.navigate("editAdvanced/-1")
                            },
                            onDebug = { navController.navigate("debug") },
                        )
                    }

                    composable(
                        "editBasic/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType }),
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: -1
                        val trainings by trainingVm.basicTrainings.collectAsState()
                        val existing = if (id >= 0) trainings?.find { it.id == id } else null

                        BasicEditorScreen(
                            initial = existing,
                            onSave = { training ->
                                trainingVm.saveBasicTraining(training)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                            nextId = { trainingVm.nextBasicId() },
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
                        )
                    }
                }
            }
        }
    }
}
