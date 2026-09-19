package app.mirro.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mirro.android.R
import app.mirro.android.domain.engine.workprofile.ProvisioningStatus
import app.mirro.android.ui.theme.StatusLimited
import app.mirro.android.ui.theme.StatusProtected
import app.mirro.android.ui.theme.StatusSupported

@Composable
fun MirroSpaceCard(
    status: ProvisioningStatus,
    onCreateSpaceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is ProvisioningStatus.Active -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                is ProvisioningStatus.Available -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                is ProvisioningStatus.Conflict -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                is ProvisioningStatus.Failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                is ProvisioningStatus.NotSupported -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                is ProvisioningStatus.Provisioning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("mirro_space_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when (status) {
                                    is ProvisioningStatus.Active -> StatusSupported.copy(alpha = 0.18f)
                                    is ProvisioningStatus.Available -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    is ProvisioningStatus.Conflict, is ProvisioningStatus.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (status) {
                                is ProvisioningStatus.Active -> Icons.Default.CheckCircle
                                is ProvisioningStatus.Available -> Icons.Default.AddModerator
                                is ProvisioningStatus.Conflict -> Icons.Default.Warning
                                is ProvisioningStatus.Failed -> Icons.Default.ErrorOutline
                                is ProvisioningStatus.NotSupported -> Icons.Default.Info
                                is ProvisioningStatus.Provisioning -> Icons.Default.Security
                            },
                            contentDescription = null,
                            tint = when (status) {
                                is ProvisioningStatus.Active -> StatusSupported
                                is ProvisioningStatus.Available -> MaterialTheme.colorScheme.primary
                                is ProvisioningStatus.Conflict, is ProvisioningStatus.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.space_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (status) {
                                is ProvisioningStatus.Active -> stringResource(R.string.space_status_active)
                                is ProvisioningStatus.Available -> stringResource(R.string.space_status_ready)
                                is ProvisioningStatus.Provisioning -> stringResource(R.string.space_status_provisioning)
                                is ProvisioningStatus.Conflict -> stringResource(R.string.space_status_conflict)
                                is ProvisioningStatus.NotSupported -> stringResource(R.string.space_status_unavailable)
                                is ProvisioningStatus.Failed -> stringResource(R.string.space_status_failed)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (status) {
                                is ProvisioningStatus.Active -> StatusSupported
                                is ProvisioningStatus.Available -> MaterialTheme.colorScheme.primary
                                is ProvisioningStatus.Conflict, is ProvisioningStatus.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (status is ProvisioningStatus.Active) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(StatusSupported.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.space_card_profile_owner_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSupported,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Description
            Text(
                text = when (status) {
                    is ProvisioningStatus.Active -> stringResource(R.string.space_card_active_desc)
                    is ProvisioningStatus.Available -> stringResource(R.string.space_setup_desc)
                    is ProvisioningStatus.Conflict -> stringResource(R.string.space_status_conflict_desc)
                    is ProvisioningStatus.NotSupported -> status.reason.ifBlank { stringResource(R.string.space_card_not_supported_desc) }
                    is ProvisioningStatus.Provisioning -> stringResource(R.string.space_status_provisioning)
                    is ProvisioningStatus.Failed -> status.error
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            // Action section based on status
            when (status) {
                is ProvisioningStatus.Available -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onCreateSpaceClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_create_mirro_space")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddModerator,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.space_action_create),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.space_disclaimer_system_consent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                }

                is ProvisioningStatus.Provisioning -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.space_status_provisioning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is ProvisioningStatus.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onCreateSpaceClick) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                is ProvisioningStatus.Conflict, is ProvisioningStatus.NotSupported -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onRefreshClick) {
                            Text(stringResource(R.string.refresh), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                is ProvisioningStatus.Active -> {
                    // Profile active, nothing blocking required
                }
            }
        }
    }
}
