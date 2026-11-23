package app.flightcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.flightcompanion.data.AirlineDb
import app.flightcompanion.data.AirportDb
import app.flightcompanion.data.ManualFlight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private fun parseTime(s: String): LocalTime? {
    val m = Regex("""^(\d{1,2}):?(\d{2})$""").find(s.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
}

/** Inserimento a mano di un volo (anche molto vecchio): finisce direttamente nello storico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFlightScreen(vm: MainViewModel, onBack: () -> Unit) {
    var number by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var dep by remember { mutableStateOf("") }
    var arr by remember { mutableStateOf("") }
    var depTime by remember { mutableStateOf("") }
    var arrTime by remember { mutableStateOf("") }
    var dayOffset by remember { mutableIntStateOf(0) }
    var delayDep by remember { mutableStateOf("") }
    var delayArr by remember { mutableStateOf("") }
    var airline by remember { mutableStateOf("") }
    var icao by remember { mutableStateOf("") }
    var airlineTouched by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }

    val depInfo = remember(dep) { if (dep.length == 3) AirportDb.get(dep) else null }
    val arrInfo = remember(arr) { if (arr.length == 3) AirportDb.get(arr) else null }

    LaunchedEffect(number) {
        if (!airlineTouched) {
            val a = AirlineDb.fromFlightNumber(number)
            if (a != null) {
                airline = a.name
                icao = a.icao
            }
        }
    }

    val depT = parseTime(depTime)
    val arrT = parseTime(arrTime)
    val valid = number.trim().length >= 3 &&
        depInfo != null && arrInfo != null && dep != arr &&
        (depTime.isBlank() || depT != null) &&
        (arrTime.isBlank() || arrT != null) &&
        (delayDep.isBlank() || delayDep.trim().toIntOrNull() != null) &&
        (delayArr.isBlank() || delayArr.trim().toIntOrNull() != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inserisci un volo a mano") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Il volo viene salvato direttamente nello storico. Gli orari sono quelli locali degli aeroporti.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.uppercase() },
                label = { Text("Numero volo (es. AZ610)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Data di partenza: $date")
            }

            OutlinedTextField(
                value = dep,
                onValueChange = { dep = it.uppercase().take(3) },
                label = { Text("Aeroporto di partenza (codice IATA, es. MXP)") },
                singleLine = true,
                isError = dep.length == 3 && depInfo == null,
                supportingText = {
                    Text(
                        when {
                            depInfo != null -> "${depInfo.name} · ${depInfo.city}"
                            dep.length == 3 -> "Codice non trovato"
                            else -> ""
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = arr,
                onValueChange = { arr = it.uppercase().take(3) },
                label = { Text("Aeroporto di arrivo (codice IATA)") },
                singleLine = true,
                isError = arr.length == 3 && arrInfo == null,
                supportingText = {
                    Text(
                        when {
                            arrInfo != null -> "${arrInfo.name} · ${arrInfo.city}"
                            arr.length == 3 -> "Codice non trovato"
                            else -> ""
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = depTime,
                    onValueChange = { depTime = it },
                    label = { Text("Partenza (HH:mm)") },
                    singleLine = true,
                    isError = depTime.isNotBlank() && depT == null,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = arrTime,
                    onValueChange = { arrTime = it },
                    label = { Text("Arrivo (HH:mm)") },
                    singleLine = true,
                    isError = arrTime.isNotBlank() && arrT == null,
                    modifier = Modifier.weight(1f)
                )
            }
            Text("Giorno di arrivo rispetto alla partenza", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf(-1 to "Giorno prima", 0 to "Stesso giorno", 1 to "Giorno dopo")) {
                    FilterChip(selected = dayOffset == value, onClick = { dayOffset = value }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = delayDep,
                    onValueChange = { delayDep = it },
                    label = { Text("Ritardo partenza (min)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = delayArr,
                    onValueChange = { delayArr = it },
                    label = { Text("Ritardo arrivo (min)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Ritardi facoltativi: usa un numero negativo se il volo era in anticipo. " +
                    "Servono gli orari per calcolare durata e ritardi.",
                style = MaterialTheme.typography.bodySmall
            )

            Text("Compagnia e aereo (facoltativi)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = airline,
                onValueChange = { airline = it; airlineTouched = true },
                label = { Text("Compagnia") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = icao,
                onValueChange = { icao = it.uppercase().take(3); airlineTouched = true },
                label = { Text("Codice ICAO compagnia (per il logo, es. BAW)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modello aereo (es. Airbus A320)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reg,
                onValueChange = { reg = it.uppercase() },
                label = { Text("Matricola (es. EI-DEN)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = valid,
                onClick = {
                    val iataCode = AirlineDb.byIcao(icao)?.iata ?: number.replace(" ", "").take(2)
                    vm.addManual(
                        ManualFlight(
                            flightNumber = number,
                            date = date,
                            depIata = dep,
                            arrIata = arr,
                            depTime = depT,
                            arrTime = arrT,
                            arrDayOffset = dayOffset,
                            delayDep = delayDep.trim().toIntOrNull(),
                            delayArr = delayArr.trim().toIntOrNull(),
                            airlineName = airline.trim().ifBlank { null },
                            airlineIata = iataCode,
                            airlineIcao = icao.ifBlank { null },
                            aircraftModel = model.trim().ifBlank { null },
                            aircraftReg = reg.trim().ifBlank { null }
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva nello storico") }
        }
    }

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
