package app.flightcompanion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.flightcompanion.data.AirportDb
import app.flightcompanion.data.AirportInfo
import app.flightcompanion.data.Graph
import app.flightcompanion.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

private fun normGate(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }.trimStart('0')

/** Mappa dell'aeroporto: OpenStreetMap (con il gate di interesse in giallo) oppure PDF caricato dall'utente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportMapScreen(iata: String, gate: String?, onBack: () -> Unit) {
    val info = remember(iata) { AirportDb.get(iata) }
    val saved by Graph.repo.airport(iata).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("osm") }
    var gates by remember { mutableStateOf(Graph.repo.cachedGates(iata)) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val pdfPath = saved?.pdfPath

    // Per evidenziare il gate servono le coordinate: si scaricano una volta da OpenStreetMap e restano in cache
    LaunchedEffect(iata, gate) {
        if (gate != null && gates.isEmpty()) {
            loading = true
            status = Graph.repo.downloadGates(iata)
            gates = Graph.repo.cachedGates(iata)
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                Graph.repo.addAirport(iata)
                val err = Graph.repo.importPdf(iata, uri)
                status = err
                if (err == null) mode = "pdf"
            }
        }
    }

    val target = remember(gates, gate) {
        if (gate == null) null else {
            val n = normGate(gate)
            gates.firstOrNull { normGate(it.ref) == n }
        }
    }
    val notFound = gate != null && gates.isNotEmpty() && target == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MAPPA · $iata", style = MaterialTheme.typography.headlineMedium, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
                },
                actions = {
                    IconButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = "Carica mappa PDF")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "osm", onClick = { mode = "osm" }, label = { Text("OpenStreetMap") })
                FilterChip(
                    selected = mode == "pdf",
                    onClick = { if (pdfPath != null) mode = "pdf" else picker.launch(arrayOf("application/pdf")) },
                    label = { Text(if (pdfPath != null) "Mappa PDF" else "Carica PDF") }
                )
                if (mode == "pdf" && pdfPath != null) {
                    TextButton(onClick = { scope.launch { Graph.repo.removePdf(iata); mode = "osm" } }) { Text("Rimuovi PDF") }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val msg = when {
                status != null -> status
                notFound -> "Gate $gate non trovato in OpenStreetMap: puoi comunque cercarlo sulla mappa."
                target != null -> "Gate ${target.ref} evidenziato in giallo."
                else -> null
            }
            if (msg != null) {
                Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (info == null) {
                    Text("Aeroporto sconosciuto.", Modifier.padding(16.dp))
                } else if (mode == "pdf" && pdfPath != null) {
                    PdfViewer(pdfPath)
                } else {
                    OsmGateMap(info, target)
                }
            }
        }
    }
}

private fun gateIcon(ctx: Context, text: String): Drawable {
    val d = ctx.resources.displayMetrics.density
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 16 * d
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val w = (tp.measureText(text) + 22 * d).toInt().coerceAtLeast((36 * d).toInt())
    val h = (30 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 196, 0) }
    c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 8 * d, 8 * d, fill)
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
        color = android.graphics.Color.BLACK
    }
    c.drawRoundRect(RectF(d, d, w - d, h - d), 7 * d, 7 * d, edge)
    c.drawText(text, w / 2f, h / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
    return BitmapDrawable(ctx.resources, bmp)
}

@Composable
private fun OsmGateMap(info: AirportInfo, target: Repository.Gate?) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        val conf = Configuration.getInstance()
        conf.userAgentValue = ctx.packageName
        conf.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        conf.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(info.lat, info.lon))
            overlays.add(CopyrightOverlay(ctx))
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { mv ->
            val key = target?.ref ?: ""
            if (mv.tag != key) {
                mv.tag = key
                mv.overlays.removeAll { it is Marker }
                if (target != null) {
                    val m = Marker(mv)
                    m.position = GeoPoint(target.lat, target.lon)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.icon = gateIcon(ctx, target.ref)
                    m.title = "Gate ${target.ref}"
                    mv.overlays.add(m)
                    mv.controller.setZoom(18.0)
                    mv.controller.animateTo(GeoPoint(target.lat, target.lon))
                }
                mv.invalidate()
            }
        }
    )
}

@Composable
private fun PdfViewer(path: String) {
    var page by remember(path) { mutableIntStateOf(0) }
    var count by remember(path) { mutableIntStateOf(1) }
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var scale by remember(path, page) { mutableFloatStateOf(1f) }
    var offset by remember(path, page) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(path, page) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                count = renderer.pageCount
                val idx = page.coerceIn(0, renderer.pageCount - 1)
                val p = renderer.openPage(idx)
                val ratio = p.height.toFloat() / p.width
                val w = if (ratio > 3f) (2000 / ratio * 1.5f).toInt() else 2000
                val h = (w * ratio).toInt().coerceAtLeast(1)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                b.eraseColor(android.graphics.Color.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                p.close()
                renderer.close()
                bmp = b.asImageBitmap()
                error = null
            } catch (e: Exception) {
                error = "Impossibile aprire il PDF: ${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { box = it }
                .pointerInput(path, page) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(1f, 6f)
                        scale = ns
                        val maxX = box.width * (ns - 1f) / 2f
                        val maxY = box.height * (ns - 1f) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
                .pointerInput(path, page) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1.2f) 1f else 2.5f
                        offset = Offset.Zero
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val b = bmp
            if (b != null) {
                Image(
                    b,
                    contentDescription = "Mappa PDF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                )
            }
            if (error != null) Text(error ?: "", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        if (count > 1) {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(enabled = page > 0, onClick = { page-- }) { Text("‹  Precedente") }
                Text("Pagina ${page + 1} / $count", fontWeight = FontWeight.Medium)
                TextButton(enabled = page < count - 1, onClick = { page++ }) { Text("Successiva  ›") }
            }
        }
    }
}
