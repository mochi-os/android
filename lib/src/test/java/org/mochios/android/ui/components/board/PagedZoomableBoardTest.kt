// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives a long-press drag across a board of column pages, each page one drop
 * target, with the card in a lazy column inside its page as on the real
 * boards. The board is 360dp wide: 312dp pages at rest, 171.6dp zoomed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PagedZoomableBoardTest {

    @get:Rule
    val rule = createComposeRule()

    init {
        // Capture through the hardware renderer, as a phone draws: a software
        // capture replays a recorded layer by re-running its recording, which
        // the dragged item's layer cannot do outside its own draw pass.
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
    }

    private val drag = DragState()
    private var dropped: String? = null
    private var pages = 0
    private lateinit var list: LazyListState

    private fun show(count: Int, start: Int = 0) {
        pages = count
        list = LazyListState(firstVisibleItemIndex = start)
        rule.setContent {
            Box(Modifier.size(360.dp, 640.dp).background(Color.White)) {
                PagedZoomableBoard(pageCount = count, cardDragState = drag, state = list) { index ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("page$index")
                            .dropTarget(drag, "column:$index", DropOrientation.OnOnly) { _, _ ->
                                dropped = "column:$index"
                            }
                    ) {
                        if (index == start) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            // Inside the drag modifier, as a card's
                                            // own surface is, so its picture holds it.
                                            // Blue while lifted, as a board's card
                                            // turns to a placeholder.
                                            .draggableItem(drag, "card")
                                            .background(
                                                if (drag.draggingItemId == "card") Color.Blue else Color.Red
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // The auto-scroll loop never idles while a drag is on; time is stepped by hand.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(100)
        // Robolectric draws only when asked. A card is on screen before anyone
        // can lift it, and its picture is taken as it is drawn.
        rule.onRoot().captureToImage()
    }

    private fun point(x: Dp, y: Dp): Offset = with(rule.density) { Offset(x.toPx(), y.toPx()) }

    private fun press(x: Dp, y: Dp) {
        rule.onRoot().performTouchInput { down(point(x, y)) }
        // Past the long-press timeout and the zoom.
        rule.mainClock.advanceTimeBy(1000)
    }

    private fun move(x: Dp, y: Dp) {
        rule.onRoot().performTouchInput { moveTo(point(x, y)) }
    }

    private fun release() {
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
    }

    /** On-screen bounds of page [index] in root pixels; empty when it is out of view or gone. */
    private fun bounds(index: Int): Rect =
        rule.onAllNodesWithTag("page$index").fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .firstOrNull { it.width > 0f } ?: Rect.Zero

    private fun pageUnder(x: Dp, y: Dp): Int {
        val target = point(x, y)
        return (0 until pages).single { index -> bounds(index).contains(target) }
    }

    private fun Float.inDp(): Float = with(rule.density) { this@inDp.toDp().value }

    /**
     * Holds the pointer at the right edge until the board has scrolled well
     * past the card's own column, then drops mid-screen. Returns the page
     * under the drop.
     */
    private fun dragAcross(): Int {
        show(count = 8)
        press(150.dp, 40.dp)
        move(350.dp, 300.dp)
        rule.mainClock.advanceTimeBy(1200)
        assertTrue("the card's column scrolled out of view", bounds(0).width == 0f)
        move(180.dp, 300.dp)
        rule.mainClock.advanceTimeBy(100)
        val under = pageUnder(180.dp, 300.dp)
        release()
        return under
    }

    @Test
    fun `a drag outlives its column scrolling out of view`() {
        val under = dragAcross()
        assertTrue("dropped three or more columns over, not $under", under >= 3)
        assertEquals("column:$under", dropped)
    }

    @Test
    fun `after the drop the board settles on the column it was dropped on`() {
        val under = dragAcross()
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(under, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
        assertEquals(24f, bounds(under).left.inDp(), 1f)
    }

    @Test
    fun `a pointer that holds still is re-targeted as the board zooms beneath it`() {
        show(count = 4)
        // Near the card's right edge: once zoomed, the next column is under it.
        press(290.dp, 40.dp)
        assertEquals(1, pageUnder(290.dp, 40.dp))
        release()
        assertEquals("column:1", dropped)
    }

    @Test
    fun `zooming centres the column the drag began in with both neighbours in view`() {
        show(count = 5, start = 2)
        press(180.dp, 40.dp)
        assertEquals(180f, bounds(2).center.x.inDp(), 1f)
        assertTrue("left neighbour shows ${bounds(1).width.inDp()}dp", bounds(1).width.inDp() > 60f)
        assertTrue("right neighbour shows ${bounds(3).width.inDp()}dp", bounds(3).width.inDp() > 60f)
        release()
    }

    @Test
    fun `the lifted card is drawn under the pointer as it was when lifted`() {
        show(count = 3)
        press(100.dp, 40.dp)
        // Empty column space, well below where the card itself sits.
        move(200.dp, 400.dp)
        rule.mainClock.advanceTimeBy(100)
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val at = point(200.dp, 400.dp)
        assertEquals("the ghost, under the pointer", Color.Red, pixels[at.x.toInt(), at.y.toInt()])
        // The zoomed column is centred: 94.2dp in, the card 8dp down.
        val source = point(100.dp, 20.dp)
        assertEquals("the placeholder, where the card sits", Color.Blue, pixels[source.x.toInt(), source.y.toInt()])
        release()
    }
}
