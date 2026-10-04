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
import com.rootfix.app.data.repository.GoogleServicesRepository
import com.rootfix.app.data.repository.GooglePackageInfo
import com.rootfix.app.service.PifSyncWorker
import com.rootfix.app.ui.theme.*
import com.rootfix.app.ui.extra.RootFixGlassCard
import androidx.compose.foundation.BorderStroke
import com.rootfix.app.data.repository.ModuleUpdateRepository
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    magiskRepo: MagiskRepository,
    pifRepo: PifRepository,
    updateRepo: ModuleUpdateRepository? = null,
    googleRepo: GoogleServicesRepository = remember { GoogleServicesRepository() },
    onNavigateToPif: () -> Unit,
    onNavigateToModules: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modUpdateRepo = remember { updateRepo ?: ModuleUpdateRepository(context, magiskRepo) }

    var rootStatus by remember { mutableStateOf(RootStatus()) }
    var activePif by remember { mutableStateOf<PifProfile?>(null) }
    var moduleCount by remember { mutableIntStateOf(0) }
    var availableUpdatesCount by remember { mutableIntStateOf(0) }
    var isAutoSyncEnabled by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isRestartingGms by remember { mutableStateOf(false) }

    val defaultGooglePackages = remember {
        listOf(
            GooglePackageInfo("com.google.android.gms", "Play Services", true),
            GooglePackageInfo("com.android.vending", "Play Store", true),
            GooglePackageInfo("com.google.android.gsf", "Services Framework", true)
        )
    }
    var googlePackages by remember { mutableStateOf(defaultGooglePackages) }
    var selectedPkgs by remember { mutableStateOf(setOf("com.google.android.gms", "com.android.vending", "com.google.android.gsf")) }
    var isClearingCache by remember { mutableStateOf(false) }
    var isClearingData by remember { mutableStateOf(false) }
    var showConfirmWipeDialog by remember { mutableStateOf(false) }

    fun refreshAll() {
        scope.launch {
            isLoading = true
            rootStatus = magiskRepo.getRootStatus()
            activePif = pifRepo.getActiveProfile()
            val modules = magiskRepo.getInstalledModules()
            moduleCount = modules.size
            googlePackages = googleRepo.getInstalledGooglePackages()
            try {
                val tracked = modUpdateRepo.getTrackedModules()
                availableUpdatesCount = tracked.count { it.hasUpdate }
            } catch (ignored: Exception) {}
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
            RootFixGlassCard(
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
            RootFixGlassCard(
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

            // Google Services Data & Cache Card
            RootFixGlassCard(
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
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Google Services Reset",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Clear cache & attestation tokens",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Warning Box with Warning Sign
                    Surface(
                        color = WarningAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = WarningAmber,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Warning: Wiping Google Play Services data resets device attestation tokens and Play Store caches. Your Google account and Wallet will resync on next launch.",
                                fontSize = 12.sp,
                                color = WarningAmber,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Target Services:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        googlePackages.filter { it.isInstalled }.forEach { pkgInfo ->
                            val isSel = selectedPkgs.contains(pkgInfo.packageName)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedPkgs = if (isSel) {
                                        selectedPkgs - pkgInfo.packageName
                                    } else {
                                        selectedPkgs + pkgInfo.packageName
                                    }
                                },
                                label = { Text(pkgInfo.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (selectedPkgs.isEmpty()) {
                                    Toast.makeText(context, "Select at least one service", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    isClearingCache = true
                                    val res = googleRepo.clearCache(selectedPkgs.toList())
                                    isClearingCache = false
                                    Toast.makeText(
                                        context,
                                        if (res) "Google cache cleared & GMS reloaded" else "Failed to clear cache",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isClearingCache && !isClearingData
                        ) {
                            if (isClearingCache) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Cache", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                if (selectedPkgs.isEmpty()) {
                                    Toast.makeText(context, "Select at least one service", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                showConfirmWipeDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isClearingCache && !isClearingData
                        ) {
                            if (isClearingData) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wipe Data", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Autonomous Sync Card
            RootFixGlassCard(
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
            RootFixGlassCard(
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Installed Modules", fontWeight = FontWeight.SemiBold)
                                if (availableUpdatesCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = WarningAmber.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "$availableUpdatesCount UPDATE${if (availableUpdatesCount > 1) "S" else ""}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (availableUpdatesCount > 0) "$moduleCount installed • Update available!" else "$moduleCount modules active in /data/adb/modules",
                                fontSize = 12.sp,
                                color = if (availableUpdatesCount > 0) WarningAmber else TextSecondary
                            )
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

    // Confirmation Dialog for Wiping Data
    if (showConfirmWipeDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmWipeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Wipe Google Services Data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will completely reset data for: ${selectedPkgs.joinToString(", ")}.\n\nPlay Integrity tokens, Google Play caches, and local attestation state will be wiped clean. Google account sync and contactless cards will briefly resync. Do you want to proceed?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmWipeDialog = false
                        scope.launch {
                            isClearingData = true
                            val res = googleRepo.clearFullData(selectedPkgs.toList())
                            isClearingData = false
                            Toast.makeText(
                                context,
                                if (res) "Google Services data wiped successfully" else "Failed to wipe data",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Wipe Data Now", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmWipeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
