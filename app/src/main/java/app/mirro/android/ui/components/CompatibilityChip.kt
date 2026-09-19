package app.mirro.android.ui.components

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
import app.mirro.android.R
import app.mirro.android.domain.model.CompatibilityStatus
import app.mirro.android.ui.theme.StatusLimited
import app.mirro.android.ui.theme.StatusProtected
import app.mirro.android.ui.theme.StatusSupported
import app.mirro.android.ui.theme.StatusUnknown

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
        CompatibilityStatus.CONTAINER_NOT_TESTED -> Triple(R.string.compat_status_container_not_tested, StatusUnknown, Icons.AutoMirrored.Filled.Help)
        CompatibilityStatus.CONTAINER_RUNTIME_READY -> Triple(R.string.compat_status_container_runtime_ready, StatusSupported, Icons.Default.CheckCircle)
        CompatibilityStatus.CONTAINER_LAUNCH_VERIFIED -> Triple(R.string.compat_status_container_verified, StatusSupported, Icons.Default.CheckCircle)
        CompatibilityStatus.CONTAINER_LAUNCH_FAILED -> Triple(R.string.compat_status_container_failed, StatusProtected, Icons.Default.Info)
        CompatibilityStatus.WORK_PROFILE_AVAILABLE -> Triple(R.string.compat_status_work_profile_available, StatusSupported, Icons.Default.CheckCircle)
        CompatibilityStatus.WORK_PROFILE_INSTALL_REQUIRED -> Triple(R.string.compat_status_work_profile_install_required, StatusLimited, Icons.Default.Info)
        CompatibilityStatus.VERIFIED_WORK_PROFILE -> Triple(R.string.compat_status_verified_work_profile, StatusSupported, Icons.Default.CheckCircle)
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
