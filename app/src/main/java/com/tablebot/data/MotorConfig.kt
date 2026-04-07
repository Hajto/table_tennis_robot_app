package com.tablebot.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

class MotorConfig(private val context: Context) {
    private val file get() = File(context.filesDir, "motor-config.json")
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

    suspend fun importJson(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        configs = json.decodeFromString<List<MotorParams>>(text).toMutableList()
        file.writeText(json.encodeToString<List<MotorParams>>(configs))
    }

    fun isCustomized(): Boolean = file.exists()
}
