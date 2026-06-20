// Copyright © 2026 Mochi OÜ
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
 * Flipboard-style book-fold over a [VerticalPager]'s pages. Overlay this on
 * top of the pager (drawn after it, no pointer input of its own so drags
 * still reach the pager). It renders the fold ONLY while a transition is in
 * progress; at rest it draws nothing, so the pager's own live (interactive)
 * page shows through.
 *
 * The page is hinged at the mid-screen crease. Going from page A (front, the
 * lower index) to page B (next):
 *   - A's TOP half stays put (a static layer) — it never slides; it is simply
 *     covered at the end when B's top folds down over it.
 *   - B's BOTTOM half is a static layer behind the leaf, revealed as the leaf
 *     lifts off the bottom region.
 *   - A single rigid leaf, hinged at the crease, rotates 0° → 180° toward the
 *     viewer. Its FRONT face is A's bottom half (shown 0°–90°); its BACK face
 *     is B's top half (shown 90°–180°, pre-rotated 180° so it reads upright
 *     once the leaf lies over the top region). Back-face culling via the
 *     per-face alpha means each face only draws while pointing at the viewer.
 *
 * Driven entirely off [PagerState] so it stays in lock-step with the drag /
 * fling; reversing the swipe just plays the fold backwards.
 *
 * [page] renders the post at a given index. It is invoked for the two pages
 * involved in the current fold, so it must be cheap to call again with a
 * different index (it is the same content the pager renders).
 */
@Composable
fun FlipBook(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    page: @Composable (index: Int) -> Unit,
) {
    if (pageCount == 0) return

    // Continuous scroll position across pages. position == an integer means a
    // page is settled (no fold). The pair being folded is (front, front+1)
    // with progress f ∈ (0,1). Rendering off the offset (rather than
    // isScrollInProgress) keeps the fold smooth across the whole gesture —
    // drag AND settle. The freeze guard lives at the call site: if the pager
    // ever rests at a fractional offset, it is snapped to the nearest page so
    // f returns to ~0 and this overlay clears.
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
