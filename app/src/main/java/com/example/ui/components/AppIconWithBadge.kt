package com.example.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIconWithBadge(
    drawable: Drawable?,
    badgeColorHex: String?,
    badgeSymbol: String?,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(drawable) {
        drawable?.let {
            try {
                it.toBitmap(width = 120, height = 120)
            } catch (_: Exception) {
                null
            }
        }
    }

    val parsedBadgeColor = remember(badgeColorHex) {
        if (badgeColorHex != null) {
            try {
                Color(android.graphics.Color.parseColor(badgeColorHex))
            } catch (_: Exception) {
                Color(0xFF10B981)
            }
        } else null
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Base Icon
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.22f))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.22f))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeSymbol?.take(1) ?: "A",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Badge Overlay
        if (parsedBadgeColor != null && !badgeSymbol.isNullOrBlank()) {
            val badgeSize = size * 0.44f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (size * 0.1f), y = (size * 0.1f))
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(parsedBadgeColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeSymbol.take(2),
                    fontSize = (badgeSize.value * 0.48f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}
