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
import io.github.jpsubway.app.ui.home.HomeScreen
import io.github.jpsubway.app.ui.map.LineMapScreen
import io.github.jpsubway.app.ui.map.MapScreen
import io.github.jpsubway.app.ui.region.RegionPickerScreen
import io.github.jpsubway.app.ui.route.RouteScreen
import io.github.jpsubway.app.ui.search.StationSearchScreen
import io.github.jpsubway.app.ui.settings.SettingsScreen
import io.github.jpsubway.app.ui.station.StationScreen

object Routes {
    const val HOME = "home"
    const val MAP = "map"
    const val STATION = "station/{group}?line={line}"
    const val LINE_MAP = "line/{line}?group={group}"
    const val ROUTE = "route"
    const val SEARCH = "search/{mode}"
    const val REGION = "region"
    const val SETTINGS = "settings"
    fun station(group: String, line: String? = null) =
        "station/" + Uri.encode(group) + (if (line != null) "?line=" + Uri.encode(line) else "")
    fun lineMap(line: String, group: String? = null) =
        "line/" + Uri.encode(line) + (if (group != null) "?group=" + Uri.encode(group) else "")
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
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            // 국가 선택은 HomeScreen 이 지역을 바꾼 뒤 알려 준다 → 그 나라 노선도로
            HomeScreen(onOpenCountry = { nav.navigate(Routes.MAP) { launchSingleTop = true } })
        }
        composable(Routes.MAP) {
            MapScreen(
                onOpenStation = { nav.navigate(Routes.station(it)) },
                onSearch = { nav.navigate(Routes.search("station")) },
                onRoute = { nav.navigate(Routes.ROUTE) { launchSingleTop = true } },
                onRegion = { nav.navigate(Routes.REGION) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.STATION,
            arguments = listOf(
                navArgument("group") { type = NavType.StringType },
                navArgument("line") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { e ->
            StationScreen(
                group = e.arguments?.getString("group").orEmpty(),
                initialLine = e.arguments?.getString("line"),
                onBack = { nav.popBackStack() },
                onRoute = {
                    nav.navigate(Routes.ROUTE) {
                        popUpTo(Routes.MAP)
                        launchSingleTop = true
                    }
                },
                onOpenLineMap = { line, group -> nav.navigate(Routes.lineMap(line, group)) },
            )
        }
        composable(
            Routes.LINE_MAP,
            arguments = listOf(
                navArgument("line") { type = NavType.StringType },
                navArgument("group") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { e ->
            LineMapScreen(
                lineId = e.arguments?.getString("line").orEmpty(),
                group = e.arguments?.getString("group"),
                onBack = { nav.popBackStack() },
                onOpenStation = { group, line -> nav.navigate(Routes.station(group, line)) },
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
