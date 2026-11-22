// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An embedded player's WebView is paused with the app and torn down with the
 * post: left alone, its player went on sounding after the post closed, and
 * each post opened kept another renderer alive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoEmbedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun webView(): WebView? = find(compose.activity.window.decorView)

    private fun find(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test
    fun `the player is paused with the app and destroyed with the post`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var shown by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown) VideoEmbed(VideoInfo("youtube", "abc123"))
            }
        }
        compose.waitForIdle()
        val view = webView()!!
        assertTrue(shadowOf(view).lastLoadDataWithBaseURL.data.contains("https://www.youtube.com/embed/abc123"))

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        assertTrue("THE POINT: paused in the background", shadowOf(view).wasOnPauseCalled())
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertTrue(shadowOf(view).wasOnResumeCalled())

        assertFalse(shadowOf(view).wasDestroyCalled())
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        assertTrue("THE POINT: no player outlives the post", shadowOf(view).wasDestroyCalled())
        assertEquals("about:blank", shadowOf(view).lastLoadedUrl)
    }

    @Test
    fun `another video gets a player of its own and ends the one before`() {
        var video by mutableStateOf(VideoInfo("youtube", "abc123"))
        compose.setContent { VideoEmbed(video) }
        compose.waitForIdle()
        val first = webView()!!

        compose.runOnIdle { video = VideoInfo("vimeo", "42") }
        compose.waitForIdle()
        val second = webView()!!
        assertNotSame("a new view, not the old page loaded over", first, second)
        assertTrue("the one before is ended", shadowOf(first).wasDestroyCalled())
        assertTrue(shadowOf(second).lastLoadDataWithBaseURL.data.contains("https://player.vimeo.com/video/42"))
    }
}
