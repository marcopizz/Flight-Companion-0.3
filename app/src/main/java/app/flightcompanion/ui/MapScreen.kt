package app.flightcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Prefs

/** Globo 3D a schermo intero. mode: "upcoming", "past" oppure "flight-<id>". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(mode: String, vm: MainViewModel, onOpenFlight: (Long) -> Unit, onBack: () -> Unit) {
    val upcoming by vm.upcoming.collectAsState()
    val past by vm.past.collectAsState()
    val focusId: Long? = if (mode.startsWith("flight-")) mode.removePrefix("flight-").toLongOrNull() else null
    val flights: List<Flight> = remember(mode, upcoming, past) {
        when {
            mode == "past" -> past
            focusId != null -> (upcoming + past).filter { it.id == focusId }
            else -> upcoming
        }
    }
    var style by remember { mutableStateOf(Prefs.mapStyle) }
    val title = when {
        mode == "past" -> "GLOBO · STORICO"
        focusId != null -> "GLOBO · VOLO"
        else -> "GLOBO · IN ARRIVO"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineMedium, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            FlightGlobe(flights, Modifier.fillMaxSize(), focusId = focusId, style = style, onOpenFlight = onOpenFlight)
            MapStyleToggle(
                style = style,
                onChange = { style = it; Prefs.mapStyle = it },
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            )
            Text(
                "Trascina per ruotare · pizzica per lo zoom · tocca una rotta",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
