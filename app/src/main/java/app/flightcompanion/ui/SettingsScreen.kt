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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.flightcompanion.data.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var key by remember { mutableStateOf(Prefs.apiKey) }
    var show by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var osId by remember { mutableStateOf(Prefs.openskyId) }
    var osSecret by remember { mutableStateOf(Prefs.openskySecret) }
    var miles by remember { mutableStateOf(Prefs.useMiles) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Chiave API (AeroDataBox su RapidAPI)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; saved = false },
                label = { Text("X-RapidAPI-Key") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { show = !show }) { Text(if (show) "Nascondi" else "Mostra") }
                Button(onClick = {
                    Prefs.apiKey = key
                    Prefs.openskyId = osId
                    Prefs.openskySecret = osSecret
                    saved = true
                }) { Text(if (saved) "Salvata" else "Salva") }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Mostra le distanze in miglia (invece che in km)", Modifier.weight(1f))
                Switch(checked = miles, onCheckedChange = { miles = it; Prefs.useMiles = it })
            }

            Text("Rotte reali (OpenSky Network)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = osId,
                onValueChange = { osId = it; saved = false },
                label = { Text("client_id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = osSecret,
                onValueChange = { osSecret = it; saved = false },
                label = { Text("client_secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Facoltativo ma consigliato: crea un account gratuito su opensky-network.org, apri la pagina " +
                    "Account, crea un \"API client\" e copia client_id e client_secret. " +
                    "Servono solo per recuperare la rotta reale dei voli degli ultimi 30 giorni.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Chiamate API effettuate questo mese: ${Prefs.callsThisMonth()}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Il piano gratuito ha una quota mensile limitata, e alcune chiamate possono " +
                    "consumare più di una \"unità\": controlla il consumo reale nella dashboard di RapidAPI.",
                style = MaterialTheme.typography.bodySmall
            )

            Text("Come ottenere la chiave", fontWeight = FontWeight.Bold)
            Text(
                "1. Crea un account gratuito su rapidapi.com.\n" +
                    "2. Cerca \"AeroDataBox\" e iscriviti al piano Basic (gratuito).\n" +
                    "3. Copia il valore di X-RapidAPI-Key mostrato negli esempi di codice e incollalo qui sopra.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Per risparmiare quota l'app interroga l'API solo in momenti chiave " +
                    "(all'inserimento, 72h, 24h, 6h, 3h prima, poi ogni 30 minuti vicino alla partenza).",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
