package app.flightcompanion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flightcompanion.data.AirportDb
import app.flightcompanion.data.AirportInfo
import app.flightcompanion.data.Graph
import app.flightcompanion.data.SavedAirport
import app.flightcompanion.data.flagEmoji
import kotlinx.coroutines.launch

/** Elenco degli aeroporti: preferiti in alto, poi gli altri dal più recente. */
@Composable
fun AirportsScreen(onOpen: (String) -> Unit) {
    val list by Graph.repo.airports().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val favs = list.filter { it.favorite }
    val others = list.filter { !it.favorite }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (list.isEmpty()) {
            item {
                Text(
                    "Nessun aeroporto. Tocca + per aggiungerne uno: quelli dei voli in programma compaiono da soli.",
                    Modifier.padding(8.dp)
                )
            }
        }
        if (favs.isNotEmpty()) {
            item { SectionLabel("Preferiti") }
            items(favs, key = { it.iata }) { a ->
                AirportRow(a, onOpen) { scope.launch { Graph.repo.setFavorite(a.iata, !a.favorite) } }
            }
        }
        if (others.isNotEmpty()) {
            item { SectionLabel(if (favs.isNotEmpty()) "Altri aeroporti" else "Aeroporti") }
            items(others, key = { it.iata }) { a ->
                AirportRow(a, onOpen) { scope.launch { Graph.repo.setFavorite(a.iata, !a.favorite) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleLarge,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

@Composable
private fun AirportRow(a: SavedAirport, onOpen: (String) -> Unit, onToggleFavorite: () -> Unit) {
    val info = remember(a.iata) { AirportDb.get(a.iata) }
    Card(Modifier.fillMaxWidth().clickable { onOpen(a.iata) }) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.iata, style = MaterialTheme.typography.displaySmall, fontSize = 40.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info?.name ?: a.iata, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    "${flagEmoji(info?.country)} ${info?.city ?: ""}  ·  ${info?.icao ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (a.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (a.favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                    tint = if (a.favorite) androidx.compose.ui.graphics.Color(0xFFF2B200) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Ricerca durante la digitazione: riconosce codice, nome o città e propone gli aeroporti. */
@Composable
fun AddAirportDialog(onDismiss: () -> Unit, onPick: (AirportInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { AirportDb.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi aeroporto") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Codice, nome o città (es. MXP, Malpensa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (query.trim().length >= 2 && results.isEmpty()) {
                    Text("Nessun aeroporto trovato.", style = MaterialTheme.typography.bodySmall)
                }
                for (a in results) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(a.iata, fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(56.dp))
                        Column {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(
                                "${flagEmoji(a.country)} ${a.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}
