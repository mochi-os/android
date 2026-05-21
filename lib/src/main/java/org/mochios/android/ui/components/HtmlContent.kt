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
    onClick: (() -> Unit)? = null,
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
            textView.maxLines = maxLines
            if (maxLines != Int.MAX_VALUE) {
                textView.ellipsize = TextUtils.TruncateAt.END
            }
            (textView as ClickableLinkTextView).onNonLinkClick = onClick
            markwon.setParsedMarkdown(textView, spanned)
            onTextViewReady?.invoke(textView)
        },
        modifier = modifier
    )
}
