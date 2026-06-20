// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    clampToBoundedHeight: Boolean = false,
    onClick: (() -> Unit)? = null,
    onImageLongPress: ((String) -> Unit)? = null,
    onTextViewReady: ((TextView) -> Unit)? = null
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    val spanned = remember(html) {
        markwon.toMarkdown(html)
    }
    // Alt/title text per image URL, recovered from the source (the renderer
    // keeps only the URL). Used for the long-press-shows-alt-text affordance.
    val altByUrl = remember(html) { parseImageAlts(html) }

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
                // Selectable so the user can long-press to pick text inside
                // the rendered markdown — used by wikis' quote-on-select
                // reply path, which reads the active selection at the moment
                // Reply is tapped.
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            val view = textView as ClickableLinkTextView
            view.clampToHeight = clampToBoundedHeight
            if (clampToBoundedHeight) {
                // The visible line count and ellipsis are decided in the view's
                // onMeasure from its bounded height; start uncapped so the clamp
                // can both grow and shrink it.
                textView.maxLines = Int.MAX_VALUE
            } else {
                textView.maxLines = maxLines
                if (maxLines != Int.MAX_VALUE) {
                    textView.ellipsize = TextUtils.TruncateAt.END
                }
            }
            view.onNonLinkClick = onClick
            textView.onImageLongPress = onImageLongPress
            textView.imageAltByUrl = altByUrl
            markwon.setParsedMarkdown(textView, spanned)
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
 * Recover per-image alt/title text from the source HTML/markdown, keyed by
 * image URL (the rendered image span keeps only the URL). Prefers `title` (web
 * comics put the punchline there) and falls back to `alt`.
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
