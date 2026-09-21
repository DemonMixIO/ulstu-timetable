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

/**
 * Фильтр по парам: снятая галочка прячет столбец с этой парой
 * в таблице расписания.
 */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFilterBinding? = null
    private val binding get() = _binding!!

    /** (скрытые пары, скрывать пустые дни, скрывать прошедшие) */
    var onApply: ((Set<Int>, Boolean, Boolean) -> Unit)? = null

    /** Номера пар, которые сейчас показаны. */
    var pairCount: Int = 8

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

        var count = pairCount.coerceAtLeast(1)
        val hidden = prefs.hiddenPairs.toMutableSet()

        fun rebuildChips() {
            binding.chips.removeAllViews()
            for (pair in 1..count) {
                val chip = Chip(requireContext()).apply {
                    text = getString(R.string.pair_short, pair)
                    isCheckable = true
                    isChecked = pair !in hidden
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) hidden.remove(pair) else hidden.add(pair)
                    }
                }
                binding.chips.addView(chip)
            }
        }

        rebuildChips()

        binding.hideEmptyDays.isChecked = prefs.hideEmptyDays
        binding.hidePast.isChecked = prefs.hidePast

        binding.selectAll.setOnClickListener {
            hidden.clear()
            rebuildChips()
        }
        binding.selectNone.setOnClickListener {
            for (pair in 1..count) hidden.add(pair)
            rebuildChips()
        }

        binding.applyButton.setOnClickListener {
            onApply?.invoke(hidden.toSet(), binding.hideEmptyDays.isChecked, binding.hidePast.isChecked)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
