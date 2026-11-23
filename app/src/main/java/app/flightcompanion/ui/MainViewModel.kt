package app.flightcompanion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flightcompanion.data.Flight
import app.flightcompanion.data.FlightDto
import app.flightcompanion.data.Graph
import app.flightcompanion.data.Lookup
import app.flightcompanion.data.ManualFlight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repo = Graph.repo

    val upcoming: StateFlow<List<Flight>> =
        repo.upcoming().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val past: StateFlow<List<Flight>> =
        repo.past().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var candidates by mutableStateOf<List<FlightDto>>(emptyList())
        private set

    private var pendingNumber = ""
    private var pendingDate = ""

    fun search(number: String, date: String) {
        viewModelScope.launch {
            busy = true
            when (val r = repo.lookup(number, date)) {
                is Lookup.Found -> {
                    if (r.list.size == 1) {
                        save(r.list[0], number, date)
                    } else {
                        pendingNumber = number
                        pendingDate = date
                        candidates = r.list
                    }
                }
                is Lookup.NotFound -> message = "Nessun volo trovato per quel numero e quella data."
                is Lookup.Error -> message = r.msg
            }
            busy = false
        }
    }

    fun choose(dto: FlightDto) {
        candidates = emptyList()
        viewModelScope.launch {
            busy = true
            save(dto, pendingNumber, pendingDate)
            busy = false
        }
    }

    fun dismissChoice() {
        candidates = emptyList()
    }

    private suspend fun save(dto: FlightDto, number: String, date: String) {
        repo.add(dto, number, date)
        message = "Volo aggiunto."
    }

    fun addManual(m: ManualFlight) {
        viewModelScope.launch {
            busy = true
            repo.addManual(m)
            message = "Volo aggiunto allo storico."
            busy = false
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            repo.syncDue()
        }
    }
}
