package com.eltnegcellist.emma

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Local WebAssembly worker shared byte-for-byte with Web; never uploads speech. */
internal class MultilingualRuntime private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val sequence = AtomicLong()
    private val requests = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    @Volatile private var loaded = CompletableFuture<Unit>()
    private var view: WebView? = null
    @Volatile var ready = false
        private set
    @Volatile private var onProgress: (Int?) -> Unit = {}

    private inner class Bridge {
        @JavascriptInterface fun loaded() { loaded.complete(Unit) }
        @JavascriptInterface fun progress(json: String) {
            val p = JSONObject(json); val total = p.optDouble("total", 0.0)
            onProgress(if(total>0) ((p.optDouble("loaded",0.0)/total)*100).toInt().coerceIn(0,100) else null)
        }
        @JavascriptInterface fun complete(id: String, json: String) {
            val future = requests.remove(id) ?: return
            runCatching { JSONObject(json) }.onSuccess { value ->
                if(value.has("error")) future.completeExceptionally(IllegalStateException(value.getString("error")))
                else future.complete(value.getJSONObject("result"))
            }.onFailure { future.completeExceptionally(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureView() {
        main.post {
            if(view!=null) return@post
            runCatching {
                view = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    addJavascriptInterface(Bridge(), "AndroidMultilingual")
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            ready=false
                            val failure=IllegalStateException("Model runtime stopped. Please retry.")
                            loaded.completeExceptionally(failure)
                            for((id,future) in requests){requests.remove(id);future.completeExceptionally(failure)}
                            this@MultilingualRuntime.view=null;view?.destroy();loaded=CompletableFuture()
                            return true
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                            val url = request.url
                            if(url.scheme!="https" || url.host!="appassets.androidplatform.net") {
                                // The worker can only GET the pinned model files from Hugging Face.
                                if(request.method=="GET" && url.scheme=="https" && url.host=="huggingface.co") return null
                                return WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),ByteArrayInputStream(byteArrayOf()))
                            }
                            val path = Uri.decode(url.path.orEmpty()).removePrefix("/assets/")
                            if(!path.startsWith("multilingual/") || path.split('/').any { it==".." }) return null
                            return runCatching {
                                val mime=when { path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript"
                                    path.endsWith(".wasm") -> "application/wasm"
                                    path.endsWith(".json") -> "application/json"
                                    path.endsWith(".html") -> "text/html"
                                    else -> "application/octet-stream" }
                                WebResourceResponse(mime,"UTF-8",200,"OK",mapOf("Access-Control-Allow-Origin" to "*"),context.assets.open(path))
                            }.getOrElse { WebResourceResponse("text/plain","UTF-8",404,"Not found",emptyMap(),ByteArrayInputStream(byteArrayOf())) }
                        }
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/multilingual/multilingual-runtime.html")
                }
            }.onFailure { loaded.completeExceptionally(it) }
        }
    }

    fun request(type: String, payload: JSONObject = JSONObject()): JSONObject {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Inference must not block the UI" }
        ensureView(); loaded.get(60,TimeUnit.SECONDS)
        val id=sequence.incrementAndGet().toString(); val future=CompletableFuture<JSONObject>();requests[id]=future
        main.post { view?.evaluateJavascript("window.localMultilingual.request(${JSONObject.quote(id)},${JSONObject.quote(type)},$payload)",null) }
        return try { future.get(20,TimeUnit.MINUTES) } finally { requests.remove(id) }
    }

    @Synchronized fun prepare(progress: (Int?) -> Unit = {}): Result<Unit> = runCatching {
        onProgress=progress
        try { request("init");ready=true;context.getSharedPreferences("emma_speech",Context.MODE_PRIVATE).edit().putBoolean("multilingual_models_cached_v1",true).apply() }
        finally { onProgress={} }
    }
    fun release() {
        ready=false
        for((id,future) in requests){requests.remove(id);future.completeExceptionally(IllegalStateException("Language operation cancelled"))}
        main.post { view?.evaluateJavascript("window.localMultilingual?.cancel()",null) }
    }
    companion object {
        @Volatile private var instance: MultilingualRuntime? = null
        fun get(context: Context): MultilingualRuntime = instance ?: synchronized(this) {
            instance ?: MultilingualRuntime(context.applicationContext).also { instance=it }
        }
        fun isCached(context: Context) = context.getSharedPreferences("emma_speech",Context.MODE_PRIVATE).getBoolean("multilingual_models_cached_v1",false)
    }
}
