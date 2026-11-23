package app.flightcompanion.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.flightcompanion.MainActivity
import kotlin.math.abs

object Notifier {
    private const val CH = "voli"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CH, "Aggiornamenti voli", NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun send(f: Flight, kind: Int, text: String) {
        val ctx = Graph.appContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val title = "${f.flightNumber}  ${f.depIata ?: "?"} → ${f.arrIata ?: "?"}"
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((f.id * 10 + kind).toInt(), n)
        } catch (e: SecurityException) {
            // permesso notifiche negato
        }
    }

    /** Confronta il volo prima e dopo l'aggiornamento e notifica le variazioni rilevanti. */
    fun diff(old: Flight, nw: Flight) {
        if (nw.depGate != null && nw.depGate != old.depGate) {
            send(nw, 1, "Gate di partenza: ${old.depGate ?: "non assegnato"} → ${nw.depGate}")
        }
        if (nw.depTerminal != null && nw.depTerminal != old.depTerminal) {
            send(nw, 2, "Terminal di partenza: ${old.depTerminal ?: "n.d."} → ${nw.depTerminal}")
        }
        val od = old.depRevisedUtc ?: old.depScheduledUtc
        val nd = nw.depRevisedUtc ?: nw.depScheduledUtc
        if (od != null && nd != null && abs(nd - od) >= 10 * 60_000L) {
            val delta = ((nd - od) / 60_000L).toInt()
            send(nw, 3, "Partenza ${if (delta > 0) "posticipata" else "anticipata"} di ${abs(delta)} min: ora ${fmtTime(nd, nw.depTz)}")
        }
        val oa = old.arrRevisedUtc ?: old.arrScheduledUtc
        val na = nw.arrRevisedUtc ?: nw.arrScheduledUtc
        if (oa != null && na != null && abs(na - oa) >= 10 * 60_000L) {
            val delta = ((na - oa) / 60_000L).toInt()
            send(nw, 4, "Arrivo ${if (delta > 0) "posticipato" else "anticipato"} di ${abs(delta)} min: ora ${fmtTime(na, nw.arrTz)}")
        }
        if (nw.baggageBelt != null && nw.baggageBelt != old.baggageBelt) {
            send(nw, 5, "Nastro bagagli: ${nw.baggageBelt}")
        }
        if (nw.status != old.status &&
            nw.status in setOf("Canceled", "CanceledUncertain", "Diverted", "Boarding", "GateClosed")
        ) {
            send(nw, 6, "Stato: ${statusIt(nw.status)}")
        }
    }
}
