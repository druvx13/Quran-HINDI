package com.quran.hindi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.quran.hindi.databinding.ActivityMainBinding

/**
 * Main activity hosting the WebView that renders Quranic content from bundled HTML assets.
 * Provides surah navigation, night-mode toggle, and font-size controls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // SharedPreferences keys
    private val prefsName = "QuranHindiPrefs"
    private val prefLastUrl = "last_url"
    private val prefNightMode = "night_mode"
    private val prefFontScale = "font_scale"

    // Default font scale (percentage, 100 = normal)
    private var fontScale = 100
    private var nightMode = false

    // Flag to suppress spinner's item-selected callback while we are programmatically updating it
    private var suppressSpinnerCallback = false

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

    // -----------------------------------------------------------------------------------------
    // Surah data: number (1-based), Arabic name, English name, ayah count
    // -----------------------------------------------------------------------------------------
    private val surahList: List<Triple<Int, String, Int>> = listOf(
        Triple(1,  "Al-Fatihah (الفاتحة)", 7),
        Triple(2,  "Al-Baqarah (البقرة)", 286),
        Triple(3,  "Al-Imran (آل عمران)", 200),
        Triple(4,  "An-Nisa (النساء)", 176),
        Triple(5,  "Al-Maidah (المائدة)", 120),
        Triple(6,  "Al-Anam (الأنعام)", 165),
        Triple(7,  "Al-Araf (الأعراف)", 206),
        Triple(8,  "Al-Anfal (الأنفال)", 75),
        Triple(9,  "At-Tawbah (التوبة)", 129),
        Triple(10, "Yunus (يونس)", 109),
        Triple(11, "Hud (هود)", 123),
        Triple(12, "Yusuf (يوسف)", 111),
        Triple(13, "Ar-Rad (الرعد)", 43),
        Triple(14, "Ibrahim (إبراهيم)", 52),
        Triple(15, "Al-Hijr (الحجر)", 99),
        Triple(16, "An-Nahl (النحل)", 128),
        Triple(17, "Al-Isra (الإسراء)", 111),
        Triple(18, "Al-Kahf (الكهف)", 110),
        Triple(19, "Maryam (مريم)", 98),
        Triple(20, "Ta-Ha (طه)", 135),
        Triple(21, "Al-Anbiya (الأنبياء)", 112),
        Triple(22, "Al-Hajj (الحج)", 78),
        Triple(23, "Al-Muminun (المؤمنون)", 118),
        Triple(24, "An-Nur (النور)", 64),
        Triple(25, "Al-Furqan (الفرقان)", 77),
        Triple(26, "Ash-Shuara (الشعراء)", 227),
        Triple(27, "An-Naml (النمل)", 93),
        Triple(28, "Al-Qasas (القصص)", 88),
        Triple(29, "Al-Ankabut (العنكبوت)", 69),
        Triple(30, "Ar-Rum (الروم)", 60),
        Triple(31, "Luqman (لقمان)", 34),
        Triple(32, "As-Sajdah (السجدة)", 30),
        Triple(33, "Al-Ahzab (الأحزاب)", 73),
        Triple(34, "Saba (سبأ)", 54),
        Triple(35, "Fatir (فاطر)", 45),
        Triple(36, "Ya-Sin (يس)", 83),
        Triple(37, "As-Saffat (الصافات)", 182),
        Triple(38, "Sad (ص)", 88),
        Triple(39, "Az-Zumar (الزمر)", 75),
        Triple(40, "Ghafir (غافر)", 85),
        Triple(41, "Fussilat (فصلت)", 54),
        Triple(42, "Ash-Shura (الشورى)", 53),
        Triple(43, "Az-Zukhruf (الزخرف)", 89),
        Triple(44, "Ad-Dukhan (الدخان)", 59),
        Triple(45, "Al-Jathiyah (الجاثية)", 37),
        Triple(46, "Al-Ahqaf (الأحقاف)", 35),
        Triple(47, "Muhammad (محمد)", 38),
        Triple(48, "Al-Fath (الفتح)", 29),
        Triple(49, "Al-Hujurat (الحجرات)", 18),
        Triple(50, "Qaf (ق)", 45),
        Triple(51, "Adh-Dhariyat (الذاريات)", 60),
        Triple(52, "At-Tur (الطور)", 49),
        Triple(53, "An-Najm (النجم)", 62),
        Triple(54, "Al-Qamar (القمر)", 55),
        Triple(55, "Ar-Rahman (الرحمن)", 78),
        Triple(56, "Al-Waqiah (الواقعة)", 96),
        Triple(57, "Al-Hadid (الحديد)", 29),
        Triple(58, "Al-Mujadila (المجادلة)", 22),
        Triple(59, "Al-Hashr (الحشر)", 24),
        Triple(60, "Al-Mumtahanah (الممتحنة)", 13),
        Triple(61, "As-Saf (الصف)", 14),
        Triple(62, "Al-Jumuah (الجمعة)", 11),
        Triple(63, "Al-Munafiqun (المنافقون)", 11),
        Triple(64, "At-Taghabun (التغابن)", 18),
        Triple(65, "At-Talaq (الطلاق)", 12),
        Triple(66, "At-Tahrim (التحريم)", 12),
        Triple(67, "Al-Mulk (الملك)", 30),
        Triple(68, "Al-Qalam (القلم)", 52),
        Triple(69, "Al-Haqqah (الحاقة)", 52),
        Triple(70, "Al-Maarij (المعارج)", 44),
        Triple(71, "Nuh (نوح)", 28),
        Triple(72, "Al-Jinn (الجن)", 28),
        Triple(73, "Al-Muzzammil (المزمل)", 20),
        Triple(74, "Al-Muddaththir (المدثر)", 56),
        Triple(75, "Al-Qiyamah (القيامة)", 40),
        Triple(76, "Al-Insan (الإنسان)", 31),
        Triple(77, "Al-Mursalat (المرسلات)", 50),
        Triple(78, "An-Naba (النبأ)", 40),
        Triple(79, "An-Naziat (النازعات)", 46),
        Triple(80, "Abasa (عبس)", 42),
        Triple(81, "At-Takwir (التكوير)", 29),
        Triple(82, "Al-Infitar (الانفطار)", 19),
        Triple(83, "Al-Mutaffifin (المطففين)", 36),
        Triple(84, "Al-Inshiqaq (الانشقاق)", 25),
        Triple(85, "Al-Buruj (البروج)", 22),
        Triple(86, "At-Tariq (الطارق)", 17),
        Triple(87, "Al-Ala (الأعلى)", 19),
        Triple(88, "Al-Ghashiyah (الغاشية)", 26),
        Triple(89, "Al-Fajr (الفجر)", 30),
        Triple(90, "Al-Balad (البلد)", 20),
        Triple(91, "Ash-Shams (الشمس)", 15),
        Triple(92, "Al-Layl (الليل)", 21),
        Triple(93, "Ad-Duha (الضحى)", 11),
        Triple(94, "Ash-Sharh (الشرح)", 8),
        Triple(95, "At-Tin (التين)", 8),
        Triple(96, "Al-Alaq (العلق)", 19),
        Triple(97, "Al-Qadr (القدر)", 5),
        Triple(98, "Al-Bayyinah (البينة)", 8),
        Triple(99, "Az-Zalzalah (الزلزلة)", 8),
        Triple(100, "Al-Adiyat (العاديات)", 11),
        Triple(101, "Al-Qariah (القارعة)", 11),
        Triple(102, "At-Takathur (التكاثر)", 8),
        Triple(103, "Al-Asr (العصر)", 3),
        Triple(104, "Al-Humazah (الهمزة)", 9),
        Triple(105, "Al-Fil (الفيل)", 5),
        Triple(106, "Quraysh (قريش)", 4),
        Triple(107, "Al-Maun (الماعون)", 7),
        Triple(108, "Al-Kawthar (الكوثر)", 3),
        Triple(109, "Al-Kafirun (الكافرون)", 6),
        Triple(110, "An-Nasr (النصر)", 3),
        Triple(111, "Al-Masad (المسد)", 5),
        Triple(112, "Al-Ikhlas (الإخلاص)", 4),
        Triple(113, "Al-Falaq (الفلق)", 5),
        Triple(114, "An-Nas (الناس)", 6)
    )

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

        setupSurahSpinner()
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

    private fun setupSurahSpinner() {
        // "Home" entry + 114 surahs
        val entries = mutableListOf(getString(R.string.nav_home))
        surahList.forEach { (num, name, _) -> entries.add("$num. $name") }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.surahSpinner.adapter = adapter

        binding.surahSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                if (suppressSpinnerCallback) return
                if (pos == 0) {
                    navigateToUrl(ASSET_INDEX)
                } else {
                    val surahNum = surahList[pos - 1].first
                    navigateToUrl(surahAssetUrl(surahNum))
                }
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

        webView.webChromeClient = WebChromeClient()

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

    /** Update spinner selection to match the current page URL without triggering navigation. */
    private fun syncSpinnerToUrl(url: String) {
        suppressSpinnerCallback = true
        val surahNum = surahNumberFromUrl(url)
        binding.surahSpinner.setSelection(if (surahNum == null) 0 else surahNum)
        suppressSpinnerCallback = false
    }

    /** Returns 1-based surah number from a local asset URL, or null if not a surah page. */
    private fun surahNumberFromUrl(url: String): Int? {
        val filename = url.substringAfterLast("/")
        val match = Regex("^(\\d{3})\\.html$").find(filename) ?: return null
        return match.groupValues[1].toIntOrNull()
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

        fun surahAssetUrl(surahNumber: Int): String {
            return "file:///android_asset/%03d.html".format(surahNumber)
        }
    }
}
