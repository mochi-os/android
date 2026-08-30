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
import io.noties.markwon.SoftBreakAddsNewLinePlugin
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
import org.mochios.android.util.webUri
import java.text.Normalizer

/**
 * A heading in the page's table of contents. Only H2..H4 are exposed - H1 is
 * the page title, deeper levels are dropped.
 */
data class TocHeading(val id: String, val text: String, val level: Int)

/**
 * Wiki-flavoured markdown renderer over the lib Markwon stack: TOC anchors
 * (H2..H4), attachment URL rewriting against [LocalWikiContext], red styling
 * for [missingLinks], internal page links through [onInternalLink], image taps
 * into the lightbox, external links in a Custom Tab.
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

    // Split into alternating prose / fenced-code segments so each code block
    // can carry a Compose header strip while the prose stays one Markwon
    // TextView.
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
            // CommonMark folds a single newline into the paragraph around it and
            // renders it as a space, so a page typed as two lines came out as
            // one. Matches HtmlContent, which every other body is drawn with.
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
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

    // Heading positions are measured per segment TextView and merged as
    // `headingId -> yOffsetPx` relative to this Column; the host adds the
    // Column's own scroll offset when matching.
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
        // Hand each prose TextView the sub-slice of heading ids its spans will
        // carry, in document order. Safe because a fenced code block cannot
        // contain a heading.
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

    // TODO: trailing external-link icon after http(s) links. Needs an ImageSpan
    // built from a rasterised vector drawable in afterSetText.
}

/**
 * One prose run - everything that is not a fenced code block - rendered by
 * Markwon in an `AndroidView`.
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
    // Markwon renders into a platform TextView, which knows nothing about the
    // Compose theme; onSurface keeps prose readable on either scheme, where the
    // old fixed android.R.color.primary_text_light went black in dark mode.
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        factory = { ctx ->
            ClickableLinkTextView(ctx).apply {
                // TableAwareMovementMethod (wraps LinkMovementMethod) so links
                // inside Markwon table cells are tappable — plain
                // LinkMovementMethod can't dispatch clicks into TableRowSpans.
                movementMethod = TableAwareMovementMethod.create()
                textSize = 16f
                setTextColor(textColor)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
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

            // OnPreDraw fires on every layout pass, so heading offsets stay
            // fresh through text-size, configuration and content changes.
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
 * A fenced code block: Compose header strip (language + copy) over a
 * Markwon-rendered body. Markwon renders the body so escaped fences and
 * embedded markdown survive identically.
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
    // The block sits on surfaceVariant, so its code follows that role rather
    // than the fixed near-black it used to draw in.
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

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
                            setTextColor(textColor)
                        }
                    },
                    update = { textView ->
                        textView.setTextColor(textColor)
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
 * Tap handling for bare `![alt](img)` images, which Markwon never surfaces to
 * [LinkResolver]. Link spans are left alone.
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
 * Split [content] into alternating prose / fenced-code segments. Recognises ```
 * and ~~~ fences; the closing fence must use the same character and at least
 * the same length. Indented code blocks stay in the prose segment.
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
 * Count H2..H4 headings in [markdown]; used to slice the global heading-id list
 * per prose segment.
 */
internal fun countH2H4Headings(markdown: String): Int {
    val parser = Parser.builder().build()
    val doc = parser.parse(markdown)
    return extractHeadings(doc).size
}

/**
 * Emit H2..H4 headings in document order. Slug collisions get a `-2`, `-3`, ...
 * suffix so anchor ids stay stable.
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
 * Flatten a node's inline children to plain text.
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
 * Canonical heading slug; must match web's `slugifyHeading`.
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
 * Rewrite every `![alt](url)` / `[text](url)` through [resolve]; non-attachment
 * URLs pass through. Rewriting the source once is cheaper than hooking
 * Markwon's rendering twice.
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
    // Attachment links are rewritten to absolute URLs before this runs, so an
    // attachments path here is never an internal page link.
    if (href.startsWith("attachments/") || href.contains("/attachments/")) return false
    return true
}

/**
 * Routes link taps: relative slugs to [onInternalLink], attachment URLs to
 * [onImageLink] for the lightbox, everything else to a Chrome Custom Tab.
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

        // For `[![](img)](resolved)` the LinkSpan targets the outer URL; if it
        // is an attachment, hand it to onImageLink so the host can open the
        // lightbox at that image.
        if (resolved.contains("/attachments/")) {
            onImageLink(resolved)
            return
        }

        // The link text is peer-authored page content: only web schemes may
        // leave the app. Anything else — tel:, intent:, another app's custom
        // scheme — is dropped rather than launched.
        val uri = webUri(resolved) ?: return
        try {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(view.context, uri)
        } catch (_: Exception) {
            // No browser; swallow so a bad link doesn't crash the page.
            // Markwon's default behaviour here is to throw, which would
            // propagate to the user.
        }
    }
}

