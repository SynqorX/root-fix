package com.rootfix.app.ui.modules

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootfix.app.data.model.MagiskModule
import com.rootfix.app.data.repository.MagiskRepository
import com.rootfix.app.ui.theme.*
import com.rootfix.app.ui.extra.RootFixGlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    magiskRepo: MagiskRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var modules by remember { mutableStateOf<List<MagiskModule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            isLoading = true
            modules = magiskRepo.getInstalledModules()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magisk Modules") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            text = "Module toggle and removal changes take effect upon the next reboot.",
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
            } else if (modules.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No Magisk modules found in /data/adb/modules", color = TextSecondary)
                    }
                }
            } else {
                items(modules) { module ->
                    var isEnabled by remember(module.id, module.isEnabled) { mutableStateOf(module.isEnabled) }
                    var isRemovePending by remember(module.id, module.isRemovePending) { mutableStateOf(module.isRemovePending) }

                    RootFixGlassCard(
                        tint = if (isRemovePending) DangerRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
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

                                TextButton(
                                    onClick = {
                                        val newState = !isRemovePending
                                        isRemovePending = newState
                                        scope.launch {
                                            magiskRepo.setModuleRemoval(module.id, newState)
                                            Toast.makeText(
                                                context,
                                                if (newState) "${module.name} marked for removal" else "Removal undone",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (isRemovePending) "Undo Removal" else "Uninstall",
                                        color = if (isRemovePending) PrimaryEmerald else DangerRed,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
