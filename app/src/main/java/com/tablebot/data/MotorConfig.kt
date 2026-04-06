package com.tablebot.data

import android.content.Context
import kotlinx.serialization.json.Json

class MotorConfig(context: Context) {
    private val configs: List<MotorParams>

    init {
        val json = context.assets.open("base-conf.json").bufferedReader().use { it.readText() }
        configs = Json.decodeFromString<List<MotorParams>>(json)
    }

    fun lookup(ball: Int, spin: Int, power: Int, landarea: Int): MotorParams? {
        return configs.find { it.ball == ball && it.spin == spin && it.power == power && it.landarea == landarea }
    }
}
