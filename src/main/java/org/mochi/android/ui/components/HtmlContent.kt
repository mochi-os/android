package org.mochi.android.ui.components

import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin

@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .build()
    }

    val spanned = remember(html) {
        markwon.toMarkdown(html)
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
            textView.maxLines = maxLines
            if (maxLines != Int.MAX_VALUE) {
                textView.ellipsize = TextUtils.TruncateAt.END
            }
            (textView as ClickableLinkTextView).onNonLinkClick = onClick
            markwon.setParsedMarkdown(textView, spanned)
        },
        modifier = modifier
    )
}
