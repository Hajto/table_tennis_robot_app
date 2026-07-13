package com.tablebot.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppPrefs {
    private const val PREFS_NAME = "tablebot_prefs"
    private const val KEY_SHOW_FIELD_NUMBERS = "show_field_numbers"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_INFER_ROW_CALIBRATION = "infer_row_calibration"
    private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    private const val KEY_BALL_TRAY_CAPACITY = "ball_tray_capacity"
    private const val KEY_TRAINING_MIGRATION_VERSION = "training_migration_version"
    const val CURRENT_MIGRATION_VERSION = 2 // 1 = tags, 2 = isDefault

    /** Default tray capacity (a JOOLA with wings holds ~100 balls). */
    const val DEFAULT_BALL_TRAY_CAPACITY = 100

    private lateinit var prefs: SharedPreferences

    private val _showFieldNumbers = MutableStateFlow(true)
    val showFieldNumbers: StateFlow<Boolean> = _showFieldNumbers

    private val _debugMode = MutableStateFlow(false)
    val debugMode: StateFlow<Boolean> = _debugMode

    private val _inferRowCalibration = MutableStateFlow(false)
    val inferRowCalibration: StateFlow<Boolean> = _inferRowCalibration

    private val _hasSeenOnboarding = MutableStateFlow(false)
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding

    private val _ballTrayCapacity = MutableStateFlow(DEFAULT_BALL_TRAY_CAPACITY)
    val ballTrayCapacity: StateFlow<Int> = _ballTrayCapacity

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _showFieldNumbers.value = prefs.getBoolean(KEY_SHOW_FIELD_NUMBERS, true)
        _debugMode.value = prefs.getBoolean(KEY_DEBUG_MODE, false)
        _inferRowCalibration.value = prefs.getBoolean(KEY_INFER_ROW_CALIBRATION, false)
        _hasSeenOnboarding.value = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        _ballTrayCapacity.value = prefs.getInt(KEY_BALL_TRAY_CAPACITY, DEFAULT_BALL_TRAY_CAPACITY)
    }

    fun setShowFieldNumbers(show: Boolean) {
        _showFieldNumbers.value = show
        prefs.edit().putBoolean(KEY_SHOW_FIELD_NUMBERS, show).apply()
    }

    fun setDebugMode(enabled: Boolean) {
        _debugMode.value = enabled
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    fun setInferRowCalibration(enabled: Boolean) {
        _inferRowCalibration.value = enabled
        prefs.edit().putBoolean(KEY_INFER_ROW_CALIBRATION, enabled).apply()
    }

    fun setHasSeenOnboarding(seen: Boolean) {
        _hasSeenOnboarding.value = seen
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
    }

    fun setBallTrayCapacity(capacity: Int) {
        _ballTrayCapacity.value = capacity
        prefs.edit().putInt(KEY_BALL_TRAY_CAPACITY, capacity).apply()
    }

    fun trainingMigrationVersion(): Int =
        prefs.getInt(KEY_TRAINING_MIGRATION_VERSION, 0)

    fun setTrainingMigrationVersion(version: Int) {
        prefs.edit().putInt(KEY_TRAINING_MIGRATION_VERSION, version).apply()
    }
}
