package com.tablebot.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppPrefs {
    private const val PREFS_NAME = "tablebot_prefs"
    private const val KEY_SHOW_FIELD_NUMBERS = "show_field_numbers"
    private const val KEY_DEBUG_MODE = "debug_mode"

    private lateinit var prefs: SharedPreferences

    private val _showFieldNumbers = MutableStateFlow(true)
    val showFieldNumbers: StateFlow<Boolean> = _showFieldNumbers

    private val _debugMode = MutableStateFlow(false)
    val debugMode: StateFlow<Boolean> = _debugMode

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _showFieldNumbers.value = prefs.getBoolean(KEY_SHOW_FIELD_NUMBERS, true)
        _debugMode.value = prefs.getBoolean(KEY_DEBUG_MODE, false)
    }

    fun setShowFieldNumbers(show: Boolean) {
        _showFieldNumbers.value = show
        prefs.edit().putBoolean(KEY_SHOW_FIELD_NUMBERS, show).apply()
    }

    fun setDebugMode(enabled: Boolean) {
        _debugMode.value = enabled
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }
}
