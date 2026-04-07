package com.tablebot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablebot.ble.ConnectionState
import com.tablebot.ble.RobotManager
import com.tablebot.data.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class RobotViewModel(app: Application) : AndroidViewModel(app) {

    val robotManager = RobotManager(app.applicationContext)
    val motorConfig = MotorConfig(app.applicationContext)

    val connectionState: StateFlow<ConnectionState> = robotManager.state
    val deviceName: StateFlow<String?> = robotManager.deviceName
    val statusMessage: StateFlow<String?> = robotManager.statusMessage

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrainingName = MutableStateFlow<String?>(null)
    val currentTrainingName: StateFlow<String?> = _currentTrainingName

    init {
        robotManager.onPatternDone = {
            _isPlaying.value = false
            _currentTrainingName.value = null
        }
    }

    fun scan() = robotManager.scan()

    fun disconnect() = robotManager.disconnect()

    fun playBasicTraining(
        training: BasicTraining,
        timesOverride: Int? = null,
        ballTimeOverride: Int? = null,
    ) {
        robotManager.drillJob?.cancel()
        robotManager.drillJob = viewModelScope.launch {
            _isPlaying.value = true
            _currentTrainingName.value = training.name
            val payload = RobotProtocol.encodeBasicPattern(
                training, motorConfig,
                timesOverride = timesOverride,
                ballTimeOverride = ballTimeOverride,
            )
            robotManager.sendBasicDrill(payload, reps = timesOverride ?: training.times)
        }
    }

    fun playAdvancedTraining(
        training: AdvancedTraining,
        repeatNumOverride: Int? = null,
        repeatDelayOverride: Int? = null,
    ) {
        robotManager.drillJob?.cancel()
        robotManager.drillJob = viewModelScope.launch {
            _isPlaying.value = true
            _currentTrainingName.value = training.name
            val payload = RobotProtocol.encodeAdvancedPattern(
                training, motorConfig,
                repeatNumOverride = repeatNumOverride,
                repeatDelayOverride = repeatDelayOverride,
            )
            robotManager.sendAdvancedDrill(payload, reps = repeatNumOverride ?: training.repeatNum)
        }
    }

    fun sendTestBall(params: MotorParams, ballTime: Int = 15) {
        viewModelScope.launch {
            val payload = RobotProtocol.encodeSingleBall(params, ballTime)
            robotManager.sendBasicDrill(payload, reps = 1)
        }
    }

    fun saveMotorParams(params: MotorParams) {
        viewModelScope.launch { motorConfig.save(params) }
    }

    fun stop() {
        viewModelScope.launch {
            robotManager.stop()
            _isPlaying.value = false
            _currentTrainingName.value = null
        }
    }

    override fun onCleared() {
        robotManager.destroy()
    }
}

private typealias RobotProtocol = com.tablebot.ble.RobotProtocol
