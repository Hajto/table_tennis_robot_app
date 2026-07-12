package com.tablebot.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.tablebot.ui.screens.AdvancedEditorState
import com.tablebot.ui.screens.DrillEditorState

/**
 * Holds the home screen's in-progress Basic/Dynamic editing state. Activity-scoped, so the draft
 * survives navigating to Calibration/Settings and screen rotation (the previous `remember`-scoped
 * state was disposed and reset on navigation).
 */
class QuickPlayDraftViewModel : ViewModel() {
    var mode by mutableIntStateOf(0)

    // ids get their real nextId assigned once by QuickPlayScreen (VM can't call composable lambdas).
    val basicState = DrillEditorState(initial = null, id = 0)
    val advancedState = AdvancedEditorState(initial = null, id = 0)
    var idsInitialized = false

    var loadedBasicId by mutableStateOf<Int?>(null)
    var loadedAdvancedId by mutableStateOf<Int?>(null)

    var calibrationSeed by mutableStateOf<CalibrationSeed?>(null)
}
