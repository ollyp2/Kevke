package de.kevke.servercontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.graphics.vector.ImageVector
import de.kevke.servercontrol.ui.components.LineIcon
import de.kevke.servercontrol.ui.screens.*
import de.kevke.servercontrol.ui.theme.*
import kotlinx.coroutines.launch

private data class MenuEntry(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val MENU = listOf(
    MenuEntry("home", "Start", LineIcons.Power),
    MenuEntry("resources", "Ressourcen", LineIcons.Gauge),
    MenuEntry("billing", "Kosten", LineIcons.Billing),
    MenuEntry("worlds", "Welten", LineIcons.World),
    MenuEntry("give", "Items geben", LineIcons.Item),
    MenuEntry("teleport", "Teleport", LineIcons.Teleport),
    MenuEntry("config", "Konfiguration", LineIcons.Sliders),
    MenuEntry("settings", "Einstellungen", LineIcons.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val surface by vm.surfacePreset.collectAsState()
            val accent by vm.accentPreset.collectAsState()

            ServerControlTheme(surface = surface, accent = accent) {
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val c = LocalAppColors.current
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val start = if (vm.hasEndpoint) "picker" else "setup"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { DrawerBody(nav, drawerState.isOpen) { route ->
            scope.launch { drawerState.close() }
            nav.navigate(route) { launchSingleTop = true }
        } },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            NavHost(navController = nav, startDestination = start) {
                composable("setup") {
                    SetupScreen(vm) {
                        nav.navigate("picker") { popUpTo("setup") { inclusive = true } }
                    }
                }
                composable("picker") {
                    LaunchedEffect(Unit) { vm.loadServers() }
                    ServerPickerScreen(
                        vm,
                        onPicked = { nav.navigate("home") },
                        onAddNew = { nav.navigate("setup") },
                    )
                }
                composable("home") {
                    HomeScreen(vm) { scope.launch { drawerState.open() } }
                }
                composable("resources") { Screen("Ressourcen", nav) { ResourcesScreen(vm) } }
                composable("billing") { Screen("Kosten", nav) { BillingScreen(vm) } }
                composable("worlds") { Screen("Welten", nav) { WorldsScreen(vm) } }
                composable("give") { Screen("Items geben", nav) { GiveItemScreen(vm) } }
                composable("teleport") { Screen("Teleport", nav) { TeleportScreen(vm) } }
                composable("config") { Screen("Konfiguration", nav) { ConfigScreen(vm) } }
                composable("settings") { Screen("Einstellungen", nav) { SettingsScreen(vm) } }
            }
        }
    }
}

/** Sub-screen chrome: a back arrow and a title, nothing more. */
@Composable
private fun Screen(title: String, nav: NavHostController, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            LineIcon(
                LineIcons.Back,
                tint = c.onBackground,
                size = 24.dp,
                modifier = Modifier.clickableNoRipple { nav.popBackStack() },
            )
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.onBackground)
        }
        content()
    }
}

@Composable
private fun DrawerBody(
    nav: NavHostController,
    isOpen: Boolean,
    onNavigate: (String) -> Unit,
) {
    val c = LocalAppColors.current
    ModalDrawerSheet(
        drawerContainerColor = c.surface,
        modifier = Modifier.fillMaxWidth(0.78f),
    ) {
        Column(Modifier.padding(20.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(LineIcons.Server, tint = c.accent, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Server Control",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onBackground,
                )
            }
            Spacer(Modifier.height(28.dp))

            MENU.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { onNavigate(entry.route) }
                        .padding(vertical = 14.dp),
                ) {
                    LineIcon(entry.icon, tint = c.onMuted, size = 20.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onBackground,
                    )
                }
            }
        }
    }
}
