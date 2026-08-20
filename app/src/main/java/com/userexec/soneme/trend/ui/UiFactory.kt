package com.userexec.soneme.trend.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

object UiFactory {
    const val BLUE = 0xFF4F6F8F.toInt()
    const val TAB_GRAY = 0xFFD9DEE3.toInt()
    const val FOCUS_BLUE = 0xFFF0F5F7.toInt()
    const val FOCUS_BORDER = 0xFF6E9DB4.toInt()
    const val HEADER_GRAY = 0xFFF2F2F2.toInt()
    const val RED = 0xFFB00020.toInt()

    fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    fun vertical(context: Context, scroll: Boolean = false, horizontalPadding: Int = 7): ViewGroup {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(
                context.dp(horizontalPadding),
                if (horizontalPadding == 0) 0 else context.dp(5),
                context.dp(horizontalPadding),
                if (horizontalPadding == 0) 0 else context.dp(6)
            )
        }
        if (!scroll) return content
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            tag = content
        }
    }

    fun text(context: Context, value: String = "", size: Float = 15f, bold: Boolean = false): TextView = TextView(context).apply {
        text = value
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(context.dp(2), context.dp(3), context.dp(2), context.dp(3))
    }

    fun heading(context: Context, value: String): TextView = text(context, value, 18f, true).apply {
        setPadding(context.dp(5), context.dp(5), context.dp(5), context.dp(6))
    }

    fun chartTitleHeader(context: Context, value: String): TextView = text(context, value, 18f, true).apply {
        setBackgroundColor(HEADER_GRAY)
        setPadding(context.dp(8), context.dp(7), context.dp(8), context.dp(7))
    }

    fun focusableText(context: Context, value: String, size: Float = 16f): TextView = text(context, value, size).apply {
        isFocusable = true
        isClickable = true
        background = focusDrawable(context)
        setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
    }

    fun edit(context: Context, hintText: String = ""): EditText = EditText(context).apply {
        hint = hintText
        textSize = 16f
        setSingleLine(true)
        minimumHeight = context.dp(46)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(6), context.dp(8), context.dp(6), context.dp(8))
    }

    fun labeled(context: Context, label: String, control: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(context, label, 14f, true))
        addView(control, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setPadding(0, context.dp(2), 0, context.dp(4))
    }

    /** Soneme-style tabs: visual state only. D-pad tab switching is handled by the screen. */
    fun tabs(context: Context, selected: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val names = listOf("Data", "Analyses", "Correlations")
        names.forEachIndexed { index, name ->
            val tab = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(if (index == selected) Color.WHITE else 0xFF1A1A1A.toInt())
                isFocusable = false
                isClickable = false
                setBackgroundColor(if (index == selected) BLUE else TAB_GRAY)
            }
            addView(tab, LinearLayout.LayoutParams(0, context.dp(40), 1f))
        }
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(0xFFE0E0E0.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1))
    }

    fun focusDrawable(context: Context): StateListDrawable {
        fun focused() = GradientDrawable().apply {
            setColor(FOCUS_BLUE)
            setStroke(context.dp(2), FOCUS_BORDER)
        }
        val normal = ColorDrawable(Color.WHITE)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused())
            addState(intArrayOf(android.R.attr.state_selected), focused())
            addState(intArrayOf(android.R.attr.state_pressed), focused())
            addState(intArrayOf(), normal)
        }
    }

    fun row(context: Context, title: String, subtitle: String? = null, warning: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = context.dp(50)
        isFocusable = true
        isClickable = true
        background = focusDrawable(context)
        setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        addView(text(context, title, 17f, true).apply {
            setPadding(0, 0, 0, 0)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        })
        if (!subtitle.isNullOrBlank()) addView(text(context, subtitle, 12f).apply {
            setPadding(0, 0, 0, 0)
            if (warning) setTextColor(RED)
            if (warning) { setSingleLine(true); ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true }
        })
    }

    fun dataRow(context: Context, title: String, countText: String?, lastText: String?, warningText: String? = null): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = context.dp(50)
        isFocusable = true
        isClickable = true
        background = focusDrawable(context)
        setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        addView(text(context, title, 17f, true).apply {
            setPadding(0, 0, 0, 0)
            setSingleLine(true); ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true
        })
        if (warningText != null) {
            addView(text(context, warningText, 12f).apply {
                setPadding(0, 0, 0, 0)
                setTextColor(RED); setSingleLine(true); ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true
            })
        } else {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(context, countText.orEmpty(), 12f).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(context, lastText.orEmpty(), 12f).apply { setPadding(0, 0, 0, 0); gravity = Gravity.END }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            })
        }
    }

    fun pointRow(context: Context, timestamp: String, value: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(46)
        isFocusable = true
        isClickable = true
        background = focusDrawable(context)
        setPadding(context.dp(8), context.dp(5), context.dp(8), context.dp(5))
        addView(text(context, timestamp, 14.5f).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(context, value, 14.5f, true).apply { setPadding(0, 0, 0, 0); gravity = Gravity.END }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun correlationRow(context: Context, title: String, members: List<Pair<String, Boolean>>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = true
        isClickable = true
        background = focusDrawable(context)
        setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        addView(text(context, title, 17f, true).apply {
            setPadding(0, 0, 0, 0)
            setSingleLine(true); ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true
        })
        members.forEach { (name, unavailable) ->
            addView(text(context, name, 12f).apply {
                setPadding(0, 0, 0, 0)
                if (unavailable) setTextColor(RED)
                setSingleLine(true); ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true
            })
        }
    }

    fun pickerRow(context: Context, label: String, value: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(44)
        isFocusable = true
        isClickable = true
        background = focusDrawable(context)
        setPadding(context.dp(8), context.dp(5), context.dp(8), context.dp(5))
        addView(text(context, label, 16f).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(context, value, 17f, true).apply { setPadding(0, 0, 0, 0); gravity = Gravity.END }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun centeredMessage(context: Context, value: String): TextView = text(context, value, 15f).apply {
        gravity = Gravity.CENTER
        setPadding(context.dp(10), context.dp(15), context.dp(10), context.dp(15))
    }
}
