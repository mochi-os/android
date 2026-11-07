// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.widget.TextView
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.target
import coil3.size.Dimension
import coil3.size.Size
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.DrawableUtils
import io.noties.markwon.image.ImageSpanFactory
import org.commonmark.node.Image

/**
 * Markwon plugin that draws markdown and HTML images through the app's Coil
 * loader, in place of Markwon's `ImagesPlugin`. That plugin fetched every image
 * with a bare `HttpURLConnection` each time the text was drawn: no cache, no
 * session, so a session-gated image failed and a repeated one downloaded again.
 * Through Coil an image shares the memory and disk caches and the concurrent
 * request dedupe, and a server-relative path resolves against the session
 * server. The body is someone else's writing, so its images go as
 * [AnonymousImage]: never with the session cookie or app token, or
 * `![](/someapp/-/some-action)` would run that action as each reader.
 */
class CoilImagesPlugin private constructor(
    private val loader: CoilDrawableLoader,
) : AbstractMarkwonPlugin() {

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.asyncDrawableLoader(loader)
    }

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(Image::class.java, ImageSpanFactory())
    }

    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        AsyncDrawableScheduler.unschedule(textView)
    }

    override fun afterSetText(textView: TextView) {
        AsyncDrawableScheduler.schedule(textView)
    }

    /** Factory for [CoilImagesPlugin]. */
    companion object {

        /**
         * A plugin loading through the app's singleton Coil loader.
         *
         * @param context Any context; only its application context is kept.
         * @return The plugin, for `Markwon.Builder.usePlugin`.
         */
        fun create(context: Context): CoilImagesPlugin =
            CoilImagesPlugin(CoilDrawableLoader(context.applicationContext))
    }
}

/**
 * Loads each [AsyncDrawable]'s destination with Coil and hands the result to the
 * drawable. Markwon calls [load] and [cancel] on the main thread, where Coil also
 * delivers its target callbacks, so [requests] needs no lock.
 */
internal class CoilDrawableLoader(private val context: Context) : AsyncDrawableLoader() {

    private val requests = mutableMapOf<AsyncDrawable, Disposable>()

    override fun load(drawable: AsyncDrawable) {
        if (drawable in requests) {
            return
        }
        val maxWidth = Dimension(context.resources.displayMetrics.widthPixels)
        val request = ImageRequest.Builder(context)
            .data(imageData(drawable.destination))
            .size(Size(maxWidth, Dimension.Undefined))
            .allowHardware(false)
            .target(
                onSuccess = { image ->
                    requests.remove(drawable)
                    deliver(drawable, image.asDrawable(context.resources))
                },
                onError = { _ ->
                    requests.remove(drawable)
                },
            )
            .build()
        val disposable = SingletonImageLoader.get(context).enqueue(request)
        if (!disposable.job.isCompleted) {
            requests[drawable] = disposable
        }
    }

    override fun cancel(drawable: AsyncDrawable) {
        requests.remove(drawable)?.dispose()
    }

    override fun placeholder(drawable: AsyncDrawable): Drawable? = null

    // An inline `data:` image needs no request; everything else is an address
    // that must load without credentials.
    private fun imageData(destination: String): Any =
        if (destination.startsWith("data:", ignoreCase = true)) {
            destination
        } else {
            AnonymousImage(destination)
        }

    private fun deliver(drawable: AsyncDrawable, result: Drawable) {
        if (!drawable.isAttached) {
            return
        }
        DrawableUtils.applyIntrinsicBoundsIfEmpty(result)
        drawable.result = result
    }
}
