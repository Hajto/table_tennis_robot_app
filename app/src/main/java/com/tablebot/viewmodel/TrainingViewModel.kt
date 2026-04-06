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
        viewModelScope.launch {
            store.saveBasicTraining(training)
            _basicTrainings.value = store.loadBasicTrainings()
        }
    }

    fun saveAdvancedTraining(training: AdvancedTraining) {
        viewModelScope.launch {
            store.saveAdvancedTraining(training)
            _advancedTrainings.value = store.loadAdvancedTrainings()
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

    fun nextBasicId(): Int = store.nextBasicId()
    fun nextAdvancedId(): Int = store.nextAdvancedId()
}
