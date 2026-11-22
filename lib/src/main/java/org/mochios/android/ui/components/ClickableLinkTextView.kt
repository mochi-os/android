// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.os.Build
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
     * When true, no touch is consumed (onTouchEvent returns false) so drags and
     * taps pass to the Compose parent.
     */
    var passThroughTouches = false

    /**
     * When true, the text is truncated to the view's bounded height with a
     * literal "…" appended; `ellipsize = END` does not draw on Markwon
     * `Spanned` content, whose spannable text the view lays out dynamically,
     * and a dynamic layout ignores `maxLines`' ellipsis. Set [fullText]
     * alongside so the un-truncated source stays available.
     */
    var clampToHeight = false

    /**
     * The most lines the text may take, truncated as [clampToHeight] does when
     * it needs more. Set [fullText] alongside; `maxLines` stays uncapped.
     */
    var clampToLines = Int.MAX_VALUE

    /**
     * The complete, un-truncated text to clamp from when [clampToHeight] or
     * [clampToLines] is set. Separate from `text`, which may already be
     * truncated.
     */
    var fullText: CharSequence? = null

    /** The rendered markdown last set on this view, so an unchanged body is not set again. */
    var shownMarkdown: Spanned? = null

    private var downX = 0f
    private var downY = 0f
    private var pendingLink: ClickableSpan? = null
    private var imageAlt: String? = null
    private var longPressFired = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val ELLIPSIS = "…"

    private val longPressRunnable = Runnable {
        imageAlt?.let { alt ->
            longPressFired = true
            onImageLongPress?.invoke(alt)
        }
    }

    override fun onMeasure(widthMeasure: Int, heightMeasureSpec: Int) {
        val widthMeasureSpec = tableWidth(widthMeasure)
        val source = fullText
        if (!source.isNullOrEmpty()) {
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            // A height is only meaningful when the parent bounds ours (EXACTLY
            // from a weighted slot, or AT_MOST). Unbounded → nothing to fit against.
            val bounded = heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST
            val available = if (clampToHeight && bounded) {
                MeasureSpec.getSize(heightMeasureSpec) - compoundPaddingTop - compoundPaddingBottom
            } else {
                Int.MAX_VALUE
            }
            val width = MeasureSpec.getSize(widthMeasureSpec) -
                compoundPaddingLeft - compoundPaddingRight
            val limited = available != Int.MAX_VALUE || clampToLines != Int.MAX_VALUE
            if (limited && available > 0 && width > 0) {
                val clamped = truncated(source, width, clampToLines, available)
                // Guard against re-truncating our own output each pass (which
                // would loop): only swap the text when it actually changes.
                if (text?.toString() != clamped.toString()) {
                    text = clamped
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * A table row reports no width until it has been drawn once, so a body
     * holding a table, measured to fit its content, would come out as wide as
     * its other text, or zero wide with none, and draw the table in that.
     * Such a body takes the whole width it is offered.
     */
    private fun tableWidth(spec: Int): Int {
        if (MeasureSpec.getMode(spec) != MeasureSpec.AT_MOST) return spec
        val shown = (fullText ?: text) as? Spanned ?: return spec
        if (shown.getSpans(0, shown.length, TableRowSpan::class.java).isEmpty()) return spec
        return MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(spec), MeasureSpec.EXACTLY)
    }

    /**
     * Clears any scroll left from an earlier, narrower layout. The view is
     * always as tall as its text, so it never scrolls; a selectable TextView
     * scrolls itself to keep the cursor in view, and text set while it was
     * laid out narrower would otherwise draw shifted or blank.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (scrollX != 0 || scrollY != 0) {
            scrollTo(0, 0)
        }
    }

    private fun staticLayout(text: CharSequence, width: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(includeFontPadding)
            // Wrapped as the view wraps it: with the builder's defaults a cut
            // could fit in a line count the view then exceeds, pushing the
            // ellipsis out of sight.
            .setBreakStrategy(breakStrategy)
            .setHyphenationFrequency(hyphenationFrequency)
            .setJustificationMode(justificationMode)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUseLineSpacingFromFallbacks(isFallbackLineSpacing)
                }
            }
            .build()

    /**
     * Return [source] truncated to at most [lines] lines and [available] px
     * tall at [width], ending in a literal ellipsis, or [source] itself when
     * it fits. `ellipsize = END` does not draw on Markwon `Spanned` content.
     */
    private fun truncated(source: CharSequence, width: Int, lines: Int, available: Int): CharSequence {
        fun fits(layout: StaticLayout) = layout.lineCount <= lines && layout.height <= available
        val full = staticLayout(source, width)
        if (fits(full)) return source
        var fit = 0
        while (fit < full.lineCount && fit < lines && full.getLineBottom(fit) <= available) fit++
        if (fit <= 0) fit = 1
        var cut = full.getLineEnd(fit - 1)
        while (cut > 0 && source[cut - 1].isWhitespace()) cut--
        // Drop a word at a time until "<text>…" fits.
        while (cut > 0) {
            val candidate = TextUtils.concat(source.subSequence(0, cut), ELLIPSIS)
            if (fits(staticLayout(candidate, width))) break
            var previous = cut
            while (previous > 0 && !source[previous - 1].isWhitespace()) previous--
            while (previous > 0 && source[previous - 1].isWhitespace()) previous--
            cut = if (previous < cut) previous else cut - 1
        }
        return TextUtils.concat(source.subSequence(0, cut.coerceAtLeast(0)), ELLIPSIS)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Don't consume anything: let the page's verticalScroll / pager own the
        // gesture and the card's clickable handle taps.
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
                    postDelayed(longPressRunnable, longPressTimeout)
                } else {
                    pendingLink = linkAt(event)
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
                    if (link != null) link.onClick(this) else onNonLinkClick?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                imageAlt = null
            }
        }
        // Over an image with alt text: we own the gesture (tap + long-press).
        if (imageAlt != null) return true
        // A link, in the text or in a table cell: consume so we fire it
        // ourselves on UP. No movement method is relied on for either: a
        // selectable view has the platform's, which knows nothing of links.
        if (pendingLink != null) return true
        // Plain text under a card-level handler: the card's tap.
        if (onNonLinkClick != null) return true
        // Plain text otherwise: TextView's own handling, where a long-press or
        // a double tap starts a selection. A view that cannot select leaves
        // the touch to its parent.
        return isTextSelectable && super.onTouchEvent(event)
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
     * slop on each side: a near-miss still opens the link. On a table row the
     * link is looked for in the cell under the touch instead.
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
        val offset = layout.getOffsetForHorizontal(line, x.coerceIn(left, right))
        val row = spanned.getSpans(offset, offset, TableRowSpan::class.java).firstOrNull()
        if (row != null) return cellLinkAt(row, x.toInt(), y - layout.getLineTop(line))
        val xLeft = (x - touchSlop).coerceIn(left, right)
        val xRight = (x + touchSlop).coerceIn(left, right)
        val o1 = layout.getOffsetForHorizontal(line, xLeft)
        val o2 = layout.getOffsetForHorizontal(line, xRight)
        return spanned.getSpans(min(o1, o2), max(o1, o2), ClickableSpan::class.java).firstOrNull()
    }

    /**
     * The link at [x] and [y] (from the row's top) in a Markwon table [row].
     * A row is one replacement character in this view's text, and its cells
     * are laid out apart from it, so their links are found the way Markwon's
     * TableAwareMovementMethod finds them. The cells are laid out when the row
     * is first drawn; before that there is no link to find.
     */
    private fun cellLinkAt(row: TableRowSpan, x: Int, y: Int): ClickableSpan? {
        val width = row.cellWidth()
        if (width <= 0) return null
        val cell = row.findLayoutForHorizontalOffset(x) ?: return null
        val offset = cell.getOffsetForHorizontal(cell.getLineForVertical(y), (x % width).toFloat())
        val text = cell.text as? Spanned ?: return null
        return text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }
}
