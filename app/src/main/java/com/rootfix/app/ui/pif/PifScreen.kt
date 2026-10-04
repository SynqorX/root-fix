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
import com.rootfix.app.data.model.KeyboxStatus
import com.rootfix.app.data.model.PifProfile
import com.rootfix.app.data.repository.AutoPifDevice
import com.rootfix.app.data.repository.KeyboxRepository
import com.rootfix.app.data.repository.PifRepository
import com.rootfix.app.ui.extra.RootFixGlassCard
import com.rootfix.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PifScreen(
    pifRepo: PifRepository,
    keyboxRepo: KeyboxRepository = remember { KeyboxRepository() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeProfile by remember { mutableStateOf<PifProfile?>(null) }
    var availableProfiles by remember { mutableStateOf<List<PifProfile>>(emptyList()) }
    var autoPifDevices by remember { mutableStateOf<List<AutoPifDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<AutoPifDevice?>(null) }
    var keyboxStatus by remember { mutableStateOf<KeyboxStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRunningAutoPif by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Keybox dialogs
    var showKeyboxDialog by remember { mutableStateOf(false) }
    var keyboxXmlInput by remember { mutableStateOf("") }
    var isDeployingKeybox by remember { mutableStateOf(false) }
    var showTargetsDialog by remember { mutableStateOf(false) }
    var targetsInput by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            isLoading = true
            activeProfile = pifRepo.getActiveProfile()
            availableProfiles = pifRepo.getAvailableProfiles(fetchRemote = false)
            autoPifDevices = pifRepo.getAutoPifDevices()
            if (autoPifDevices.isNotEmpty() && selectedDevice == null) {
                selectedDevice = autoPifDevices.firstOrNull { it.model.contains("Pixel 9") } ?: autoPifDevices.first()
            }
            keyboxStatus = keyboxRepo.getKeyboxStatus()
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
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (activeProfile == null) {
                            Text(text = "No active pif.prop configuration found.", color = TextSecondary)
                        } else {
                            val prof = activeProfile!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${prof.manufacturer} ${prof.model}".ifBlank { prof.name },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Source: ${prof.sourcePath}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }

                                IconButton(onClick = { showEditDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = AccentCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            // 7 Enforced Spoofs Badges
                            Text(
                                text = "Enforced Spoofs (All 7 Required):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val spoofs = listOf(
                                "Build" to prof.spoofBuild,
                                "Props" to prof.spoofProps,
                                "Provider" to prof.spoofProvider,
                                "Signature" to prof.spoofSignature,
                                "VendingBuild" to prof.spoofVendingBuild,
                                "VendingSDK" to prof.spoofVendingSdk,
                                "DEBUG" to prof.debug
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                spoofs.forEach { (name, enabled) ->
                                    Surface(
                                        color = if (enabled) PrimaryEmerald.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (enabled) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (enabled) PrimaryEmerald else DangerRed,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (enabled) PrimaryEmerald else DangerRed
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Decomposed matching build properties
                            Surface(
                                color = DarkCard,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Matching 13-Tuple Build Properties (InfinityX):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "BRAND=${prof.brand} | PRODUCT=${prof.product} | DEVICE=${prof.device}\nRELEASE=${prof.release} | ID=${prof.id} | INCREMENTAL=${prof.incremental}\nPATCH=${prof.securityPatch} | INIT_SDK=${prof.deviceInitialSdkInt}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Autopilot Quick Canary Fetch (AutoPIF)
            item {
                Text(
                    text = "Google FlashStation Canary Autopilot",
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
                        Text(
                            text = "Fetches the latest unreleased Google Pixel Canary release candidate directly from Google's FlashStation API and synchronizes /data/adb/pif.prop with all 7 spoofs enforced.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (autoPifDevices.isNotEmpty()) {
                            Text(
                                text = "Select Target Pixel Device:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(autoPifDevices) { dev ->
                                    val isSelected = selectedDevice?.product == dev.product
                                    Surface(
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable { selectedDevice = dev }
                                    ) {
                                        Text(
                                            text = dev.model,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) AccentCyan else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isRunningAutoPif = true
                                    Toast.makeText(context, "Running AutoPIF Canary fetch...", Toast.LENGTH_SHORT).show()
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
                                            "AutoPIF fetched ${newProfile.model} Canary! 7 spoofs enforced & GMS reloaded.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "AutoPIF failed to fetch Canary build.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isRunningAutoPif,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isRunningAutoPif) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching & Enforcing Spoofs...")
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetch Canary & Auto-Apply")
                            }
                        }
                    }
                }
            }

            // Section 3: Project InfinityX Hardware Attestation & Keybox
            item {
                Text(
                    text = "Project InfinityX Keybox & Hardware Attestation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            item {
                val kStatus = keyboxStatus
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
                                    text = "Keystore Attestation Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Bypasses Keymaster/KeyMint hardware checks via keybox.xml injection",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Surface(
                                color = if (kStatus?.hasKeyboxFile == true) PrimaryEmerald.copy(alpha = 0.2f) else DarkCard,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (kStatus?.hasKeyboxFile == true) "KEYBOX ACTIVE" else "NO KEYBOX",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (kStatus?.hasKeyboxFile == true) PrimaryEmerald else WarningAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Project InfinityX recovers MEETS_DEVICE_INTEGRITY and MEETS_STRONG_INTEGRITY by pairing the 13-tuple build spoof with a certified Keystore keybox. On rooted Android, this is deployed via /data/adb/tricky_store/keybox.xml.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status grid
                        Surface(
                            color = DarkCard,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TrickyStore Engine:", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = if (kStatus?.isTrickyStoreInstalled == true) "Installed (${kStatus.trickyStoreVersion ?: "Active"})" else "Not Installed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (kStatus?.isTrickyStoreInstalled == true) PrimaryEmerald else WarningAmber
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Keybox File:", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = if (kStatus?.hasKeyboxFile == true) "/data/adb/tricky_store/keybox.xml" else "Missing",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (kStatus?.hasKeyboxFile == true) AccentCyan else WarningAmber
                                    )
                                }
                                if (kStatus?.hasKeyboxFile == true) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Key Algorithm:", fontSize = 11.sp, color = TextSecondary)
                                        Text(kStatus.keyAlgorithm ?: "Unknown", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Certificates Chain:", fontSize = 11.sp, color = TextSecondary)
                                        Text("${kStatus.certificateCount} certificates", fontSize = 11.sp, color = TextPrimary)
                                    }
                                    if (kStatus.deviceId != null) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Device ID:", fontSize = 11.sp, color = TextSecondary)
                                            Text(kStatus.deviceId, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                        }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Target Packages:", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = if (kStatus?.targetPackages.isNullOrEmpty()) "Default (GMS, Vending)" else "${kStatus?.targetPackages?.size} active",
                                        fontSize = 11.sp,
                                        color = PrimaryEmerald
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    keyboxXmlInput = kStatus?.rawXml ?: ""
                                    showKeyboxDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (kStatus?.hasKeyboxFile == true) "Manage Keybox" else "Import Keybox", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    targetsInput = kStatus?.targetPackages?.joinToString("\n")
                                        ?: "com.google.android.gms\ncom.android.vending\ngr.nikolasspyr.integritycheck"
                                    showTargetsDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Target Apps", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section 4: Certified Profiles & Presets
            item {
                Text(
                    text = "Certified OEM Release Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
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

    // Keybox XML Dialog
    if (showKeyboxDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeployingKeybox) showKeyboxDialog = false },
            title = { Text("Deploy Keybox XML") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Paste your Android Keybox XML below. It will be validated and deployed to /data/adb/tricky_store/keybox.xml with atomic replacement:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyboxXmlInput,
                        onValueChange = { keyboxXmlInput = it },
                        placeholder = { Text("<?xml version=\"1.0\"?>\n<AndroidAttestation>\n  <Keybox DeviceID=\"...\">\n...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Requires <Keybox>, <PrivateKey>, and <Certificate> tags.",
                        fontSize = 11.sp,
                        color = AccentCyan
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeployingKeybox = true
                            val (success, msg) = keyboxRepo.saveKeyboxXml(context.cacheDir, keyboxXmlInput)
                            isDeployingKeybox = false
                            if (success) {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showKeyboxDialog = false
                                keyboxStatus = keyboxRepo.getKeyboxStatus()
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = keyboxXmlInput.isNotBlank() && !isDeployingKeybox
                ) {
                    if (isDeployingKeybox) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Deploy Keybox")
                }
            },
            dismissButton = {
                Row {
                    if (keyboxStatus?.hasKeyboxFile == true) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    keyboxRepo.deleteKeybox()
                                    Toast.makeText(context, "Keybox deleted", Toast.LENGTH_SHORT).show()
                                    showKeyboxDialog = false
                                    keyboxStatus = keyboxRepo.getKeyboxStatus()
                                }
                            }
                        ) {
                            Text("Delete", color = DangerRed)
                        }
                    }
                    TextButton(onClick = { showKeyboxDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Targets Configuration Dialog
    if (showTargetsDialog) {
        AlertDialog(
            onDismissRequest = { showTargetsDialog = false },
            title = { Text("Target Packages") },
            text = {
                Column {
                    Text(
                        text = "Packages configured to receive Keybox attestation (one package per line in /data/adb/tricky_store/target.txt):",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetsInput,
                        onValueChange = { targetsInput = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val pkgs = targetsInput.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            keyboxRepo.saveTargetPackages(context.cacheDir, pkgs)
                            Toast.makeText(context, "Target packages saved & GMS reloaded", Toast.LENGTH_SHORT).show()
                            showTargetsDialog = false
                            keyboxStatus = keyboxRepo.getKeyboxStatus()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
