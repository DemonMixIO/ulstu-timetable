package ru.ulstu.timetable.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.R
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.data.ScheduleParser
import ru.ulstu.timetable.data.ScheduleRepository
import ru.ulstu.timetable.data.UrlTools
import ru.ulstu.timetable.databinding.ActivityMainBinding
import ru.ulstu.timetable.model.Schedule
import ru.ulstu.timetable.model.ScheduleLogic
import ru.ulstu.timetable.web.InjectedScripts
import ru.ulstu.timetable.web.WebAppInterface
import ru.ulstu.timetable.widget.WidgetRenderer
import java.time.LocalDateTime

class MainActivity : AppCompatActivity(), WebAppInterface.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var repository: ScheduleRepository

    private val handler = Handler(Looper.getMainLooper())

    private var mobileUserAgent = ""
    private var desktopUserAgent = ""

    private var currentUrl = ""
    private var currentPageIsSchedule = false
    private var cachedSchedule: Schedule? = null

    private var mainFrameFailed = false
    private var showingCachedCopy = false
    private var pendingScrollRestore = -1
    private var lastPauseAt = 0L

    private var searchQuery = ""
    private var searchHitCount = 0
    private var searchDebounce: Runnable? = null

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val url = result.data?.getStringExtra(GroupPickerActivity.EXTRA_URL).orEmpty()
        if (result.resultCode == RESULT_OK && url.isNotBlank()) {
            binding.webView.loadUrl(UrlTools.toAscii(url))
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        applyPreferences()
    }

    // Раз в минуту обновляем «следующую пару» и, если нужно, прячем прошедшие занятия.
    private val minuteTicker = object : Runnable {
        override fun run() {
            updateNextLessonBar()
            if (prefs.hidePast) evaluatePairFilter()
            handler.postDelayed(this, 60_000L)
        }
    }

    // Периодическая перезагрузка страницы, пока приложение открыто.
    private val refreshTicker = object : Runnable {
        override fun run() {
            if (prefs.periodicRefresh) binding.webView.reload()
            handler.postDelayed(this, PERIODIC_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        repository = ScheduleRepository(this)

        setupToolbar()
        setupWebView()
        setupSwipeRefresh()
        setupBackHandling()
        setupSearch()

        val restoredState = savedInstanceState
        prefs.register(prefsListener)
        applyPreferences()

        if (restoredState != null) {
            // Возвращаем последнее открытое окно вместе с историей переходов.
            binding.webView.restoreState(restoredState)
            currentUrl = prefs.lastUrl
        } else {
            pendingScrollRestore = prefs.lastScrollY
            loadInitialUrl(intent)
        }

        cachedSchedule = repository.cachedSchedule()
        updateNextLessonBar()
    }

    // --- Инициализация ------------------------------------------------------

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.main)
        binding.toolbar.setOnMenuItemClickListener { onMenuItemClick(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = binding.webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.textZoom = prefs.textZoom

        mobileUserAgent = settings.userAgentString ?: ""
        desktopUserAgent = mobileUserAgent
            .replace("Android", "X11; Linux x86_64")
            .replace(Regex("""\s*Mobile\s*"""), " ")

        CookieManager.getInstance().setAcceptCookie(true)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        binding.webView.addJavascriptInterface(WebAppInterface(this), Constants.JS_BRIDGE)
        binding.webView.webViewClient = timetableClient
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.progress = newProgress
                binding.progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        binding.webView.setDownloadListener { url, _, _, _, _ -> openExternally(url) }
    }

    private fun setupSwipeRefresh() {
        // SwipeRefreshLayout спрашивает про возможность прокрутки вверх у своего прямого
        // ребёнка, а это FrameLayout-обёртка (нужна, чтобы показывать экран ошибки поверх
        // WebView). FrameLayout не скроллится, поэтому без этого колбэка свайп вниз всегда
        // считался жестом обновления: страница не листалась вверх, а перезагружалась.
        binding.swipe.setOnChildScrollUpCallback { _, _ ->
            binding.webView.canScrollVertically(-1)
        }
        binding.swipe.setOnRefreshListener { binding.webView.reload() }
    }

    private fun setupBackHandling() {
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
    }

    /**
     * Открываем последнее окно. Если включено автообновление, запрашиваем страницу
     * заново с запретом кэша — так при запуске сразу видны свежие данные.
     */
    private fun loadInitialUrl(intent: Intent?) {
        val deepLink = intent?.data?.toString()
        val url = when {
            !deepLink.isNullOrBlank() -> deepLink
            prefs.lastUrl.isNotBlank() -> prefs.lastUrl
            else -> Constants.HOME_URL
        }
        currentUrl = url
        if (prefs.autoRefreshOnOpen) {
            binding.webView.loadUrl(UrlTools.toAscii(url), NO_CACHE_HEADERS)
        } else {
            binding.webView.loadUrl(UrlTools.toAscii(url))
        }
    }

    // --- Жизненный цикл -----------------------------------------------------

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.takeIf { it.isNotBlank() }?.let {
            binding.webView.loadUrl(UrlTools.toAscii(it))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(minuteTicker)
        handler.post(minuteTicker)
        handler.removeCallbacks(refreshTicker)
        handler.postDelayed(refreshTicker, PERIODIC_REFRESH_MS)

        // Вернулись в приложение спустя время — обновляем расписание.
        if (lastPauseAt > 0 &&
            prefs.autoRefreshOnOpen &&
            !showingCachedCopy &&
            System.currentTimeMillis() - lastPauseAt > IDLE_REFRESH_MS &&
            binding.webView.url != null
        ) {
            binding.webView.reload()
        }
        applyPreferences()
    }

    override fun onPause() {
        super.onPause()
        lastPauseAt = System.currentTimeMillis()
        handler.removeCallbacks(minuteTicker)
        handler.removeCallbacks(refreshTicker)

        prefs.lastScrollY = binding.webView.scrollY
        (binding.webView.url ?: currentUrl).takeIf { it.isNotBlank() }?.let { prefs.lastUrl = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        prefs.unregister(prefsListener)
        handler.removeCallbacksAndMessages(null)
        binding.webView.destroy()
        super.onDestroy()
    }

    // --- Меню ---------------------------------------------------------------

    private fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            binding.webView.reload()
            true
        }
        R.id.action_filter -> {
            showFilterSheet()
            true
        }
        R.id.action_search -> {
            toggleSearch()
            true
        }
        R.id.action_group -> {
            pickerLauncher.launch(Intent(this, GroupPickerActivity::class.java))
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.action_track -> {
            trackCurrentPage()
            true
        }
        R.id.action_share -> {
            shareLink()
            true
        }
        R.id.action_copy -> {
            copyLink()
            true
        }
        R.id.action_home -> {
            binding.webView.loadUrl(Constants.HOME_URL)
            true
        }
        R.id.action_desktop -> {
            item.isChecked = !item.isChecked
            prefs.desktopMode = item.isChecked
            applyUserAgent()
            binding.webView.reload()
            true
        }
        R.id.action_browser -> {
            openExternally(currentLink())
            true
        }
        else -> false
    }

    private fun showFilterSheet() {
        val sheet = FilterBottomSheet()
        sheet.pairCount = prefs.lastPairCount
        sheet.onApply = { state ->
            prefs.hiddenPairs = state.hiddenPairs
            prefs.optionalPairs = state.optionalPairs
            prefs.hideEmptyDays = state.hideEmptyDays
            prefs.hidePast = state.hidePast
            prefs.skipOptionalInWidget = state.skipOptionalInWidget
            evaluatePairFilter()
            updateNextLessonBar()
            cachedSchedule?.let { WidgetRenderer.updateAll(this) }
        }
        sheet.show(supportFragmentManager, "filter")
    }

    // --- Фильтр по парам ----------------------------------------------------

    private fun visiblePairs(): List<Int> =
        (1..prefs.lastPairCount).filter { it !in prefs.hiddenPairs }

    private fun evaluatePairFilter() {
        if (binding.webView.url == null) return
        val script = InjectedScripts.pairFilter(
            visiblePairs(),
            prefs.optionalPairs.sorted(),
            prefs.hideEmptyDays,
            prefs.hidePast,
            System.currentTimeMillis()
        )
        binding.webView.evaluateJavascript(script, null)
    }

    // --- Поиск по парам -----------------------------------------------------

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                searchDebounce?.let { handler.removeCallbacks(it) }
                val runnable = Runnable { applySearch() }
                searchDebounce = runnable
                handler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
            }
        })

        binding.searchClose.setOnClickListener {
            searchQuery = ""
            binding.searchInput.setText("")
            binding.searchBar.visibility = View.GONE
            binding.searchCount.text = ""
            binding.webView.evaluateJavascript(InjectedScripts.searchClear(), null)
            hideKeyboard()
        }
        binding.searchNext.setOnClickListener { stepSearch(1) }
        binding.searchPrev.setOnClickListener { stepSearch(-1) }
    }

    private fun toggleSearch() {
        if (binding.searchBar.visibility == View.VISIBLE) {
            binding.searchClose.performClick()
        } else {
            binding.searchBar.visibility = View.VISIBLE
            binding.searchInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    /** Подсвечивает найденные пары; всё остальное на странице становится полупрозрачным. */
    private fun applySearch() {
        if (binding.webView.url == null) return
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchHitCount = 0
            binding.searchCount.text = ""
            binding.webView.evaluateJavascript(InjectedScripts.searchClear(), null)
            return
        }
        binding.webView.evaluateJavascript(InjectedScripts.searchApply(query)) { result ->
            val count = result?.trim('"')?.toIntOrNull() ?: 0
            searchHitCount = count.coerceAtLeast(0)
            binding.searchCount.text = when {
                count > 0 -> getString(R.string.search_found, count)
                count == 0 -> getString(R.string.search_nothing)
                else -> ""
            }
        }
    }

    private fun stepSearch(delta: Int) {
        if (searchQuery.isBlank() || binding.webView.url == null) return
        binding.webView.evaluateJavascript(InjectedScripts.searchStep(delta)) { result ->
            val position = result?.trim('"')?.toIntOrNull() ?: 0
            if (position > 0 && searchHitCount > 0) {
                binding.searchCount.text =
                    getString(R.string.search_position, position, searchHitCount)
            }
        }
    }

    // --- Настройки на лету --------------------------------------------------
    private fun applyPreferences() {
        val settings = binding.webView.settings
        settings.textZoom = prefs.textZoom
        applyUserAgent()
        binding.webView.evaluateJavascript(
            InjectedScripts.styleSheet(prefs.siteDarkTheme, prefs.fitWidth), null
        )
        evaluatePairFilter()
        updateNextLessonBar()
    }

    private fun applyUserAgent() {
        binding.webView.settings.userAgentString =
            if (prefs.desktopMode) desktopUserAgent else mobileUserAgent
    }

    // --- Загрузка страниц ---------------------------------------------------

    private val timetableClient = object : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = handleUrl(request.url.toString())

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            mainFrameFailed = false
            currentUrl = url ?: currentUrl
            if (binding.progress.progress == 0) binding.progress.visibility = View.VISIBLE
            hideState()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            binding.progress.visibility = View.GONE
            binding.swipe.isRefreshing = false
            if (mainFrameFailed) return

            currentUrl = url ?: currentUrl
            if (currentUrl.isNotBlank()) {
                prefs.lastUrl = currentUrl
                prefs.lastLoadAt = System.currentTimeMillis()
            }

            binding.webView.evaluateJavascript(
                InjectedScripts.styleSheet(prefs.siteDarkTheme, prefs.fitWidth), null
            )
            evaluatePairFilter()
            // После перезагрузки подсветка поиска ставится заново.
            if (searchQuery.isNotBlank()) applySearch()

            if (pendingScrollRestore > 0) {
                binding.webView.evaluateJavascript(
                    InjectedScripts.scrollTo(pendingScrollRestore), null
                )
                pendingScrollRestore = -1
            }

            binding.webView.evaluateJavascript(InjectedScripts.pageProbe(), null)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            mainFrameFailed = true
            showOfflineCopy()
        }
    }

    private fun handleUrl(url: String): Boolean {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return if (UrlTools.isSameSite(url, Constants.HOME_URL)) {
                false
            } else {
                openExternally(url)
                true
            }
        }
        if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("intent:")) {
            openExternally(url)
            return true
        }
        return false
    }

    // --- Мост со страницей --------------------------------------------------

    override fun onPageReady(
        url: String,
        title: String,
        pairCount: Int,
        isSchedule: Boolean,
        html: String
    ) {
        currentPageIsSchedule = isSchedule

        if (pairCount > 0) prefs.lastPairCount = pairCount
        if (title.isNotBlank()) {
            prefs.lastTitle = title
            binding.toolbar.title = title
        }

        if (!isSchedule || html.isBlank()) {
            updateNextLessonBar()
            return
        }

        // Разбираем страницу и делаем её источником для виджета и напоминаний.
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.Default) { ScheduleParser.parse(html, url) }
            if (parsed == null) {
                updateNextLessonBar()
                return@launch
            }
            repository.saveSchedule(parsed)
            repository.savePageHtml(url, html)
            prefs.trackedUrl = url
            prefs.trackedTitle = parsed.title
            cachedSchedule = parsed
            updateNextLessonBar()
            WidgetRenderer.updateAll(this@MainActivity)
        }
    }

    // --- «Следующая пара» в приложении -------------------------------------

    private fun updateNextLessonBar() {
        val bar = binding.nextBar
        val schedule = cachedSchedule

        if (!prefs.showNextLessonBar || schedule == null || !currentPageIsSchedule) {
            bar.root.visibility = View.GONE
            return
        }

        val now = LocalDateTime.now()
        val slot = ScheduleLogic.nextSlot(schedule, now, prefs.pairsExcludedFromWidget())
        if (slot == null) {
            bar.root.visibility = View.GONE
            return
        }

        val dismissKey = "${slot.date}|${slot.pairIndex}|${slot.lesson.subject}"
        if (prefs.dismissedNextBarKey == dismissKey) {
            bar.root.visibility = View.GONE
            return
        }

        bar.root.visibility = View.VISIBLE
        bar.nextBadge.text = slot.pairIndex.toString()
        bar.nextSubject.text = slot.lesson.subject.ifBlank { slot.lesson.raw }
        bar.nextDetails.text = slot.lesson.subtitle()
        bar.nextWhen.text =
            "${ScheduleLogic.dayLabel(slot, now)} · ${WidgetRenderer.timeRange(slot)}"
        bar.nextCountdown.text = ScheduleLogic.humanUntil(slot, now)
        bar.nextClose.setOnClickListener {
            prefs.dismissedNextBarKey = dismissKey
            bar.root.visibility = View.GONE
        }
    }

    // --- Офлайн-копия и ошибки ---------------------------------------------

    private fun showOfflineCopy() {
        val url = currentUrl.ifBlank { prefs.lastUrl }
        val cachedHtml = repository.cachedPageFor(url)
        if (cachedHtml != null && !showingCachedCopy) {
            showingCachedCopy = true
            binding.webView.loadDataWithBaseURL(url, cachedHtml, "text/html", "utf-8", null)
            Toast.makeText(this, R.string.offline_title, Toast.LENGTH_LONG).show()
        } else {
            showState(
                title = getString(R.string.error_offline),
                message = getString(R.string.error_no_cache)
            ) { binding.webView.reload() }
        }
    }

    private fun showState(title: String, message: String, action: () -> Unit) {
        binding.stateView.visibility = View.VISIBLE
        binding.stateTitle.text = title
        binding.stateMessage.text = message
        binding.stateAction.setOnClickListener { action() }
    }

    private fun hideState() {
        binding.stateView.visibility = View.GONE
    }

    // --- Действия -----------------------------------------------------------

    private fun trackCurrentPage() {
        val schedule = cachedSchedule
        if (schedule == null || currentUrl.isBlank() || !currentPageIsSchedule) {
            Toast.makeText(this, R.string.no_schedule_on_page, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.trackedUrl = currentUrl
        prefs.trackedTitle = schedule.title
        WidgetRenderer.updateAll(this)
        Toast.makeText(
            this,
            getString(R.string.tracked_group_set, schedule.title),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun currentLink(): String =
        binding.webView.url ?: prefs.lastUrl.ifBlank { Constants.HOME_URL }

    private fun shareLink() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentLink())
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.menu_share)))
        }
    }

    private fun copyLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), currentLink()))
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openExternally(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UrlTools.toAscii(url)))
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.error_load, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val PERIODIC_REFRESH_MS = 15 * 60 * 1000L
        private const val IDLE_REFRESH_MS = 5 * 60 * 1000L
        private const val SEARCH_DEBOUNCE_MS = 250L

        private val NO_CACHE_HEADERS = mapOf(
            "Cache-Control" to "no-cache, no-store",
            "Pragma" to "no-cache"
        )
    }
}
