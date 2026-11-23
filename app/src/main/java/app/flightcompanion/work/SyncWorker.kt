package app.flightcompanion.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.flightcompanion.data.Graph

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.repo.syncDue()
        return Result.success()
    }
}
