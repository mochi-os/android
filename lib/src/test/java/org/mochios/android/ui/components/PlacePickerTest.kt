// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.android.model.PlaceData
import org.mochios.android.places.NominatimService
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The place picker against a geocoder that answers from the test: a failed
 * search says so rather than "No places found", and a tap on the map selects
 * a new place through the caller's current callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class PlacePickerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    /** A geocoder answering searches with [search] and refusing every reverse lookup. */
    private fun geocoder(code: Int, search: String = ""): NominatimService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val searching = request.url.encodedPath == "/search"
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (searching) code else 500)
                    .message("")
                    .body((if (searching) search else "").toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return NominatimService(client)
    }

    private fun waitFor(text: String) {
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a failed search says why, not that no place was found`() {
        rule.setContent { PlacePicker(place = null, onPlaceSelected = {}, service = geocoder(503)) }
        rule.onNode(hasSetTextAction()).performTextInput("Tallinn")
        waitFor("Server error (503). Please try again later.")
        rule.onNodeWithText("No places found").assertDoesNotExist()
    }

    @Test
    fun `a search that matched nothing says no place was found`() {
        rule.setContent { PlacePicker(place = null, onPlaceSelected = {}, service = geocoder(200, "[]")) }
        rule.onNode(hasSetTextAction()).performTextInput("Tallinn")
        waitFor("No places found")
    }

    @Test
    fun `a picked suggestion carries nothing of the place before it`() {
        val picked = mutableListOf<PlaceData>()
        val answer = """[{"display_name":"Tallinn, Estonia","name":"Tallinn","lat":"59.43","lon":"24.75","class":"boundary","osm_type":"relation","osm_id":"1"}]"""
        rule.setContent {
            PlacePicker(
                place = PlaceData(name = "", lat = 1.0, lon = 2.0, country = "Latvia", state = "Riga", city = "Riga"),
                onPlaceSelected = { place -> picked += place },
                service = geocoder(200, answer),
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("Tallinn")
        waitFor("Tallinn, Estonia")
        rule.onNodeWithText("Tallinn, Estonia").performClick()
        rule.waitForIdle()
        assertEquals(
            PlaceData(name = "Tallinn, Estonia", lat = 59.43, lon = 24.75, category = "boundary"),
            picked.last(),
        )
    }

    /**
     * A geocoder whose reverse lookup counts down [asked] and answers
     * "Somewhere Else" once [answer] is counted down; searches answer [search].
     */
    private fun held(asked: CountDownLatch, answer: CountDownLatch, search: String = "[]"): NominatimService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val reversing = request.url.encodedPath == "/reverse"
                if (reversing) {
                    asked.countDown()
                    answer.await(5, TimeUnit.SECONDS)
                }
                val body = if (reversing) {
                    """{"display_name":"Somewhere Else","name":"Somewhere Else","lat":"1","lon":"2","class":"place"}"""
                } else {
                    search
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return NominatimService(client)
    }

    private fun tapMap() {
        rule.runOnUiThread {
            val map = rule.activity.window.decorView.map()!!
            val overlay = map.overlays.filterIsInstance<MapEventsOverlay>().single()
            val tap = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, map.width / 2f, map.height / 2f, 0)
            overlay.onSingleTapConfirmed(tap, map)
            tap.recycle()
        }
    }

    @Test
    fun `a name typed while the tap's lookup is out is not overwritten by its answer`() {
        val picked = mutableListOf<PlaceData>()
        val asked = CountDownLatch(1)
        val answer = CountDownLatch(1)
        rule.setContent { PlacePicker(place = null, onPlaceSelected = { place -> picked += place }, service = held(asked, answer)) }
        rule.waitForIdle()
        tapMap()
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeBy(100)
            asked.count == 0L
        }
        rule.onNode(hasSetTextAction()).performTextInput("Home")
        answer.countDown()
        Thread.sleep(500)
        rule.waitForIdle()
        assertEquals("THE POINT: the typed name stands", "Home", picked.last().name)
        rule.onNodeWithText("Somewhere Else").assertDoesNotExist()
    }

    @Test
    fun `a suggestion picked while the tap's lookup is out is not overwritten by its answer`() {
        val picked = mutableListOf<PlaceData>()
        val asked = CountDownLatch(1)
        val answer = CountDownLatch(1)
        val tallinn = """[{"display_name":"Tallinn, Estonia","name":"Tallinn","lat":"59.43","lon":"24.75","class":"boundary","osm_type":"relation","osm_id":"1"}]"""
        rule.setContent {
            PlacePicker(place = null, onPlaceSelected = { place -> picked += place }, service = held(asked, answer, tallinn))
        }
        rule.onNode(hasSetTextAction()).performTextInput("Tallinn")
        waitFor("Tallinn, Estonia")
        tapMap()
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeBy(100)
            asked.count == 0L
        }
        rule.onNodeWithText("Tallinn, Estonia").performClick()
        answer.countDown()
        Thread.sleep(500)
        rule.waitForIdle()
        assertEquals("THE POINT: the picked place stands", "Tallinn, Estonia", picked.last().name)
    }

    private fun View.map(): MapView? = when (this) {
        is MapView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index -> getChildAt(index).map() }
        else -> null
    }

    @Test
    fun `a tap on the map is a new place, given to the caller's current callback`() {
        val first = mutableListOf<PlaceData>()
        val second = mutableListOf<PlaceData>()
        var later by mutableStateOf(false)
        rule.setContent {
            val sink = if (later) second else first
            PlacePicker(
                place = PlaceData(name = "Old Town", lat = 59.43, lon = 24.75, country = "Estonia", category = "boundary"),
                onPlaceSelected = { place -> sink += place },
                service = geocoder(200, "[]"),
            )
        }
        rule.waitForIdle()
        later = true
        rule.waitForIdle()

        tapMap()
        rule.waitForIdle()

        assertTrue("the first composition's callback is stale", first.isEmpty())
        val tapped = second.single()
        assertEquals("Old Town", tapped.name)
        assertEquals("", tapped.country)
        assertEquals("", tapped.category)
    }
}
