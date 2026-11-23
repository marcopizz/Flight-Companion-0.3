package app.flightcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Stats
import app.flightcompanion.data.fmtAge

private val Navy = Color(0xFF0B2A5B)
private val Chalk = Color(0xFFF3EEE3)
private val Yellow = Color(0xFFFFC400)

// ---------------------------------------------------------------- Aerei

@Composable
fun AircraftCard(s: Stats) {
    if (s.aircraftCount == 0 && s.topModel == null) return
    val bg = Brush.verticalGradient(listOf(Color(0xFF0F3B6B), Color(0xFF0B2F57)))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Block("Aerei diversi", fmtNum(s.aircraftCount), s.topManufacturer?.let { "${s.topManufacturerPct}% $it" }, Modifier.weight(1f))
            Block("Più nuovo", fmtAge(s.newest?.aircraftAgeYears), s.newest?.aircraftModel, Modifier.weight(1f))
            Block("Più vecchio", fmtAge(s.oldest?.aircraftAgeYears), s.oldest?.aircraftModel, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        s.topModel?.let { m ->
            Text("AEREO PIÙ VOLATO", color = Yellow, fontFamily = PlexMono, fontSize = 12.sp, letterSpacing = 1.sp)
            Text(m.label, color = Chalk, style = MaterialTheme.typography.displaySmall)
            Text("${m.count} voli • ${s.topModelMinutes / 60} ore", color = Chalk.copy(alpha = 0.8f), fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            BlueprintPlane(m.label)
        }

        s.topReg?.let { r ->
            Spacer(Modifier.height(16.dp))
            Text("MATRICOLA PIÙ FREQUENTE", color = Yellow, fontFamily = PlexMono, fontSize = 12.sp, letterSpacing = 1.sp)
            Text("${r.count} ${if (r.count == 1) "volo" else "voli"}", color = Chalk, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val f = s.topRegFlight
                if (f != null) {
                    AirlineLogo(f.airlineIcao, f.airlineIata, height = 26.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(r.label, color = Chalk, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 1.sp)
                    val details = listOfNotNull(f?.aircraftModel, f?.aircraftAgeYears?.let { fmtAge(it) }).joinToString(" • ")
                    if (details.isNotBlank()) Text(details, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        // Classi di viaggio
        val labels = listOf("Economy" to "Eco", "Premium Economy" to "Eco+", "Business" to "Business", "First" to "First")
        val total = labels.sumOf { s.classCounts[it.first] ?: 0 }
        if (total > 0) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                for ((key, short) in labels) {
                    val n = s.classCounts[key] ?: 0
                    if (n > 0) Text(short, color = Chalk, fontSize = 13.sp, modifier = Modifier.weight(n.toFloat()))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((key, _) in labels) {
                    val n = s.classCounts[key] ?: 0
                    if (n > 0) {
                        Box(
                            Modifier.weight(n.toFloat()).height(22.dp).background(Color(0x33FFC400)),
                            contentAlignment = Alignment.CenterEnd
                        ) { Text("$n", color = Chalk, fontFamily = PlexMono, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp)) }
                    }
                }
            }
        }

        s.favSeat?.let { seat ->
            Spacer(Modifier.height(14.dp))
            Text("POSTO PREFERITO", color = Yellow, fontFamily = PlexMono, fontSize = 12.sp, letterSpacing = 1.sp)
            Text("${seat.label}  (${seat.count} ${if (seat.count == 1) "volta" else "volte"})", color = Chalk, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun Block(title: String, value: String, sub: String?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22FFFFFF))
            .padding(10.dp)
    ) {
        Text(title.uppercase(), color = Yellow, fontFamily = PlexMono, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Chalk, fontFamily = PlexMono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(sub ?: " ", color = Chalk.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
    }
}

// --------------------------------------------------------------- Ritardi

@Composable
fun DelayCard(label: String, s: Stats) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0C0C0C)))
    val gold = Color(0xFFFFB000)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RAPPORTO RITARDI", color = Color.White, style = MaterialTheme.typography.headlineSmall, letterSpacing = 1.5.sp)
            Text(label, color = gold, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(gold))
        Spacer(Modifier.height(14.dp))

        if (s.delayFlights == 0) {
            Text(
                "Nessun dato di ritardo: compare dopo l'atterraggio dei voli.",
                color = Color.White.copy(alpha = 0.85f)
            )
            return@Column
        }

        Text("${s.delayedPct}%", color = gold, fontFamily = PlexMono, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text("I MIEI VOLI IN RITARDO", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "${s.delayedCount} dei miei ${s.delayFlights} voli sono arrivati dopo l'orario previsto",
            color = Color(0xFFB8B8B8), fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        FlipRow("${s.lostMinutes / 60}ORE")
        Spacer(Modifier.height(6.dp))
        Text("PERSI A CAUSA DEI RITARDI", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("In media ${s.avgDelayedMin} min di ritardo sui voli in ritardo", color = Color(0xFFB8B8B8), fontSize = 13.sp)

        s.worstDelay?.let { f ->
            Spacer(Modifier.height(16.dp))
            Text("RITARDO PEGGIORE", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            val m = f.delayArrMin ?: 0
            val amount = if (m >= 120) "${m / 60} ORE" else "$m MIN"
            val board = "$amount  ${f.flightNumber}  ${f.depIata ?: "?"}-${f.arrIata ?: "?"}"
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF120202)).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(board, color = Color(0xFFFFA726), fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        val worst = s.airlineDelays.take(3)
        if (worst.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("COMPAGNIA PEGGIORE", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                "${worst[0].name} • ${worst[0].minutes / 60} ore di ritardo in totale",
                color = Color(0xFFB8B8B8), fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            val reds = listOf(Color(0xFFE8501A), Color(0xFFC13F12), Color(0xFF8F2E0C))
            Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for ((i, a) in worst.withIndex()) {
                    Box(
                        Modifier.weight((a.minutes.coerceAtLeast(1)).toFloat()).height(56.dp).background(reds[i]),
                        contentAlignment = Alignment.Center
                    ) {
                        AirlineLogo(a.icao, a.iata, height = 28.dp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (a in worst) {
                    Text(
                        "${a.minutes / 60}h",
                        color = Color(0xFFFF8A5C), fontFamily = PlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight((a.minutes.coerceAtLeast(1)).toFloat())
                    )
                }
            }
        }
    }
}

/** Tabellone a ribalta: le cifre sono accese, le lettere attenuate. */
@Composable
private fun FlipRow(text: String) {
    val gold = Color(0xFFFFB000)
    val size = if (text.length > 5) 36.sp else 44.sp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (ch in text) {
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF262626))
                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ch.toString(),
                    color = if (ch.isDigit()) gold else Color(0x66FFB000),
                    fontFamily = PlexMono,
                    fontSize = size,
                    fontWeight = FontWeight.Bold
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xAA000000)))
            }
        }
    }
}
