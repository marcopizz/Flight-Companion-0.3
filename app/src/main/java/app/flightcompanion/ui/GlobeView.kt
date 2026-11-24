package app.flightcompanion.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import app.flightcompanion.data.Flight
import app.flightcompanion.data.Track
import org.json.JSONArray
import org.json.JSONObject

class GlobeBridge(private val onOpen: (Long) -> Unit) {
    @JavascriptInterface
    fun openFlight(id: String) {
        val v = id.toLongOrNull() ?: return
        Handler(Looper.getMainLooper()).post { onOpen(v) }
    }
}

private class GlobeState {
    var ready = false
    var pending: String? = null
}

fun globeJson(flights: List<Flight>, focusId: Long?, style: String): String {
    val arr = JSONArray()
    for (f in flights) {
        val la1 = f.depLat
        val lo1 = f.depLon
        val la2 = f.arrLat
        val lo2 = f.arrLon
        if (la1 == null || lo1 == null || la2 == null || lo2 == null) continue
        val o = JSONObject()
        o.put("id", f.id)
        o.put("dep", f.depIata ?: "")
        o.put("arr", f.arrIata ?: "")
        o.put("depLat", la1)
        o.put("depLng", lo1)
        o.put("arrLat", la2)
        o.put("arrLng", lo2)
        o.put("kind", if (f.completed) "past" else "future")
        val tr = Track.decode(f.trackPoints)
        if (f.trackState == 1 && tr.size >= 3) {
            val t = JSONArray()
            for ((la, lo) in tr) {
                t.put(JSONArray().put(la).put(lo))
            }
            o.put("track", t)
        }
        arr.put(o)
    }
    val root = JSONObject()
    root.put("flights", arr)
    root.put("focus", focusId ?: JSONObject.NULL)
    root.put("style", style)
    return root.toString()
}

/** Globo 3D (globe.gl dentro una WebView, tutto offline): si ruota, si ingrandisce, si tocca una rotta. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FlightGlobe(
    flights: List<Flight>,
    modifier: Modifier = Modifier,
    focusId: Long? = null,
    style: String = "sat",
    onOpenFlight: (Long) -> Unit
) {
    val json = remember(flights, focusId, style) { globeJson(flights, focusId, style) }
    val openState = rememberUpdatedState(onOpenFlight)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()
            val state = GlobeState()
            WebView(ctx).apply {
                tag = state
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(GlobeBridge { openState.value(it) }, "AndroidBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        state.ready = true
                        state.pending?.let { view.evaluateJavascript("setData($it)", null) }
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/globe/index.html")
            }
        },
        update = { wv ->
            val st = wv.tag as GlobeState
            if (st.pending != json) {
                st.pending = json
                if (st.ready) wv.evaluateJavascript("setData($json)", null)
            }
        },
        onRelease = { wv -> wv.destroy() }
    )
}

/** Selettore Satellite / Mappa da sovrapporre al globo. */
@Composable
fun MapStyleToggle(style: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color(0xB3000000), RoundedCornerShape(8.dp))
            .padding(3.dp)
    ) {
        for ((key, label) in listOf("sat" to "Satellite", "map" to "Mappa")) {
            val on = style == key
            Text(
                label,
                color = if (on) Color(0xFF0F1B2D) else Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(if (on) Color(0xFFF2B200) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onChange(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
