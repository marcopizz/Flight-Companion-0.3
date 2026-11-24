package app.flightcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.AirportDb
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Freq
import app.flightcompanion.data.Graph
import app.flightcompanion.data.Runway
import app.flightcompanion.data.flagEmoji
import app.flightcompanion.data.fmtDayMonth
import app.flightcompanion.data.fmtDelay
import app.flightcompanion.data.zoneOf
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val surfaceNames = mapOf(
    "ASP" to "asfalto", "ASPH" to "asfalto", "CON" to "cemento", "CONC" to "cemento", "GRS" to "erba", "GRVL" to "ghiaia",
    "GRV" to "ghiaia", "TURF" to "erba", "DIRT" to "terra", "WATER" to "acqua", "SAND" to "sabbia", "MAC" to "macadam"
)

private val freqNames = mapOf(
    "ATIS" to "ATIS", "TWR" to "Torre", "GND" to "Ground", "CLD" to "Autorizzazioni", "DEL" to "Autorizzazioni",
    "APP" to "Avvicinamento", "DEP" to "Partenze", "A/D" to "Arrivi/Partenze", "AFIS" to "AFIS", "CTAF" to "CTAF",
    "UNIC" to "UNICOM", "RDO" to "Radio"
)

class MetarView(
    val raw: String,
    val category: String?,
    val temp: String,
    val dew: String,
    val wind: String,
    val vis: String,
    val qnh: String,
    val clouds: String,
    val obsMs: Long?
)

fun parseMetar(json: String): MetarView? = try {
    val o = JSONObject(json)
    fun num(k: String): Double? = if (o.has(k) && !o.isNull(k) && o.opt(k) is Number) o.getDouble(k) else null
    val wdir = o.opt("wdir")
    val dir = when (wdir) {
        is Number -> "${wdir.toInt()}°"
        is String -> wdir
        else -> "—"
    }
    val spd = num("wspd")
    val gst = num("wgst")
    val wind = if (spd == null) "—" else buildString {
        append("$dir / ${spd.toInt()} kt")
        if (gst != null) append(" (raffiche ${gst.toInt()} kt)")
    }
    val v = o.opt("visib")
    val vis = when (v) {
        is Number -> String.format(Locale.ITALY, "%.0f km", v.toDouble() * 1.609)
        is String -> ">" + String.format(Locale.ITALY, "%.0f km", (v.replace("+", "").toDoubleOrNull() ?: 10.0) * 1.609)
        else -> "—"
    }
    val alt = num("altim")
    val qnh = when {
        alt == null -> "—"
        alt < 100 -> "${(alt * 33.8639).toInt()} hPa"
        else -> "${alt.toInt()} hPa"
    }
    val cl = StringBuilder()
    val arr = o.optJSONArray("clouds")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            if (cl.isNotEmpty()) cl.append(" · ")
            cl.append(c.optString("cover", "?"))
            if (c.has("base") && !c.isNull("base")) cl.append(" ${c.optInt("base")} ft")
        }
    }
    MetarView(
        raw = o.optString("rawOb", ""),
        category = o.optString("flightCategory", "").ifBlank { null },
        temp = num("temp")?.let { "${it.toInt()}°C" } ?: "—",
        dew = num("dewp")?.let { "${it.toInt()}°C" } ?: "—",
        wind = wind,
        vis = vis,
        qnh = qnh,
        clouds = if (cl.isEmpty()) "—" else cl.toString(),
        obsMs = if (o.has("obsTime") && o.opt("obsTime") is Number) o.getLong("obsTime") * 1000L else null
    )
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportDetailScreen(
    iata: String,
    onBack: () -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenFlight: (Long) -> Unit
) {
    val info = remember(iata) { AirportDb.get(iata) }
    val saved by Graph.repo.airport(iata).collectAsState(initial = null)
    val past by Graph.repo.past().collectAsState(initial = emptyList())
    val upcoming by Graph.repo.upcoming().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var wxBusy by remember { mutableStateOf(false) }
    var wxError by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    val runways = remember(info?.icao) { info?.let { AirportDb.runways(it.icao) } ?: emptyList<Runway>() }
    val freqs = remember(info?.icao) { info?.let { AirportDb.freqs(it.icao) } ?: emptyList<Freq>() }
    val metar = remember(saved?.metar) { saved?.metar?.let { parseMetar(it) } }
    val mine = remember(past, iata) { past.filter { !it.isCanceled && (it.depIata == iata || it.arrIata == iata) } }
    val nextGate = remember(upcoming, iata) {
        upcoming.firstOrNull { it.depIata == iata && it.depGate != null }?.depGate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(iata, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
                },
                actions = {
                    val fav = saved?.favorite == true
                    IconButton(onClick = { scope.launch { Graph.repo.addAirport(iata); Graph.repo.setFavorite(iata, !fav) } }) {
                        Icon(
                            if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Preferito",
                            tint = if (fav) Color(0xFFF2B200) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { pad ->
        if (info == null) {
            Text("Aeroporto non trovato.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Intestazione
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(info.iata, style = MaterialTheme.typography.displayLarge, fontSize = 72.sp)
                    Text(info.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${flagEmoji(info.country)} ${info.city}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    val now = ZonedDateTime.now(zoneOf(info.tz))
                    Text(
                        "ORA LOCALE  ${DateTimeFormatter.ofPattern("HH:mm").format(now)}  ·  ${info.tz ?: "UTC"}",
                        fontFamily = PlexMono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Scheda di base
            Heading("Scheda di base")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fact("IATA / ICAO", "${info.iata} / ${info.icao.ifBlank { "—" }}")
                    Fact("Coordinate", String.format(Locale.US, "%.4f, %.4f", info.lat, info.lon))
                    Fact(
                        "Quota",
                        info.elevFt?.let { "${(it * 0.3048).toInt()} m  (${it} ft)" } ?: "—"
                    )
                    Fact("Fuso orario", info.tz ?: "—")
                    if (info.home != null) {
                        Text(
                            "Sito ufficiale", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(info.home) }.padding(vertical = 2.dp)
                        )
                    }
                    if (info.wiki != null) {
                        Text(
                            "Wikipedia", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(info.wiki) }.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // ---- Piste e frequenze
            Heading("Piste e frequenze")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (runways.isEmpty()) {
                        Text("Nessun dato sulle piste.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (r in runways) {
                        val len = r.lengthFt?.let { String.format(Locale.ITALY, "%,d m", (it * 0.3048).toInt()) } ?: "—"
                        val wid = r.widthFt?.let { "${(it * 0.3048).toInt()} m" } ?: "—"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip4().background(Color(0xFF1B2A41)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("${r.le}/${r.he}", color = Color.White, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("$len × $wid", fontFamily = PlexMono, fontSize = 14.sp)
                                Text(
                                    listOfNotNull(
                                        surfaceNames[r.surface.uppercase()] ?: r.surface.lowercase().ifBlank { null },
                                        if (r.lighted) "illuminata" else null,
                                        if (r.closed) "chiusa" else null
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (freqs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("FREQUENZE RADIO", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (f in freqs) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(freqNames[f.type] ?: f.type, style = MaterialTheme.typography.bodyMedium)
                                if (f.description.isNotBlank()) {
                                    Text(f.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("${f.mhz} MHz", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // ---- Meteo
            Heading("Meteo in tempo reale")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (metar == null) {
                        Text(
                            "Nessun meteo scaricato. Premi il pulsante per leggere METAR e TAF.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val catColor = when (metar.category) {
                            "VFR" -> Color(0xFF1F7A6B)
                            "MVFR" -> Color(0xFF1A73FF)
                            "IFR" -> Color(0xFFC77D00)
                            "LIFR" -> Color(0xFFB3261E)
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(metar.temp, style = MaterialTheme.typography.displaySmall, fontSize = 44.sp)
                            if (metar.category != null) {
                                Box(Modifier.clip4().background(catColor).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text(metar.category, color = Color.White, fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        Fact("Vento", metar.wind)
                        Fact("Visibilità", metar.vis)
                        Fact("Pressione (QNH)", metar.qnh)
                        Fact("Punto di rugiada", metar.dew)
                        Fact("Nubi", metar.clouds)
                        if (metar.obsMs != null) {
                            Fact(
                                "Osservazione",
                                DateTimeFormatter.ofPattern("dd/MM HH:mm").format(Instant.ofEpochMilli(metar.obsMs).atZone(zoneOf(info.tz)))
                            )
                        }
                        Text("METAR", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(metar.raw, fontFamily = PlexMono, fontSize = 12.sp)
                    }
                    val taf = saved?.taf
                    if (taf != null) {
                        Text("TAF", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(taf, fontFamily = PlexMono, fontSize = 12.sp)
                    }
                    val at = saved?.metarAt ?: 0L
                    if (at > 0) {
                        Text(
                            "Scaricato alle " + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(at).atZone(zoneOf(info.tz))) + " (ora locale)",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (wxError != null) {
                        Text(wxError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = !wxBusy,
                        onClick = {
                            scope.launch {
                                wxBusy = true
                                Graph.repo.addAirport(iata)
                                wxError = Graph.repo.refreshWeather(iata)
                                wxBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (wxBusy) "Aggiornamento…" else "Aggiorna meteo") }
                }
            }

            // ---- Mappa
            Heading("Mappa dei gate")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (saved?.pdfPath != null) "Mappa PDF caricata: disponibile offline." else "OpenStreetMap con i gate. Puoi anche caricare la mappa PDF ufficiale.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { onOpenMap(iata) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (nextGate != null) "Apri la mappa (gate $nextGate in giallo)" else "Apri la mappa")
                    }
                }
            }

            // ---- I miei dati
            Heading("I miei dati")
            MyAirportData(iata, mine, onOpenFlight)

            OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Rimuovi dall'elenco")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Rimuovere $iata?") },
            text = { Text("Sparisce dall'elenco insieme al PDF caricato e al meteo salvato. I tuoi voli restano.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        Graph.repo.removePdf(iata)
                        Graph.repo.removeAirport(iata)
                        onBack()
                    }
                }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun MyAirportData(iata: String, mine: List<Flight>, onOpenFlight: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mine.isEmpty()) {
                Text("Nessun volo registrato da o verso questo aeroporto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val deps = mine.filter { it.depIata == iata }
            val arrs = mine.filter { it.arrIata == iata }
            val depDelays = deps.mapNotNull { it.delayDepMin }
            val arrDelays = arrs.mapNotNull { it.delayArrMin }
            Fact("Voli totali", "${mine.size}  (${deps.size} partenze · ${arrs.size} arrivi)")
            Fact("Ritardo medio in partenza", if (depDelays.isEmpty()) "—" else fmtDelay(depDelays.average().toInt()))
            Fact("Ritardo medio in arrivo", if (arrDelays.isEmpty()) "—" else fmtDelay(arrDelays.average().toInt()))
            val gate = deps.mapNotNull { it.depGate }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (gate != null) Fact("Gate più usato", "${gate.key}  (${gate.value}×)")
            val airline = mine.mapNotNull { it.airlineName }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (airline != null) Fact("Compagnia più usata", "${airline.key}  (${airline.value}×)")
            Spacer(Modifier.height(4.dp))
            Text("ULTIMI VOLI", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (f in mine.sortedByDescending { it.depScheduledUtc ?: 0L }.take(5)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenFlight(f.id) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${f.flightNumber}  ${f.depIata ?: "?"} → ${f.arrIata ?: "?"}", fontFamily = PlexMono, fontSize = 14.sp)
                    Text(fmtDayMonth(f.depScheduledUtc, f.depTz, f.dateLocal), fontFamily = PlexMono, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleLarge,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun Modifier.clip4(): Modifier = this.clip(RoundedCornerShape(4.dp))
