package app.flightcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Stats
import app.flightcompanion.data.flagEmoji
import app.flightcompanion.data.fmtHm
import app.flightcompanion.data.kmToMiles
import app.flightcompanion.data.mrzLine
import kotlinx.coroutines.isActive
import java.util.Locale

fun fmtNum(v: Double): String = String.format(Locale.ITALY, "%,.0f", v)
fun fmtNum(v: Int): String = String.format(Locale.ITALY, "%,d", v)

private val PassportText = Color(0xFFCFC6B2)

@Composable
fun PassportCard(label: String, s: Stats, flights: List<Flight>, useMiles: Boolean) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF14263F), Color(0xFF0C1A2E), Color(0xFF07101D)))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
    ) {
        Ticker(s.airportCodes)
        FlightMap(
            flights,
            Modifier.padding(horizontal = 10.dp).fillMaxWidth().aspectRatio(2f),
            style = PassportMapStyle
        )
        Spacer(Modifier.height(10.dp))

        // Bandiere
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (c in s.countryCodes.take(24)) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Text(flagEmoji(c), fontSize = 17.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Loghi delle compagnie
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (a in s.airlines.take(16)) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xE6FFFFFF)).padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) { AirlineLogo(a.icao, a.iata, height = 20.dp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        DashedLine()

        // Titolo
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("IL MIO FLIGHT PASSPORT", color = Color.White, fontFamily = BarlowCondensed, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("PASSAPORTO · PASSPORT · PASAPORTE  ·  $label", color = PassportText, fontSize = 11.sp, letterSpacing = 0.5.sp)
            }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF9A62), Color(0xFFE8501A)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = Color.White) }
        }

        // Numeri
        val distance = if (useMiles) fmtNum(kmToMiles(s.km)) else fmtNum(s.km)
        val unit = if (useMiles) " mi" else " km"
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                PassportLabel("VOLI")
                Text(fmtNum(s.flights), color = Color.White, fontFamily = BarlowCondensed, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1.3f)) {
                PassportLabel("DISTANZA")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(distance, color = Color.White, fontFamily = BarlowCondensed, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(unit, color = Color.White, fontSize = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                PassportLabel("TEMPO DI VOLO")
                Text(fmtHm(s.minutes), color = Color.White, fontFamily = BarlowCondensed, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(0.65f)) {
                PassportLabel("AEROPORTI")
                Text(fmtNum(s.airportCount), color = Color.White, fontFamily = BarlowCondensed, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(0.65f)) {
                PassportLabel("COMPAGNIE")
                Text(fmtNum(s.airlineCount), color = Color.White, fontFamily = BarlowCondensed, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Runway()
        Spacer(Modifier.height(10.dp))

        // Striscia in stile documento di viaggio
        val year = if (label.all { it.isDigit() }) label else "TOT"
        val l1 = mrzLine("${year}<<<VIAGGIATORE<<<<<<<MEMBER<<<<<@FLIGHTCOMPANION")
        val l2 = mrzLine("VOLI${s.flights}<<KM${s.km.toInt()}<<H${s.minutes / 60}<<APT${s.airportCount}<<AL${s.airlineCount}")
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(l1, color = PassportText.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
            Text(l2, color = PassportText.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PassportLabel(t: String) {
    Text(t, color = PassportText, fontSize = 12.sp, letterSpacing = 1.sp)
}

@Composable
private fun DashedLine() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            Color(0x66FFFFFF), Offset(0f, 0f), Offset(size.width, 0f),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        )
    }
}

@Composable
private fun Runway() {
    Box(Modifier.fillMaxWidth().height(26.dp).background(Color(0x33000000))) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawLine(
                Color(0xAAFFFFFF), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f))
            )
            drawLine(Color(0x66FF8A5C), Offset(0f, 2f), Offset(size.width, 2f), strokeWidth = 1.5f)
            drawLine(Color(0x66FF8A5C), Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), strokeWidth = 1.5f)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("01", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("19", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Fascia animata in alto: aeroplani e codici degli aeroporti visitati che scorrono. */
@Composable
private fun Ticker(codes: List<String>) {
    val labels = if (codes.isEmpty()) listOf("FLY") else codes
    val state = rememberLazyListState()
    LaunchedEffect(labels) {
        while (isActive) {
            state.scrollBy(1.3f)
            withFrameNanos { }
        }
    }
    LazyRow(
        state = state,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        items(100_000) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FlightTakeoff,
                    contentDescription = null,
                    tint = Color(0xFFFF8A5C).copy(alpha = if (i % 3 == 0) 1f else 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(labels[i % labels.size], color = Color(0xFFFF8A5C), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}
