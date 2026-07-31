package com.bhavani.barcodeprinter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.bhavani.barcodeprinter.data.PurgeWorker
import com.bhavani.barcodeprinter.ui.components.CrashLogDialog
import com.bhavani.barcodeprinter.ui.screens.*
import com.bhavani.barcodeprinter.ui.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        PurgeWorker.schedule(applicationContext)

        val crashLog = CrashHandler.readCrashLog(applicationContext)

        lifecycleScope.launch {
            val db = AppContainer.db
            if (db.prnHistoryDao().all().first().isEmpty()) {
                val current = AppContainer.settings.currentPrnTemplate()
                db.prnHistoryDao().insert(
                    com.bhavani.barcodeprinter.data.PrnHistoryEntry(content = current, source = "initial")
                )
            }
        }

        setContent {
            BhavaniBarcodePrinterTheme {
                Surface(color = Bg) {
                    RootNav()

                    var showCrashDialog by remember { mutableStateOf(crashLog != null) }
                    if (showCrashDialog && crashLog != null) {
                        CrashLogDialog(
                            log = crashLog,
                            onDismiss = {
                                CrashHandler.clearCrashLog(applicationContext)
                                showCrashDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class TopLevelDest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val topLevelDests = listOf(
    TopLevelDest("print", "Print", Icons.Filled.Home),
    TopLevelDest("database", "Database", Icons.AutoMirrored.Filled.List),
    TopLevelDest("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun RootNav() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = EntryBg) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                topLevelDests.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold, selectedTextColor = Gold,
                            unselectedIconColor = Fg, unselectedTextColor = Fg,
                            indicatorColor = Accent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).background(Bg)) {
            NavHost(navController = navController, startDestination = "print") {
                composable("print") { PrintScreen(navController, itemId = null) }
                composable(
                    "print/{itemId}",
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                    PrintScreen(navController, itemId = if (itemId <= 0) null else itemId)
                }
                composable("database") { DatabaseScreen(navController) }
                composable("recycleBin") { RecycleBinScreen(navController) }
                composable("settings") { SettingsScreen(navController) }
                composable("schemaEditor") { SchemaEditorScreen(navController) }
                composable("labelDesigner") { LabelDesignerScreen(navController) }
                composable("rawPrnEditor") { RawPrnEditorScreen(navController) }
                composable("prnHistory") { PrnHistoryScreen(navController) }
                composable("printHistory") { PrintHistoryScreen(navController) }
            }
        }
    }
}
