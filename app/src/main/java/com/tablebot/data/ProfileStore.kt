package com.tablebot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

class ProfileStore(private val context: Context) {

    private val indexFile get() = File(context.filesDir, "profiles.json")

    suspend fun migrateIfNeeded() = withContext(Dispatchers.IO) {
        if (indexFile.exists()) return@withContext

        val defaultId = UUID.randomUUID().toString()
        val newFileName = "motor-config-$defaultId.json"
        val oldFile = File(context.filesDir, "motor-config.json")

        if (oldFile.exists()) {
            oldFile.copyTo(File(context.filesDir, newFileName), overwrite = true)
        }

        val defaultProfile = Profile(
            id = defaultId,
            name = "Infinity",
            motorConfigFileName = newFileName,
        )
        val index = ProfileIndex(
            activeProfileId = defaultId,
            profiles = listOf(defaultProfile),
        )
        indexFile.writeText(json.encodeToString(index))

        if (oldFile.exists()) {
            oldFile.delete()
        }
    }

    suspend fun loadIndex(): ProfileIndex = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            migrateIfNeeded()
        }
        val index = json.decodeFromString<ProfileIndex>(indexFile.readText())

        // Validate activeProfileId — fall back to first profile if invalid, and persist the fix
        val validIndex = if (index.profiles.none { it.id == index.activeProfileId }) {
            val healed = index.copy(activeProfileId = index.profiles.first().id)
            indexFile.writeText(json.encodeToString(healed))
            healed
        } else {
            index
        }

        // Orphan sweep: delete motor-config files not referenced by any profile
        val referencedFiles = validIndex.profiles.map { it.motorConfigFileName }.toSet()
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("motor-config-") && it.name.endsWith(".json") && it.name !in referencedFiles
        }?.forEach { it.delete() }

        validIndex
    }

    suspend fun saveIndex(index: ProfileIndex) = withContext(Dispatchers.IO) {
        indexFile.writeText(json.encodeToString(index))
    }

    suspend fun createProfile(name: String, robotType: RobotType = RobotType.JOOLA_V2): Profile = withContext(Dispatchers.IO) {
        val index = loadIndex()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Profile name cannot be empty" }
        require(index.profiles.none { it.name.equals(trimmed, ignoreCase = true) }) {
            "A profile named \"$trimmed\" already exists"
        }

        val id = UUID.randomUUID().toString()
        val newFileName = "motor-config-$id.json"

        // Start with empty calibration — user must calibrate from scratch
        File(context.filesDir, newFileName).writeText("[]")

        val profile = Profile(
            id = id,
            name = trimmed,
            motorConfigFileName = newFileName,
            robotType = robotType,
        )
        val updated = index.copy(profiles = index.profiles + profile)
        saveIndex(updated)
        profile
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        val index = loadIndex()
        require(index.profiles.size > 1) { "Cannot delete the last profile" }

        val profile = index.profiles.find { it.id == id } ?: return@withContext
        File(context.filesDir, profile.motorConfigFileName).delete()

        val remaining = index.profiles.filter { it.id != id }
        val newActiveId = if (index.activeProfileId == id) remaining.first().id else index.activeProfileId
        saveIndex(index.copy(activeProfileId = newActiveId, profiles = remaining))
    }

    suspend fun updateProfile(profile: Profile) = withContext(Dispatchers.IO) {
        val index = loadIndex()
        val trimmed = profile.copy(name = profile.name.trim())
        require(trimmed.name.isNotEmpty()) { "Profile name cannot be empty" }
        require(
            index.profiles.none { it.id != trimmed.id && it.name.equals(trimmed.name, ignoreCase = true) }
        ) { "A profile named \"${trimmed.name}\" already exists" }

        val updated = index.copy(
            profiles = index.profiles.map { if (it.id == trimmed.id) trimmed else it }
        )
        saveIndex(updated)
    }

    suspend fun setActiveProfile(id: String) = withContext(Dispatchers.IO) {
        val index = loadIndex()
        require(index.profiles.any { it.id == id }) { "Profile not found" }
        saveIndex(index.copy(activeProfileId = id))
    }
}
