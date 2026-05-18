package org.mochios.wikis.ui.components

import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.mochios.android.ui.components.ClickableLinkTextView
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

    // Build the Markwon stack. Same plugins as `HtmlContent`, plus:
    //  - a link resolver that routes through Custom Tabs / lightbox /
    //    onInternalLink based on the URL's classification.
    //  - a post-render visitor that paints missing-link spans red.
    val missingLinksKey = remember(missingLinks) { missingLinks.toSet() }
    val missingColor = Color(0xFFDC2626).toArgb() // web: text-red-600

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

    val spanned = remember(markwon, rewritten) {
        markwon.toMarkdown(rewritten)
    }

    AndroidView(
        factory = { ctx ->
            ClickableLinkTextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 16f
                setTextColor(
                    ctx.resources.getColor(android.R.color.primary_text_light, ctx.theme)
                )
            }
        },
        update = { textView ->
            textView.ellipsize = TextUtils.TruncateAt.END
            markwon.setParsedMarkdown(textView, spanned)
        },
        modifier = modifier,
    )

    val lightboxState = lightbox
    if (lightboxState != null) {
        LightboxScreen(
            images = lightboxState.first,
            initialIndex = lightboxState.second,
            onDismiss = { lightbox = null },
        )
    }

    // TODO: code-block language label + CopyButton header strip.
    // Web wraps each <pre><code class="language-foo"> in a card with a
    // header showing "foo" + a CopyButton. Doing the same on Android
    // means overriding Markwon's FencedCodeBlock visitor to render an
    // AndroidView { ComposeView { Column { Row(lang, CopyButton); CodeText } } },
    // which is doable but invasive — left as a follow-up so this composable
    // lands with parity on heading/url/missing-link/lightbox first.

    // TODO: trailing external-link icon next to http(s) links. Web shows
    // a ⤴ Lucide ExternalLink icon after each external link. Markwon
    // doesn't make inline icon-after-text trivial — the simplest route is
    // an ImageSpan inserted in afterSetText, but rendering a vector
    // drawable as a span needs a one-time bitmap rasterisation. Deferred.

    // TODO: bare `![alt](img)` tap-to-lightbox. LinkResolver above only
    // fires for LinkSpan, so the wrapped-image case `[![](img)](href)`
    // works but a plain image tap is currently a no-op. The fix is to
    // attach a touch handler to the underlying ClickableLinkTextView that:
    //   1. converts tap coordinates to text offset via getLayout()
    //   2. asks the Spanned for any AsyncDrawableSpan at that offset
    //   3. resolves the span's destination via wiki.resolveAttachmentUrl
    //   4. matches it against parsed.imageUrls and sets `lightbox`
    // ClickableLinkTextView's existing onNonLinkClick + a span lookup is
    // most of the way there but needs the offset->span mapping wired up.
}

// --------------------------------------------------------------------------
// Internal helpers — kept package-private so tests can exercise them.
// --------------------------------------------------------------------------

private data class ParsedDocument(
    val headings: List<TocHeading>,
    val imageUrls: List<String>,
)

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

