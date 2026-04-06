package com.tablebot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

class TrainingStore(private val context: Context) {

    private val basicFile get() = File(context.filesDir, "basic-trainings.json")
    private val advancedFile get() = File(context.filesDir, "advanced-trainings.json")

    suspend fun loadBasicTrainings(): List<BasicTraining> = withContext(Dispatchers.IO) {
        val file = basicFile
        val text = if (file.exists()) {
            file.readText()
        } else {
            context.assets.open("basic-trainings.json").bufferedReader().use { it.readText() }
        }
        json.decodeFromString<List<BasicTraining>>(text)
    }

    suspend fun loadAdvancedTrainings(): List<AdvancedTraining> = withContext(Dispatchers.IO) {
        val file = advancedFile
        val text = if (file.exists()) {
            file.readText()
        } else {
            context.assets.open("advanced-trainings.json").bufferedReader().use { it.readText() }
        }
        json.decodeFromString<List<AdvancedTraining>>(text)
    }

    suspend fun saveBasicTraining(training: BasicTraining) = withContext(Dispatchers.IO) {
        val list = loadBasicTrainings().toMutableList()
        val idx = list.indexOfFirst { it.id == training.id }
        if (idx >= 0) list[idx] = training else list.add(0, training)
        basicFile.writeText(json.encodeToString(list))
    }

    suspend fun saveAdvancedTraining(training: AdvancedTraining) = withContext(Dispatchers.IO) {
        val list = loadAdvancedTrainings().toMutableList()
        val idx = list.indexOfFirst { it.id == training.id }
        if (idx >= 0) list[idx] = training else list.add(0, training)
        advancedFile.writeText(json.encodeToString(list))
    }

    suspend fun deleteBasicTraining(id: Int) = withContext(Dispatchers.IO) {
        val list = loadBasicTrainings().filter { it.id != id }
        basicFile.writeText(json.encodeToString(list))
    }

    suspend fun deleteAdvancedTraining(id: Int) = withContext(Dispatchers.IO) {
        val list = loadAdvancedTrainings().filter { it.id != id }
        advancedFile.writeText(json.encodeToString(list))
    }

    suspend fun toggleBasicFavourite(id: Int) = withContext(Dispatchers.IO) {
        val list = loadBasicTrainings().map {
            if (it.id == id) it.copy(isFavourite = if (it.isFavourite == 1) 0 else 1) else it
        }
        basicFile.writeText(json.encodeToString(list))
    }

    suspend fun toggleAdvancedFavourite(id: Int) = withContext(Dispatchers.IO) {
        val list = loadAdvancedTrainings().map {
            if (it.id == id) it.copy(isFavourite = if (it.isFavourite == 1) 0 else 1) else it
        }
        advancedFile.writeText(json.encodeToString(list))
    }

    fun nextBasicId(): Int = (basicFile.takeIf { it.exists() }?.let {
        json.decodeFromString<List<BasicTraining>>(it.readText()).maxOfOrNull { t -> t.id }
    } ?: 999) + 1

    fun nextAdvancedId(): Int = (advancedFile.takeIf { it.exists() }?.let {
        json.decodeFromString<List<AdvancedTraining>>(it.readText()).maxOfOrNull { t -> t.id }
    } ?: 999) + 1
}
