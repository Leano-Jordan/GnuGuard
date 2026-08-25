package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalGood
import com.example.ui.theme.SignalWeak

@Composable
fun SignalBars(
    level: Int, // 0 to 4
    modifier: Modifier = Modifier,
    barWidth: Dp = 4.dp,
    maxBarHeight: Dp = 18.dp
) {
    val activeColor = when (level) {
        4 -> SignalExcellent
        3 -> SignalGood
        2 -> SignalFair
        else -> SignalWeak
    }
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barHeights = listOf(0.35f, 0.55f, 0.75f, 1.0f)
        barHeights.forEachIndexed { index, fraction ->
            val isActive = index < level
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxBarHeight * fraction)
                    .background(
                        color = if (isActive) activeColor else inactiveColor,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable
fun SignalDbmBadge(
    rssiDbm: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        rssiDbm >= -55 -> SignalExcellent
        rssiDbm >= -67 -> SignalGood
        rssiDbm >= -78 -> SignalFair
        else -> SignalWeak
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$rssiDbm dBm",
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
    }
}
