package com.rootfix.app.ui.modules

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootfix.app.data.model.InstallProgress
import com.rootfix.app.data.model.InstallStage
import com.rootfix.app.data.model.MagiskModule
import com.rootfix.app.data.model.TrackedModule
import com.rootfix.app.data.repository.MagiskRepository
import com.rootfix.app.data.repository.ModuleUpdateRepository
import com.rootfix.app.ui.extra.RootFixGlassCard
import com.rootfix.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ModulesTab {
    INSTALLED,
    UPDATES_REPOSITORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    magiskRepo: MagiskRepository,
    updateRepo: ModuleUpdateRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { updateRepo ?: ModuleUpdateRepository(context, magiskRepo) }

    var selectedTab by remember { mutableStateOf(ModulesTab.INSTALLED) }
    var installedModules by remember { mutableStateOf<List<MagiskModule>>(emptyList()) }
    var trackedModules by remember { mutableStateOf<List<TrackedModule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    // Dialog & Installation state
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoInput by remember { mutableStateOf("") }
    var isAddingRepo by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf<InstallProgress?>(null) }
    var showInstallSheet by remember { mutableStateOf(false) }

    // Module Action execution state
    var actionModalTitle by remember { mutableStateOf("") }
    var actionLogs by remember { mutableStateOf("") }
    var isActionRunning by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }

    // Magisk Zygisk state
    var isMagiskZygiskEnabled by remember { mutableStateOf(false) }
    var isTogglingZygisk by remember { mutableStateOf(false) }

    // Instant module removal confirmation dialog state
    var modulePendingRemoval by remember { mutableStateOf<MagiskModule?>(null) }
    var isRemovingModule by remember { mutableStateOf(false) }

    fun triggerModuleAction(moduleId: String, moduleName: String) {
        scope.launch {
            actionModalTitle = "$moduleName Action"
            actionLogs = "Executing `sh /data/adb/modules/$moduleId/action.sh` under root shell...\n\n"
            isActionRunning = true
            showActionDialog = true

            val (success, logs) = magiskRepo.executeModuleAction(moduleId)
            isActionRunning = false
            actionLogs = if (logs.isNotBlank()) logs else if (success) "Action script completed successfully (exit code 0)." else "Action script finished with non-zero exit code."
        }
    }

    fun refreshInstalled() {
        scope.launch {
            isLoading = true
            installedModules = magiskRepo.getInstalledModules()
            trackedModules = repo.getTrackedModules()
            isMagiskZygiskEnabled = magiskRepo.isMagiskZygiskEnabled()
            isLoading = false
        }
    }

    fun checkForUpdates() {
        scope.launch {
            isCheckingUpdates = true
            Toast.makeText(context, "Checking module repositories for updates...", Toast.LENGTH_SHORT).show()
            trackedModules = repo.checkAllUpdates()
            isCheckingUpdates = false
            val updateCount = trackedModules.count { it.hasUpdate }
            if (updateCount > 0) {
                Toast.makeText(context, "$updateCount module update(s) available!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "All tracked modules are up to date.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startInstallOrUpdate(module: TrackedModule) {
        scope.launch {
            showInstallSheet = true
            installProgress = InstallProgress(InstallStage.FETCHING_RELEASE, module.name)

            // If latest release isn't fetched yet, fetch it first
            val targetModule = if (module.latestRelease?.primaryZipAsset == null) {
                repo.checkModuleUpdate(module)
            } else {
                module
            }

            if (targetModule.latestRelease?.primaryZipAsset == null) {
                installProgress = InstallProgress(
                    stage = InstallStage.FAILED,
                    moduleName = module.name,
                    errorMessage = "No downloadable .zip release asset available."
                )
                return@launch
            }

            repo.downloadAndInstallModule(targetModule) { progress ->
                installProgress = progress
            }

            // Refresh modules list after installation completes
            installedModules = magiskRepo.getInstalledModules()
            trackedModules = repo.getTrackedModules()
        }
    }

    LaunchedEffect(Unit) {
        refreshInstalled()
    }

    val availableUpdates = trackedModules.filter { it.hasUpdate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magisk Modules") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(
                        onClick = { checkForUpdates() },
                        enabled = !isCheckingUpdates
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Check Updates", tint = AccentCyan)
                        }
                    }
                    IconButton(onClick = { showAddRepoDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Tracked Repo", tint = PrimaryEmerald)
                    }
                    IconButton(onClick = { refreshInstalled() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
            ) {
                Tab(
                    selected = selectedTab == ModulesTab.INSTALLED,
                    onClick = { selectedTab = ModulesTab.INSTALLED },
                    text = {
                        Text("Installed (${installedModules.size})", fontWeight = FontWeight.SemiBold)
                    }
                )
                Tab(
                    selected = selectedTab == ModulesTab.UPDATES_REPOSITORY,
                    onClick = { selectedTab = ModulesTab.UPDATES_REPOSITORY },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Updates & Repo", fontWeight = FontWeight.SemiBold)
                            if (availableUpdates.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DangerRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${availableUpdates.size}",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                ModulesTab.INSTALLED -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Magisk Built-in Zygisk Toggle Card
                        item {
                            RootFixGlassCard(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isMagiskZygiskEnabled) PrimaryEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Extension,
                                                contentDescription = null,
                                                tint = if (isMagiskZygiskEnabled) PrimaryEmerald else TextSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Magisk Built-in Zygisk",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isMagiskZygiskEnabled) "Status: Enabled (Turn OFF to use Zygisk Next)" else "Status: Disabled (Required for Zygisk Next)",
                                                fontSize = 11.sp,
                                                color = if (isMagiskZygiskEnabled) AccentCyan else TextSecondary
                                            )
                                        }
                                    }

                                    if (isTogglingZygisk) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Switch(
                                            checked = isMagiskZygiskEnabled,
                                            onCheckedChange = { newState ->
                                                scope.launch {
                                                    isTogglingZygisk = true
                                                    val success = magiskRepo.setMagiskZygiskEnabled(newState)
                                                    if (success) {
                                                        isMagiskZygiskEnabled = newState
                                                        Toast.makeText(
                                                            context,
                                                            "Magisk Zygisk set to ${if (newState) "ON" else "OFF"}. Reboot device to apply.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to toggle Magisk Zygisk", Toast.LENGTH_SHORT).show()
                                                    }
                                                    isTogglingZygisk = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Toggle changes take effect on reboot. Removing a module deletes it immediately.",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        if (isLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (installedModules.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "No Magisk modules found in /data/adb/modules", color = TextSecondary)
                                }
                            }
                        } else {
                            items(installedModules) { module ->
                                var isEnabled by remember(module.id, module.isEnabled) { mutableStateOf(module.isEnabled) }
                                var isRemovePending by remember(module.id, module.isRemovePending) { mutableStateOf(module.isRemovePending) }

                                val trackedMatch = trackedModules.firstOrNull { it.id.equals(module.id, ignoreCase = true) }

                                RootFixGlassCard(
                                    tint = if (isRemovePending) DangerRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        // Update Banner if available
                                        if (trackedMatch?.hasUpdate == true && trackedMatch.latestRelease != null) {
                                            Surface(
                                                color = WarningAmber.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.ArrowCircleUp, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Update available: ${trackedMatch.latestRelease.tagName}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = WarningAmber
                                                        )
                                                    }
                                                    Button(
                                                        onClick = { startInstallOrUpdate(trackedMatch) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text("Update", fontSize = 11.sp, color = DarkBackground, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = module.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isRemovePending) DangerRed else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${module.version} • by ${module.author}",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }

                                            Switch(
                                                checked = isEnabled && !isRemovePending,
                                                enabled = !isRemovePending,
                                                onCheckedChange = { checkState ->
                                                    isEnabled = checkState
                                                    scope.launch {
                                                        magiskRepo.setModuleEnabled(module.id, checkState)
                                                        Toast.makeText(
                                                            context,
                                                            "${module.name} ${if (checkState) "enabled" else "disabled"}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            )
                                        }

                                        if (module.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = module.description,
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "ID: ${module.id}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (module.hasAction && !isRemovePending) {
                                                    OutlinedButton(
                                                        onClick = { triggerModuleAction(module.id, module.name) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Action", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }

                                                if (module.isRemovePending) {
                                                    TextButton(
                                                        onClick = {
                                                            scope.launch {
                                                                magiskRepo.setModuleRemoval(module.id, false)
                                                                Toast.makeText(context, "Removal undone", Toast.LENGTH_SHORT).show()
                                                                refreshInstalled()
                                                            }
                                                        }
                                                    ) {
                                                        Text("Undo", color = PrimaryEmerald, fontSize = 12.sp)
                                                    }
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }

                                                TextButton(
                                                    onClick = {
                                                        modulePendingRemoval = module
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteForever,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = DangerRed
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = if (module.isRemovePending) "Remove Now" else "Uninstall",
                                                        color = DangerRed,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ModulesTab.UPDATES_REPOSITORY -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Obtainium-style Action Card
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
                                        Column {
                                            Text(
                                                text = "Autonomous Module Updates",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Tracks upstream GitHub releases & update.json directly",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { checkForUpdates() },
                                            modifier = Modifier.weight(1f),
                                            enabled = !isCheckingUpdates,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Check All Updates", fontSize = 13.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { showAddRepoDialog = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Track Repo", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Section 1: Available Updates (if any)
                        if (availableUpdates.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Available Updates (${availableUpdates.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                            }

                            items(availableUpdates) { module ->
                                val release = module.latestRelease
                                RootFixGlassCard(
                                    shape = RoundedCornerShape(14.dp),
                                    tint = WarningAmber.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = module.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "Installed: ${module.installedVersion ?: "None"}  ➜  Latest: ${release?.tagName ?: "Unknown"}",
                                                    fontSize = 13.sp,
                                                    color = WarningAmber,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (module.githubSlug != null) {
                                                    Text(
                                                        text = "Source: github.com/${module.githubSlug}",
                                                        fontSize = 11.sp,
                                                        color = TextSecondary
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { startInstallOrUpdate(module) },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Update", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (!release?.releaseNotes.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Surface(
                                                color = DarkCard,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "Changelog:",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AccentCyan
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = release?.releaseNotes?.take(300) ?: "",
                                                        fontSize = 11.sp,
                                                        color = TextSecondary,
                                                        lineHeight = 15.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Catalog & Tracked Modules
                        item {
                            Text(
                                text = "Tracked & Curated Modules Catalog",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        }

                        items(trackedModules) { module ->
                            RootFixGlassCard(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = module.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = if (module.isInstalled) PrimaryEmerald.copy(alpha = 0.2f) else DarkCard,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (module.isInstalled) "Installed" else "Not Installed",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (module.isInstalled) PrimaryEmerald else TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${module.category} • by ${module.author}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (module.isInstalled && module.hasAction) {
                                                OutlinedButton(
                                                    onClick = { triggerModuleAction(module.id, module.name) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Action", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }

                                            Button(
                                                onClick = { startInstallOrUpdate(module) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (module.hasUpdate) WarningAmber else MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (module.hasUpdate) "Update" else if (module.isInstalled) "Reinstall" else "Install",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (module.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = module.description,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (module.githubSlug != null) "Repo: ${module.githubSlug}" else "ID: ${module.id}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )

                                        if (module.isCustom) {
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        repo.removeCustomTrackedModule(module.id)
                                                        trackedModules = repo.getTrackedModules()
                                                        Toast.makeText(context, "Untracked ${module.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("Untrack", color = DangerRed, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        // Dialog: Add Custom GitHub Repo / Module URL
        if (showAddRepoDialog) {
            AlertDialog(
                onDismissRequest = { if (!isAddingRepo) showAddRepoDialog = false },
                title = { Text("Track Module Repository") },
                text = {
                    Column {
                        Text(
                            text = "Track upstream GitHub releases directly (Obtainium style). Enter 'owner/repo' or full URL:",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newRepoInput,
                            onValueChange = { newRepoInput = it },
                            placeholder = { Text("e.g. 5ec1cff/TrickyStore or https://github.com/...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Examples:\n• 5ec1cff/TrickyStore (Keybox Attestation)\n• Dr-TSNG/ZygiskNext\n• j-hc/zygisk-detach",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isAddingRepo = true
                                val (success, message) = repo.addCustomTrackedModule(newRepoInput)
                                isAddingRepo = false
                                if (success) {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    showAddRepoDialog = false
                                    newRepoInput = ""
                                    trackedModules = repo.getTrackedModules()
                                } else {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = newRepoInput.isNotBlank() && !isAddingRepo
                    ) {
                        if (isAddingRepo) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Track & Verify")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddRepoDialog = false },
                        enabled = !isAddingRepo
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Installation Progress Bottom Sheet / Dialog
        if (showInstallSheet && installProgress != null) {
            val progress = installProgress!!
            AlertDialog(
                onDismissRequest = {
                    if (progress.stage == InstallStage.COMPLETED || progress.stage == InstallStage.FAILED) {
                        showInstallSheet = false
                    }
                },
                title = {
                    Text(
                        text = when (progress.stage) {
                            InstallStage.FETCHING_RELEASE -> "Checking Release..."
                            InstallStage.DOWNLOADING -> "Downloading ${progress.moduleName}"
                            InstallStage.INSTALLING -> "Flashing via Magisk..."
                            InstallStage.COMPLETED -> "Installation Successful!"
                            InstallStage.FAILED -> "Installation Failed"
                            else -> "Installing..."
                        }
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (progress.stage) {
                            InstallStage.DOWNLOADING -> {
                                LinearProgressIndicator(
                                    progress = { progress.progressPercent },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = PrimaryEmerald
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val mbDownloaded = progress.downloadedBytes / (1024f * 1024f)
                                val mbTotal = progress.totalBytes / (1024f * 1024f)
                                Text(
                                    text = "%.1f MB / %.1f MB (%.0f%%)".format(mbDownloaded, mbTotal, progress.progressPercent * 100),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            InstallStage.INSTALLING -> {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Executing `magisk --install-module` under root shell...",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            InstallStage.COMPLETED -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${progress.moduleName} installed successfully. Please reboot to activate changes.",
                                        fontSize = 13.sp,
                                        color = PrimaryEmerald
                                    )
                                }
                            }
                            InstallStage.FAILED -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = progress.errorMessage ?: "Unknown installation error",
                                        fontSize = 13.sp,
                                        color = DangerRed
                                    )
                                }
                            }
                            else -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }

                        // Terminal Log Viewer
                        if (progress.installLogs.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Magisk Output Log:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = progress.installLogs,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AccentCyan,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (progress.stage == InstallStage.COMPLETED || progress.stage == InstallStage.FAILED) {
                        Button(
                            onClick = { showInstallSheet = false }
                        ) {
                            Text("Done")
                        }
                    }
                }
            )
        }

        // Module Action Terminal Dialog
        if (showActionDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isActionRunning) showActionDialog = false
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(actionModalTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isActionRunning) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = PrimaryEmerald
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = actionLogs,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentCyan,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showActionDialog = false },
                        enabled = !isActionRunning
                    ) {
                        Text(if (isActionRunning) "Running..." else "Done")
                    }
                }
            )
        }

        // Instant Module Removal Confirmation Dialog
        if (modulePendingRemoval != null) {
            val mod = modulePendingRemoval!!
            AlertDialog(
                onDismissRequest = {
                    if (!isRemovingModule) modulePendingRemoval = null
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Module?", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Are you sure you want to permanently remove \"${mod.name}\"?",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "This will immediately delete the module files from /data/adb/modules without waiting for a reboot.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        if (isRemovingModule) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = DangerRed
                                )
                                Text("Removing module files...", fontSize = 12.sp, color = DangerRed)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isRemovingModule = true
                                val success = magiskRepo.removeModuleImmediately(mod.id)
                                isRemovingModule = false
                                modulePendingRemoval = null
                                if (success) {
                                    Toast.makeText(context, "${mod.name} removed successfully.", Toast.LENGTH_SHORT).show()
                                    refreshInstalled()
                                } else {
                                    Toast.makeText(context, "Failed to remove ${mod.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isRemovingModule,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Remove Now", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { modulePendingRemoval = null },
                        enabled = !isRemovingModule
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
