package app.mirro.android.ui.container

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mirro.android.domain.engine.container.model.ContainerLaunchResult
import app.mirro.android.domain.engine.container.model.ContainerRuntimeDiagnostics
import app.mirro.android.domain.engine.container.runtime.ContainerRuntime
import app.mirro.android.ui.theme.MirroTheme

/**
 * Android Host Activity that boots, hosts, and monitors target application code
 * running inside Mirro's isolated user-space Container runtime.
 */
class ContainerHostActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CLONE_ID = "extra_clone_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CUSTOM_NAME = "extra_custom_name"
        const val EXTRA_BADGE_COLOR = "extra_badge_color"
        const val EXTRA_BADGE_SYMBOL = "extra_badge_symbol"
    }

    private lateinit var containerRuntime: ContainerRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        containerRuntime = ContainerRuntime(applicationContext)

        val cloneId = intent.getStringExtra(EXTRA_CLONE_ID) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val customName = intent.getStringExtra(EXTRA_CUSTOM_NAME) ?: "Clone Instance"
        val badgeColorHex = intent.getStringExtra(EXTRA_BADGE_COLOR) ?: "#6750A4"
        val badgeSymbol = intent.getStringExtra(EXTRA_BADGE_SYMBOL) ?: "2"

        val launchResult = if (cloneId.isNotEmpty() && packageName.isNotEmpty()) {
            containerRuntime.prepareContainer(cloneId, packageName)
        } else {
            null
        }

        setContent {
            MirroTheme {
                ContainerHostScreen(
                    cloneId = cloneId,
                    packageName = packageName,
                    customName = customName,
                    badgeColorHex = badgeColorHex,
                    badgeSymbol = badgeSymbol,
                    initialResult = launchResult,
                    onReload = {
                        containerRuntime.prepareContainer(cloneId, packageName)
                    },
                    onBack = { finish() },
                    onCopyLogs = { text ->
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Mirro Diagnostics", text))
                        Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerHostScreen(
    cloneId: String,
    packageName: String,
    customName: String,
    badgeColorHex: String,
    badgeSymbol: String,
    initialResult: ContainerLaunchResult?,
    onReload: () -> ContainerLaunchResult,
    onBack: () -> Unit,
    onCopyLogs: (String) -> Unit
) {
    var result by remember { mutableStateOf(initialResult) }
    var showRawLogs by remember { mutableStateOf(false) }

    val badgeColor = try {
        Color(android.graphics.Color.parseColor(badgeColorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badgeSymbol,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = customName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Mirro Container Sandbox",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { result = onReload() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val diag = result?.diagnostics

            // Status Card
            val isSuccess = result?.isSuccess == true
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isSuccess) "Container Runtime Active" else "Startup Stoppage Detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = result?.message ?: "Container initialized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sandbox & Isolation Info Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sandbox Boundaries",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    ContainerInfoRow("Package", packageName)
                    ContainerInfoRow("Clone ID", cloneId)
                    ContainerInfoRow("Target SDK", "${diag?.androidVersion ?: "N/A"}")
                    ContainerInfoRow("Architecture", diag?.supportedAbis?.joinToString(", ") ?: "N/A")
                    ContainerInfoRow("Main Activity", diag?.mainActivity ?: "N/A")
                    ContainerInfoRow("Application Class", diag?.applicationClassName ?: "Default Application")
                    ContainerInfoRow("WebView Suffix", diag?.webViewSuffixResult ?: "mirro_${cloneId.take(8)}")
                    ContainerInfoRow("Storage Root", "/files/virtual/${cloneId.take(8)}...")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostics & Telemetry Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local Runtime Diagnostics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                val fullLog = buildString {
                                    appendLine("=== MIRRO CONTAINER RUNTIME DIAGNOSTICS ===")
                                    appendLine("Clone ID: $cloneId")
                                    appendLine("Package: $packageName")
                                    appendLine("Status: ${result?.status}")
                                    appendLine("Device: ${diag?.deviceModel} (SDK ${diag?.androidVersion})")
                                    appendLine("APK Path: ${diag?.apkPath}")
                                    appendLine("Splits: ${diag?.splitCount}")
                                    appendLine("Main Activity: ${diag?.mainActivity}")
                                    appendLine("Classloader: ${diag?.classloaderResult}")
                                    appendLine("Resources: ${diag?.resourcesResult}")
                                    appendLine("Application: ${diag?.applicationInitResult}")
                                    appendLine("WebView Suffix: ${diag?.webViewSuffixResult}")
                                    appendLine("Logs:")
                                    diag?.logs?.forEach { appendLine(" - $it") }
                                    if (diag?.errorStackTrace != null) {
                                        appendLine("Stacktrace:\n${diag.errorStackTrace}")
                                    }
                                }
                                onCopyLogs(fullLog)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ContainerInfoRow("ClassLoader", diag?.classloaderResult ?: "N/A")
                    ContainerInfoRow("Resources Asset", diag?.resourcesResult ?: "N/A")
                    ContainerInfoRow("Application Life", diag?.applicationInitResult ?: "N/A")

                    if (diag?.errorStackTrace != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Exception Trace:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = diag.errorStackTrace,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showRawLogs = !showRawLogs },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showRawLogs) "Hide Execution Logs" else "Show Execution Logs (${diag?.logs?.size ?: 0})")
                    }

                    if (showRawLogs && diag != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            diag.logs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Return to Mirro")
            }
        }
    }
}

@Composable
private fun ContainerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
