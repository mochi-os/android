// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.mochios.android.util.webUri

@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    passThroughTouches: Boolean = false,
    clampToBoundedHeight: Boolean = false,
    onClick: (() -> Unit)? = null,
    onImageLongPress: ((String) -> Unit)? = null,
    onTextViewReady: ((TextView) -> Unit)? = null
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            // CommonMark folds a single newline into the surrounding paragraph and
            // renders it as a space, so a body typed as two lines came out as one.
            // People write these bodies in a message box, where Enter means Enter.
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            // The body is peer-authored content; Markwon's default resolver
            // fires ACTION_VIEW on whatever scheme a link carries. Only web
            // links may leave the app — tel:, intent:, file: and custom
            // schemes are dropped rather than launched.
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        webUri(link)?.let { LinkResolverDef().resolve(view, it.toString()) }
                    }
                }
            })
            .build()
    }

    val spanned = remember(html) {
        markwon.toMarkdown(html)
    }
    // Alt/title text per image URL, recovered from the source (the renderer
    // keeps only the URL). Used for the long-press-shows-alt-text affordance.
    val altByUrl = remember(html) { parseImageAlts(html) }

    // The platform TextView knows nothing about the Compose theme, so feed it
    // onSurface. Applied in `update` as well as `factory`, since `factory` runs
    // once and a theme switch only recomposes.
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
                // Selectable so the user can long-press to pick text inside
                // the rendered markdown — used by wikis' quote-on-select
                // reply path, which reads the active selection at the moment
                // Reply is tapped.
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            val view = textView as ClickableLinkTextView
            view.clampToHeight = clampToBoundedHeight
            // The clamped preview drops text selection. Toggling selection
            // clears the movement method, so re-assert it; guarded so other
            // callers keep selection and don't thrash.
            val wantSelectable = !clampToBoundedHeight
            if (textView.isTextSelectable != wantSelectable) {
                textView.setTextIsSelectable(wantSelectable)
                textView.movementMethod = TableAwareMovementMethod.create()
            }
            if (clampToBoundedHeight) {
                // Truncation (with an appended "…") is done in onMeasure from the
                // bounded height, against fullText below — leave maxLines uncapped.
                textView.maxLines = Int.MAX_VALUE
            } else {
                textView.maxLines = maxLines
                if (maxLines != Int.MAX_VALUE) {
                    textView.ellipsize = TextUtils.TruncateAt.END
                }
            }
            view.onNonLinkClick = onClick
            textView.passThroughTouches = passThroughTouches
            textView.onImageLongPress = onImageLongPress
            textView.imageAltByUrl = altByUrl
            markwon.setParsedMarkdown(textView, spanned)
            // Source for height-clamped truncation; null when not clamping so
            // onMeasure leaves the text alone for every other caller.
            view.fullText = if (clampToBoundedHeight) spanned else null
            onTextViewReady?.invoke(textView)
        },
        modifier = modifier
    )
}

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMG_SRC = Regex("\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val IMG_TITLE = Regex("\\btitle\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
private val IMG_ALT = Regex("\\balt\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
private val MD_IMG = Regex("!\\[([^\\]]*)\\]\\(\\s*([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\s*\\)")

/**
 * Recover per-image alt/title text from the source, keyed by image URL - the
 * rendered span keeps only the URL. Prefers `title`, falling back to `alt`.
 */
private fun parseImageAlts(source: String): Map<String, String> {
    val map = HashMap<String, String>()
    for (m in IMG_TAG.findAll(source)) {
        val tag = m.value
        val src = IMG_SRC.find(tag)?.groupValues?.get(1) ?: continue
        val title = IMG_TITLE.find(tag)?.groupValues?.get(1)?.trim().orEmpty()
        val alt = IMG_ALT.find(tag)?.groupValues?.get(1)?.trim().orEmpty()
        val text = title.ifBlank { alt }
        if (text.isNotBlank()) map[src] = decodeEntities(text)
    }
    for (m in MD_IMG.findAll(source)) {
        val alt = m.groupValues[1].trim()
        val url = m.groupValues[2]
        val title = m.groupValues[3].trim()
        val text = title.ifBlank { alt }
        if (text.isNotBlank()) map.putIfAbsent(url, decodeEntities(text))
    }
    return map
}

private fun decodeEntities(s: String): String = s
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
