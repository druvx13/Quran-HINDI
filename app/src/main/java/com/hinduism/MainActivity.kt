package com.hinduism

import android.Manifest
import android.app.DownloadManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.hinduism.databinding.ActivityMainBinding

/**
 * Main activity hosting the WebView that renders bundled Hinduism content.
 * Provides home navigation, night-mode toggle, and font-size controls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // SharedPreferences keys
    private val prefsName = "HinduismPrefs"
    private val prefLastUrl = "last_url"
    private val prefNightMode = "night_mode"
    private val prefFontScale = "font_scale"

    // Default font scale (percentage, 100 = normal)
    private var fontScale = 100
    private var nightMode = false

    // Flag to suppress spinner's item-selected callback while we are programmatically updating it
    private var suppressSpinnerCallback = false

    // Pending blob download bytes held while awaiting runtime permission on API 23–28
    private var pendingBlobBytes: ByteArray? = null
    private var pendingBlobName: String? = null
    private var pendingBlobMime: String? = null

    // Runtime permission launcher (needed for WRITE_EXTERNAL_STORAGE on API 23–28)
    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val bytes = pendingBlobBytes
                val name  = pendingBlobName ?: "hinduism-download"
                val mime  = pendingBlobMime ?: "application/octet-stream"
                if (bytes != null) {
                    val ok = saveBytesToDownloads(bytes, name, mime)
                    Toast.makeText(
                        this,
                        if (ok) getString(R.string.download_saved, name) else getString(R.string.download_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.download_permission_denied), Toast.LENGTH_LONG).show()
            }
            pendingBlobBytes = null
            pendingBlobName  = null
            pendingBlobMime  = null
        }

    /**
     * JavaScript bridge injected into the WebView as "Android".
     * Used to receive base64-encoded blob data from download.html so it can be
     * saved to the Downloads folder on the device.
     */
    inner class DownloadBridge {
        @JavascriptInterface
        fun downloadBase64(base64Data: String, filename: String, mimeType: String) {
            // base64Data is a data-URI: "data:<mime>;base64,<data>"
            val commaIdx = base64Data.indexOf(',')
            val pure = if (commaIdx >= 0) base64Data.substring(commaIdx + 1) else base64Data
            val bytes = try {
                Base64.decode(pure, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                }
                return
            }
            val savedName = sanitizeFilename(filename.ifBlank { "hinduism-download" })
            val savedMime = mimeType.ifBlank { "application/octet-stream" }

            // On Android < 10 we need WRITE_EXTERNAL_STORAGE at runtime
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread {
                    pendingBlobBytes = bytes
                    pendingBlobName  = savedName
                    pendingBlobMime  = savedMime
                    requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                return
            }

            val ok = saveBytesToDownloads(bytes, savedName, savedMime)
            runOnUiThread {
                if (ok) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.download_saved, savedName),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Save raw bytes to the public Downloads directory. Returns true on success. */
    private fun saveBytesToDownloads(bytes: ByteArray, filename: String, mimeType: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore (no storage permission required)
                val resolver = contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
                cv.clear()
                cv.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, cv, null, null)
            } else {
                // Android 9 and below — use classic public Downloads folder
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = java.io.File(dir, filename)
                file.outputStream().use { it.write(bytes) }
            }
            true
        } catch (e: java.io.IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    // AudioService binding
    private var audioService: AudioService? = null
    private var audioServiceBound = false
    private val audioConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioService.LocalBinder
            audioService = localBinder?.getService()
            audioServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            audioServiceBound = false
            audioService = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        nightMode = prefs.getBoolean(prefNightMode, false)
        fontScale = prefs.getInt(prefFontScale, 100)
        applyNightMode(nightMode)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Handle back press with the modern OnBackPressedCallback API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setupNavSpinner()
        setupWebView()
        setupFontButtons()

        // Bind to AudioService
        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, audioConnection, Context.BIND_AUTO_CREATE)
        }

        // Restore last viewed page or open index
        val lastUrl = prefs.getString(prefLastUrl, ASSET_INDEX) ?: ASSET_INDEX
        binding.webView.loadUrl(lastUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (audioServiceBound) unbindService(audioConnection)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_night_mode -> {
                nightMode = !nightMode
                applyNightMode(nightMode)
                getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().putBoolean(prefNightMode, nightMode).apply()
                true
            }
            R.id.action_home -> {
                navigateToUrl(ASSET_INDEX)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        // Save the current URL so we can restore it next launch
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().putString(prefLastUrl, binding.webView.url ?: ASSET_INDEX).apply()
    }

    // -----------------------------------------------------------------------------------------
    // Setup helpers
    // -----------------------------------------------------------------------------------------

    private fun setupNavSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.nav_home))
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.navSpinner.adapter = adapter

        binding.navSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                if (suppressSpinnerCallback || pos != 0) return
                navigateToUrl(ASSET_INDEX)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = fontScale
        }

        // Expose "Android" object so download.html can pass blob data to the app
        webView.addJavascriptInterface(DownloadBridge(), "Android")

        webView.webChromeClient = WebChromeClient()

        // Handle file downloads triggered by the WebView (including blob: URLs)
        webView.setDownloadListener(DownloadListener { url, _, contentDisposition, mimetype, _ ->
            val filename = sanitizeFilename(guessFilename(url, contentDisposition, mimetype))
            when {
                url.startsWith("blob:") -> {
                    // Blob URLs cannot be fetched by DownloadManager; inject JS to convert to base64.
                    // JSON-encode the strings to safely embed them in the script.
                    val jsonUrl      = org.json.JSONObject.quote(url)
                    val jsonFilename = org.json.JSONObject.quote(filename)
                    val jsonMime     = org.json.JSONObject.quote(mimetype)
                    webView.loadUrl(
                        "javascript:(function(){" +
                            "var xhr=new XMLHttpRequest();" +
                            "xhr.open('GET',$jsonUrl,true);" +
                            "xhr.responseType='blob';" +
                            "xhr.onload=function(){" +
                                "if(this.status===200){" +
                                    "var reader=new FileReader();" +
                                    "reader.readAsDataURL(this.response);" +
                                    "reader.onloadend=function(){" +
                                        "Android.downloadBase64(reader.result,$jsonFilename,$jsonMime);" +
                                    "};" +
                                "}" +
                            "};" +
                            "xhr.send();" +
                        "})()"
                    )
                }
                url.startsWith("https://") || url.startsWith("http://") -> {
                    enqueueDownload(url, mimetype, filename)
                }
                else -> {
                    Toast.makeText(this, getString(R.string.download_unsupported), Toast.LENGTH_SHORT).show()
                }
            }
        })

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when {
                    // Allow all local asset URLs
                    url.startsWith("file:///android_asset/") -> {
                        view.loadUrl(url)
                        true
                    }
                    // Allow HTTPS audio/content URLs (audio elements use src= not href=,
                    // but just in case any external link is tapped)
                    url.startsWith("https://") -> {
                        view.loadUrl(url)
                        true
                    }
                    // Block everything else (http, javascript:, etc.)
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                syncSpinnerToUrl(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Inject font-scale after page loads in case it differs from 100
                if (fontScale != 100) applyFontScale(fontScale)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                // Show connectivity hint if audio/external content fails
                if (!isOnline()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.offline_audio_hint),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** Enqueues an HTTP/HTTPS URL with the system DownloadManager. */
    private fun enqueueDownload(url: String, mimeType: String, filename: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription(getString(R.string.download_in_progress))
            setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        }
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(this, getString(R.string.download_started, filename), Toast.LENGTH_SHORT).show()
    }

    /**
     * Derive a sensible filename from a download URL, Content-Disposition header, and MIME type.
     */
    private fun guessFilename(url: String, contentDisposition: String?, mimeType: String?): String {
        // Try Content-Disposition: attachment; filename="foo.zip"
        if (!contentDisposition.isNullOrBlank()) {
            val match = Regex("""filename[^;=\n]*=["']?([^"';\n]+)""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
            val name = match?.groupValues?.get(1)?.trim()?.trim('"', '\'')
            if (!name.isNullOrBlank()) return name
        }
        // Fall back to last segment of the URL path
        val urlName = url.substringBefore('?').substringAfterLast('/').ifBlank { null }
        if (urlName != null) return urlName
        // Last resort: use MIME type extension
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        return "download.$ext"
    }

    /**
     * Sanitize a filename to prevent path traversal and remove characters that are
     * invalid in file names on Android/FAT32 (slashes, null bytes, colons, etc.).
     */
    private fun sanitizeFilename(name: String): String {
        // Replace any directory separator or null byte with an underscore, then strip
        // leading dots/spaces and trim to a safe length.
        val safe = name
            .replace(Regex("[/\\\\:*?\"<>|\u0000]"), "_")
            .trimStart('.', ' ')
            .take(200)
        return safe.ifBlank { "download" }
    }

    private fun setupFontButtons() {
        binding.btnFontDecrease.setOnClickListener {
            if (fontScale > FONT_SCALE_MIN) {
                fontScale -= FONT_SCALE_STEP
                applyFontScale(fontScale)
                getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().putInt(prefFontScale, fontScale).apply()
            }
        }
        binding.btnFontIncrease.setOnClickListener {
            if (fontScale < FONT_SCALE_MAX) {
                fontScale += FONT_SCALE_STEP
                applyFontScale(fontScale)
                getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().putInt(prefFontScale, fontScale).apply()
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Navigation helpers
    // -----------------------------------------------------------------------------------------

    private fun navigateToUrl(url: String) {
        binding.webView.loadUrl(url)
    }

    /** Keep spinner pinned to Home without triggering navigation loops. */
    private fun syncSpinnerToUrl(@Suppress("UNUSED_PARAMETER") url: String) {
        suppressSpinnerCallback = true
        binding.navSpinner.setSelection(0)
        suppressSpinnerCallback = false
    }

    // -----------------------------------------------------------------------------------------
    // Display helpers
    // -----------------------------------------------------------------------------------------

    private fun applyNightMode(enabled: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }

    private fun applyFontScale(scale: Int) {
        binding.webView.settings.textZoom = scale
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // -----------------------------------------------------------------------------------------
    // Companion object
    // -----------------------------------------------------------------------------------------

    companion object {
        private const val ASSET_INDEX = "file:///android_asset/index.html"
        private const val FONT_SCALE_MIN = 50
        private const val FONT_SCALE_MAX = 200
        private const val FONT_SCALE_STEP = 10

    }
}
