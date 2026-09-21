package ru.ulstu.timetable.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.R
import ru.ulstu.timetable.data.GroupCatalog
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.data.ScheduleRepository
import ru.ulstu.timetable.databinding.ActivityGroupPickerBinding
import ru.ulstu.timetable.databinding.ItemGroupBinding

/** Поиск группы по всем разделам сайта с избранным. */
class GroupPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupPickerBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: GroupAdapter

    private var all: List<GroupCatalog.Entry> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = GroupAdapter { entry -> openGroup(entry) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim()
                render()
            }
        })

        // Сначала показываем сохранённый список, затем обновляем его из сети.
        val cached = GroupCatalog.fromJson(prefs.groupsCache)
        if (cached.isNotEmpty()) {
            all = cached
            render()
        } else {
            showLoading(true)
        }
        loadGroups()
    }

    private fun loadGroups() {
        lifecycleScope.launch {
            val repository = ScheduleRepository(this@GroupPickerActivity)
            val fetched = GroupCatalog.fetch(repository, Constants.HOME_URL)
            showLoading(false)

            if (fetched.isEmpty()) {
                if (all.isEmpty()) {
                    binding.stateView.visibility = View.VISIBLE
                    binding.stateMessage.text = getString(R.string.error_picker)
                    binding.stateAction.setOnClickListener { loadGroups() }
                }
                return@launch
            }

            all = fetched
            prefs.groupsCache = GroupCatalog.toJson(fetched)
            prefs.groupsCacheAt = System.currentTimeMillis()
            render()
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.stateView.visibility = View.GONE
    }

    private fun render() {
        val favorites = prefs.favorites
        val filtered = if (query.isBlank()) {
            all
        } else {
            val q = query.lowercase()
            all.filter { it.name.lowercase().contains(q) || it.section.lowercase().contains(q) }
        }

        val sorted = filtered.sortedWith(
            compareBy(
                { if (favorites.contains(it.url)) 0 else 1 },
                { it.section },
                { it.course },
                { it.name.lowercase() }
            )
        )

        adapter.submit(sorted, favorites)
        binding.stateView.visibility =
            if (sorted.isEmpty() && all.isNotEmpty()) View.VISIBLE else View.GONE
        if (sorted.isEmpty() && all.isNotEmpty()) {
            binding.stateMessage.text = getString(R.string.error_picker)
            binding.stateAction.visibility = View.GONE
        } else {
            binding.stateAction.visibility = View.VISIBLE
        }
    }

    private fun openGroup(entry: GroupCatalog.Entry) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_URL, entry.url).putExtra(EXTRA_NAME, entry.name)
        )
        finish()
    }

    private inner class GroupAdapter(
        private val onClick: (GroupCatalog.Entry) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.Holder>() {

        private var items: List<GroupCatalog.Entry> = emptyList()
        private var favorites: Set<String> = emptySet()

        fun submit(list: List<GroupCatalog.Entry>, favs: Set<String>) {
            items = list
            favorites = favs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val itemBinding = ItemGroupBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(itemBinding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.bind(entry, favorites.contains(entry.url))
        }

        inner class Holder(private val item: ItemGroupBinding) :
            RecyclerView.ViewHolder(item.root) {

            fun bind(entry: GroupCatalog.Entry, isFavorite: Boolean) {
                item.groupName.text = entry.name
                item.groupSection.text = if (entry.course > 0) {
                    "${entry.section} · ${entry.course} курс"
                } else {
                    entry.section
                }
                item.groupStar.setImageResource(
                    if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_border
                )
                item.groupStar.setOnClickListener {
                    val nowFavorite = prefs.toggleFavorite(entry.url)
                    render()
                    if (nowFavorite) {
                        prefs.trackedUrl = entry.url
                        prefs.trackedTitle = entry.name
                    }
                }
                item.root.setOnClickListener { onClick(entry) }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NAME = "extra_name"
    }
}
