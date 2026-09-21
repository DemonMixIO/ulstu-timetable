package ru.ulstu.timetable.web

/**
 * Скрипты, которые внедряются в страницу расписания.
 *
 * Сайт — это статический HTML, сгенерированный Word, без классов и id,
 * поэтому фильтр работает прямо по таблице: колонка N соответствует N-й паре,
 * а первая колонка — день недели.
 *
 * Все скрипты сначала проверяют, что на странице действительно есть таблица
 * расписания (со строками «Пары» и «Время»). На страницах выбора группы таблицы
 * другие — там фильтры не должны ничего трогать.
 */
object InjectedScripts {

    /**
     * Скрывает ненужные пары (по номерам), пустые дни, прошедшие занятия
     * и пары чужой подгруппы.
     *
     * Повторный вызов полностью пересчитывает состояние, поэтому фильтр
     * можно менять без перезагрузки страницы.
     */
    fun pairFilter(
        visiblePairs: List<Int>,
        hideEmptyDays: Boolean,
        hidePast: Boolean,
        subgroup: Int,
        nowMillis: Long
    ): String {
        val template = """
(function(){
  try {
    var VISIBLE = __VISIBLE__;
    var HIDE_EMPTY = __HIDE_EMPTY__;
    var HIDE_PAST = __HIDE_PAST__;
    var SUBGROUP = __SUBGROUP__;
    var NOW = new Date(__NOW__);
    var NBSP = String.fromCharCode(160);

    function txt(el){
      if (!el) return '';
      var s = el.innerText;
      if (s === undefined || s === null) s = el.textContent || '';
      return s.split(NBSP).join(' ').replace(/\s+/g, ' ').trim();
    }
    function isVisible(p){
      for (var i = 0; i < VISIBLE.length; i++) if (VISIBLE[i] === p) return true;
      return false;
    }
    function parseRange(s){
      var m = /(\d{1,2}):(\d{2})\s*[\u2013\u2014-]\s*(\d{1,2}):(\d{2})/.exec(s || '');
      if (!m) return null;
      return { start: (+m[1]) * 60 + (+m[2]), end: (+m[3]) * 60 + (+m[4]) };
    }
    // «2-я п/г» -> 2; если подгруппа не указана — 0 (занятие общее для всех).
    function subgroupNumber(el){
      var m = /(\d)\s*-\s*я\s*п\s*\/\s*г/.exec(txt(el).toLowerCase());
      return m ? (+m[1]) : 0;
    }
    // Фильтры имеют смысл только там, где есть таблица расписания. На странице
    // выбора группы таких строк нет, и без этой проверки «скрывать дни без пар»
    // прятал всю таблицу с направлениями.
    function scheduleTables(){
      var out = [];
      var all = document.getElementsByTagName('TABLE');
      for (var t = 0; t < all.length; t++) {
        var rows = all[t].rows;
        if (!rows || rows.length < 2) continue;
        for (var r = 0; r < rows.length; r++) {
          var c = rows[r].cells;
          if (!c || !c.length) continue;
          var f = txt(c[0]);
          if (/^Пары/.test(f) || /^Время/.test(f)) { out.push(all[t]); break; }
        }
      }
      return out;
    }

    var tables = scheduleTables();
    if (!tables.length) return;

    var today = new Date(NOW.getFullYear(), NOW.getMonth(), NOW.getDate());
    var nowMin = NOW.getHours() * 60 + NOW.getMinutes();

    for (var t = 0; t < tables.length; t++) {
      var rows = tables[t].rows;
      if (!rows || rows.length < 2) continue;

      // Время звонков по колонкам — из строки «Время».
      var times = [];
      for (var r = 0; r < rows.length; r++) {
        var hc = rows[r].cells;
        if (!hc || !hc.length) continue;
        if (/^Время/.test(txt(hc[0]))) {
          for (var c = 1; c < hc.length; c++) times[c] = parseRange(txt(hc[c]));
          break;
        }
      }

      var dayRows = 0;
      var shownDays = 0;
      for (var r = 0; r < rows.length; r++) {
        var cells = rows[r].cells;
        if (!cells || !cells.length) continue;

        var first = txt(cells[0]);
        var dm = /^([А-Яа-яЁё]{3})\s*,\s*(\d{2})\.(\d{2})\.(\d{4})/.exec(first);
        var isDay = !!dm;
        var isToday = false;
        var past = false;
        if (isDay) {
          dayRows++;
          var d = new Date((+dm[4]), (+dm[3]) - 1, (+dm[2]));
          isToday = d.getTime() === today.getTime();
          past = d.getTime() < today.getTime();
        }

        for (var c = 1; c < cells.length; c++) {
          var keep = isVisible(c);
          if (keep && HIDE_PAST && isToday && times[c] && times[c].end < nowMin) {
            keep = false;
          }
          if (keep && isDay && SUBGROUP > 0) {
            var sg = subgroupNumber(cells[c]);
            if (sg > 0 && sg !== SUBGROUP) keep = false;
          }
          cells[c].style.display = keep ? '' : 'none';
        }

        if (!isDay) { rows[r].style.display = ''; continue; }

        var any = false;
        for (var c = 1; c < cells.length; c++) {
          if (cells[c].style.display === 'none') continue;
          if (txt(cells[c]) !== '') { any = true; break; }
        }

        var hide = false;
        if (HIDE_PAST && past) hide = true;
        else if (HIDE_PAST && isToday && !any) hide = true;
        else if (HIDE_EMPTY && !any) hide = true;

        rows[r].style.display = hide ? 'none' : '';
        if (!hide) shownDays++;
      }

      // Прячем таблицу только если в ней были дни и все они скрыты.
      if (dayRows > 0 && (HIDE_EMPTY || HIDE_PAST)) {
        tables[t].style.display = (shownDays === 0) ? 'none' : '';
      } else {
        tables[t].style.display = '';
      }
    }
  } catch (e) { }
})();
"""
        return template
            .replace("__VISIBLE__", visiblePairs.joinToString(",", "[", "]"))
            .replace("__HIDE_EMPTY__", hideEmptyDays.toString())
            .replace("__HIDE_PAST__", hidePast.toString())
            .replace("__SUBGROUP__", subgroup.toString())
            .replace("__NOW__", nowMillis.toString())
    }

    /**
     * Теги пар: необязательная пара помечается по конкретной ячейке
     * (день + номер пары), а не по всему столбцу.
     *
     * Скрипт расставляет уже сохранённые пометки и навешивает обработчик:
     * тап по ячейке переключает пометку и сообщает об этом приложению.
     */
    fun cellTags(keys: List<String>): String {
        val template = """
(function(){
  try {
    var KEYS = __KEYS__;
    var MARK = 'tt-opt';
    var NBSP = String.fromCharCode(160);

    function txt(el){
      if (!el) return '';
      var s = el.innerText;
      if (s === undefined || s === null) s = el.textContent || '';
      return s.split(NBSP).join(' ').replace(/\s+/g, ' ').trim();
    }
    function scheduleTables(){
      var out = [];
      var all = document.getElementsByTagName('TABLE');
      for (var t = 0; t < all.length; t++) {
        var rows = all[t].rows;
        if (!rows || rows.length < 2) continue;
        for (var r = 0; r < rows.length; r++) {
          var c = rows[r].cells;
          if (!c || !c.length) continue;
          var f = txt(c[0]);
          if (/^Пары/.test(f) || /^Время/.test(f)) { out.push(all[t]); break; }
        }
      }
      return out;
    }
    // Ключ ячейки: «2026-09-14|3» — день и номер пары.
    function keyOf(cell){
      var tr = cell.parentNode;
      if (!tr || !tr.cells) return '';
      var idx = -1;
      for (var i = 0; i < tr.cells.length; i++) if (tr.cells[i] === cell) idx = i;
      if (idx < 1) return '';
      var m = /^[А-Яа-яЁё]{3}\s*,\s*(\d{2})\.(\d{2})\.(\d{4})/.exec(txt(tr.cells[0]));
      if (!m) return '';
      return m[3] + '-' + m[2] + '-' + m[1] + '|' + idx;
    }
    function has(key){
      for (var i = 0; i < KEYS.length; i++) if (KEYS[i] === key) return true;
      return false;
    }

    var tables = scheduleTables();
    if (!tables.length) return;

    for (var t = 0; t < tables.length; t++) {
      var rows = tables[t].rows;
      if (!rows) continue;
      for (var r = 0; r < rows.length; r++) {
        var cells = rows[r].cells;
        if (!cells || cells.length < 2) continue;
        if (!/^[А-Яа-яЁё]{3}\s*,/.test(txt(cells[0]))) continue;
        for (var c = 1; c < cells.length; c++) {
          var key = keyOf(cells[c]);
          if (key && has(key)) cells[c].classList.add(MARK);
          else cells[c].classList.remove(MARK);
        }
      }
    }

    if (!window.__ttTagsBound) {
      window.__ttTagsBound = true;
      document.addEventListener('click', function(ev){
        try {
          var cell = ev.target;
          while (cell && cell.tagName !== 'TD') cell = cell.parentNode;
          if (!cell || cell.tagName !== 'TD') return;
          var key = keyOf(cell);
          if (!key) return;
          ev.preventDefault();
          ev.stopPropagation();
          var on = cell.classList.contains(MARK);
          if (on) cell.classList.remove(MARK); else cell.classList.add(MARK);
          var B = window.AndroidTimetable;
          if (B && B.onOptionalToggle) B.onOptionalToggle(key, !on);
        } catch (e) { }
      }, true);
    }
  } catch (e) { }
})();
""".replace("__KEYS__", keys.joinToString(",", "[", "]") { jsString(it) })
        return template
    }

    /**
     * Оформление страницы: подгонка широкой таблицы под экран телефона
     * и, при желании, тёмная тема.
     */
    fun styleSheet(dark: Boolean, fitWidth: Boolean): String {
        val css = buildString {
            append(BASE_CSS)
            append(MARK_CSS)
            if (fitWidth) append(FIT_CSS)
            if (dark) append(DARK_CSS)
        }
        val template = """
(function(){
  try {
    var id = 'tt-style';
    var el = document.getElementById(id);
    if (!el) {
      el = document.createElement('style');
      el.id = id;
      var head = document.head || document.documentElement;
      if (head) head.appendChild(el);
    }
    el.textContent = __CSS__;
  } catch (e) { }
})();
"""
        return template.replace("__CSS__", jsString(css))
    }

    /**
     * Отдаёт приложению сведения о странице. HTML передаётся только для страниц
     * расписания, чтобы не гонять большой текст зря.
     */
    fun pageProbe(): String = """
(function(){
  try {
    var B = window.AndroidTimetable;
    if (!B || !B.onPage) return;
    var NBSP = String.fromCharCode(160);
    function txt(el){
      if (!el) return '';
      var s = el.innerText;
      if (s === undefined || s === null) s = el.textContent || '';
      return s.split(NBSP).join(' ').replace(/\s+/g, ' ').trim();
    }
    var pairCount = 0;
    var isSchedule = false;
    var tables = document.getElementsByTagName('TABLE');
    for (var t = 0; t < tables.length; t++) {
      var rows = tables[t].rows;
      if (!rows) continue;
      for (var r = 0; r < rows.length; r++) {
        var c = rows[r].cells;
        if (!c || !c.length) continue;
        var f = txt(c[0]);
        if (/^Пары/.test(f) && c.length > 1) {
          pairCount = Math.max(pairCount, c.length - 1);
          isSchedule = true;
        }
        if (/^Время/.test(f) && c.length > 1) isSchedule = true;
      }
    }
    var html = '';
    if (isSchedule && document.documentElement) {
      html = document.documentElement.outerHTML || '';
    }
    B.onPage(String(location.href), String(document.title || ''), pairCount, isSchedule, html);
  } catch (e) { }
})();
"""

    /** Прокрутка к сохранённой позиции после перезагрузки страницы. */
    fun scrollTo(y: Int): String =
        "(function(){try{window.scrollTo(0, $y);}catch(e){}})();"

    /**
     * Поиск по парам в уже открытой странице.
     *
     * Найденные ячейки подсвечиваются, а весь остальной текст становится
     * полупрозрачным — так нужная пара видна сразу. Возвращает число совпадений.
     */
    fun searchApply(query: String): String {
        val template = """
(function(){
  try {
    var NEEDLE = __QUERY__;
    var HIT = 'tt-search-hit';
    var DIM = 'tt-search-dim';
    var NBSP = String.fromCharCode(160);

    function txt(el){
      if (!el) return '';
      var s = el.innerText;
      if (s === undefined || s === null) s = el.textContent || '';
      return s.split(NBSP).join(' ').replace(/\s+/g, ' ').trim();
    }

    var allCells = document.querySelectorAll('td');
    for (var i = 0; i < allCells.length; i++) {
      allCells[i].classList.remove(HIT);
      allCells[i].classList.remove(DIM);
    }
    window.__ttHits = [];
    window.__ttHitIndex = 0;

    if (!NEEDLE) return 0;
    var needle = NEEDLE.toLowerCase();

    // Ищем только среди ячеек занятий: первая колонка — день недели.
    var hits = [];
    var tables = document.getElementsByTagName('TABLE');
    for (var t = 0; t < tables.length; t++) {
      var rows = tables[t].rows;
      if (!rows) continue;
      for (var r = 0; r < rows.length; r++) {
        var cells = rows[r].cells;
        if (!cells || cells.length < 2) continue;
        if (!/^([А-Яа-яЁё]{3})\s*,/.test(txt(cells[0]))) continue;
        for (var c = 1; c < cells.length; c++) {
          var text = txt(cells[c]);
          if (text && text.toLowerCase().indexOf(needle) >= 0) hits.push(cells[c]);
        }
      }
    }

    if (!hits.length) return 0;

    for (var i = 0; i < allCells.length; i++) allCells[i].classList.add(DIM);
    for (var i = 0; i < hits.length; i++) {
      hits[i].classList.remove(DIM);
      hits[i].classList.add(HIT);
    }
    window.__ttHits = hits;
    window.__ttHitIndex = 0;
    if (hits[0].scrollIntoView) hits[0].scrollIntoView({ block: 'center' });
    return hits.length;
  } catch (e) { return -1; }
})();
"""
        return template.replace("__QUERY__", jsString(query))
    }

    /** Переход к следующему (delta = 1) или предыдущему (delta = -1) совпадению. */
    fun searchStep(delta: Int): String = """
(function(){
  try {
    var hits = window.__ttHits || [];
    if (!hits.length) return 0;
    var i = ((window.__ttHitIndex || 0) + (__DELTA__) + hits.length) % hits.length;
    window.__ttHitIndex = i;
    if (hits[i] && hits[i].scrollIntoView) hits[i].scrollIntoView({ block: 'center' });
    return i + 1;
  } catch (e) { return 0; }
})();
""".replace("__DELTA__", delta.toString())

    /** Снять подсветку поиска. */
    fun searchClear(): String = """
(function(){
  try {
    var all = document.querySelectorAll('td');
    for (var i = 0; i < all.length; i++) {
      all[i].classList.remove('tt-search-hit');
      all[i].classList.remove('tt-search-dim');
    }
    window.__ttHits = [];
    window.__ttHitIndex = 0;
  } catch (e) { }
})();
"""

    private const val BASE_CSS = """
* { -webkit-text-size-adjust: 100% !important; }
html, body { margin: 0 !important; padding: 0 !important; }
body {
  padding: 8px 8px 48px 8px !important;
  font-family: Roboto, "Segoe UI", Arial, sans-serif !important;
  line-height: 1.3 !important;
}
body, body * { font-family: Roboto, "Segoe UI", Arial, sans-serif !important; }
/* Word выставляет размеры атрибутами FONT SIZE — приводим их к читаемым на телефоне. */
font[size="1"], font[size="1"] * { font-size: 11px !important; line-height: 1.25 !important; }
font[size="2"], font[size="2"] * { font-size: 12px !important; }
font[size="3"], font[size="3"] * { font-size: 13px !important; }
font[size="4"], font[size="4"] * { font-size: 14px !important; }
font[size="5"], font[size="5"] * { font-size: 15px !important; }
font[size="6"], font[size="6"] * { font-size: 17px !important; }
font[size="7"], font[size="7"] * { font-size: 19px !important; }
p { margin: 0.35em 0 !important; }
"""

    private const val FIT_CSS = """
table {
  width: 100% !important;
  max-width: 100% !important;
  border-collapse: collapse !important;
  table-layout: fixed !important;
}
td, th {
  overflow-wrap: anywhere !important;
  word-break: break-word !important;
  vertical-align: top !important;
  border: 1px solid rgba(128,128,128,0.35) !important;
  padding: 3px 2px !important;
}
img, video { max-width: 100% !important; height: auto !important; }
"""

    /**
     * Пометки поверх расписания: подсветка поиска и необязательные пары.
     * Идут до DARK_CSS в порядке возрастания приоритета: если ячейка и необязательная,
     * и найдена поиском, должна победить подсветка поиска.
     */
    private const val MARK_CSS = """
.tt-opt {
  opacity: 0.5 !important;
  outline: 2px dashed rgba(46, 91, 255, 0.7) !important;
  outline-offset: -2px !important;
}
.tt-search-dim { opacity: 0.22 !important; }
.tt-search-hit {
  opacity: 1 !important;
  background: rgba(255, 205, 60, 0.38) !important;
  box-shadow: inset 0 0 0 2px #F2A93B !important;
}
"""

    private const val DARK_CSS = """
html, body { background: #10131a !important; color: #e7e9f0 !important; }
body * { background-color: transparent !important; color: #e7e9f0 !important; }
a, a * { color: #9db2ff !important; }
font[size="6"], font[size="6"] * { color: #cdd8ff !important; }
font[size="5"], font[size="5"] * { color: #9aa3b8 !important; }
b, i, strong, em { color: #ffffff !important; }
td, th { border-color: rgba(255,255,255,0.16) !important; }
hr { border-color: rgba(255,255,255,0.16) !important; }
table { background: #10131a !important; }
input, select, textarea {
  background: #1b1f29 !important;
  color: #e7e9f0 !important;
  border: 1px solid rgba(255,255,255,0.2) !important;
}
"""

    /** Экранирует произвольный текст в корректный строковый литерал JavaScript. */
    private fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 16)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u").append("%04x".format(ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
