package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.CompatibilityStatus
import com.example.ui.theme.StatusLimited
import com.example.ui.theme.StatusProtected
import com.example.ui.theme.StatusSupported
import com.example.ui.theme.StatusUnknown

@Composable
fun CompatibilityChip(
    status: CompatibilityStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (labelRes, accentColor, icon) = when (status) {
        CompatibilityStatus.SUPPORTED -> Triple(R.string.compat_status_supported, StatusSupported, Icons.Default.CheckCircle)
        CompatibilityStatus.LIMITED -> Triple(R.string.compat_status_limited, StatusLimited, Icons.Default.Info)
        CompatibilityStatus.PROTECTED -> Triple(R.string.compat_status_protected, StatusProtected, Icons.Default.Lock)
        CompatibilityStatus.UNKNOWN -> Triple(R.string.compat_status_unknown, StatusUnknown, Icons.AutoMirrored.Filled.Help)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(labelRes),
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
