package org.mochios.android.ui.components

import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs

class ClickableLinkTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    var onNonLinkClick: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var touchedLink = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                touchedLink = hitsLink(event)
            }
            MotionEvent.ACTION_UP -> {
                val moved = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                if (!touchedLink && !moved) {
                    onNonLinkClick?.invoke()
                }
            }
        }
        if (touchedLink) {
            return super.onTouchEvent(event)
        }
        return onNonLinkClick != null
    }

    private fun hitsLink(event: MotionEvent): Boolean {
        val spanned = text as? Spanned ?: return false
        val layout = layout ?: return false
        val x = event.x.toInt() - totalPaddingLeft + scrollX
        val y = event.y.toInt() - totalPaddingTop + scrollY
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return false
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        return spanned.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()
    }
}
