package com.tablebot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablebot.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TrainingViewModel(app: Application) : AndroidViewModel(app) {

    private val store = TrainingStore(app.applicationContext)

    private val _basicTrainings = MutableStateFlow<List<BasicTraining>?>(null)
    val basicTrainings: StateFlow<List<BasicTraining>?> = _basicTrainings

    private val _advancedTrainings = MutableStateFlow<List<AdvancedTraining>?>(null)
    val advancedTrainings: StateFlow<List<AdvancedTraining>?> = _advancedTrainings

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _basicTrainings.value = store.loadBasicTrainings()
            _advancedTrainings.value = store.loadAdvancedTrainings()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun filteredBasic(): List<BasicTraining> {
        val list = _basicTrainings.value ?: return emptyList()
        val q = _searchQuery.value.lowercase()
        return if (q.isBlank()) list else list.filter { it.name.lowercase().contains(q) }
    }

    fun filteredAdvanced(): List<AdvancedTraining> {
        val list = _advancedTrainings.value ?: return emptyList()
        val q = _searchQuery.value.lowercase()
        return if (q.isBlank()) list else list.filter { it.name.lowercase().contains(q) }
    }

    fun saveBasicTraining(training: BasicTraining) {
        // Optimistic update — immediately reflect in memory so export/UI never sees stale data
        val saved = training.copy(isDefault = false)
        val current = _basicTrainings.value?.toMutableList() ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == saved.id }
        if (idx >= 0) current[idx] = saved else current.add(0, saved)
        _basicTrainings.value = current
        viewModelScope.launch {
            store.saveBasicTraining(training)
        }
    }

    fun saveAdvancedTraining(training: AdvancedTraining) {
        val saved = training.copy(isDefault = false)
        val current = _advancedTrainings.value?.toMutableList() ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == saved.id }
        if (idx >= 0) current[idx] = saved else current.add(0, saved)
        _advancedTrainings.value = current
        viewModelScope.launch {
            store.saveAdvancedTraining(training)
        }
    }

    fun deleteBasicTraining(id: Int) {
        viewModelScope.launch {
            store.deleteBasicTraining(id)
            _basicTrainings.value = store.loadBasicTrainings()
        }
    }

    fun deleteAdvancedTraining(id: Int) {
        viewModelScope.launch {
            store.deleteAdvancedTraining(id)
            _advancedTrainings.value = store.loadAdvancedTrainings()
        }
    }

    fun toggleBasicFavourite(id: Int) {
        viewModelScope.launch {
            store.toggleBasicFavourite(id)
            _basicTrainings.value = store.loadBasicTrainings()
        }
    }

    fun toggleAdvancedFavourite(id: Int) {
        viewModelScope.launch {
            store.toggleAdvancedFavourite(id)
            _advancedTrainings.value = store.loadAdvancedTrainings()
        }
    }

    fun nextBasicId(): Int =
        (_basicTrainings.value?.maxOfOrNull { it.id } ?: 999) + 1

    fun nextAdvancedId(): Int =
        (_advancedTrainings.value?.maxOfOrNull { it.id } ?: 999) + 1

    suspend fun exportToJson(): String {
        val basic = _basicTrainings.value?.filter { !it.isDefault } ?: emptyList()
        val advanced = _advancedTrainings.value?.filter { !it.isDefault } ?: emptyList()
        return store.exportFromMemory(basic, advanced)
    }

    suspend fun parseImportBundle(jsonText: String) = store.parseImportBundle(jsonText)

    suspend fun findCollisions(bundle: com.tablebot.data.TrainingStore.DrillExportBundle) =
        store.findCollisions(bundle)

    fun importBundle(bundle: com.tablebot.data.TrainingStore.DrillExportBundle, overwriteNames: Set<String>) {
        viewModelScope.launch {
            store.importBundle(bundle, overwriteNames)
            _basicTrainings.value = store.loadBasicTrainings()
            _advancedTrainings.value = store.loadAdvancedTrainings()
        }
    }
}
