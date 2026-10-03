package com.rootfix.app.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootfix.app.data.model.PifProfile
import com.rootfix.app.data.model.RootStatus
import com.rootfix.app.data.repository.MagiskRepository
import com.rootfix.app.data.repository.PifRepository
import com.rootfix.app.service.PifSyncWorker
import com.rootfix.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    magiskRepo: MagiskRepository,
    pifRepo: PifRepository,
    onNavigateToPif: () -> Unit,
    onNavigateToModules: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootStatus by remember { mutableStateOf(RootStatus()) }
    var activePif by remember { mutableStateOf<PifProfile?>(null) }
    var moduleCount by remember { mutableIntStateOf(0) }
    var isAutoSyncEnabled by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isRestartingGms by remember { mutableStateOf(false) }

    fun refreshAll() {
        scope.launch {
            isLoading = true
            rootStatus = magiskRepo.getRootStatus()
            activePif = pifRepo.getActiveProfile()
            val modules = magiskRepo.getInstalledModules()
            moduleCount = modules.size
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RootFix Autopilot",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Automated Magisk & Play Integrity Engine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = { refreshAll() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Root & System Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
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
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (rootStatus.isRootGranted) PrimaryEmerald else DangerRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (rootStatus.isRootGranted) "Root Access Granted" else "Root Not Detected",
                                fontWeight = FontWeight.SemiBold,
                                color = if (rootStatus.isRootGranted) PrimaryEmerald else DangerRed
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "SELinux: ${rootStatus.seLinuxMode}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "Device Model", fontSize = 12.sp, color = TextSecondary)
                            Text(text = rootStatus.deviceModel, fontWeight = FontWeight.Medium)
                        }
                        Column {
                            Text(text = "Android OS", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "Android ${rootStatus.androidVersion} (API ${rootStatus.apiLevel})", fontWeight = FontWeight.Medium)
                        }
                        Column {
                            Text(text = "Magisk Version", fontSize = 12.sp, color = TextSecondary)
                            Text(text = rootStatus.magiskVersion, fontWeight = FontWeight.Medium, color = AccentCyan)
                        }
                    }
                }
            }

            // Active PIF Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
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
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Play Integrity Fingerprint",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(onClick = onNavigateToPif) {
                            Text(text = "Manage", color = AccentCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (activePif != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Spoofing: ${activePif!!.manufacturer} ${activePif!!.model}",
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryEmerald
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activePif!!.fingerprint,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Security Patch: ${activePif!!.securityPatch}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No active /data/adb/pif.prop detected.",
                            color = WarningAmber,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isRestartingGms = true
                                val res = pifRepo.restartGms()
                                isRestartingGms = false
                                Toast.makeText(
                                    context,
                                    if (res) "Google Play Services reloaded" else "Failed to restart GMS",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isRestartingGms
                    ) {
                        if (isRestartingGms) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Restarting GMS...")
                        } else {
                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Hot-Reload Play Services (No Reboot)")
                        }
                    }
                }
            }

            // Autonomous Sync Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Autonomous Fingerprint Sync",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Checks tested community fingerprints periodically and auto-applies them without user intervention.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isAutoSyncEnabled,
                            onCheckedChange = { enabled ->
                                isAutoSyncEnabled = enabled
                                if (enabled) {
                                    PifSyncWorker.schedulePeriodicSync(context, intervalHours = 12)
                                    Toast.makeText(context, "Autonomous Sync Enabled (12h interval)", Toast.LENGTH_SHORT).show()
                                } else {
                                    PifSyncWorker.cancelPeriodicSync(context)
                                    Toast.makeText(context, "Autonomous Sync Disabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Magisk Modules Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Installed Modules", fontWeight = FontWeight.SemiBold)
                            Text(text = "$moduleCount modules active in /data/adb/modules", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToModules,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "View")
                    }
                }
            }
        }
    }
}
