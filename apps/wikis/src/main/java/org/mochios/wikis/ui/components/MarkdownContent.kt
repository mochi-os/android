// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.android.ui.components.CopyButton
import org.mochios.android.ui.components.LightboxScreen
import java.text.Normalizer

/**
 * A heading visible in the wiki page's table of contents.
 *
 * Mirrors web's `TocHeading` in
 * `apps/wikis/web/src/features/wiki/markdown-content.utils.ts`. Only
 * H2/H3/H4 are exposed (H1 is the page title, deeper levels are dropped),
 * matching the web extractor.
 */
data class TocHeading(val id: String, val text: String, val level: Int)

/**
 * Wiki-flavoured markdown renderer.
 *
 * Wraps the lib-level [org.mochios.android.ui.components.HtmlContent]
 * Markwon stack and layers the wikis-specific behaviour on top:
 *
 *  1. **Heading anchor extraction** for the table of contents (H2..H4 only,
 *     deterministic slugify with `-2`, `-3`, ... suffixes on collisions).
 *  2. **Attachment URL rewriting** against [LocalWikiContext]'s `baseURL`
 *     so relative `attachments/<id>` / `-/attachments/<id>` /
 *     `/<entity>/-/attachments/<id>[/thumbnail]` references resolve to
 *     absolute downloads.
 *  3. **Missing-link red styling** — links whose target appears in
 *     [missingLinks] (after stripping `#fragment` and `?query`) are
 *     coloured red, Wikipedia-style.
 *  4. **Internal page-link routing** — relative links that look like wiki
 *     page slugs surface through [onInternalLink] instead of opening the
 *     browser.
 *  5. **Image taps open the in-app lightbox** ([LightboxScreen]) — for
 *     `[![](img)](other-img)` wrapped-image links. Bare `![alt](img)`
 *     tap-to-lightbox is a TODO (see notes at the bottom of this file).
 *  6. **External links** open in Chrome Custom Tabs.
 *
 * Code-block "language label + copy button" header strips and the trailing
 * external-link icon are *not* implemented yet (see TODOs below) — they
 * need a custom [io.noties.markwon.MarkwonVisitor] override that's worth
 * a follow-up pass. Plain ``` fenced blocks still render correctly via
 * Markwon's default code-block renderer.
 *
 * Reference: `apps/wikis/web/src/features/wiki/markdown-content.tsx`.
 *
 * @param content Markdown source.
 * @param missingLinks Slugs of wiki pages that don't exist yet — links
 *                     pointing at these are styled red. Anchor (`#...`) and
 *                     query (`?...`) suffixes on the link are stripped before
 *                     the lookup.
 * @param onHeadingsExtracted Invoked once after parsing with the H2..H4
 *                            heading list (in document order). Pass a lambda
 *                            from the TableOfContents host; pass `null` to
 *                            skip extraction entirely.
 * @param onHeadingPositions  Invoked from the TextView's layout pass with a
 *                            map of `headingId -> yOffsetPx` (relative to the
 *                            article's start) every time the layout changes.
 *                            Hosts use it to compute the active TOC row given
 *                            the current scroll position. Null to skip.
 * @param onInternalLink Invoked when the user taps a relative link that
 *                       looks like a wiki page slug (not http/https, not
 *                       anchor-only). Hosts route to
 *                       [org.mochios.wikis.navigation.WikisApp.pageView].
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    missingLinks: List<String> = emptyList(),
    onHeadingsExtracted: ((List<TocHeading>) -> Unit)? = null,
    onHeadingPositions: ((Map<String, Int>) -> Unit)? = null,
    onInternalLink: (slug: String) -> Unit = {},
) {
    val context = LocalContext.current
    val wiki = LocalWikiContext.current
        ?: error("MarkdownContent requires LocalWikiContext")

    // Lightbox state — set to (urls, index) when the user taps an image,
    // cleared on dismiss.
    var lightbox by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // Walk the markdown AST once per `content` change. This is used for
    // *both* heading extraction and image-URL collection — cheaper than
    // running two regex passes and more correct (commonmark respects code
    // fences, HTML blocks, etc., where a naive regex would miss).
    val parsed = remember(content) {
        val parser = Parser.builder().build()
        val doc = parser.parse(content)

        val headings = extractHeadings(doc)
        val imageUrls = extractImageUrls(doc).map { wiki.resolveAttachmentUrl(stripThumbnail(it)) }

        ParsedDocument(headings = headings, imageUrls = imageUrls)
    }

    // Push headings up to the caller's TableOfContents host. Using
    // DisposableEffect (not LaunchedEffect) lets the caller treat the
    // callback as a side-effect that doesn't keep the composition alive.
    DisposableEffect(parsed.headings, onHeadingsExtracted) {
        onHeadingsExtracted?.invoke(parsed.headings)
        onDispose { }
    }

    // Pre-process the markdown source so Markwon sees absolute attachment
    // URLs directly. This means ImagesPlugin can fetch them and any link
    // through Markwon's LinkResolver gets the resolved href.
    val rewritten = remember(content, wiki.baseURL) {
        rewriteAttachmentUrls(content) { url -> wiki.resolveAttachmentUrl(url) }
    }

    // Split the rewritten source into a sequence of segments — alternating
    // ordinary-markdown chunks and fenced-code-block chunks. This lets each
    // code block render with its own Compose-native header strip (language
    // label + CopyButton, mirroring web's `<pre>` wrapper) while the rest
    // of the article stays a single Markwon-rendered TextView.
    val segments = remember(rewritten) { splitIntoSegments(rewritten) }

    // Build the Markwon stack. Same plugins as `HtmlContent`, plus:
    //  - a link resolver that routes through Custom Tabs / lightbox /
    //    onInternalLink based on the URL's classification.
    //  - a post-render visitor that paints missing-link spans red.
    val missingLinksKey = remember(missingLinks) { missingLinks.toSet() }
    val missingColor = Color(0xFFDC2626).toArgb() // web: text-red-600

    // Build a stable ordered list of heading ids so we can match the
    // n-th `HeadingSpan` in the rendered Spannable back to the heading id
    // we computed up-front. (Markwon's spans don't carry the slug; we walk
    // them in document order to attach the right id by index.)
    val headingIds = remember(parsed.headings) { parsed.headings.map { it.id } }

    val markwon = remember(context, wiki.baseURL, missingLinksKey, onInternalLink) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            // LinkifyPlugin auto-links bare URLs in the text. We *don't*
            // want it to fight our LinkResolver on already-linked markdown,
            // but it only adds spans where none exist, so it's safe.
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    // LinkResolver fires for every `LinkSpan` click —
                    // attachment links, internal page links, external
                    // links all funnel here. Classify and dispatch.
                    builder.linkResolver(WikiLinkResolver(
                        wiki = wiki,
                        onInternalLink = onInternalLink,
                        onImageLink = { resolvedUrl ->
                            val imageUrls = parsed.imageUrls
                            val idx = imageUrls.indexOf(resolvedUrl)
                            if (idx >= 0) {
                                lightbox = imageUrls to idx
                            }
                        },
                    ))
                }

                override fun afterSetText(textView: android.widget.TextView) {
                    if (missingLinksKey.isEmpty()) return
                    val text = textView.text as? Spannable ?: return
                    val builder = if (text is SpannableStringBuilder) text
                        else SpannableStringBuilder(text)

                    val linkSpans: Array<LinkSpan> =
                        builder.getSpans(0, builder.length, LinkSpan::class.java)
                    for (span in linkSpans) {
                        val href = span.link ?: continue
                        if (!isInternalRelative(href)) continue
                        val clean = href.substringBefore('#').substringBefore('?')
                        if (clean !in missingLinksKey) continue

                        val start = builder.getSpanStart(span)
                        val end = builder.getSpanEnd(span)
                        if (start < 0 || end < 0) continue
                        builder.setSpan(
                            ForegroundColorSpan(missingColor),
                            start, end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }

                    if (builder !== text) {
                        textView.text = builder
                    }
                }
            })
            .build()
    }

    // The Column lays out the article as a stack of (markdown TextView,
    // code-block-with-header, markdown TextView, ...). Each TextView is
    // OnPreDraw-listened to compute per-heading Y positions, and each
    // code-block segment uses a pure-Compose surface so the language
    // label + CopyButton can be Compose primitives.
    //
    // For active-heading scroll tracking we need every heading's absolute
    // Y offset within this Column. We collect them per-segment-TextView
    // (with each TextView's `top` measured from the layout pass) and
    // re-merge as `headingId -> yOffsetPx` for the host. The host adds
    // the Column's own offset (it lives inside a `verticalScroll`) when
    // matching against the scroll position.
    val segmentTops = remember(segments) { mutableMapOf<Int, Int>() }
    val segmentHeadingOffsets = remember(segments) { mutableMapOf<Int, Map<String, Int>>() }

    fun publishHeadingPositions() {
        if (onHeadingPositions == null) return
        val merged = mutableMapOf<String, Int>()
        for ((segIdx, perSeg) in segmentHeadingOffsets) {
            val segTop = segmentTops[segIdx] ?: continue
            for ((id, offset) in perSeg) merged[id] = segTop + offset
        }
        if (merged.isNotEmpty()) onHeadingPositions(merged)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Walk the heading-id list as we encounter prose segments so each
        // TextView gets a sub-slice of the ids in the order Markwon's spans
        // will appear inside it. The web's extractor walks the AST in the
        // same order, so this index-zipping stays correct as long as we
        // include heading text only in prose segments (FencedCodeBlock
        // can't contain Heading nodes).
        var headingCursor = 0
        for ((idx, seg) in segments.withIndex()) {
            when (seg) {
                is MarkdownSegment.Prose -> {
                    // Count how many H2..H4 headings appear in this prose
                    // chunk so the TextView can match them by ordinal.
                    val proseHeadingCount = countH2H4Headings(seg.markdown)
                    val proseIds = headingIds.subList(
                        headingCursor.coerceAtMost(headingIds.size),
                        (headingCursor + proseHeadingCount).coerceAtMost(headingIds.size),
                    ).toList()
                    headingCursor += proseHeadingCount

                    ProseSegment(
                        markwon = markwon,
                        markdown = seg.markdown,
                        headingIds = proseIds,
                        imageUrls = parsed.imageUrls,
                        wiki = wiki,
                        onTopMeasured = { top ->
                            segmentTops[idx] = top
                            publishHeadingPositions()
                        },
                        onHeadingOffsetsMeasured = { perHeading ->
                            segmentHeadingOffsets[idx] = perHeading
                            publishHeadingPositions()
                        },
                        onImageTap = { resolvedUrl ->
                            val imageUrls = parsed.imageUrls
                            val i = imageUrls.indexOf(resolvedUrl)
                            if (i >= 0) lightbox = imageUrls to i
                        },
                    )
                }
                is MarkdownSegment.Code -> {
                    CodeBlockSegment(
                        markwon = markwon,
                        language = seg.language,
                        codeMarkdown = seg.fullMarkdown,
                        codeText = seg.codeText,
                        onTopMeasured = { top ->
                            segmentTops[idx] = top
                            publishHeadingPositions()
                        },
                    )
                }
            }
        }
    }

    val lightboxState = lightbox
    if (lightboxState != null) {
        LightboxScreen(
            images = lightboxState.first,
            initialIndex = lightboxState.second,
            onDismiss = { lightbox = null },
        )
    }

    // TODO: trailing external-link icon next to http(s) links. Web shows
    // a ⤴ Lucide ExternalLink icon after each external link. Markwon
    // doesn't make inline icon-after-text trivial — the simplest route is
    // an ImageSpan inserted in afterSetText, but rendering a vector
    // drawable as a span needs a one-time bitmap rasterisation. Deferred.
}

/**
 * A single prose run of markdown — anything that isn't a fenced code
 * block. Rendered as a Markwon `AndroidView` with:
 *
 *  - Per-heading Y-offset measurement (matches the i-th HeadingSpan in
 *    the rendered Spannable to `headingIds[i]`).
 *  - Bare-image tap-to-lightbox via the TextView's touch interceptor.
 *  - The same LinkResolver routing as before, since the Markwon instance
 *    is shared across all prose segments.
 */
@Composable
private fun ProseSegment(
    markwon: Markwon,
    markdown: String,
    headingIds: List<String>,
    imageUrls: List<String>,
    wiki: WikiContextValue,
    onTopMeasured: (top: Int) -> Unit,
    onHeadingOffsetsMeasured: (perHeading: Map<String, Int>) -> Unit,
    onImageTap: (resolvedUrl: String) -> Unit,
) {
    val spanned = remember(markwon, markdown) { markwon.toMarkdown(markdown) }

    AndroidView(
        factory = { ctx ->
            ClickableLinkTextView(ctx).apply {
                // TableAwareMovementMethod (wraps LinkMovementMethod) so links
                // inside Markwon table cells are tappable — plain
                // LinkMovementMethod can't dispatch clicks into TableRowSpans.
                movementMethod = TableAwareMovementMethod.create()
                textSize = 16f
                setTextColor(
                    ctx.resources.getColor(android.R.color.primary_text_light, ctx.theme)
                )
            }
        },
        update = { textView ->
            textView.ellipsize = TextUtils.TruncateAt.END
            markwon.setParsedMarkdown(textView, spanned)

            // Wire up bare-image tap-to-lightbox. The TextView already
            // routes LinkSpan clicks through its movement method; here
            // we hook the touch event to look for AsyncDrawableSpan at
            // the tap offset (which LinkResolver never sees).
            installImageTapInterceptor(
                textView = textView,
                wiki = wiki,
                imageUrls = imageUrls,
                onImageTap = onImageTap,
            )

            // After layout we can read each HeadingSpan's character
            // offset and translate it to a vertical pixel position via
            // the TextView's layout. The OnPreDrawListener fires on
            // every layout pass, which keeps the map fresh through
            // text-size changes, configuration changes, or content
            // updates.
            textView.viewTreeObserver.addOnPreDrawListener {
                onTopMeasured(textView.top)
                val layout = textView.layout
                val text = textView.text as? Spannable
                if (layout != null && text != null && headingIds.isNotEmpty()) {
                    val perHeading = mutableMapOf<String, Int>()
                    val spans = text.getSpans(0, text.length, HeadingSpan::class.java)
                        .sortedBy { text.getSpanStart(it) }
                    val limit = minOf(spans.size, headingIds.size)
                    for (i in 0 until limit) {
                        val start = text.getSpanStart(spans[i]).coerceAtLeast(0)
                        val line = layout.getLineForOffset(start)
                        perHeading[headingIds[i]] = layout.getLineTop(line)
                    }
                    if (perHeading.isNotEmpty()) {
                        onHeadingOffsetsMeasured(perHeading)
                    }
                }
                true
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A fenced code block rendered with a Compose-native header strip
 * (language label on the left, CopyButton on the right) over a
 * Markwon-rendered code body. Mirrors web's `<pre>` wrapper in
 * `apps/wikis/web/src/features/wiki/markdown-content.tsx`.
 *
 * The body is Markwon-rendered (rather than a plain `Text`) so syntax
 * inside the code block — escaped backticks, embedded markdown that
 * happens to start a code fence — survives the round-trip identically
 * to what users would see in a non-split MarkdownContent.
 */
@Composable
private fun CodeBlockSegment(
    markwon: Markwon,
    language: String,
    codeMarkdown: String,
    codeText: String,
    onTopMeasured: (top: Int) -> Unit,
) {
    val spanned = remember(markwon, codeMarkdown) { markwon.toMarkdown(codeMarkdown) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                M3Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CopyButton(value = codeText)
            }
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                AndroidView(
                    factory = { ctx ->
                        ClickableLinkTextView(ctx).apply {
                            movementMethod = LinkMovementMethod.getInstance()
                            textSize = 13f
                            setTextColor(
                                ctx.resources.getColor(android.R.color.primary_text_light, ctx.theme)
                            )
                        }
                    },
                    update = { textView ->
                        textView.ellipsize = TextUtils.TruncateAt.END
                        markwon.setParsedMarkdown(textView, spanned)
                        textView.viewTreeObserver.addOnPreDrawListener {
                            onTopMeasured(textView.top)
                            true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Wire a touch listener on [textView] that, after a clean tap on text
 * NOT covered by a clickable link span, looks for an AsyncDrawableSpan
 * at the tap offset and opens the lightbox via [onImageTap].
 *
 * `[![](img)](href)` wrapped-image links keep working through the
 * existing LinkResolver because they're [LinkSpan]s; this fills in the
 * gap for plain `![alt](img)` taps which Markwon doesn't surface to
 * LinkResolver.
 */
private fun installImageTapInterceptor(
    textView: android.widget.TextView,
    wiki: WikiContextValue,
    imageUrls: List<String>,
    onImageTap: (resolvedUrl: String) -> Unit,
) {
    textView.setOnTouchListener { v, event ->
        if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false
        val spanned = textView.text as? Spannable ?: return@setOnTouchListener false
        val layout = textView.layout ?: return@setOnTouchListener false

        val x = event.x.toInt() - textView.totalPaddingLeft + textView.scrollX
        val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return@setOnTouchListener false
        }
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())

        // Don't fire when the tap also lands inside a clickable span —
        // ClickableLinkTextView already handles those via the movement
        // method and our LinkResolver.
        val clickable = spanned.getSpans(offset, offset, ClickableSpan::class.java)
        if (clickable.isNotEmpty()) return@setOnTouchListener false

        val drawSpans = spanned.getSpans(offset, offset, AsyncDrawableSpan::class.java)
        if (drawSpans.isEmpty()) return@setOnTouchListener false

        val dest = drawSpans[0].drawable.destination ?: return@setOnTouchListener false
        val resolved = wiki.resolveAttachmentUrl(stripThumbnail(dest))
        val idx = imageUrls.indexOf(resolved)
        if (idx >= 0) {
            onImageTap(resolved)
            v.performClick()
            true
        } else {
            false
        }
    }
}

// --------------------------------------------------------------------------
// Internal helpers — kept package-private so tests can exercise them.
// --------------------------------------------------------------------------

private data class ParsedDocument(
    val headings: List<TocHeading>,
    val imageUrls: List<String>,
)

/**
 * One step of the article — either a prose run rendered by Markwon as a
 * single Spannable, or a fenced code block rendered with our own header
 * strip (language label + CopyButton) above a code-only Markwon body.
 */
internal sealed class MarkdownSegment {
    data class Prose(val markdown: String) : MarkdownSegment()
    data class Code(
        val language: String,
        /** Inner code, without the fence lines. Used by the CopyButton. */
        val codeText: String,
        /** Full original block including fences, so Markwon can render it. */
        val fullMarkdown: String,
    ) : MarkdownSegment()
}

/**
 * Split [content] into alternating prose / fenced-code segments. Mirrors
 * the way the web renderer hands `<pre>` blocks to a dedicated component
 * with a header strip while the rest of the article walks through the
 * normal Markdown component.
 *
 * Recognises both ``` and ~~~ fences with the same fence character. The
 * opening fence may have an info string after it (e.g. ```kotlin); the
 * closing fence must use the same character and length and stand alone
 * on its line. Indented (non-fenced) code blocks are left in the prose
 * segment — they get the normal Markwon rendering, no header strip.
 */
internal fun splitIntoSegments(content: String): List<MarkdownSegment> {
    val out = mutableListOf<MarkdownSegment>()
    val lines = content.split("\n")
    val prose = StringBuilder()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fenceMatch = Regex("^(\\s*)(`{3,}|~{3,})(.*)$").matchEntire(line)
        if (fenceMatch != null) {
            val indent = fenceMatch.groupValues[1]
            val fence = fenceMatch.groupValues[2]
            val info = fenceMatch.groupValues[3].trim()
            // Find the matching closing fence (same char, >= same length).
            val fenceChar = fence[0]
            val fenceLen = fence.length
            var close = -1
            var j = i + 1
            while (j < lines.size) {
                val l = lines[j]
                val cm = Regex("^(\\s*)(`{3,}|~{3,})\\s*$").matchEntire(l)
                if (cm != null) {
                    val cFence = cm.groupValues[2]
                    if (cFence[0] == fenceChar && cFence.length >= fenceLen) {
                        close = j
                        break
                    }
                }
                j++
            }
            if (close < 0) {
                // No closing fence — treat the rest of the document as
                // an open code block. Match commonmark behaviour.
                close = lines.size
            }

            if (prose.isNotEmpty()) {
                out += MarkdownSegment.Prose(prose.toString().trimEnd('\n'))
                prose.clear()
            }

            val codeLines = if (close <= lines.size) lines.subList(i + 1, minOf(close, lines.size)) else emptyList()
            val codeText = codeLines.joinToString("\n")
            // Reconstruct the original block so Markwon parses it the
            // same way the un-split renderer would have.
            val fullBlock = buildString {
                append(line)
                append('\n')
                for (cl in codeLines) {
                    append(cl)
                    append('\n')
                }
                if (close < lines.size) {
                    append(lines[close])
                }
            }
            val language = info.split(Regex("\\s+")).firstOrNull()?.ifBlank { null } ?: "text"
            out += MarkdownSegment.Code(
                language = language,
                codeText = codeText,
                fullMarkdown = fullBlock,
            )
            // Skip past the closing fence so the next iteration starts
            // on the line after it (or off the end if the block was
            // unterminated).
            i = if (close < lines.size) close + 1 else lines.size
            // Discard the indent — it's only relevant to the fence
            // detection; the block itself is reconstructed verbatim.
            @Suppress("UNUSED_EXPRESSION") indent
        } else {
            prose.append(line)
            prose.append('\n')
            i++
        }
    }
    if (prose.isNotEmpty()) {
        out += MarkdownSegment.Prose(prose.toString().trimEnd('\n'))
    }
    return out
}

/**
 * Count H2..H4 headings in [markdown] (ATX `#` form), respecting fenced
 * code blocks. Used to slice the global heading-ids list across prose
 * segments so each TextView's HeadingSpans get the right ids.
 *
 * We rely on the same fence-detection rules as [splitIntoSegments] so
 * the counts add up to the total in the full document (fenced code
 * blocks already live in their own segments, so we never see a fenced
 * block inside a prose segment; this guard is defensive against future
 * changes to the splitter).
 */
internal fun countH2H4Headings(markdown: String): Int {
    val parser = Parser.builder().build()
    val doc = parser.parse(markdown)
    return extractHeadings(doc).size
}

/**
 * Walk the commonmark AST and emit H2..H4 headings in document order.
 *
 * Matches web's [`extractTocHeadings`]
 * (`apps/wikis/web/src/features/wiki/markdown-content.utils.ts`). Code
 * fences are skipped automatically — they're block nodes, not headings.
 *
 * Slug collisions are resolved by suffixing `-2`, `-3`, ... so the IDs are
 * stable for anchor links even when two headings share the same text.
 */
internal fun extractHeadings(root: Node): List<TocHeading> {
    val headings = mutableListOf<TocHeading>()
    val seen = mutableMapOf<String, Int>()

    root.accept(object : AbstractVisitor() {
        override fun visit(heading: Heading) {
            val level = heading.level
            if (level < 2 || level > 4) {
                super.visit(heading)
                return
            }
            val text = nodeText(heading).trim()
            if (text.isEmpty()) {
                super.visit(heading)
                return
            }

            val base = slugifyHeading(text)
            val count = (seen[base] ?: 0) + 1
            seen[base] = count
            val id = if (count == 1) base else "$base-$count"

            headings += TocHeading(id = id, text = text, level = level)
            // Don't recurse — heading children are inline text we've
            // already flattened via nodeText().
        }
    })

    return headings
}

/**
 * Flatten a node's inline children to plain text. Mirrors web's
 * `getNodeText` / `stripInlineMarkdown` combo, but driven by the AST
 * instead of regex so emphasis / code / links flatten correctly.
 */
private fun nodeText(node: Node): String {
    val sb = StringBuilder()
    node.accept(object : AbstractVisitor() {
        override fun visit(text: Text) {
            sb.append(text.literal)
        }
    })
    return sb.toString()
}

/**
 * Canonical heading slug. Mirrors web's `slugifyHeading`:
 *
 *  - NFKD-normalise + strip combining marks (so `café` -> `cafe`)
 *  - lowercase
 *  - keep `[a-z0-9 -]`, drop the rest
 *  - collapse spaces / underscores to `-`
 *  - collapse runs of `-`
 *  - fall back to `section` when nothing's left
 */
internal fun slugifyHeading(text: String): String {
    val noAccents = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val cleaned = noAccents.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("-+"), "-")
    return cleaned.ifEmpty { "section" }
}

/**
 * Pull every `Image` node's destination out of the AST, in document order.
 * Used both for the lightbox media list and (after attachment-URL
 * resolution) for click-to-lightbox matching.
 */
internal fun extractImageUrls(root: Node): List<String> {
    val urls = mutableListOf<String>()
    root.accept(object : AbstractVisitor() {
        override fun visit(image: Image) {
            image.destination?.let(urls::add)
            super.visit(image)
        }
    })
    return urls
}

/** Drop a trailing `/thumbnail` segment so the lightbox shows full-size. */
private fun stripThumbnail(url: String): String =
    if (url.endsWith("/thumbnail")) url.removeSuffix("/thumbnail") else url

/**
 * Rewrite every `![alt](url)` and `[text](url)` whose `url` looks like a
 * wiki attachment path through [resolve]. Other URLs (http://..., external
 * links, anchor-only) are left as-is.
 *
 * Mirrors web's `resolveAttachmentUrl` being applied inline by `Markdown`
 * components — easier here to rewrite the source text once, before
 * Markwon parses it, than to hook the rendering pipeline twice.
 *
 * The regex is permissive about the `[text]` and `(url)` shapes — the
 * brackets-balanced edge cases that commonmark handles still fall back to
 * the original URL because [resolve] only modifies wiki-attachment
 * patterns and passes everything else through.
 */
internal fun rewriteAttachmentUrls(content: String, resolve: (String) -> String): String {
    val linkRe = Regex("(!?)\\[([^]]*)]\\(([^)\\s]+)(\\s+\"[^\"]*\")?\\)")
    return linkRe.replace(content) { match ->
        val bang = match.groupValues[1]
        val alt = match.groupValues[2]
        val url = match.groupValues[3]
        val title = match.groupValues[4]
        val resolved = resolve(url)
        "$bang[$alt]($resolved$title)"
    }
}

/** Is `href` a relative wiki-page link (i.e. not http/https/scheme/anchor-only)? */
private fun isInternalRelative(href: String): Boolean {
    if (href.isEmpty()) return false
    if (href.startsWith("#")) return false
    if (href.startsWith("http://") || href.startsWith("https://")) return false
    if (href.startsWith("//")) return false
    // Any other scheme (mailto:, tel:, ftp:, ...) is external too.
    if (Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*:").containsMatchIn(href)) return false
    // Already-resolved attachment URLs (rewritten upstream to absolute
    // baseURL/attachments/...) start with the server origin, which the
    // http check above already rejects. Bare "attachments/..." would be
    // rewritten before this function ever sees the link, so don't treat
    // it as internal here.
    if (href.startsWith("attachments/") || href.contains("/attachments/")) return false
    return true
}

/**
 * Routes link clicks based on what kind of URL it is. Installed once on
 * the Markwon configuration.
 *
 *  - Relative wiki-page links (no scheme, not anchor-only) call
 *    [onInternalLink] with the page slug so the host can navigate.
 *  - Wrapped-image links `[![](img)](other-img)` whose href matches an
 *    image in the document open the lightbox via [onImageLink]. Bare
 *    `![alt](img)` clicks don't reach `LinkResolver` — that's why
 *    `MarkdownContent` also wires a separate tap handler on the
 *    underlying `ClickableLinkTextView`.
 *  - Everything else opens in a Chrome Custom Tab so the user stays
 *    inside the Mochi-themed shell — consistent with how
 *    [org.mochios.android.ui.auth.AuthNavigation] handles OAuth.
 */
private class WikiLinkResolver(
    private val wiki: WikiContextValue,
    private val onInternalLink: (String) -> Unit,
    private val onImageLink: (String) -> Unit,
) : LinkResolver {
    override fun resolve(view: View, link: String) {
        if (isInternalRelative(link)) {
            val slug = link.substringBefore('#').substringBefore('?')
            if (slug.isNotEmpty()) {
                onInternalLink(slug)
                return
            }
        }

        val resolved = wiki.resolveAttachmentUrl(link)

        // `[![](img)](resolved)` — the inner image is what the user sees,
        // but the click target on the LinkSpan is `resolved`. If that URL
        // looks like an attachment, surface it through onImageLink so the
        // host can match it against the document's image set and open the
        // lightbox at that index.
        if (resolved.contains("/attachments/")) {
            onImageLink(resolved)
            return
        }

        try {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(view.context, Uri.parse(resolved))
        } catch (_: Exception) {
            // Malformed URL or no browser; swallow so a bad link doesn't
            // crash the page. Markwon's default behaviour here is to
            // throw, which would propagate to the user.
        }
    }
}

