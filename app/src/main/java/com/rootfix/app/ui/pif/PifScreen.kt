package com.rootfix.app.ui.pif

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.rootfix.app.data.repository.AutoPifDevice
import com.rootfix.app.data.repository.PifRepository
import com.rootfix.app.ui.theme.*
import com.rootfix.app.ui.extra.RootFixGlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PifScreen(
    pifRepo: PifRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeProfile by remember { mutableStateOf<PifProfile?>(null) }
    var availableProfiles by remember { mutableStateOf<List<PifProfile>>(emptyList()) }
    var autoPifDevices by remember { mutableStateOf<List<AutoPifDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<AutoPifDevice?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRunningAutoPif by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            isLoading = true
            activeProfile = pifRepo.getActiveProfile()
            availableProfiles = pifRepo.getAvailableProfiles(fetchRemote = false)
            autoPifDevices = pifRepo.getAutoPifDevices()
            if (autoPifDevices.isNotEmpty() && selectedDevice == null) {
                selectedDevice = autoPifDevices.firstOrNull { it.model.contains("Pixel 9") } ?: autoPifDevices.first()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Play Integrity Fix Autopilot") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Active Configuration
            item {
                Text(
                    text = "Active Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            item {
                RootFixGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (activeProfile != null) {
                            val prof = activeProfile!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${prof.manufacturer} ${prof.model}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryEmerald
                                )

                                IconButton(onClick = { showEditDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = AccentCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "FINGERPRINT", fontSize = 10.sp, color = TextSecondary)
                            Text(
                                text = prof.fingerprint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(text = "Security Patch", fontSize = 10.sp, color = TextSecondary)
                                    Text(text = prof.securityPatch, fontSize = 12.sp)
                                }
                                Column {
                                    Text(text = "Target Path", fontSize = 10.sp, color = TextSecondary)
                                    Text(text = prof.sourcePath, fontSize = 12.sp, color = TextSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))

                            // 7 Spoof Badges
                            Text(text = "Enforced Spoofs (All 7 Required)", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "Build" to prof.spoofBuild,
                                    "Props" to prof.spoofProps,
                                    "Provider" to prof.spoofProvider,
                                    "Signature" to prof.spoofSignature,
                                    "VendingBuild" to prof.spoofVendingBuild,
                                    "VendingSDK" to prof.spoofVendingSdk,
                                    "DEBUG" to prof.debug
                                ).forEach { (name, enabled) ->
                                    Surface(
                                        color = if (enabled) PrimaryEmerald.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (enabled) PrimaryEmerald else DangerRed,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(text = "No profile active in /data/adb/pif.prop", color = WarningAmber)
                        }
                    }
                }
            }

            // Section 2: AutoPIF Engine
            item {
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
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AutoPIF Canary Engine",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Queries Google's FlashStation Canary releases, generates verified Pixel Canary fingerprints, and enforces all 7 spoofs automatically.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (autoPifDevices.isNotEmpty()) {
                            Text(text = "Select Canary Target Device:", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(autoPifDevices) { dev ->
                                    val isDevSelected = selectedDevice?.product == dev.product
                                    FilterChip(
                                        selected = isDevSelected,
                                        onClick = { selectedDevice = dev },
                                        label = { Text(dev.model, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isRunningAutoPif = true
                                    val (success, newProfile) = pifRepo.runAutoPif(
                                        cacheDir = context.cacheDir,
                                        device = selectedDevice,
                                        restartGmsNow = true
                                    )
                                    isRunningAutoPif = false
                                    if (success && newProfile != null) {
                                        activeProfile = newProfile
                                        Toast.makeText(
                                            context,
                                            "AutoPIF applied ${newProfile.model} with all 7 spoofs!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "AutoPIF execution failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isRunningAutoPif
                        ) {
                            if (isRunningAutoPif) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Running AutoPIF Canary Engine...", color = DarkBackground)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run AutoPIF & Enforce All 7 Spoofs", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 3: Tested Presets
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verified Presets (All 7 Spoofs)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap to apply",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            items(availableProfiles) { profile ->
                val isSelected = activeProfile?.fingerprint == profile.fingerprint
                RootFixGlassCard(
                    tint = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                isApplying = true
                                val success = pifRepo.applyProfile(
                                    cacheDir = context.cacheDir,
                                    profile = profile.withAllSpoofsEnabled(),
                                    restartGmsNow = true
                                )
                                if (success) {
                                    activeProfile = profile.withAllSpoofsEnabled()
                                    Toast.makeText(context, "Applied ${profile.name} & reloaded GMS", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to apply profile", Toast.LENGTH_SHORT).show()
                                }
                                isApplying = false
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isSelected) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = PrimaryEmerald.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = PrimaryEmerald,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile.fingerprint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isSelected) PrimaryEmerald else TextSecondary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Manual Edit Dialog
    if (showEditDialog && activeProfile != null) {
        var editFp by remember { mutableStateOf(activeProfile!!.fingerprint) }
        var editModel by remember { mutableStateOf(activeProfile!!.model) }
        var editMfr by remember { mutableStateOf(activeProfile!!.manufacturer) }
        var editSecPatch by remember { mutableStateOf(activeProfile!!.securityPatch) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit PIF Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editFp,
                        onValueChange = { editFp = it },
                        label = { Text("Fingerprint") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editMfr,
                        onValueChange = { editMfr = it },
                        label = { Text("Manufacturer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSecPatch,
                        onValueChange = { editSecPatch = it },
                        label = { Text("Security Patch") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "All 7 spoofs (Build, Props, Provider, Signature, VendingBuild, VendingSDK, DEBUG) will be automatically enforced.",
                        fontSize = 11.sp,
                        color = PrimaryEmerald
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = activeProfile!!.copy(
                            fingerprint = editFp.trim(),
                            manufacturer = editMfr.trim(),
                            model = editModel.trim(),
                            securityPatch = editSecPatch.trim()
                        ).withAllSpoofsEnabled()

                        scope.launch {
                            val saved = pifRepo.applyProfile(context.cacheDir, updated, restartGmsNow = true)
                            if (saved) {
                                activeProfile = updated
                                Toast.makeText(context, "Saved & GMS reloaded", Toast.LENGTH_SHORT).show()
                            }
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
