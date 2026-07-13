package com.tablebot.data

/** Converts the legacy per-BallEntry advanced model into the step model. */
fun migrateBallEntriesToSteps(ballList: List<BallEntry>): List<Step> =
    ballList.flatMap { e ->
        val random = e.random == 1
        when {
            random && e.points.size <= 1 ->
                listOf(Step(e.ball, e.spin, e.power, e.ballTime, e.points, orderRandom = true))
            random ->
                e.points.chunked(5).map { chunk ->
                    Step(e.ball, e.spin, e.power, e.ballTime, chunk, orderRandom = false)
                }
            else ->
                e.points.map { p ->
                    Step(e.ball, e.spin, e.power, e.ballTime, listOf(p), orderRandom = false)
                }
        }
    }
