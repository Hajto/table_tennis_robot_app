package com.tablebot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablebot.ble.ConnectionState
import com.tablebot.ble.RobotManager
import com.tablebot.data.*
import com.tablebot.data.HistoryStore
import com.tablebot.ui.components.AndroidStartCue
import com.tablebot.ui.components.NoOpStartCue
import com.tablebot.ui.components.StartCue
import com.tablebot.ui.components.runStartCountdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _playCountdownSec = MutableStateFlow<Int?>(null)
    val playCountdownSec: StateFlow<Int?> = _playCountdownSec
    private var countdownJob: Job? = null

    // ── Delayed start (get-in-position lead-in) ──────────────────────────
    // Kept fully separate from the in-play `playCountdownSec` above: this is a pre-start phase
    // that only gates *when* an unchanged play call happens; the robot is untouched until fire.
    private val _startCountdownSec = MutableStateFlow<Int?>(null)
    val startCountdownSec: StateFlow<Int?> = _startCountdownSec
    private var startCountdownJob: Job? = null
    private var startCue: StartCue? = null
    /** Overridable so unit tests can supply a fake cue instead of real audio. */
    var startCueFactory: () -> StartCue = { AndroidStartCue() }

    private val _profileError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val profileError: SharedFlow<String> = _profileError.asSharedFlow()

    private val _breakReminder = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val breakReminder: SharedFlow<Unit> = _breakReminder.asSharedFlow()

    private var profilesLoaded = false

    init {
        robotManager.onPatternDone = {
            _isPlaying.value = false
            _currentTrainingName.value = null
            clearCountdown()
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

    fun playBasicTraining(training: BasicTraining) {
        robotManager.drillJob?.cancel()
        clearCountdown()
        val resolved = resolvePlay(
            PlayMode.fromValue(training.playMode),
            training.times, training.ballCount, training.durationSec,
            ballsPerPatternBasic(training), patternDurationTenthsBasic(training),
        )
        // History logging is decoupled from the drill path: it runs in its own
        // coroutine so history I/O can never block or crash drill playback.
        val profile = activeProfile.value
        viewModelScope.launch {
            if (historyStore.logEntry(HistoryEntry(
                    trainingName = training.name,
                    trainingType = "basic",
                    trainingId = training.id,
                    timestamp = System.currentTimeMillis(),
                    snapshot = DrillSnapshot.Basic(training, resolved.reps),
                    profileName = profile?.name,
                    robotType = profile?.robotType,
                ))) {
                _breakReminder.tryEmit(Unit)
            }
        }
        robotManager.drillJob = viewModelScope.launch {
            _isPlaying.value = true
            _currentTrainingName.value = training.name
            val payload = RobotProtocol.encodeBasicPattern(
                training, motorConfig, timesOverride = resolved.reps,
            )
            robotManager.sendBasicDrill(payload, reps = resolved.reps)
            resolved.timedDurationSec?.let { startTimedCountdown(it) }
        }
    }

    fun playAdvancedTraining(training: AdvancedTraining) {
        robotManager.drillJob?.cancel()
        clearCountdown()
        val resolved = resolvePlay(
            PlayMode.fromValue(training.playMode),
            training.repeatNum, training.ballCount, training.durationSec,
            ballsPerPatternAdvanced(training), patternDurationTenthsAdvanced(training),
        )
        // History logging is decoupled from the drill path: it runs in its own
        // coroutine so history I/O can never block or crash drill playback.
        val profile = activeProfile.value
        viewModelScope.launch {
            if (historyStore.logEntry(HistoryEntry(
                    trainingName = training.name,
                    trainingType = "advanced",
                    trainingId = training.id,
                    timestamp = System.currentTimeMillis(),
                    snapshot = DrillSnapshot.Advanced(training, resolved.reps),
                    profileName = profile?.name,
                    robotType = profile?.robotType,
                ))) {
                _breakReminder.tryEmit(Unit)
            }
        }
        robotManager.drillJob = viewModelScope.launch {
            _isPlaying.value = true
            _currentTrainingName.value = training.name
            val payload = RobotProtocol.encodeAdvancedPattern(
                training, motorConfig, repeatNumOverride = resolved.reps,
            )
            robotManager.sendAdvancedDrill(payload, reps = resolved.reps)
            resolved.timedDurationSec?.let { startTimedCountdown(it) }
        }
    }

    private fun startTimedCountdown(durationSec: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = durationSec
            _playCountdownSec.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                _playCountdownSec.value = remaining
            }
            stop()
        }
    }

    private fun clearCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _playCountdownSec.value = null
    }

    /**
     * Start a get-in-position lead-in of [delaySec] whole seconds, then invoke [onFire] (the
     * existing, unchanged `playBasicTraining` / `playAdvancedTraining` call). Beeps through the
     * last 3 seconds and plays a distinct "go" tone at zero. Cancels any lead-in already running.
     * A [delaySec] of 0 or less fires immediately with no countdown.
     */
    fun beginDelayedStart(delaySec: Int, onFire: () -> Unit) {
        startCountdownJob?.cancel()
        startCue?.release()
        startCue = null
        if (delaySec < 1) {
            _startCountdownSec.value = null
            onFire()
            return
        }
        // Respect the Settings toggle: a silent cue still shows the countdown, just no beeps.
        val cue = (if (AppPrefs.startSoundEnabled.value) startCueFactory() else NoOpStartCue)
            .also { startCue = it }
        startCountdownJob = viewModelScope.launch {
            try {
                runStartCountdown(
                    delaySec = delaySec,
                    cue = cue,
                    publish = { _startCountdownSec.value = it },
                    onFire = onFire,
                )
            } finally {
                cue.release()
                if (startCue === cue) startCue = null
            }
        }
    }

    /** Cancel a running lead-in. The robot is never touched (nothing was sent yet). */
    fun cancelStartCountdown() {
        startCountdownJob?.cancel()
        startCountdownJob = null
        _startCountdownSec.value = null
        startCue?.release()
        startCue = null
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
            clearCountdown()
            robotManager.stop()
            _isPlaying.value = false
            _currentTrainingName.value = null
        }
    }

    override fun onCleared() {
        startCountdownJob?.cancel()
        startCue?.release()
        startCue = null
        robotManager.destroy()
    }
}

private typealias RobotProtocol = com.tablebot.ble.RobotProtocol
