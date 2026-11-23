package app.flightcompanion.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.Flight
import app.flightcompanion.data.FlightDto
import app.flightcompanion.data.fmtCountdown
import app.flightcompanion.data.fmtDayMonth
import app.flightcompanion.data.fmtDuration
import app.flightcompanion.data.fmtTime
import app.flightcompanion.data.statusIt
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpen: (Long) -> Unit,
    onOpenMap: (String) -> Unit,
    onManual: () -> Unit,
    onSettings: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val upcoming by vm.upcoming.collectAsState()
    val past by vm.past.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snack.showSnackbar(m)
            vm.message = null
        }
    }

    val titles = listOf("IN ARRIVO", "STORICO", "STATISTICHE")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titles[tab],
                        style = MaterialTheme.typography.headlineMedium,
                        letterSpacing = 2.sp
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Impostazioni") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.FlightTakeoff, contentDescription = null) },
                    label = { Text("In arrivo") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("Storico") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    label = { Text("Statistiche") }
                )
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Aggiungi volo")
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab != 2) {
                FlightsPane(
                    flights = if (tab == 0) upcoming else past,
                    heading = if (tab == 0) "Voli in programma" else "Voli effettuati",
                    emptyText = if (tab == 0) "Nessun volo in programma. Tocca + per aggiungerne uno." else "Nessun volo nello storico. I voli completati compaiono qui.",
                    onOpen = onOpen,
                    onOpenGlobe = { onOpenMap(if (tab == 0) "upcoming" else "past") }
                )
            } else {
                StatsScreen(past)
            }
            if (vm.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (showAdd) {
        AddFlightDialog(
            onManual = {
                showAdd = false
                onManual()
            },
            onDismiss = { showAdd = false },
            onConfirm = { n, d ->
                showAdd = false
                vm.search(n, d)
            }
        )
    }
    if (vm.candidates.isNotEmpty()) {
        CandidateDialog(vm.candidates, onChoose = { vm.choose(it) }, onDismiss = { vm.dismissChoice() })
    }
}

/** Globo 3D a 3/4 dello schermo; l'elenco dei voli è un foglio che sale fino a coprire tutto. */
@Composable
fun FlightsPane(
    flights: List<Flight>,
    heading: String,
    emptyText: String,
    onOpen: (Long) -> Unit,
    onOpenGlobe: () -> Unit
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalPx = with(density) { maxHeight.toPx() }
        val collapsed = totalPx * 0.75f
        val offset = remember(totalPx) { mutableFloatStateOf(collapsed) }

        val snap: suspend (Float) -> Unit = { vy ->
            val target = when {
                vy < -500f -> 0f
                vy > 500f -> collapsed
                offset.floatValue < collapsed / 2f -> 0f
                else -> collapsed
            }
            animate(offset.floatValue, target) { v, _ -> offset.floatValue = v }
        }

        val connection = remember(totalPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy < 0f && offset.floatValue > 0f) {
                        val v = (offset.floatValue + dy).coerceAtLeast(0f)
                        val used = v - offset.floatValue
                        offset.floatValue = v
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy > 0f && offset.floatValue < collapsed) {
                        val v = (offset.floatValue + dy).coerceAtMost(collapsed)
                        val used = v - offset.floatValue
                        offset.floatValue = v
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offset.floatValue > 0f && offset.floatValue < collapsed) {
                        snap(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (offset.floatValue > 0f && offset.floatValue < collapsed) snap(available.y)
                    return Velocity.Zero
                }
            }
        }

        // Globo
        Box(Modifier.fillMaxWidth().height(maxHeight * 0.75f)) {
            FlightGlobe(flights, Modifier.fillMaxSize(), onOpenFlight = onOpen)
            IconButton(
                onClick = onOpenGlobe,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
            ) { Icon(Icons.Filled.Public, contentDescription = "Globo a schermo intero", tint = Color.White) }
        }

        // Foglio con l'elenco
        Column(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val off = offset.floatValue.roundToInt()
                    val h = (constraints.maxHeight - off).coerceAtLeast(0)
                    val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, off) }
                }
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(connection)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .draggable(
                        state = rememberDraggableState { d ->
                            offset.floatValue = (offset.floatValue + d).coerceIn(0f, collapsed)
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> snap(v) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(heading.uppercase(), style = MaterialTheme.typography.titleLarge, letterSpacing = 1.5.sp)
                Text("${flights.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (flights.isEmpty()) {
                    item { Text(emptyText, Modifier.padding(8.dp)) }
                }
                items(flights, key = { it.id }) { f ->
                    FlightTicket(f, now) { onOpen(f.id) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Carta d'imbarco

@Composable
fun StatusPill(f: Flight) {
    val late = !f.completed && f.depRevisedUtc != null && f.depScheduledUtc != null &&
        f.depRevisedUtc - f.depScheduledUtc >= 15 * 60_000L
    val color = when {
        f.isCanceled -> MaterialTheme.colorScheme.error
        late || f.status == "Delayed" -> Color(0xFFC77D00)
        f.completed -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            statusIt(f.status).uppercase(),
            color = color,
            fontFamily = PlexMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun TicketCell(label: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, value: @Composable () -> Unit) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(2.dp))
        value()
    }
}

@Composable
fun GateSign(gate: String?, terminal: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (terminal != null) {
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Ink).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("T$terminal", color = Color.White, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Amber).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(gate ?: "—", color = Ink, fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun FlightTicket(f: Flight, now: Long, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(
            Modifier.fillMaxWidth().clip(TicketHalf(true)).background(surface)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirlineLogo(f.airlineIcao, f.airlineIata, height = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.flightNumber, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(f.airlineName ?: "", style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1)
                }
                StatusPill(f)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(f.depIata ?: "?", style = MaterialTheme.typography.displayMedium, fontSize = 48.sp)
                    Text(f.depCity ?: f.depName ?: "", style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1)
                }
                Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    val line = MaterialTheme.colorScheme.outline
                    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                        drawLine(line, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    }
                    Box(Modifier.background(surface).padding(horizontal = 6.dp)) {
                        Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(f.arrIata ?: "?", style = MaterialTheme.typography.displayMedium, fontSize = 48.sp)
                    Text(f.arrCity ?: f.arrName ?: "", style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(TicketHalf(false)).background(surface)) {
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp)) {
                drawLine(
                    Color(0x66808080), Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TicketCell("DATA") {
                    Text(fmtDayMonth(f.depScheduledUtc, f.depTz, f.dateLocal), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
                TicketCell("PARTENZA") {
                    val rev = f.depRevisedUtc
                    val sched = f.depScheduledUtc
                    Text(
                        fmtTime(sched, f.depTz) + if (rev != null && sched != null && rev != sched) " → ${fmtTime(rev, f.depTz)}" else "",
                        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp
                    )
                }
                if (f.completed) {
                    TicketCell("DURATA") {
                        Text(fmtDuration(f.durationMin), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                } else {
                    TicketCell("GATE") { GateSign(f.depGate, f.depTerminal) }
                }
            }
            if (!f.completed) {
                val countdown = fmtCountdown(f.depRevisedUtc ?: f.depScheduledUtc, now)
                Box(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        if (countdown != null) "TRA  ${countdown.uppercase()}" else statusIt(f.status).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlightDialog(onManual: () -> Unit, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi volo") },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.uppercase() },
                    label = { Text("Numero volo (es. AZ610)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { pickDate = true }) { Text("Data partenza: $date") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onManual) { Text("Volo non trovato? Inseriscilo a mano") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.isNotBlank(),
                onClick = { onConfirm(number, date.toString()) }
            ) { Text("Cerca") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )

    if (pickDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = st.selectedDateMillis
                    if (ms != null) {
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = st)
        }
    }
}

@Composable
fun CandidateDialog(list: List<FlightDto>, onChoose: (FlightDto) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Più tratti trovati: scegli il tuo") },
        text = {
            Column {
                list.forEach { d ->
                    val a = d.departure?.airport
                    val b = d.arrival?.airport
                    val label = "${a?.iata ?: "?"} → ${b?.iata ?: "?"}  " +
                        "(${d.departure?.scheduledTime?.local ?: d.departure?.scheduledTime?.utc ?: ""})"
                    TextButton(onClick = { onChoose(d) }) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
