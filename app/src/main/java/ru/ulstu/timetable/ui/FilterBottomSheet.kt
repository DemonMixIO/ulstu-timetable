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
    val optionalPairs: Set<Int>,
    val hideEmptyDays: Boolean,
    val hidePast: Boolean,
    val skipOptionalInWidget: Boolean
)

/**
 * Фильтр по парам — единственное место, где настраиваются пары:
 *
 *  - снятая галочка в «Какие пары показывать» прячет столбец с этой парой;
 *  - отмеченная в «Необязательные пары» показывается приглушённо с пунктирной
 *    рамкой и (по умолчанию) не попадает в виджет и напоминания.
 */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFilterBinding? = null
    private val binding get() = _binding!!

    var onApply: ((FilterState) -> Unit)? = null

    /** Сколько пар в таблице на текущей странице. */
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
        val count = pairCount.coerceAtLeast(1)

        val hidden = prefs.hiddenPairs.toMutableSet()
        val optional = prefs.optionalPairs.toMutableSet()

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

        fun rebuildOptional() {
            binding.optionalChips.removeAllViews()
            for (pair in 1..count) {
                val chip = Chip(requireContext()).apply {
                    text = getString(R.string.pair_short, pair)
                    isCheckable = true
                    isChecked = pair in optional
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) optional.add(pair) else optional.remove(pair)
                    }
                }
                binding.optionalChips.addView(chip)
            }
        }

        rebuildVisibility()
        rebuildOptional()

        binding.hideEmptyDays.isChecked = prefs.hideEmptyDays
        binding.hidePast.isChecked = prefs.hidePast
        binding.skipOptional.isChecked = prefs.skipOptionalInWidget

        binding.selectAll.setOnClickListener {
            hidden.clear()
            rebuildVisibility()
        }
        binding.selectNone.setOnClickListener {
            for (pair in 1..count) hidden.add(pair)
            rebuildVisibility()
        }

        binding.applyButton.setOnClickListener {
            onApply?.invoke(
                FilterState(
                    hiddenPairs = hidden.toSet(),
                    optionalPairs = optional.toSet(),
                    hideEmptyDays = binding.hideEmptyDays.isChecked,
                    hidePast = binding.hidePast.isChecked,
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
