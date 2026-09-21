package ru.ulstu.timetable.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import ru.ulstu.timetable.R

/**
 * Небольшой конструктор строк настроек: экран настроек целиком собирается в коде,
 * чтобы не расписывать десятки почти одинаковых layout-файлов.
 */
object SettingsRows {

    fun section(context: Context, parent: LinearLayout, titleRes: Int) {
        parent.addView(
            TextView(context).apply {
                setText(titleRes)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTextColor(color(context, com.google.android.material.R.attr.colorPrimary))
                setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 4))
            }
        )
    }

    fun switchRow(
        context: Context,
        parent: LinearLayout,
        titleRes: Int,
        descRes: Int?,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = row(context)
        row.addView(textBlock(context, titleRes, descRes), weight())
        row.addView(
            MaterialSwitch(context).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, value -> onChange(value) }
            }
        )
        parent.addView(row)
    }

    fun clickRow(
        context: Context,
        parent: LinearLayout,
        titleRes: Int,
        value: String?,
        onClick: () -> Unit
    ) {
        val row = row(context, clickable = true)
        row.addView(textBlock(context, titleRes, null, value), weight())
        row.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right)
                layoutParams = LinearLayout.LayoutParams(dp(context, 20), dp(context, 20))
            }
        )
        row.setOnClickListener { onClick() }
        parent.addView(row)
    }

    fun infoRow(context: Context, parent: LinearLayout, titleRes: Int, value: String) {
        val row = row(context)
        row.addView(textBlock(context, titleRes, null, value), weight())
        parent.addView(row)
    }

    fun choiceRow(
        context: Context,
        parent: LinearLayout,
        titleRes: Int,
        entries: Array<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val valueView = textBlock(context, titleRes, null, entries.getOrNull(selectedIndex).orEmpty())
        val row = row(context, clickable = true)
        row.addView(valueView, weight())
        row.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right)
                layoutParams = LinearLayout.LayoutParams(dp(context, 20), dp(context, 20))
            }
        )
        row.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setSingleChoiceItems(entries, selectedIndex) { dialog, which ->
                    onSelect(which)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        parent.addView(row)
    }

    fun sliderRow(
        context: Context,
        parent: LinearLayout,
        titleRes: Int,
        from: Float,
        to: Float,
        step: Float,
        value: Float,
        valueSuffix: String,
        onValue: (Int) -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 10), dp(context, 20), 0)
        }

        val title = TextView(context).apply {
            setText(titleRes)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(color(context, com.google.android.material.R.attr.colorOnSurface))
        }
        val valueView = TextView(context).apply {
            text = "${value.toInt()}$valueSuffix"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(color(context, com.google.android.material.R.attr.colorPrimary))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, weight())
            addView(valueView)
        }

        val slider = Slider(context).apply {
            valueFrom = from
            valueTo = to
            stepSize = step
            this.value = value
            addOnChangeListener { _, newValue, _ -> valueView.text = "${newValue.toInt()}$valueSuffix" }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit
                override fun onStopTrackingTouch(slider: Slider) = onValue(slider.value.toInt())
            })
        }

        container.addView(header)
        container.addView(slider)
        parent.addView(container)
    }

    // --- Примитивы ----------------------------------------------------------

    private fun row(context: Context, clickable: Boolean = false): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 56)
            setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 8))
            if (clickable) background = selectableBackground(context)
        }

    private fun textBlock(
        context: Context,
        titleRes: Int,
        descRes: Int?,
        value: String? = null
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                setText(titleRes)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setTextColor(color(context, com.google.android.material.R.attr.colorOnSurface))
            }
        )
        if (descRes != null) {
            addView(
                TextView(context).apply {
                    setText(descRes)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(color(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
                }
            )
        }
        if (!value.isNullOrBlank()) {
            addView(
                TextView(context).apply {
                    text = value
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(color(context, com.google.android.material.R.attr.colorPrimary))
                }
            )
        }
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun color(context: Context, attr: Int): Int =
        MaterialColors.getColor(context, attr, 0)

    private fun selectableBackground(context: Context): Drawable? {
        val typed = TypedValue()
        return if (context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, typed, true
            )
        ) {
            ContextCompat.getDrawable(context, typed.resourceId)
        } else {
            null
        }
    }
}
