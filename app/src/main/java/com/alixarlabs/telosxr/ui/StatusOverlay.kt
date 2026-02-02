package com.alixarlabs.telosxr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Status overlay matching Vision Pro layout
 *
 * Shows:
 * - Connection indicator (green dot + "FEC")
 * - FPS counter
 * - Voice command indicator (mic with red/gray color)
 * - Last voice command transcript
 * - 2D/3D toggle switch
 * - Disconnect button
 */
@Composable
fun StatusOverlay(
    fps: Float,
    isVoiceListening: Boolean,
    lastCommand: String,
    isStereoMode: Boolean,
    onToggleStereo: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(800.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status row (matches Vision Pro)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Green, CircleShape)
                )
                Text(
                    "FEC",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // FPS counter
            Text(
                text = "FPS: ${"%.1f".format(fps)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            // Last voice command
            if (lastCommand.isNotEmpty()) {
                Text(
                    text = "🎤 $lastCommand",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }

            Spacer(Modifier.weight(1f))

            // Voice indicator (pulsing when active)
            Row(
                modifier = Modifier
                    .background(
                        if (isVoiceListening) Color.Red.copy(alpha = 0.1f)
                        else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎤",
                    color = if (isVoiceListening) Color.Red else Color.Gray
                )
                Text(
                    text = "Voice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Controls row (centered)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2D/3D Toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isStereoMode,
                    onCheckedChange = {
                        android.util.Log.d("StatusOverlay", "Toggle clicked: $isStereoMode -> ${!isStereoMode}")
                        onToggleStereo()
                    }
                )
                Text(
                    text = if (isStereoMode) "3D" else "2D",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
