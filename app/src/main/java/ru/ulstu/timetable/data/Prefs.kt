package ru.ulstu.timetable.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.model.LessonSlot

/**
 * Все настройки и «последнее открытое окно» приложения.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // --- Последнее открытое окно -------------------------------------------

    var lastUrl: String
        get() = sp.getString(KEY_LAST_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_URL, v).apply()

    var lastTitle: String
        get() = sp.getString(KEY_LAST_TITLE, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_TITLE, v).apply()

    var lastScrollY: Int
        get() = sp.getInt(KEY_LAST_SCROLL, 0)
        set(v) = sp.edit().putInt(KEY_LAST_SCROLL, v).apply()

    var lastLoadAt: Long
        get() = sp.getLong(KEY_LAST_LOAD_AT, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_LOAD_AT, v).apply()

    // --- Моя группа (источник для виджетов и уведомлений) -------------------

    var trackedUrl: String
        get() = sp.getString(KEY_TRACKED_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_TRACKED_URL, v).apply()

    var trackedTitle: String
        get() = sp.getString(KEY_TRACKED_TITLE, "") ?: ""
        set(v) = sp.edit().putString(KEY_TRACKED_TITLE, v).apply()

    // --- Просмотр -----------------------------------------------------------

    var autoRefreshOnOpen: Boolean
        get() = sp.getBoolean(KEY_AUTO_REFRESH, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_REFRESH, v).apply()

    var periodicRefresh: Boolean
        get() = sp.getBoolean(KEY_PERIODIC, false)
        set(v) = sp.edit().putBoolean(KEY_PERIODIC, v).apply()

    var siteDarkTheme: Boolean
        get() = sp.getBoolean(KEY_SITE_DARK, false)
        set(v) = sp.edit().putBoolean(KEY_SITE_DARK, v).apply()

    var textZoom: Int
        get() = sp.getInt(KEY_TEXT_ZOOM, 100)
        set(v) = sp.edit().putInt(KEY_TEXT_ZOOM, v.coerceIn(60, 200)).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean(KEY_DESKTOP, false)
        set(v) = sp.edit().putBoolean(KEY_DESKTOP, v).apply()

    /** Вписывать широкую таблицу расписания в ширину экрана. */
    var fitWidth: Boolean
        get() = sp.getBoolean(KEY_FIT_WIDTH, true)
        set(v) = sp.edit().putBoolean(KEY_FIT_WIDTH, v).apply()

    /** 0 — как в системе, 1 — светлое, 2 — тёмное. */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = sp.edit().putInt(KEY_THEME, v).apply()

    // --- Фильтр по парам ----------------------------------------------------

    /** Номера пар (1-based), которые скрыты. Пустое множество — показаны все. */
    var hiddenPairs: Set<Int>
        get() = sp.getString(KEY_HIDDEN_PAIRS, "")
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        set(v) = sp.edit().putString(KEY_HIDDEN_PAIRS, v.sorted().joinToString(",")).apply()

    var hideEmptyDays: Boolean
        get() = sp.getBoolean(KEY_HIDE_EMPTY_DAYS, false)
        set(v) = sp.edit().putBoolean(KEY_HIDE_EMPTY_DAYS, v).apply()

    var hidePast: Boolean
        get() = sp.getBoolean(KEY_HIDE_PAST, false)
        set(v) = sp.edit().putBoolean(KEY_HIDE_PAST, v).apply()

    /**
     * Пары, помеченные необязательными. Тег ставится по конкретной ячейке
     * (день + номер пары), потому что необязательная пара может быть в любом месте:
     * ключ имеет вид «2026-09-14|3».
     */
    var optionalCells: Set<String>
        get() = sp.getStringSet(KEY_OPTIONAL_CELLS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_OPTIONAL_CELLS, v).apply()

    fun isCellOptional(slot: LessonSlot): Boolean = slot.cellKey() in optionalCells

    /** Не показывать необязательные пары в виджете и напоминаниях. */
    var skipOptionalInWidget: Boolean
        get() = sp.getBoolean(KEY_SKIP_OPTIONAL, true)
        set(v) = sp.edit().putBoolean(KEY_SKIP_OPTIONAL, v).apply()

    // --- Подгруппы ----------------------------------------------------------

    /** Показывать пары только своей подгруппы (у части занятий есть «1-я» и «2-я п/г»). */
    var subgroupEnabled: Boolean
        get() = sp.getBoolean(KEY_SUBGROUP_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_SUBGROUP_ENABLED, v).apply()

    /** Номер своей подгруппы: 1 или 2. */
    var subgroup: Int
        get() = sp.getInt(KEY_SUBGROUP, 1).coerceIn(1, 2)
        set(v) = sp.edit().putInt(KEY_SUBGROUP, v.coerceIn(1, 2)).apply()

    /**
     * Нужно ли скрыть пару от пользователя (в расписании, виджете, напоминаниях):
     * скрытые им пары, необязательные пометки и занятия чужой подгруппы.
     */
    fun isSlotExcludedFromWidget(slot: LessonSlot): Boolean {
        if (slot.pairIndex in hiddenPairs) return true
        if (skipOptionalInWidget && isCellOptional(slot)) return true
        if (subgroupEnabled) {
            val sg = slot.lesson.subgroupNumber()
            if (sg > 0 && sg != subgroup) return true
        }
        return false
    }

    var showNextLessonBar: Boolean
        get() = sp.getBoolean(KEY_NEXT_BAR, true)
        set(v) = sp.edit().putBoolean(KEY_NEXT_BAR, v).apply()

    /** Сколько пар в таблице на последней открытой странице (для полоски фильтра). */
    var lastPairCount: Int
        get() = sp.getInt(KEY_PAIR_COUNT, 8).coerceIn(1, 12)
        set(v) = sp.edit().putInt(KEY_PAIR_COUNT, v.coerceIn(1, 12)).apply()

    // --- Уведомления --------------------------------------------------------

    var notificationsEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIFY, false)
        set(v) = sp.edit().putBoolean(KEY_NOTIFY, v).apply()

    /** За сколько минут предупреждать о паре. */
    var notifyLeadMinutes: Int
        get() = sp.getInt(KEY_NOTIFY_LEAD, 15)
        set(v) = sp.edit().putInt(KEY_NOTIFY_LEAD, v).apply()

    var lastNotifiedKey: String
        get() = sp.getString(KEY_LAST_NOTIFIED, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_NOTIFIED, v).apply()

    // --- Избранные группы ---------------------------------------------------

    var favorites: Set<String>
        get() = sp.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_FAVORITES, v).apply()

    fun toggleFavorite(key: String): Boolean {
        val cur = favorites.toMutableSet()
        val added = if (cur.contains(key)) {
            cur.remove(key); false
        } else {
            cur.add(key); true
        }
        favorites = cur
        return added
    }

    /** Кэш списка групп, чтобы экран выбора открывался мгновенно. */
    var groupsCache: String
        get() = sp.getString(KEY_GROUPS_CACHE, "") ?: ""
        set(v) = sp.edit().putString(KEY_GROUPS_CACHE, v).apply()

    var groupsCacheAt: Long
        get() = sp.getLong(KEY_GROUPS_CACHE_AT, 0L)
        set(v) = sp.edit().putLong(KEY_GROUPS_CACHE_AT, v).apply()

    // --- Прочее -------------------------------------------------------------

    var dismissedNextBarKey: String
        get() = sp.getString(KEY_DISMISSED_NEXT, "") ?: ""
        set(v) = sp.edit().putString(KEY_DISMISSED_NEXT, v).apply()

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** Сброс к состоянию «как после установки», кроме выбранной группы. */
    fun resetSettings() {
        val tracked = trackedUrl
        val trackedName = trackedTitle
        val favs = favorites
        sp.edit().clear().apply()
        trackedUrl = tracked
        trackedTitle = trackedName
        favorites = favs
    }

    companion object {
        private const val NAME = "timetable_prefs"

        private const val KEY_LAST_URL = "last_url"
        private const val KEY_LAST_TITLE = "last_title"
        private const val KEY_LAST_SCROLL = "last_scroll"
        private const val KEY_LAST_LOAD_AT = "last_load_at"
        private const val KEY_TRACKED_URL = "tracked_url"
        private const val KEY_TRACKED_TITLE = "tracked_title"
        private const val KEY_AUTO_REFRESH = "auto_refresh"
        private const val KEY_PERIODIC = "periodic_refresh"
        private const val KEY_SITE_DARK = "site_dark"
        private const val KEY_TEXT_ZOOM = "text_zoom"
        private const val KEY_DESKTOP = "desktop_mode"
        private const val KEY_FIT_WIDTH = "fit_width"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_HIDDEN_PAIRS = "hidden_pairs"
        private const val KEY_OPTIONAL_CELLS = "optional_cells"
        private const val KEY_SKIP_OPTIONAL = "skip_optional_in_widget"
        private const val KEY_SUBGROUP_ENABLED = "subgroup_enabled"
        private const val KEY_SUBGROUP = "subgroup"
        private const val KEY_HIDE_EMPTY_DAYS = "hide_empty_days"
        private const val KEY_HIDE_PAST = "hide_past"
        private const val KEY_NEXT_BAR = "next_lesson_bar"
        private const val KEY_PAIR_COUNT = "last_pair_count"
        private const val KEY_NOTIFY = "notifications"
        private const val KEY_NOTIFY_LEAD = "notify_lead"
        private const val KEY_LAST_NOTIFIED = "last_notified_key"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_GROUPS_CACHE = "groups_cache"
        private const val KEY_GROUPS_CACHE_AT = "groups_cache_at"
        private const val KEY_DISMISSED_NEXT = "dismissed_next_key"
    }
}

/** Значения по умолчанию для «за сколько минут напоминать». */
val NOTIFY_LEAD_OPTIONS = intArrayOf(5, 10, 15, 30)

const val HOME_URL: String = Constants.HOME_URL
