package com.tablebot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tablebot.data.AppPrefs
import com.tablebot.data.Point

/**
 * 3x5 grid representing the table half.
 * Cells are numbered 1-15 (left-to-right, top-to-bottom):
 *   Row 0: 1  2  3  4  5    (close to net)
 *   Row 1: 6  7  8  9  10   (middle)
 *   Row 2: 11 12 13 14 15   (far from net / close to player)
 *
 * The robot's x parameter maps to cell number (1-15).
 * The y parameter is depth (1=short, 2=medium, 3=long).
 *
 * @param cellBallNumbers optional map of cell number → list of ball sequence numbers to display.
 *   When provided, selected cells show ball numbers instead of cell numbers (capped at 4 per cell).
 */
@Composable
fun TableGrid(
    selectedPoints: List<Point>,
    onCellClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    cellBallNumbers: Map<Int, List<Int>>? = null,
) {
    val selectedCells = selectedPoints.map { it.x }.toSet()
    val showFieldNumbers by AppPrefs.showFieldNumbers.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Net",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        // Table surface
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B5E20).copy(alpha = 0.15f))
                .border(2.dp, Color(0xFF1B5E20).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (col in 0..4) {
                        val cellNum = row * 5 + col + 1
                        val isSelected = cellNum in selectedCells
                        val ballNums = cellBallNumbers?.get(cellNum)?.take(MAX_BALLS_PER_CELL)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color(0xFF2E7D32).copy(alpha = 0.25f)
                                )
                                .then(
                                    if (onCellClick != null) Modifier.clickable { onCellClick(cellNum) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ballNums != null && isSelected) {
                                Text(
                                    text = ballNums.joinToString(","),
                                    fontSize = if (ballNums.size > 2) 9.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            } else if (showFieldNumbers) {
                                Text(
                                    text = "$cellNum",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Player",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

const val MAX_BALLS_PER_CELL = 4

/** Build a cell→ball-numbers map from a list of [BallEntry]-like items. */
fun buildCellBallNumbers(ballEntries: List<Pair<Int, List<Point>>>): Map<Int, List<Int>> {
    val result = mutableMapOf<Int, MutableList<Int>>()
    ballEntries.forEach { (ballIndex, points) ->
        points.forEach { point ->
            result.getOrPut(point.x) { mutableListOf() }.add(ballIndex)
        }
    }
    return result
}
