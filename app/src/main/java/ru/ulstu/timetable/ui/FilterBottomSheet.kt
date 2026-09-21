package ru.ulstu.timetable.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import ru.ulstu.timetable.R
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.databinding.SheetFilterBinding

/** Итоговое состояние фильтра расписания. */
data class FilterState(
    val hiddenPairs: Set<Int>,
    val hideEmptyDays: Boolean,
    val hidePast: Boolean,
    val subgroupEnabled: Boolean,
    val subgroup: Int,
    val skipOptionalInWidget: Boolean
)

/**
 * Фильтр по парам — единственное место, где настраиваются пары:
 *
 *  - снятая галочка в «Какие пары показывать» прячет столбец с этой парой;
 *  - «только моя подгруппа» оставляет занятия своей подгруппы (у части пар
 *    на сайте есть «1-я» и «2-я п/г»);
 *  - пометки «необязательная» ставятся тапом прямо по ячейке расписания,
 *    потому что такая пара может быть в любом месте.
 */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFilterBinding? = null
    private val binding get() = _binding!!

    var onApply: ((FilterState) -> Unit)? = null

    /** Сбросить все пометки «необязательная» (кнопка в разделе пометок). */
    var onResetTags: (() -> Unit)? = null

    /** Сколько пар в таблице на текущей странице. */
    var pairCount: Int = 8

    /** Сколько ячеек уже помечено необязательными. */
    var taggedCount: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())
        val count = pairCount.coerceAtLeast(1)

        val hidden = prefs.hiddenPairs.toMutableSet()
        var subgroup = prefs.subgroup

        fun rebuildVisibility() {
            binding.chips.removeAllViews()
            for (pair in 1..count) {
                val chip = Chip(requireContext()).apply {
                    text = getString(R.string.pair_short, pair)
                    isCheckable = true
                    // Сначала состояние, потом слушатель — иначе поймаем ложное событие.
                    isChecked = pair !in hidden
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) hidden.remove(pair) else hidden.add(pair)
                    }
                }
                binding.chips.addView(chip)
            }
        }

        fun rebuildSubgroups() {
            binding.subgroupChips.removeAllViews()
            for (number in 1..2) {
                val chip = Chip(requireContext()).apply {
                    text = getString(R.string.subgroup_number, number)
                    isCheckable = true
                    isChecked = subgroup == number
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) subgroup = number
                    }
                }
                binding.subgroupChips.addView(chip)
            }
            binding.subgroupChips.isEnabled = binding.onlyMySubgroup.isChecked
        }

        rebuildVisibility()
        rebuildSubgroups()

        binding.hideEmptyDays.isChecked = prefs.hideEmptyDays
        binding.hidePast.isChecked = prefs.hidePast
        binding.skipOptional.isChecked = prefs.skipOptionalInWidget
        binding.onlyMySubgroup.isChecked = prefs.subgroupEnabled
        binding.subgroupChips.isEnabled = prefs.subgroupEnabled

        binding.tagsCount.text = if (taggedCount == 0) {
            getString(R.string.filter_tags_none)
        } else {
            resources.getQuantityString(R.plurals.filter_tags_count, taggedCount, taggedCount)
        }

        binding.onlyMySubgroup.setOnCheckedChangeListener { _, checked ->
            binding.subgroupChips.isEnabled = checked
        }

        binding.selectAll.setOnClickListener {
            hidden.clear()
            rebuildVisibility()
        }
        binding.selectNone.setOnClickListener {
            for (pair in 1..count) hidden.add(pair)
            rebuildVisibility()
        }
        binding.resetTags.setOnClickListener {
            onResetTags?.invoke()
            binding.tagsCount.text = getString(R.string.filter_tags_none)
        }

        binding.applyButton.setOnClickListener {
            onApply?.invoke(
                FilterState(
                    hiddenPairs = hidden.toSet(),
                    hideEmptyDays = binding.hideEmptyDays.isChecked,
                    hidePast = binding.hidePast.isChecked,
                    subgroupEnabled = binding.onlyMySubgroup.isChecked,
                    subgroup = subgroup,
                    skipOptionalInWidget = binding.skipOptional.isChecked
                )
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
