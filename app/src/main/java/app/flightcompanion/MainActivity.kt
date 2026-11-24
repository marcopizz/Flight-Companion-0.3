package app.flightcompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.flightcompanion.ui.AirportDetailScreen
import app.flightcompanion.ui.AirportMapScreen
import app.flightcompanion.ui.DetailScreen
import app.flightcompanion.ui.FlightTheme
import app.flightcompanion.ui.HomeScreen
import app.flightcompanion.ui.ManualFlightScreen
import app.flightcompanion.ui.MapScreen
import app.flightcompanion.ui.MainViewModel
import app.flightcompanion.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlightTheme {
                Surface(Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.syncNow()
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm,
                onOpen = { nav.navigate("detail/$it") },
                onOpenMap = { nav.navigate("map/$it") },
                onManual = { nav.navigate("manual") },
                onOpenAirport = { nav.navigate("airport/$it") },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            DetailScreen(
                e.arguments?.getLong("id") ?: 0L,
                onOpenMap = { nav.navigate("map/$it") },
                onOpenAirport = { nav.navigate("airport/$it") },
                onOpenAirportMap = { code, gate -> nav.navigate("airportmap/$code/${if (gate.isNullOrBlank()) "-" else Uri.encode(gate)}") },
                onBack = { nav.popBackStack() }
            )
        }
        composable("airport/{iata}", arguments = listOf(navArgument("iata") { type = NavType.StringType })) { e ->
            AirportDetailScreen(
                iata = e.arguments?.getString("iata") ?: "",
                onBack = { nav.popBackStack() },
                onOpenMap = { nav.navigate("airportmap/$it/-") },
                onOpenFlight = { nav.navigate("detail/$it") }
            )
        }
        composable(
            "airportmap/{iata}/{gate}",
            arguments = listOf(navArgument("iata") { type = NavType.StringType }, navArgument("gate") { type = NavType.StringType })
        ) { e ->
            val g = e.arguments?.getString("gate")
            AirportMapScreen(
                iata = e.arguments?.getString("iata") ?: "",
                gate = if (g == null || g == "-") null else g,
                onBack = { nav.popBackStack() }
            )
        }
        composable("map/{mode}", arguments = listOf(navArgument("mode") { type = NavType.StringType })) { e ->
            MapScreen(
                e.arguments?.getString("mode") ?: "upcoming",
                vm,
                onOpenFlight = { nav.navigate("detail/$it") },
                onBack = { nav.popBackStack() }
            )
        }
        composable("manual") {
            ManualFlightScreen(vm) { nav.popBackStack() }
        }
        composable("settings") {
            SettingsScreen { nav.popBackStack() }
        }
    }
}
