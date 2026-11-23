package app.flightcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Prefs
import app.flightcompanion.data.RankItem
import app.flightcompanion.data.Stats
import app.flightcompanion.data.StatsCalc
import app.flightcompanion.data.fmtDuration
import app.flightcompanion.data.kmToMiles
import app.flightcompanion.data.yearOf
import java.util.Locale

@Composable
fun StatsScreen(past: List<Flight>) {
    val valid = remember(past) { past.filter { !it.isCanceled } }
    val years = remember(valid) { valid.map { yearOf(it) }.distinct().sortedDescending() }
    var sel by rememberSaveable { mutableIntStateOf(0) } // 0 = totale
    val selected = if (sel != 0 && sel !in years) 0 else sel
    val subset = remember(valid, selected) {
        if (selected == 0) valid else valid.filter { yearOf(it) == selected }
    }
    val stats = remember(subset) { StatsCalc.compute(subset) }
    val label = if (selected == 0) "TOTALE" else selected.toString()
    val useMiles = Prefs.useMiles

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = selected == 0, onClick = { sel = 0 }, label = { Text("Totale") })
                }
                items(years) { y ->
                    FilterChip(selected = selected == y, onClick = { sel = y }, label = { Text(y.toString()) })
                }
            }
        }
        item { PassportCard(label, stats, subset, useMiles) }

        if (stats.flights == 0) {
            item { Text("Nessun dato: le statistiche si popolano quando i voli vengono completati.") }
        } else {
            item { AircraftCard(stats) }
            item { DelayCard(label, stats) }
            item { TravelMixCard(stats) }
            item { Overview(stats, useMiles) }
            item { RankCard("Aeroporti più visitati", stats.airports) }
            item { RankCard("Compagnie più usate", stats.airlines) }
            item { RankCard("Rotte più frequenti", stats.routes) }
            item { RankCard("Aerei più usati (matricola)", stats.aircraftRegs) }
            item { RankCard("Modelli di aereo", stats.aircraftModels) }
            item { RankCard("Paesi visitati", stats.countries) }
        }
    }
}

@Composable
private fun Overview(s: Stats, useMiles: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Riepilogo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Stat("Voli effettuati", fmtNum(s.flights))
            Stat("Miglia volate", fmtNum(kmToMiles(s.km)))
            Stat("Chilometri volati", fmtNum(s.km))
            Stat(
                "Tempo in volo",
                "${fmtNum((s.minutes / 60).toInt())} h (${String.format(Locale.ITALY, "%.1f", s.minutes / 1440.0)} giorni)"
            )
            Stat("Durata media", fmtDuration(s.avgMinutes))
            Stat(
                "Ritardo medio all'arrivo",
                s.avgArrDelay?.let { String.format(Locale.ITALY, "%+.0f min", it) } ?: "—"
            )
            Stat("Volo più corto", s.shortest?.let { "${it.depIata}–${it.arrIata}, ${dist(it.distanceKm ?: 0.0, useMiles)}" } ?: "—")
            Stat("Volo più lungo", s.longest?.let { "${it.depIata}–${it.arrIata}, ${dist(it.distanceKm ?: 0.0, useMiles)}" } ?: "—")
            Stat("Aeroporti visitati", fmtNum(s.airportCount))
            Stat("Compagnie", fmtNum(s.airlineCount))
            Stat("Rotte diverse", fmtNum(s.routeCount))
            Stat("Paesi visitati", fmtNum(s.countryCount))
        }
    }
}

private fun dist(km: Double, miles: Boolean): String =
    if (miles) "${fmtNum(kmToMiles(km))} mi" else "${fmtNum(km)} km"

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RankCard(title: String, items: List<RankItem>) {
    if (items.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            items.take(10).forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", Modifier.width(26.dp))
                    if (item.flag != null) {
                        Text(item.flag, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                    } else if (item.icao != null || item.iata != null) {
                        AirlineLogo(item.icao, item.iata, height = 22.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(item.label, Modifier.weight(1f))
                    Text("${item.count}", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TravelMixCard(s: Stats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("COME VIAGGI", style = MaterialTheme.typography.titleLarge, letterSpacing = 2.sp)
            DonutChart("Classe di viaggio", s.classCounts.entries.map { it.key to it.value }.sortedByDescending { it.second })
            DonutChart("Tipo di posto", s.seatTypeCounts.entries.map { it.key to it.value }.sortedByDescending { it.second })
            DonutChart("Motivo del viaggio", s.reasonCounts.entries.map { it.key to it.value }.sortedByDescending { it.second })
            Text(
                "Classe, tipo di posto e motivo si impostano nella scheda di ogni volo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
