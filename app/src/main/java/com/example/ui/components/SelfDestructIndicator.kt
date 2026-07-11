package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SelfDestructManager
import kotlinx.coroutines.delay

@Composable
fun SelfDestructIndicator(
    destructionTime: Long,
    totalDuration: Long,
    modifier: Modifier = Modifier,
    onExpired: () -> Unit = {}
) {
    if (destructionTime <= 0 || totalDuration <= 0) return

    var remaining by remember { mutableStateOf(SelfDestructManager.remainingTimeMillis(destructionTime)) }

    LaunchedEffect(destructionTime) {
        while (remaining > 0) {
            delay(200)
            remaining = SelfDestructManager.remainingTimeMillis(destructionTime)
        }
        onExpired()
    }

    val progress = if (totalDuration > 0) remaining.toFloat() / totalDuration.toFloat() else 0f
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val color = if (remaining < 10000) {
        Color(0xFFBA1A1A).copy(alpha = pulseAlpha) // Pulse red in final 10 seconds
    } else {
        Color(0xFF5D6156)
    }

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            // Draw background track
            drawCircle(
                color = color.copy(alpha = 0.15f),
                radius = size.minDimension / 2,
                style = Stroke(width = 2.dp.toPx())
            )
            // Draw remaining progress arc
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        Text(
            text = SelfDestructManager.formatRemainingTime(remaining),
            fontSize = 10.sp,
            color = color,
            maxLines = 1
        )
    }
}
