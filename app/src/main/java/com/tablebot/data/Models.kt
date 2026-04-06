package com.tablebot.data

import kotlinx.serialization.Serializable

@Serializable
data class Point(val x: Int, val y: Int = 2)

@Serializable
data class SkillLevel(val id: Int = 1, val skillLevel: String = "Beginner")

@Serializable
data class BasicTraining(
    val id: Int,
    val name: String,
    val ball: Int = 1,          // 0=backspin, 1=topspin
    val spin: Int = 2,          // 0-4
    val power: Int = 2,         // 0-3
    val landType: Int = 0,      // 0=static, 1=loop, 2=random
    val ballTime: Int = 9,      // interval between balls (tenths of sec)
    val numType: Int = 1,
    val times: Int = 20,        // repetitions
    val adjustSpin: Int = 0,
    val adjustPosition: Int = 0,
    val points: List<Point> = emptyList(),
    val isFavourite: Int = 0,
    val skillLevel: SkillLevel = SkillLevel(),
)

@Serializable
data class BallEntry(
    val ball: Int = 1,
    val spin: Int = 2,
    val power: Int = 2,
    val landType: Int = 0,
    val points: List<Point> = emptyList(),
    val ballTime: Int = 9,
    val random: Int = 0,
    val adjustSpin: Int = 0,
    val adjustPosition: Int = 0,
)

@Serializable
data class AdvancedTraining(
    val id: Int,
    val name: String,
    val repeatNum: Int = 10,
    val repeatDelay: Int = 1,
    val intervalTrain: Int = 0,
    val ballList: List<BallEntry> = emptyList(),
    val isFavourite: Int = 0,
    val skillLevel: SkillLevel = SkillLevel(),
)

@Serializable
data class MotorParams(
    val id: Int,
    val ball: Int,
    val spin: Int,
    val power: Int,
    val landarea: Int,
    val m1speed: Int,
    val m2speed: Int,
    val xaxis: Int,
    val yaxis: Int,
    val zaxis: Int,
    val robotVersionId: Int = 1,
)

enum class BallType(val value: Int, val label: String) {
    SERVE(0, "Serve"),
    NORMAL(1, "Normal"),
    LOB(2, "Lob");
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: NORMAL
    }
}

enum class SpinType(val value: Int, val label: String) {
    MAX_TOPSPIN(0, "Max Topspin"),
    TOPSPIN(1, "Topspin"),
    FLOAT(2, "Float"),
    BACKSPIN(3, "Backspin"),
    MAX_BACKSPIN(4, "Max Backspin");
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: FLOAT
    }
}

enum class PowerType(val value: Int, val label: String) {
    EXTREME(0, "Extreme"),
    STRONG(1, "Strong"),
    MEDIUM(2, "Medium"),
    LIGHT(3, "Light");
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: MEDIUM
    }
}

enum class LandType(val value: Int, val label: String) {
    STATIC(0, "Static"),
    LOOP(1, "Loop"),
    RANDOM(2, "Random");
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: STATIC
    }
}

enum class SkillLevelType(val value: Int, val label: String) {
    BEGINNER(1, "Beginner"),
    INTERMEDIATE(2, "Intermediate"),
    ADVANCED(3, "Advanced");
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: BEGINNER
    }
}
