package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs

/**
 * Flipboard-style page composable for use inside VerticalPager. Renders
 * the page content as two clipped halves (top + bottom) with staged
 * rotations driven by [pagerState].
 *
 * Phasing for the current page going up (pageOffset goes 0 → -1):
 *   - 0 → -0.5: bottom half rotates 0 → -180° about the mid-screen
 *     hinge. Top half stays put. As the bottom folds up, the next
 *     page's top half (its own pager translation places it in the
 *     bottom half of the screen at this offset) is revealed underneath.
 *   - -0.5 → -1: top half rotates 0 → -180° about the top-of-screen
 *     hinge. Bottom half stays at -180° (hidden back-face).
 *
 * Mirrored for the next page coming in (pageOffset +1 → 0). Combined
 * with the pager's natural per-page translation, mid-swipe the user
 * sees the classic Flipboard signature: the top half of one post + the
 * top half of the next, joined at the hinge.
 *
 * Per-half [alpha] zeroes the back-face once rotation passes 90° so the
 * mirrored content of an outgoing half doesn't bleed through.
 */
@Composable
fun FlipboardPage(
    pagerState: PagerState,
    page: Int,
    content: @Composable () -> Unit,
) {
    val pageOffset = pagerState.getOffsetDistanceInPages(page).coerceIn(-1f, 1f)

    // Staged angles. Linear within each phase, clamped at the boundary so
    // the "waiting" half doesn't twitch into rotation mid-swipe.
    val topAngle: Float
    val bottomAngle: Float
    when {
        // Page is far below current — top half still folded down (waiting),
        // top is unfolding from above-screen as offset approaches 0.5.
        pageOffset >= 0.5f -> {
            topAngle = (pageOffset - 0.5f) * 360f
            bottomAngle = 180f
        }
        // Page is between half-way-below and current — top half done
        // unfolding, bottom is unfolding into view from above (pager
        // translation puts it where it belongs).
        pageOffset >= 0f -> {
            topAngle = 0f
            bottomAngle = pageOffset * 360f
        }
        // Current → halfway up — bottom half is folding up over hinge,
        // top half stays put.
        pageOffset >= -0.5f -> {
            topAngle = 0f
            bottomAngle = pageOffset * 360f
        }
        // Halfway up → fully gone — top half now folds up over top-edge
        // hinge, bottom half stays at -180°.
        else -> {
            topAngle = (pageOffset + 0.5f) * 360f
            bottomAngle = -180f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top half — clipped, rotates about its top edge (screen top).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = TopHalfShape
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationX = topAngle
                    cameraDistance = 16f * density
                    alpha = if (abs(topAngle) > 90f) 0f else 1f
                }
        ) {
            content()
        }
        // Bottom half — clipped, rotates about the mid-screen hinge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = BottomHalfShape
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    rotationX = bottomAngle
                    cameraDistance = 16f * density
                    alpha = if (abs(bottomAngle) > 90f) 0f else 1f
                }
        ) {
            content()
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
