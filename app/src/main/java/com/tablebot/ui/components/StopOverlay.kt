package com.tablebot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StopOverlay(
    trainingName: String?,
    countdownSec: Int? = null,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onStop),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (trainingName != null) {
                Text(
                    trainingName,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(32.dp))
            }

            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop",
                tint = Color.Red,
                modifier = Modifier.size(160.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "STOP",
                color = Color.Red,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
            )

            countdownSec?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    "%d:%02d".format(it / 60, it % 60),
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Tap anywhere to stop",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
