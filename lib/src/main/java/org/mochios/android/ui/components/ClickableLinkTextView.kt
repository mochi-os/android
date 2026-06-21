// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.image.AsyncDrawableSpan
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ClickableLinkTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    var onNonLinkClick: (() -> Unit)? = null

    /** Invoked on a long-press over an image that has alt/title text. */
    var onImageLongPress: ((String) -> Unit)? = null

    /** Image URL (AsyncDrawable destination) -> its alt/title text. */
    var imageAltByUrl: Map<String, String> = emptyMap()

    /**
     * When true, the visible line count is capped to whatever fits the view's
     * own (bounded) height and the last visible line is ellipsised. Used by the
     * feeds magazine page, where each post fills the screen: the body grows to
     * occupy the available space instead of a fixed line cap, and only truncates
     * when the text genuinely overflows. Leave false for the normal
     * maxLines-driven behaviour.
     */
    var clampToHeight = false

    /**
     * When true, all touch handling is skipped (onTouchEvent returns false) so
     * the gesture passes straight to the Compose parent. Used by the feeds
     * magazine preview, where the body fills the screen: without this the
     * full-screen text view would swallow every swipe and the pager could not
     * flip to the next post. Taps still open the post via the card's own
     * clickable; links and image alt-text remain available in the detail view.
     */
    var passThroughTouches = false

    private var downX = 0f
    private var downY = 0f
    private var pendingLink: ClickableSpan? = null
    private var touchedTableRow = false
    private var imageAlt: String? = null
    private var longPressFired = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private val longPressRunnable = Runnable {
        imageAlt?.let { alt ->
            longPressFired = true
            onImageLongPress?.invoke(alt)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (clampToHeight) {
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            // Only meaningful when the parent bounds our height (EXACTLY from a
            // weighted slot, or AT_MOST). With an unbounded height there is
            // nothing to fit against, so fall through to the default behaviour.
            if (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
                val available = MeasureSpec.getSize(heightMeasureSpec) -
                    compoundPaddingTop - compoundPaddingBottom
                val width = MeasureSpec.getSize(widthMeasureSpec) -
                    compoundPaddingLeft - compoundPaddingRight
                val content = text
                if (available > 0 && width > 0 && !content.isNullOrEmpty()) {
                    // Lay the full (uncapped) text out in a throwaway layout so
                    // the target line count is derived independently of the
                    // view's current maxLines — that keeps it stable across
                    // measure passes and avoids a maxLines feedback loop.
                    val full = StaticLayout.Builder
                        .obtain(content, 0, content.length, paint, width)
                        .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                        .setIncludePad(includeFontPadding)
                        .build()
                    var fit = 0
                    while (fit < full.lineCount && full.getLineBottom(fit) <= available) fit++
                    val target = if (fit < full.lineCount) fit.coerceAtLeast(1) else Int.MAX_VALUE
                    if (maxLines != target) {
                        maxLines = target
                        ellipsize = if (target == Int.MAX_VALUE) null else TextUtils.TruncateAt.END
                    }
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Don't consume anything: let the Compose pager flip and the card's
        // clickable open the post.
        if (passThroughTouches) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressFired = false
                // Only engage the image path when a handler is wired, so other
                // HtmlContent callers keep their existing behaviour untouched.
                imageAlt = if (onImageLongPress != null) imageAltAt(event) else null
                if (imageAlt != null) {
                    pendingLink = null
                    touchedTableRow = false
                    postDelayed(longPressRunnable, longPressTimeout)
                } else {
                    pendingLink = linkAt(event)
                    touchedTableRow = pendingLink == null && tableRowAt(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (imageAlt != null &&
                    (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)
                ) {
                    // Became a scroll/drag — cancel the pending long-press.
                    removeCallbacks(longPressRunnable)
                    imageAlt = null
                }
            }
            MotionEvent.ACTION_UP -> {
                val moved = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                if (imageAlt != null) {
                    removeCallbacks(longPressRunnable)
                    // A quick tap on an image (no long-press) falls through to
                    // the card handler, matching prior behaviour.
                    if (!longPressFired && !moved) onNonLinkClick?.invoke()
                } else if (!moved) {
                    val link = pendingLink
                    when {
                        link != null -> link.onClick(this)
                        !touchedTableRow -> onNonLinkClick?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                imageAlt = null
            }
        }
        // Over an image with alt text: we own the gesture (tap + long-press).
        if (imageAlt != null) return true
        // Regular link tap: consume so we fire it ourselves on UP.
        if (pendingLink != null) return true
        // Table rows: let TableAwareMovementMethod dispatch into the cell.
        if (touchedTableRow) return super.onTouchEvent(event)
        // Non-link: unchanged — consume only when a card-level handler is set;
        // otherwise defer to the TextView (text selection).
        return onNonLinkClick != null
    }

    private fun imageAltAt(event: MotionEvent): String? {
        if (imageAltByUrl.isEmpty()) return null
        val spanned = text as? Spanned ?: return null
        val layout = layout ?: return null
        val x = event.x.toInt() - totalPaddingLeft + scrollX
        val y = event.y.toInt() - totalPaddingTop + scrollY
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        for (span in spanned.getSpans(offset, offset, AsyncDrawableSpan::class.java)) {
            val url = span.drawable.destination ?: continue
            val alt = imageAltByUrl[url]
            if (!alt.isNullOrBlank()) return alt
        }
        return null
    }

    /**
     * Find a link span near the touch. The default per-glyph hit test makes
     * short links / URLs very hard to tap, so widen the hit area by the touch
     * slop on each side: a near-miss still opens the link.
     */
    private fun linkAt(event: MotionEvent): ClickableSpan? {
        val spanned = text as? Spanned ?: return null
        val layout = layout ?: return null
        val x = event.x - totalPaddingLeft + scrollX
        val y = event.y.toInt() - totalPaddingTop + scrollY
        val line = layout.getLineForVertical(y)
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        // Reject taps clearly off the line's text (beyond one slop of either end).
        if (x < left - touchSlop || x > right + touchSlop) return null
        val xLeft = (x - touchSlop).coerceIn(left, right)
        val xRight = (x + touchSlop).coerceIn(left, right)
        val o1 = layout.getOffsetForHorizontal(line, xLeft)
        val o2 = layout.getOffsetForHorizontal(line, xRight)
        return spanned.getSpans(min(o1, o2), max(o1, o2), ClickableSpan::class.java).firstOrNull()
    }

    /**
     * Markwon renders each table row as a TableRowSpan (a ReplacementSpan);
     * links inside cells are ClickableSpans held in the cell's own text,
     * invisible to this outer layout. Treat any tap on a table row as a link
     * tap so the event reaches TableAwareMovementMethod, which dispatches into
     * the cell and fires the link if one is there.
     */
    private fun tableRowAt(event: MotionEvent): Boolean {
        val spanned = text as? Spanned ?: return false
        val layout = layout ?: return false
        val x = event.x.toInt() - totalPaddingLeft + scrollX
        val y = event.y.toInt() - totalPaddingTop + scrollY
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return false
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        return spanned.getSpans(offset, offset, TableRowSpan::class.java).isNotEmpty()
    }
}
