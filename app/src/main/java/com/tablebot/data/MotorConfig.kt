package com.tablebot.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

class MotorConfig(private val context: Context, private val fileName: String = "motor-config.json") {
    private val file get() = File(context.filesDir, fileName)
    private var configs: MutableList<MotorParams>

    init {
        val text = if (file.exists()) {
            file.readText()
        } else {
            context.assets.open("base-conf.json").bufferedReader().use { it.readText() }
        }
        configs = json.decodeFromString<List<MotorParams>>(text).toMutableList()
    }

    fun lookup(ball: Int, spin: Int, power: Int, landarea: Int): MotorParams? {
        return configs.find { it.ball == ball && it.spin == spin && it.power == power && it.landarea == landarea }
    }

    /** Returns the set of valid landarea cell numbers (1-15) for a given ball/spin/power combo. */
    fun validLandareas(ball: Int, spin: Int, power: Int): Set<Int> {
        return configs.filter { it.ball == ball && it.spin == spin && it.power == power }
            .map { it.landarea }
            .toSet()
    }

    /** Returns the set of power values that have at least one valid landarea for the given ball/spin. */
    fun validPowers(ball: Int, spin: Int): Set<Int> {
        return configs.filter { it.ball == ball && it.spin == spin }
            .map { it.power }
            .toSet()
    }

    /** Returns the set of spin values that have at least one valid landarea for the given ball type. */
    fun validSpins(ball: Int): Set<Int> {
        return configs.filter { it.ball == ball }
            .map { it.spin }
            .toSet()
    }

    suspend fun save(params: MotorParams) = withContext(Dispatchers.IO) {
        val idx = configs.indexOfFirst { it.id == params.id }
        if (idx >= 0) configs[idx] = params else configs.add(params)
        file.writeText(json.encodeToString<List<MotorParams>>(configs))
    }

    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        val text = context.assets.open("base-conf.json").bufferedReader().use { it.readText() }
        configs = json.decodeFromString<List<MotorParams>>(text).toMutableList()
        file.delete()
    }

    fun exportJson(): String = json.encodeToString<List<MotorParams>>(configs)

    suspend fun importJson(uri: Uri): String? = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return@withContext "Could not open file"
        val text = stream.bufferedReader().use { it.readText() }
        val parsed = try {
            json.decodeFromString<List<MotorParams>>(text)
        } catch (e: Exception) {
            return@withContext "Invalid file format: ${e.message}"
        }
        configs = parsed.toMutableList()
        file.writeText(json.encodeToString<List<MotorParams>>(configs))
        null // success
    }

    fun isCustomized(): Boolean = file.exists()

    fun isEmpty(): Boolean = configs.isEmpty()
}
