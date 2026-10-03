package com.rootfix.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rootfix.app.data.repository.MagiskRepository
import com.rootfix.app.data.repository.PifRepository
import com.rootfix.app.service.PifSyncWorker
import com.rootfix.app.ui.dashboard.DashboardScreen
import com.rootfix.app.ui.modules.ModulesScreen
import com.rootfix.app.ui.pif.PifScreen
import com.rootfix.app.ui.theme.RootFixTheme

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    PIF("PIF Autopilot", Icons.Default.VerifiedUser),
    MODULES("Modules", Icons.Default.Extension)
}

class MainActivity : ComponentActivity() {

    private val magiskRepo = MagiskRepository()
    private val pifRepo = PifRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure autonomous background worker is scheduled
        PifSyncWorker.schedulePeriodicSync(applicationContext, intervalHours = 12)

        setContent {
            RootFixTheme {
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                            ),
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 8.dp
                        ) {
                            Screen.values().forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            Screen.DASHBOARD -> DashboardScreen(
                                magiskRepo = magiskRepo,
                                pifRepo = pifRepo,
                                onNavigateToPif = { currentScreen = Screen.PIF },
                                onNavigateToModules = { currentScreen = Screen.MODULES }
                            )
                            Screen.PIF -> PifScreen(
                                pifRepo = pifRepo
                            )
                            Screen.MODULES -> ModulesScreen(
                                magiskRepo = magiskRepo
                            )
                        }
                    }
                }
            }
        }
    }
}
