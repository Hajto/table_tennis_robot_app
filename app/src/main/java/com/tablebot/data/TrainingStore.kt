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

    /**
     * Run one-shot migration if needed, then load basic trainings from local file or assets.
     */
    suspend fun loadBasicTrainings(): List<BasicTraining> = withContext(Dispatchers.IO) {
        runMigrationIfNeeded()
        val file = basicFile
        if (file.exists()) {
            json.decodeFromString<List<BasicTraining>>(file.readText())
        } else {
            json.decodeFromString<List<BasicTraining>>(
                context.assets.open("basic-trainings.json").bufferedReader().use { it.readText() }
            )
        }
    }

    suspend fun loadAdvancedTrainings(): List<AdvancedTraining> = withContext(Dispatchers.IO) {
        runMigrationIfNeeded()
        val file = advancedFile
        if (file.exists()) {
            json.decodeFromString<List<AdvancedTraining>>(file.readText()).map { it.migrated() }
        } else {
            json.decodeFromString<List<AdvancedTraining>>(
                context.assets.open("advanced-trainings.json").bufferedReader().use { it.readText() }
            ).map { it.migrated() }
        }
    }

    private fun runMigrationIfNeeded() {
        val version = AppPrefs.trainingMigrationVersion()
        if (version >= AppPrefs.CURRENT_MIGRATION_VERSION) return

        val bundledBasic = json.decodeFromString<List<BasicTraining>>(
            context.assets.open("basic-trainings.json").bufferedReader().use { it.readText() }
        ).associateBy { it.id }
        val bundledAdvanced = json.decodeFromString<List<AdvancedTraining>>(
            context.assets.open("advanced-trainings.json").bufferedReader().use { it.readText() }
        ).map { it.migrated() }.associateBy { it.id }

        if (basicFile.exists()) {
            val local = json.decodeFromString<List<BasicTraining>>(basicFile.readText())
            val migrated = local.map { t ->
                val b = bundledBasic[t.id]
                t.copy(
                    tags = if (t.tags.isEmpty()) b?.tags ?: t.tags else t.tags,
                    isDefault = if (b != null) b.isDefault else t.isDefault,
                )
            }
            if (migrated != local) basicFile.writeText(json.encodeToString(migrated))
        }

        if (advancedFile.exists()) {
            val local = json.decodeFromString<List<AdvancedTraining>>(advancedFile.readText()).map { it.migrated() }
            val migrated = local.map { t ->
                val b = bundledAdvanced[t.id]
                t.copy(
                    tags = if (t.tags.isEmpty()) b?.tags ?: t.tags else t.tags,
                    isDefault = if (b != null) b.isDefault else t.isDefault,
                )
            }
            if (migrated != local) advancedFile.writeText(json.encodeToString(migrated))
        }

        AppPrefs.setTrainingMigrationVersion(AppPrefs.CURRENT_MIGRATION_VERSION)
    }

    suspend fun saveBasicTraining(training: BasicTraining) = withContext(Dispatchers.IO) {
        val list = loadBasicTrainings().toMutableList()
        val toSave = training.copy(isDefault = false)
        val idx = list.indexOfFirst { it.id == toSave.id }
        if (idx >= 0) list[idx] = toSave else list.add(0, toSave)
        basicFile.writeText(json.encodeToString(list))
    }

    suspend fun saveAdvancedTraining(training: AdvancedTraining) = withContext(Dispatchers.IO) {
        val list = loadAdvancedTrainings().toMutableList()
        val toSave = training.copy(isDefault = false)
        val idx = list.indexOfFirst { it.id == toSave.id }
        if (idx >= 0) list[idx] = toSave else list.add(0, toSave)
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

    @Deprecated("Use TrainingViewModel.nextBasicId() which derives from in-memory state")
    fun nextBasicId(): Int = (basicFile.takeIf { it.exists() }?.let {
        json.decodeFromString<List<BasicTraining>>(it.readText()).maxOfOrNull { t -> t.id }
    } ?: 999) + 1

    @Deprecated("Use TrainingViewModel.nextAdvancedId() which derives from in-memory state")
    fun nextAdvancedId(): Int = (advancedFile.takeIf { it.exists() }?.let {
        json.decodeFromString<List<AdvancedTraining>>(it.readText()).map { t -> t.migrated() }.maxOfOrNull { t -> t.id }
    } ?: 999) + 1

    // ── Export / Import ────────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class DrillExportBundle(
        val basic: List<BasicTraining> = emptyList(),
        val advanced: List<AdvancedTraining> = emptyList(),
    )

    /** Export from already-filtered in-memory lists (avoids stale disk reads). */
    fun exportFromMemory(basic: List<BasicTraining>, advanced: List<AdvancedTraining>): String =
        json.encodeToString(DrillExportBundle(basic, advanced))

    suspend fun parseImportBundle(jsonText: String): DrillExportBundle = withContext(Dispatchers.IO) {
        val bundle = json.decodeFromString<DrillExportBundle>(jsonText)
        bundle.copy(advanced = bundle.advanced.map { it.migrated() })
    }

    /**
     * Returns typed collision keys (e.g. "basic:Forehand Flick") for drills in [bundle]
     * that collide with existing drills by name within the same type.
     */
    suspend fun findCollisions(bundle: DrillExportBundle): List<String> = withContext(Dispatchers.IO) {
        val existingBasic = loadBasicTrainings().associateBy { it.name }
        val existingAdvanced = loadAdvancedTrainings().associateBy { it.name }
        val collisions = mutableListOf<String>()
        bundle.basic.forEach { incoming ->
            if (existingBasic[incoming.name] != null) collisions += "basic:${incoming.name}"
        }
        bundle.advanced.forEach { incoming ->
            if (existingAdvanced[incoming.name] != null) collisions += "advanced:${incoming.name}"
        }
        collisions
    }

    /**
     * Import drills from [bundle]. [overwriteKeys] contains typed keys like "basic:Name".
     * For collisions in [overwriteKeys], replace existing. Non-colliding always added with fresh ID.
     */
    suspend fun importBundle(bundle: DrillExportBundle, overwriteKeys: Set<String>) = withContext(Dispatchers.IO) {
        val existingBasic = loadBasicTrainings().toMutableList()
        val existingAdvanced = loadAdvancedTrainings().toMutableList()

        var nextBasic = (existingBasic.maxOfOrNull { it.id } ?: 999) + 1
        var nextAdvanced = (existingAdvanced.maxOfOrNull { it.id } ?: 999) + 1
        var basicChanged = false
        var advancedChanged = false

        bundle.basic.forEach { incoming ->
            val collidingIdx = existingBasic.indexOfFirst { it.name == incoming.name }
            if (collidingIdx >= 0 && "basic:${incoming.name}" in overwriteKeys) {
                existingBasic[collidingIdx] = incoming.copy(id = existingBasic[collidingIdx].id, isDefault = false)
                basicChanged = true
            } else if (collidingIdx < 0) {
                existingBasic.add(0, incoming.copy(id = nextBasic++, isDefault = false))
                basicChanged = true
            }
        }

        bundle.advanced.forEach { incoming ->
            val collidingIdx = existingAdvanced.indexOfFirst { it.name == incoming.name }
            if (collidingIdx >= 0 && "advanced:${incoming.name}" in overwriteKeys) {
                existingAdvanced[collidingIdx] = incoming.copy(id = existingAdvanced[collidingIdx].id, isDefault = false)
                advancedChanged = true
            } else if (collidingIdx < 0) {
                existingAdvanced.add(0, incoming.copy(id = nextAdvanced++, isDefault = false))
                advancedChanged = true
            }
        }

        if (basicChanged) basicFile.writeText(json.encodeToString(existingBasic))
        if (advancedChanged) advancedFile.writeText(json.encodeToString(existingAdvanced))
    }
}
