// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.text.Spanned
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import org.mochios.android.R
import org.mochios.android.util.linkUri
import org.mochios.android.util.linkifyPlugin

@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    passThroughTouches: Boolean = false,
    clampToBoundedHeight: Boolean = false,
    onClick: (() -> Unit)? = null,
    onImageLongPress: ((String) -> Unit)? = null,
    onTextViewReady: ((TextView) -> Unit)? = null,
    selectionAction: SelectionAction? = null,
) {
    val context = LocalContext.current
    val root = LocalView.current.rootView
    val markwon = remember(context, root) { sharedMarkwon(context, root) }

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
            // Links, table cells among them, are dispatched by the view
            // itself, so it needs no link movement method: being selectable
            // installs the platform's own in place of any set here.
            ClickableLinkTextView(ctx).apply {
                textSize = 16f
                setTextColor(textColor)
                // Selectable so the user can long-press to pick text inside
                // the rendered markdown — used by the comments' quote-on-select
                // reply, which reads the active selection at the moment Reply
                // is tapped, or from the selection's own menu.
                setTextIsSelectable(true)
                customSelectionActionModeCallback = SelectionMenu(this)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            val view = textView as ClickableLinkTextView
            // A cut preview is drawn from a copy of the body ending in "…"
            // (onMeasure, from fullText below): the text's own ellipsis draws
            // nothing on this content.
            val truncated = clampToBoundedHeight || maxLines != Int.MAX_VALUE
            view.clampToHeight = clampToBoundedHeight
            view.clampToLines = maxLines
            // A cut preview drops text selection: a tap on it belongs to the
            // card around it, which opens the whole body. Guarded so other
            // callers keep selection and don't thrash.
            val wantSelectable = !truncated
            if (textView.isTextSelectable != wantSelectable) {
                textView.setTextIsSelectable(wantSelectable)
                // Dropping selection drops the platform's movement method too;
                // Markwon's lets a keyboard step through the links instead.
                if (!wantSelectable) textView.movementMethod = TableAwareMovementMethod.create()
                view.shownMarkdown = null
            }
            view.onNonLinkClick = onClick
            textView.passThroughTouches = passThroughTouches
            textView.onImageLongPress = onImageLongPress
            textView.imageAltByUrl = altByUrl
            view.selectionAction = selectionAction
            if (view.shownMarkdown !== spanned) {
                markwon.setParsedMarkdown(textView, spanned)
                view.shownMarkdown = if (truncated) null else spanned
            }
            // Source for the truncation; null when not cutting so onMeasure
            // leaves the text alone for every other caller.
            view.fullText = if (truncated) spanned else null
            onTextViewReady?.invoke(textView)
        },
        modifier = modifier
    )
}

/**
 * An item [HtmlContent] adds to its text selection's menu: [label], and
 * [onSelected], handed the text selected when it is chosen. The menu floats
 * above the selection, clear of the handles that hang below it over whatever
 * the page draws under the body.
 */
class SelectionAction(val label: String, val onSelected: (String) -> Unit)

/**
 * The text selected in a body [HtmlContent] draws, for a Reply that quotes it
 * as the web's Reply quotes `window.getSelection()`. Handed to the body
 * through `onTextViewReady`, it reads the selection when asked.
 */
class BodySelection {
    internal var view: TextView? = null

    /** The text selected in the body now, or null when nothing is. */
    fun text(): String? = view?.let(::selectedText)?.ifEmpty { null }
}

/**
 * The text selected in [view], or "" when nothing is. An inline image is
 * drawn over an object replacement character, which is not text: it goes, as
 * the web's selection leaves an image out of the text it gives.
 */
private fun selectedText(view: TextView): String {
    val from = view.selectionStart.coerceAtLeast(0)
    val to = view.selectionEnd.coerceAtLeast(0)
    return if (to > from) view.text.subSequence(from, to).toString().replace(OBJECT_REPLACEMENT, "") else ""
}

/** What a span drawing an image stands on in the text. */
private const val OBJECT_REPLACEMENT = "￼"

/** Puts [view]'s [ClickableLinkTextView.selectionAction], when it has one, in its selection's menu. */
internal class SelectionMenu(private val view: ClickableLinkTextView) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        view.selectionAction?.let { action -> menu.add(Menu.NONE, R.id.html_content_selection_action, 0, action.label) }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != R.id.html_content_selection_action) return false
        val action = view.selectionAction ?: return false
        val selected = selectedText(view)
        // Read before the menu goes: ending it drops the selection.
        mode.finish()
        action.onSelected(selected)
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}

/** A window's renderer, kept on its root view so every body in it shares one. */
private class SharedMarkwon(val context: Context, val markwon: Markwon)

/**
 * The renderer for bodies drawn with [context] in the window under [root],
 * built the first time one is drawn there.
 */
private fun sharedMarkwon(context: Context, root: View): Markwon {
    val cached = root.getTag(R.id.html_content_markwon) as? SharedMarkwon
    if (cached != null && cached.context === context) {
        return cached.markwon
    }
    val markwon = buildMarkwon(context)
    if (cached == null) {
        root.setTag(R.id.html_content_markwon, SharedMarkwon(context, markwon))
    }
    return markwon
}

private fun buildMarkwon(context: Context): Markwon =
    Markwon.builder(context)
        // CommonMark folds a single newline into the surrounding paragraph and
        // renders it as a space, so a body typed as two lines came out as one.
        // People write these bodies in a message box, where Enter means Enter.
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(CoilImagesPlugin.create(context))
        .usePlugin(TablePlugin.create(context))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TaskListPlugin.create(context))
        // Bare text is linked only for what the resolver below opens: a
        // phone number drawn as a link would take the tap and do nothing.
        .usePlugin(linkifyPlugin())
        // The body is peer-authored content; Markwon's default resolver
        // fires ACTION_VIEW on whatever scheme a link carries. Only web and
        // e-mail addresses may leave the app — tel:, intent:, file: and
        // custom schemes are dropped rather than launched.
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    linkUri(link)?.let { LinkResolverDef().resolve(view, it.toString()) }
                }
            }
        })
        .build()

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
