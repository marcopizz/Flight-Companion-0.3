package app.flightcompanion

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.flightcompanion.data.Graph
import app.flightcompanion.data.Notifier
import app.flightcompanion.work.SyncWorker
import java.util.concurrent.TimeUnit

class FlightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannel(this)
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("flight-sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
