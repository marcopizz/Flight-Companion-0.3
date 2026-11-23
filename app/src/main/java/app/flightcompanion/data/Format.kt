package app.flightcompanion.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun zoneOf(tz: String?): ZoneId =
    try {
        if (tz != null) ZoneId.of(tz) else ZoneOffset.UTC
    } catch (e: Exception) {
        ZoneOffset.UTC
    }

private val dtFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val tFmt = DateTimeFormatter.ofPattern("HH:mm")

fun fmtDateTime(ms: Long?, tz: String?): String =
    if (ms == null) "—" else dtFmt.format(Instant.ofEpochMilli(ms).atZone(zoneOf(tz)))

fun fmtTime(ms: Long?, tz: String?): String =
    if (ms == null) "—" else tFmt.format(Instant.ofEpochMilli(ms).atZone(zoneOf(tz)))

fun fmtDuration(min: Int?): String {
    if (min == null) return "—"
    val a = abs(min)
    return "${a / 60}h ${a % 60}m"
}

fun fmtDelay(min: Int?): String =
    when {
        min == null -> "—"
        min > 0 -> "+$min min"
        min < 0 -> "$min min (in anticipo)"
        else -> "puntuale"
    }

fun statusIt(s: String?): String = when (s) {
    "Expected" -> "Programmato"
    "EnRoute" -> "In volo"
    "CheckIn" -> "Check-in aperto"
    "Boarding" -> "Imbarco"
    "GateClosed" -> "Gate chiuso"
    "Departed" -> "Partito"
    "Delayed" -> "In ritardo"
    "Approaching" -> "In avvicinamento"
    "Arrived" -> "Atterrato"
    "Canceled", "CanceledUncertain" -> "Cancellato"
    "Diverted" -> "Dirottato"
    null -> "—"
    else -> s
}

fun countryName(code: String?): String =
    if (code.isNullOrBlank()) "Sconosciuto"
    else Locale("", code).getDisplayCountry(Locale.ITALIAN).ifBlank { code }

fun kmToMiles(km: Double): Double = km * 0.621371

fun flagEmoji(code: String?): String {
    if (code == null || code.length != 2) return "🏳"
    val c = code.uppercase()
    if (!c.all { it in 'A'..'Z' }) return "🏳"
    return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) +
        String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
}

/** Tempo mancante in forma compatta ("3 g 4 h", "5 h 12 min", "40 min"); null se già passato. */
fun fmtCountdown(targetMs: Long?, nowMs: Long): String? {
    if (targetMs == null) return null
    val diff = targetMs - nowMs
    if (diff <= 0) return null
    val totalMin = diff / 60_000
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return when {
        d > 0 -> "$d g $h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}

fun fmtAge(years: Double?): String {
    if (years == null) return "—"
    return when {
        years < 1.0 / 12 -> "${(years * 365).toInt()} giorni"
        years < 1.0 -> "${(years * 12).toInt()} mesi"
        else -> String.format(Locale.ITALY, "%.0f anni", years)
    }
}

fun fmtHm(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"

fun mrzLine(s: String): String {
    val t = s.uppercase().replace(Regex("[^A-Z0-9<]"), "<")
    return if (t.length >= 44) t.take(44) else t.padEnd(44, '<')
}

fun manufacturerOf(model: String?): String? {
    if (model.isNullOrBlank()) return null
    val m = model.trim()
    val keys = listOf(
        "Boeing", "Airbus", "Embraer", "ATR", "Bombardier", "De Havilland", "Canadair",
        "McDonnell", "Fokker", "Saab", "Sukhoi", "Comac", "Tupolev", "Ilyushin", "Cessna",
        "Dornier", "British Aerospace"
    )
    keys.firstOrNull { m.contains(it, ignoreCase = true) }?.let { return it }
    if (Regex("""\bA\d{3}""").containsMatchIn(m)) return "Airbus"
    if (Regex("""\b7[0-9]7\b""").containsMatchIn(m)) return "Boeing"
    return m.substringBefore(' ')
}

enum class AcCat { NARROW, REGIONAL, TURBOPROP, WIDE, JUMBO }

fun aircraftCategory(model: String?): AcCat {
    val m = (model ?: "").uppercase()
    fun has(vararg k: String) = k.any { m.contains(it) }
    return when {
        has("A380", "747") -> AcCat.JUMBO
        has("A330", "A340", "A350", "A300", "A310", "767", "777", "787", "MD-11", "MD11", "DC-10", "L-1011", "IL-96") -> AcCat.WIDE
        has("ATR", "DASH 8", "DHC-8", "Q400", "SAAB 340", "SAAB 2000", "FOKKER 50", "DHC-6", "JETSTREAM") -> AcCat.TURBOPROP
        has("CRJ", "E170", "E175", "E190", "E195", "E-JET", "EMBRAER", "ERJ", "RJ", "SUPERJET", "SSJ", "BAE 146", "BAE-146") -> AcCat.REGIONAL
        else -> AcCat.NARROW
    }
}

/** "24 NOV" nel fuso dell'aeroporto di partenza (o dalla data inserita se manca l'orario). */
fun fmtDayMonth(ms: Long?, tz: String?, dateLocal: String): String {
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    val text = try {
        if (ms != null) fmt.format(Instant.ofEpochMilli(ms).atZone(zoneOf(tz)))
        else fmt.format(java.time.LocalDate.parse(dateLocal))
    } catch (e: Exception) {
        dateLocal
    }
    return text.uppercase(Locale.ITALIAN)
}
