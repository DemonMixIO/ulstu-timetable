package ru.ulstu.timetable.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.R
import ru.ulstu.timetable.data.NOTIFY_LEAD_OPTIONS
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.data.ScheduleRepository
import ru.ulstu.timetable.data.UrlTools
import ru.ulstu.timetable.databinding.ActivitySettingsBinding
import ru.ulstu.timetable.widget.WidgetRenderer
import ru.ulstu.timetable.work.LessonNotifier

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                prefs.notificationsEnabled = false
                Toast.makeText(this, R.string.error_load, Toast.LENGTH_SHORT).show()
            }
            rebuild()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.toolbar.setNavigationOnClickListener { finish() }
        rebuild()
    }

    private fun rebuild() {
        val content = binding.content
        content.removeAllViews()

        // --- Просмотр ---
        SettingsRows.section(this, content, R.string.settings_section_browse)
        SettingsRows.switchRow(
            this, content,
            R.string.settings_auto_refresh, R.string.settings_auto_refresh_desc,
            prefs.autoRefreshOnOpen
        ) { prefs.autoRefreshOnOpen = it }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_periodic_refresh, R.string.settings_periodic_refresh_desc,
            prefs.periodicRefresh
        ) { prefs.periodicRefresh = it }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_site_dark, R.string.settings_site_dark_desc,
            prefs.siteDarkTheme
        ) { prefs.siteDarkTheme = it }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_fit_width, R.string.settings_fit_width_desc,
            prefs.fitWidth
        ) { prefs.fitWidth = it }

        SettingsRows.choiceRow(
            this, content,
            R.string.settings_theme,
            resources.getStringArray(R.array.theme_entries),
            prefs.themeMode
        ) { which ->
            prefs.themeMode = which
            AppCompatDelegate.setDefaultNightMode(which)
        }

        SettingsRows.sliderRow(
            this, content,
            R.string.settings_text_zoom,
            from = 60f, to = 200f, step = 10f,
            value = prefs.textZoom.toFloat(),
            valueSuffix = "%"
        ) { prefs.textZoom = it }

        // --- Фильтр ---
        SettingsRows.section(this, content, R.string.settings_section_filter)
        SettingsRows.clickRow(
            this, content,
            R.string.title_filter,
            hiddenPairsLabel()
        ) { showFilterSheet() }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_hide_empty_days, null,
            prefs.hideEmptyDays
        ) { prefs.hideEmptyDays = it }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_hide_past, null,
            prefs.hidePast
        ) { prefs.hidePast = it }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_quick_bar, R.string.settings_quick_bar_desc,
            prefs.quickFilterBar
        ) { prefs.quickFilterBar = it }

        // --- Виджет и уведомления ---
        SettingsRows.section(this, content, R.string.settings_section_widget)
        SettingsRows.switchRow(
            this, content,
            R.string.settings_show_next_bar, null,
            prefs.showNextLessonBar
        ) { prefs.showNextLessonBar = it }

        SettingsRows.switchRow(
            this, content,
            R.string.settings_notifications, R.string.settings_notifications_desc,
            prefs.notificationsEnabled
        ) { enabled -> onNotificationsToggled(enabled) }

        SettingsRows.choiceRow(
            this, content,
            R.string.settings_notify_lead,
            resources.getStringArray(R.array.lead_entries),
            NOTIFY_LEAD_OPTIONS.indexOf(prefs.notifyLeadMinutes).coerceAtLeast(0)
        ) { which -> prefs.notifyLeadMinutes = NOTIFY_LEAD_OPTIONS[which] }

        // --- Данные ---
        SettingsRows.section(this, content, R.string.settings_section_data)
        SettingsRows.infoRow(
            this, content,
            R.string.settings_group,
            prefs.trackedTitle.ifBlank { getString(R.string.settings_group_none) }
        )

        SettingsRows.clickRow(this, content, R.string.settings_clear_cache, null) {
            ScheduleRepository(this).clear()
            WidgetRenderer.updateAll(this)
            Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
            rebuild()
        }

        SettingsRows.clickRow(this, content, R.string.settings_reset, null) {
            prefs.resetSettings()
            AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
            Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
            rebuild()
        }

        // --- О приложении ---
        SettingsRows.section(this, content, R.string.settings_about)
        SettingsRows.infoRow(this, content, R.string.settings_version, versionName())
        SettingsRows.infoRow(
            this, content,
            R.string.settings_source, Constants.HOME_URL_PRETTY
        )
        SettingsRows.clickRow(this, content, R.string.settings_open_source, null) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(UrlTools.toAscii(Constants.HOME_URL)))
                )
            }
        }
    }

    private fun onNotificationsToggled(enabled: Boolean) {
        prefs.notificationsEnabled = enabled
        if (!enabled) return
        LessonNotifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !LessonNotifier.areNotificationsAllowed(this)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showFilterSheet() {
        val sheet = FilterBottomSheet()
        sheet.pairCount = prefs.lastPairCount
        sheet.onApply = { hidden, hideEmpty, hidePast ->
            prefs.hiddenPairs = hidden
            prefs.hideEmptyDays = hideEmpty
            prefs.hidePast = hidePast
            WidgetRenderer.updateAll(this)
            rebuild()
        }
        sheet.show(supportFragmentManager, "filter")
    }

    private fun hiddenPairsLabel(): String {
        val hidden = prefs.hiddenPairs
        return if (hidden.isEmpty()) {
            getString(R.string.pairs_all_shown)
        } else {
            resources.getQuantityString(
                R.plurals.pairs_hidden, hidden.size, hidden.size
            )
        }
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
