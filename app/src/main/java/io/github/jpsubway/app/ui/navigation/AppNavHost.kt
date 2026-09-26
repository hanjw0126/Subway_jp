package io.github.jpsubway.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.ui.map.MapScreen
import io.github.jpsubway.app.ui.region.RegionPickerScreen
import io.github.jpsubway.app.ui.route.RouteScreen
import io.github.jpsubway.app.ui.search.StationSearchScreen
import io.github.jpsubway.app.ui.settings.SettingsScreen
import io.github.jpsubway.app.ui.station.StationScreen

object Routes {
    const val MAP = "map"
    const val STATION = "station/{group}"
    const val ROUTE = "route"
    const val SEARCH = "search/{mode}"
    const val REGION = "region"
    const val SETTINGS = "settings"
    fun station(group: String) = "station/" + Uri.encode(group)
    fun search(mode: String) = "search/$mode"
}

@Composable
fun AppNavHost(session: RegionSession) {
    val nav = rememberNavController()
    val owner = LocalLifecycleOwner.current
    // 앱이 화면에 보이는 동안만 실시간 정보 폴링
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) { session.pollRealtime() }
    }
    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                onOpenStation = { nav.navigate(Routes.station(it)) },
                onSearch = { nav.navigate(Routes.search("station")) },
                onRoute = { nav.navigate(Routes.ROUTE) { launchSingleTop = true } },
                onRegion = { nav.navigate(Routes.REGION) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.STATION, arguments = listOf(navArgument("group") { type = NavType.StringType })) { e ->
            StationScreen(
                group = e.arguments?.getString("group").orEmpty(),
                onBack = { nav.popBackStack() },
                onRoute = {
                    nav.navigate(Routes.ROUTE) {
                        popUpTo(Routes.MAP)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.ROUTE) {
            RouteScreen(
                onBack = { nav.popBackStack() },
                onPickFrom = { nav.navigate(Routes.search("from")) },
                onPickTo = { nav.navigate(Routes.search("to")) },
            )
        }
        composable(Routes.SEARCH, arguments = listOf(navArgument("mode") { type = NavType.StringType })) { e ->
            StationSearchScreen(
                mode = e.arguments?.getString("mode") ?: "station",
                onBack = { nav.popBackStack() },
                onOpenStation = { g ->
                    nav.navigate(Routes.station(g)) { popUpTo(Routes.MAP) }
                },
            )
        }
        composable(Routes.REGION) { RegionPickerScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
