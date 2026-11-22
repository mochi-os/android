// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The attachment players: paused when the app goes to the background, built
 * only when the reader plays, and taking audio focus as a media app should.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** A player that only keeps whether it is to play, and whether it was released. */
    private class Fake : SimpleBasePlayer(Looper.getMainLooper()) {
        var playing = false
        var released = false

        override fun getState(): State = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_RELEASE).build()
            )
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            playing = playWhenReady
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> {
            released = true
            return Futures.immediateVoidFuture()
        }
    }

    private fun background(owner: Owner, player: Player, resume: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PauseInBackground(player, resume = resume)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a playing video pauses behind the home screen and plays again on return`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val player = Fake()
        background(owner, player, resume = true)
        compose.runOnIdle { player.play() }

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertFalse("THE POINT: no sound behind the launcher", player.playWhenReady)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertTrue("carries on where it was", player.playWhenReady)
    }

    @Test
    fun `playing audio pauses in the background and stays paused on return`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val player = Fake()
        background(owner, player, resume = false)
        compose.runOnIdle { player.play() }

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertFalse(player.playWhenReady)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `a video paused before leaving is not started on return`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val player = Fake()
        background(owner, player, resume = true)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `an audio row builds its player on the first play, not when it is shown`() {
        val built = mutableListOf<Fake>()
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) AudioRow(build = { Fake().also { built += it } })
        }
        compose.waitForIdle()
        assertEquals("THE POINT: nothing built for a clip not played", 0, built.size)
        compose.onNodeWithText("0:00 / 0:00").assertExists()

        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(org.mochios.android.R.string.common_play))
            .performClick()
        compose.waitForIdle()
        assertEquals(1, built.size)
        assertTrue("the tap that built it plays it", built.single().playWhenReady)

        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(org.mochios.android.R.string.common_play))
            .performClick()
        compose.waitForIdle()
        assertEquals("one player for the row", 1, built.size)
        assertFalse("the next tap pauses it", built.single().playWhenReady)

        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        assertTrue("released with the row", built.single().released)
    }

    @Test
    fun `an audio row's player is paused in the background`() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val built = mutableListOf<Fake>()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AudioRow(build = { Fake().also { built += it } })
            }
        }
        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(org.mochios.android.R.string.common_play))
            .performClick()
        compose.waitForIdle()
        assertTrue(built.single().playWhenReady)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertFalse("THE POINT: no sound behind the launcher", built.single().playWhenReady)
    }

    @Test
    fun `a player asks for audio focus before it sounds`() {
        val context = RuntimeEnvironment.getApplication()
        val player = mediaPlayer(context, DefaultMediaSourceFactory(context), C.AUDIO_CONTENT_TYPE_MUSIC)
        try {
            assertEquals(C.USAGE_MEDIA, player.audioAttributes.usage)
            assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, player.audioAttributes.contentType)
            player.setMediaItem(MediaItem.fromUri("asset:///absent.mp3"))
            player.prepare()
            player.play()
            val audio = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            val asked = waitFor { audio.lastAudioFocusRequest }
            assertNotNull("THE POINT: focus is asked for, so other sound pauses", asked)
            val noisy = waitFor {
                shadowOf(context).registeredReceivers.firstOrNull { receiver ->
                    receiver.intentFilter.hasAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                }
            }
            assertNotNull("THE POINT: unplugging headphones pauses it", noisy)
        } finally {
            player.release()
        }
    }

    /** [read] once it answers, within a few seconds: the player asks from its own thread. */
    private fun <T> waitFor(read: () -> T?): T? {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(20)
        }
        return read()
    }
}
