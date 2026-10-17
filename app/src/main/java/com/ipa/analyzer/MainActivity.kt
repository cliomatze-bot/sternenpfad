package com.ipa.analyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ipa.analyzer.core.model.ListType
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.ProfileViewModel
import com.ipa.analyzer.ui.screens.CompareScreen
import com.ipa.analyzer.ui.screens.HistoryScreen
import com.ipa.analyzer.ui.screens.HomeScreen
import com.ipa.analyzer.ui.screens.ImportScreen
import com.ipa.analyzer.ui.screens.AccountListScreen
import com.ipa.analyzer.ui.screens.PostsScreen
import com.ipa.analyzer.ui.screens.ProfileScreen
import com.ipa.analyzer.ui.screens.SettingsScreen
import com.ipa.analyzer.ui.theme.AnalyzerTheme

object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val FOLLOWERS = "followers"
    const val FOLLOWING = "following"
    const val COMPARE = "compare"
    const val POSTS = "posts"
    const val IMPORT = "import"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    private val dataVm: DataViewModel by viewModels()
    private val profileVm: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnalyzerTheme {
                AppRoot(dataVm, profileVm)
            }
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector, val alsoSelectedFor: Set<String> = emptySet())

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "Start", Icons.Filled.Home, setOf(Routes.PROFILE, Routes.POSTS, Routes.IMPORT)),
    BottomItem(Routes.FOLLOWERS, "Listen", Icons.Filled.Group, setOf(Routes.FOLLOWING)),
    BottomItem(Routes.COMPARE, "Vergleich", Icons.Filled.SwapHoriz),
    BottomItem(Routes.HISTORY, "Verlauf", Icons.Filled.History),
    BottomItem(Routes.SETTINGS, "Optionen", Icons.Filled.Settings),
)

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppRoot(dataVm: DataViewModel, profileVm: ProfileViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomItems.forEach { item ->
                    val selected = current == item.route || current in item.alsoSelectedFor
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.navigateTab(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    dataVm = dataVm,
                    profileVm = profileVm,
                    onSearch = { profileVm.lookup(); nav.navigate(Routes.PROFILE) },
                    navigate = { nav.navigate(it) },
                    navigateTab = { nav.navigateTab(it) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(profileVm, dataVm, onBack = { nav.popBackStack() }, navigate = { nav.navigate(it) })
            }
            composable(Routes.FOLLOWERS) {
                AccountListScreen(ListType.FOLLOWERS, dataVm, switchTo = { nav.navigateTab(Routes.FOLLOWING) }, navigate = { nav.navigate(it) })
            }
            composable(Routes.FOLLOWING) {
                AccountListScreen(ListType.FOLLOWING, dataVm, switchTo = { nav.navigateTab(Routes.FOLLOWERS) }, navigate = { nav.navigate(it) })
            }
            composable(Routes.COMPARE) {
                CompareScreen(dataVm, navigate = { nav.navigate(it) })
            }
            composable(Routes.POSTS) {
                PostsScreen(profileVm, dataVm, onBack = { nav.popBackStack() }, navigate = { nav.navigate(it) })
            }
            composable(Routes.IMPORT) {
                ImportScreen(dataVm, onBack = { nav.popBackStack() }, navigateTab = { nav.navigateTab(it) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(dataVm, navigate = { nav.navigate(it) }, navigateTab = { nav.navigateTab(it) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(profileVm, dataVm)
            }
        }
    }
}
