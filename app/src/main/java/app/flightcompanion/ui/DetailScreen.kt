package app.flightcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Graph
import app.flightcompanion.data.Prefs
import app.flightcompanion.data.fmtAge
import app.flightcompanion.data.fmtCountdown
import app.flightcompanion.data.fmtDateTime
import app.flightcompanion.data.fmtDayMonth
import app.flightcompanion.data.fmtDuration
import app.flightcompanion.data.fmtTime
import app.flightcompanion.data.flagEmoji
import app.flightcompanion.data.kmToMiles
import app.flightcompanion.data.manufacturerOf
import app.flightcompanion.data.statusIt
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

private val CLASSES = listOf("Economy", "Premium Economy", "Business", "First")
private val SEAT_TYPES = listOf("Finestrino", "Centrale", "Corridoio")
private val REASONS = listOf("Lavoro", "Vacanza", "Famiglia", "Altro")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: Long, onOpenMap: (String) -> Unit, onBack: () -> Unit) {
    val flight by Graph.repo.observe(id).collectAsState(initial = null)
    val f = flight

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        f?.flightNumber ?: "VOLO",
                        fontFamily = PlexMono,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { pad ->
        if (f == null) {
            Text("Caricamento…", Modifier.padding(pad).padding(16.dp))
        } else {
            DetailContent(f, Modifier.padding(pad), onOpenMap, onBack)
        }
    }
}

@Composable
private fun DetailContent(f: Flight, modifier: Modifier, onOpenMap: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val past by Graph.repo.past().collectAsState(initial = emptyList())
    var seat by remember(f.id) { mutableStateOf(f.seat ?: "") }
    var seatType by remember(f.id) { mutableStateOf(f.seatType) }
    var cls by remember(f.id) { mutableStateOf(f.travelClass) }
    var reason by remember(f.id) { mutableStateOf(f.travelReason) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val useMiles = Prefs.useMiles
    val now = System.currentTimeMillis()

    LaunchedEffect(f.aircraftReg, f.photoTried) {
        if (f.aircraftReg != null && !f.photoTried) Graph.repo.ensurePhoto(f.id)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeroTicket(f, now)
        TimelineCard(f, now)
        MetricsRow(f, useMiles)

        // ---- Aereo
        SectionTitle("Aereo")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Blueprint)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlueprintPlane(f.aircraftModel)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            manufacturerOf(f.aircraftModel)?.uppercase() ?: "AEREO",
                            color = Color(0xFFFFC400), fontFamily = PlexMono, fontSize = 12.sp, letterSpacing = 1.sp
                        )
                        Text(
                            f.aircraftModel ?: "Modello non disponibile",
                            color = Paper, style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    if (f.aircraftReg != null) {
                        Box(
                            Modifier.border(1.5.dp, Paper, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(f.aircraftReg, color = Paper, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BlueprintFact("ETÀ", fmtAge(f.aircraftAgeYears))
                    BlueprintFact("MODE S", f.aircraftModeS?.uppercase() ?: "—")
                }
            }
        }
        if (f.photoUrl != null) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    AsyncImage(
                        model = f.photoUrl,
                        contentDescription = "Foto dell'aereo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                    )
                    Text(
                        "Foto: ${f.photographer ?: "autore sconosciuto"} · Planespotters.net",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { f.photoLink?.let { uriHandler.openUri(it) } }
                            .padding(12.dp)
                    )
                }
            }
        }

        // ---- Meteo
        if (f.completed && (f.weatherDep != null || f.weatherArr != null)) {
            SectionTitle("Meteo")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherBox("PARTENZA · ${f.depIata ?: ""}", f.weatherDep, Modifier.weight(1f))
                WeatherBox("ARRIVO · ${f.arrIata ?: ""}", f.weatherArr, Modifier.weight(1f))
            }
        }

        // ---- Rotta
        SectionTitle("Rotta")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlightMap(
                    listOf(f),
                    Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(6.dp)),
                    fitToFlights = true,
                    onClick = { onOpenMap("flight-${f.id}") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (f.trackState) {
                            1 -> "Rotta reale (ADS-B)"
                            2 -> if (f.completed) "Rotta ideale (traccia non disponibile)" else "Rotta ideale"
                            else -> if (f.completed) "Rotta ideale (traccia in recupero)" else "Rotta ideale"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onOpenMap("flight-${f.id}") }) { Text("Apri il globo") }
                }
                val recent = now - (f.depScheduledUtc ?: 0L) < 29L * 24 * 3_600_000
                if (f.completed && f.trackState != 1 && recent && f.aircraftModeS != null) {
                    OutlinedButton(
                        onClick = { scope.launch { Graph.repo.fetchTrackFor(f.id, true) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Riprova a recuperare la rotta reale") }
                }
            }
        }

        // ---- Il tuo viaggio
        SectionTitle("Il tuo viaggio")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val total = past.sumOf { it.distanceKm ?: 0.0 }
                    val mine = f.distanceKm ?: 0.0
                    val share = if (f.completed && total > 0.0) (mine / total).toFloat() else 0f
                    if (f.completed) {
                        ShareRing(share, "${(share * 100).toInt()}%")
                        Column {
                            Text("Quota dei tuoi km", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "sul totale di tutti i voli registrati",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "Dopo l'atterraggio qui vedi il peso di questo volo sul totale dei tuoi chilometri.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = seat,
                    onValueChange = { seat = it.uppercase(); saved = false },
                    label = { Text("Posto (es. 14A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("TIPO DI POSTO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                ChipRow(SEAT_TYPES, seatType) { seatType = it; saved = false }
                Text("CLASSE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                ChipRow(CLASSES, cls) { cls = it; saved = false }
                Text("MOTIVO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                ChipRow(REASONS, reason) { reason = it; saved = false }
                Button(onClick = {
                    scope.launch {
                        Graph.repo.updateManual(f.id, seat, seatType, cls, reason)
                        saved = true
                    }
                }) { Text(if (saved) "Salvato" else "Salva") }
            }
        }

        if (!f.completed) {
            OutlinedButton(
                onClick = { scope.launch { Graph.repo.refreshNow(f.id) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Aggiorna ora (usa quota API)") }
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Elimina volo")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il volo?") },
            text = { Text("Il volo verrà rimosso anche dalle statistiche.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Graph.repo.delete(f)
                        onBack()
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
}

// ------------------------------------------------------------------ Blocchi

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleLarge,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun HeroTicket(f: Flight, now: Long) {
    val surface = MaterialTheme.colorScheme.surface
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(TicketHalf(true, 10.dp, 14.dp)).background(surface).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirlineLogo(f.airlineIcao, f.airlineIata, height = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.airlineName ?: "Compagnia", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(f.flightNumber, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = sub)
                }
                StatusPill(f)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(f.depIata ?: "?", style = MaterialTheme.typography.displayLarge, fontSize = 72.sp)
                    Text(f.depCity ?: f.depName ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("${flagEmoji(f.depCountry)} ${f.depName ?: ""}", style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1)
                }
                Icon(
                    Icons.Filled.FlightTakeoff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp).padding(bottom = 4.dp)
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(f.arrIata ?: "?", style = MaterialTheme.typography.displayLarge, fontSize = 72.sp)
                    Text(f.arrCity ?: f.arrName ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("${flagEmoji(f.arrCountry)} ${f.arrName ?: ""}", style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(TicketHalf(false, 10.dp, 14.dp)).background(surface)) {
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp)) {
                drawLine(
                    Color(0x66808080), Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TicketCell("DATA") {
                    Text(fmtDayMonth(f.depScheduledUtc, f.depTz, f.dateLocal), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
                TicketCell("DURATA") {
                    Text(fmtDuration(f.durationMin ?: plannedMinutes(f)), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
                TicketCell("STATO") {
                    Text(statusIt(f.status), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
            }
            if (!f.completed) {
                val countdown = fmtCountdown(f.depRevisedUtc ?: f.depScheduledUtc, now)
                if (countdown != null) {
                    Box(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "MANCANO  ${countdown.uppercase()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
        }
    }
}

private fun plannedMinutes(f: Flight): Int? {
    val a = f.depScheduledUtc
    val b = f.arrScheduledUtc
    return if (a != null && b != null && b > a) ((b - a) / 60_000L).toInt() else null
}

@Composable
private fun TimelineCard(f: Flight, now: Long) {
    val dep = f.depActualUtc ?: f.depRevisedUtc ?: f.depScheduledUtc
    val arr = f.arrActualUtc ?: f.arrRevisedUtc ?: f.arrScheduledUtc
    val progress = when {
        f.completed -> 1f
        dep == null || arr == null || arr <= dep -> 0f
        now <= dep -> 0f
        else -> ((now - dep).toFloat() / (arr - dep).toFloat()).coerceIn(0f, 1f)
    }
    val track = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimeColumn(
                    "PARTENZA", f.depScheduledUtc, f.depRevisedUtc ?: f.depActualUtc, f.depTz, f.depName,
                    gate = f.depGate, terminal = f.depTerminal, belt = null, alignEnd = false
                )
                TimeColumn(
                    "ARRIVO", f.arrScheduledUtc, f.arrRevisedUtc ?: f.arrActualUtc, f.arrTz, f.arrName,
                    gate = f.arrGate, terminal = f.arrTerminal, belt = f.baggageBelt, alignEnd = true
                )
            }
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val y = size.height / 2f
                drawLine(track, Offset(8f, y), Offset(size.width - 8f, y), strokeWidth = 4f, cap = StrokeCap.Round)
                val x = 8f + (size.width - 16f) * progress
                drawLine(fill, Offset(8f, y), Offset(x, y), strokeWidth = 6f, cap = StrokeCap.Round)
                drawCircle(fill, radius = 8f, center = Offset(8f, y))
                drawCircle(track, radius = 8f, center = Offset(size.width - 8f, y))
                drawCircle(Color.White, radius = 11f, center = Offset(x, y))
                drawCircle(fill, radius = 7f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun TimeColumn(
    label: String,
    scheduled: Long?,
    revised: Long?,
    tz: String?,
    airport: String?,
    gate: String?,
    terminal: String?,
    belt: String?,
    alignEnd: Boolean
) {
    val delta = if (scheduled != null && revised != null) ((revised - scheduled) / 60_000L).toInt() else null
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        Text(fmtTime(revised ?: scheduled, tz), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
        if (delta != null && delta != 0) {
            val c = if (delta > 15) MaterialTheme.colorScheme.error else Color(0xFFC77D00)
            Text(
                "${if (delta > 0) "+" else ""}$delta min  (prev. ${fmtTime(scheduled, tz)})",
                color = c, fontFamily = PlexMono, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
        }
        Text(fmtDateTime(revised ?: scheduled, tz).take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (airport != null) Text(airport, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (gate != null || terminal != null) GateSign(gate, terminal)
            if (belt != null) {
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.secondary).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("NASTRO $belt", color = MaterialTheme.colorScheme.onSecondary, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(f: Flight, useMiles: Boolean) {
    val km = f.distanceKm
    val delay = f.delayArrMin
    val delayColor = when {
        delay == null -> MaterialTheme.colorScheme.onSurface
        delay <= 0 -> MaterialTheme.colorScheme.tertiary
        delay <= 15 -> Color(0xFFC77D00)
        else -> MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(
                "DISTANZA",
                if (km == null) "—" else if (useMiles) String.format(Locale.ITALY, "%,.0f", kmToMiles(km)) else String.format(Locale.ITALY, "%,.0f", km),
                if (useMiles) "miglia" else "km",
                MaterialTheme.colorScheme.onSurface
            )
            Metric("DURATA", fmtDuration(f.durationMin ?: plannedMinutes(f)), "ore e minuti", MaterialTheme.colorScheme.onSurface)
            Metric(
                "RITARDO",
                if (delay == null) "—" else if (delay > 0) "+$delay" else "$delay",
                if (delay == null) "arrivo" else "min all'arrivo",
                delayColor
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        Text(value, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = color)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BlueprintFact(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFFFFC400), fontFamily = PlexMono, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(value, color = Paper, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun WeatherBox(title: String, text: String?, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            val parts = (text ?: "—").split(", ")
            Text(parts.firstOrNull() ?: "—", style = MaterialTheme.typography.titleMedium)
            Text(parts.drop(1).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { o ->
            FilterChip(
                selected = selected == o,
                onClick = { onSelect(if (selected == o) null else o) },
                label = { Text(o) }
            )
        }
    }
}
