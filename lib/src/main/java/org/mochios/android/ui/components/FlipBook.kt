// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.floor
import kotlin.math.sin

// Fold direction. In Compose a positive rotationX tilts the top edge AWAY from
// the viewer, so content below the crease pivot (the leaf is the bottom half)
// swings TOWARD the viewer. Hence +1 = fold out toward you, -1 = fold away into
// the screen. The page-flip should come toward you.
private const val LEAF_FOLD_SIGN = 1f
// Higher = flatter perspective (less warp on the tall half-screen leaf).
private const val LEAF_CAMERA_DISTANCE = 14f
// Self-shadow on the rotating leaf, deepening as it goes edge-on so the
// fold reads on all-white content. Peaks at 90°, zero at 0°/180°.
private const val LEAF_SHADE_MAX = 0.35f

/**
 * Flipboard-style fold over a [VerticalPager], drawn only mid-transition. A
 * rigid leaf hinged at the mid-screen crease rotates 0°-180°, front face page
 * A's bottom half and back face page B's top half (pre-rotated), over static
 * A-top/B-bottom.
 */
@Composable
fun FlipBook(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    page: @Composable (index: Int) -> Unit,
) {
    if (pageCount == 0) return

    // Continuous scroll position: an integer means settled. Rendering off the
    // offset rather than isScrollInProgress keeps the fold smooth through drag
    // and settle; the call site snaps a fractional rest position back to a
    // page.
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    if (position <= 0f) return // first page / over-scroll below: no leaf
    val front = floor(position).toInt()
    val next = front + 1
    if (next >= pageCount) return // last page: nothing to fold to
    val f = position - front
    if (f <= 0.002f || f >= 0.998f) return // settled on a page: let live show

    val theta = f * 180f
    val frontFacing = theta <= 90f

    Box(modifier.fillMaxSize()) {
        // Static back layers. A's top half holds its place; B's bottom half is
        // revealed underneath the leaf as it lifts.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = TopHalfShape
                }
        ) { page(front) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = BottomHalfShape
                }
        ) { page(next) }

        // The rigid leaf, hinged at the mid-screen crease (center). Rotates
        // about X toward the viewer; the shadow scrim deepens as it goes
        // edge-on (sin(theta)).
        val shade = LEAF_SHADE_MAX * sin(Math.toRadians(theta.toDouble())).toFloat()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    rotationX = LEAF_FOLD_SIGN * theta
                    cameraDistance = LEAF_CAMERA_DISTANCE * density
                }
        ) {
            // Front face — A's bottom half. Visible while pointing at viewer.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = BottomHalfShape
                        alpha = if (frontFacing) 1f else 0f
                    }
                    .drawWithContent {
                        drawContent()
                        if (shade > 0f) {
                            drawRect(color = Color.Black.copy(alpha = shade))
                        }
                    }
            ) { page(front) }

            // Back face — B's top half, pre-rotated 180° so it reads upright
            // once the leaf has folded over the top region.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = TopHalfShape
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        rotationX = 180f
                        alpha = if (frontFacing) 0f else 1f
                    }
                    .drawWithContent {
                        drawContent()
                        if (shade > 0f) {
                            drawRect(color = Color.Black.copy(alpha = shade))
                        }
                    }
            ) { page(next) }
        }
    }
}

private object TopHalfShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(Rect(0f, 0f, size.width, size.height / 2f))
}

private object BottomHalfShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(Rect(0f, size.height / 2f, size.width, size.height))
}
