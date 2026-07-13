package com.tablebot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablebot.ble.ConnectionState
import com.tablebot.ble.RobotManager
import com.tablebot.data.*
import com.tablebot.data.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RobotViewModel(app: Application) : AndroidViewModel(app) {

    val robotManager = RobotManager(app.applicationContext)
    val historyStore = HistoryStore(app.applicationContext)

    private val _motorConfig = MutableStateFlow(MotorConfig(app.applicationContext))
    val motorConfigFlow: StateFlow<MotorConfig> = _motorConfig
    var motorConfig: MotorConfig
        get() = _motorConfig.value
        private set(value) { _motorConfig.value = value }

    val profileStore = ProfileStore(app.applicationContext)

    private val _profileIndex = MutableStateFlow<ProfileIndex?>(null)
    val profileIndex: StateFlow<ProfileIndex?> = _profileIndex

    val activeProfile: StateFlow<Profile?> = _profileIndex.map { index ->
        index?.profiles?.find { it.id == index.activeProfileId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val connectionState: StateFlow<ConnectionState> = robotManager.state
    val deviceName: StateFlow<String?> = robotManager.deviceName
    val statusMessage: StateFlow<String?> = robotManager.statusMessage
    val firmwareVersion: StateFlow<String?> = robotManager.firmwareVersion

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrainingName = MutableStateFlow<String?>(null)
    val currentTrainingName: StateFlow<String?> = _currentTrainingName

    private val _profileError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val profileError: SharedFlow<String> = _profileError.asSharedFlow()

    private val _breakReminder = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val breakReminder: SharedFlow<Unit> = _breakReminder.asSharedFlow()

    private var profilesLoaded = false

    init {
        robotManager.onPatternDone = {
            _isPlaying.value = false
            _currentTrainingName.value = null
        }
        viewModelScope.launch {
            profileStore.migrateIfNeeded()
            val index = profileStore.loadIndex()
            val active = index.profiles.find { it.id == index.activeProfileId }
            if (active != null) {
                // Set activeRobotType before publishing index so scan() sees the correct type
                robotManager.activeRobotType = active.robotType
                motorConfig = withContext(Dispatchers.IO) {
                    MotorConfig(getApplication(), active.motorConfigFileName)
                }
            }
            _profileIndex.value = index
            profilesLoaded = true
        }
    }

    fun scan() {
        if (!profilesLoaded) return
        robotManager.scan()
    }

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
            if (historyStore.logEntry(training.name, "basic", training.id)) {
                _breakReminder.tryEmit(Unit)
            }
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
            if (historyStore.logEntry(training.name, "advanced", training.id)) {
                _breakReminder.tryEmit(Unit)
            }
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

    suspend fun saveMotorParams(params: MotorParams) {
        motorConfig.save(params)
    }

    fun reloadMotorConfig() {
        viewModelScope.launch {
            val profile = activeProfile.value ?: return@launch
            motorConfig = withContext(Dispatchers.IO) {
                MotorConfig(getApplication(), profile.motorConfigFileName)
            }
        }
    }

    fun switchProfile(profileId: String) {
        if (_isPlaying.value) return
        viewModelScope.launch {
            try {
                profileStore.setActiveProfile(profileId)
                val index = profileStore.loadIndex()
                val profile = index.profiles.find { it.id == profileId } ?: return@launch
                // Update activeRobotType before publishing index so any observer sees a consistent state
                robotManager.activeRobotType = profile.robotType
                _profileIndex.value = index
                motorConfig = withContext(Dispatchers.IO) {
                    MotorConfig(getApplication(), profile.motorConfigFileName)
                }
            } catch (e: Exception) {
                _profileError.tryEmit(e.message ?: "Failed to switch profile")
            }
        }
    }

    fun createProfile(name: String, robotType: com.tablebot.data.RobotType = com.tablebot.data.RobotType.JOOLA_V2, onCreated: (Profile) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val profile = profileStore.createProfile(name, robotType)
                _profileIndex.value = profileStore.loadIndex()
                onCreated(profile)
            } catch (e: Exception) {
                _profileError.tryEmit(e.message ?: "Failed to create profile")
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            try {
                profileStore.deleteProfile(id)
                val index = profileStore.loadIndex()
                val active = index.profiles.find { it.id == index.activeProfileId }
                if (active != null) {
                    robotManager.activeRobotType = active.robotType
                    motorConfig = withContext(Dispatchers.IO) {
                        MotorConfig(getApplication(), active.motorConfigFileName)
                    }
                }
                _profileIndex.value = index
            } catch (e: Exception) {
                _profileError.tryEmit(e.message ?: "Failed to delete profile")
            }
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            try {
                profileStore.updateProfile(profile)
                val index = profileStore.loadIndex()
                // If the active profile was edited, sync robotType immediately
                if (profile.id == index.activeProfileId) {
                    robotManager.activeRobotType = profile.robotType
                }
                _profileIndex.value = index
            } catch (e: Exception) {
                _profileError.tryEmit(e.message ?: "Failed to update profile")
            }
        }
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
