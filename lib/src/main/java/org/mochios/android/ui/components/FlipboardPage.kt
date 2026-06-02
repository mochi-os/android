package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

// Fold-cue strengths, all scaled by fold progress (0 at rest). Tuned
// deliberately strong so the flip reads on all-white pages — soften
// here once judged on-device.
private const val FACE_SHADE_MAX = 0.5f // per-half self-shadow at edge-on
private const val HINGE_BAND_MAX = 0.5f // mid-screen crease shadow
private const val FOLD_EDGE_MAX = 0.3f // thin line on each half's fold edge

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
 *
 * Visual aids for text-heavy pages (mostly-white content where the
 * rotation alone is invisible). The dominant cue is the self-shadow:
 *   - Each rotating half is shaded by a gradient that deepens toward
 *     the lifting (hinge) edge and scales with how edge-on the half is
 *     — 0 at rest, strongest near 90°, back to 0 at 180°. Because it's
 *     drawn inside the rotating layer it tilts with the face, so a
 *     white half visibly darkens and the shadow sweeps as it turns.
 *     This is what makes the fold read on all-white content; the rest
 *     are reinforcement.
 *   - A thin dark line at each half's fold edge sharpens the silhouette.
 *   - A soft mid-screen shadow band (fixed in screen space) deepens the
 *     crease as the fold rises and fades back to nothing at 180°.
 *
 * All cue strengths live in the [FACE_SHADE_MAX] / [HINGE_BAND_MAX] /
 * [FOLD_EDGE_MAX] constants — tuned deliberately strong so the flip is
 * visible; soften there once judged on-device.
 */
@Composable
fun FlipboardPage(
    pagerState: PagerState,
    page: Int,
    content: @Composable () -> Unit,
) {
    val pageOffset = pagerState.getOffsetDistanceInPages(page).coerceIn(-1f, 1f)

    // Staged angles, eased per phase. Each rotating half maps its
    // 0→0.5 (or 0.5→1) sub-range of |pageOffset| onto 0→180° through
    // smoothstep so the fold starts gently, accelerates, then settles
    // — paper-like rather than linear. Linear mapping made the swipe
    // feel mechanical; smoothstep gives the rotation a sense of weight.
    val topAngle: Float
    val bottomAngle: Float
    when {
        // Page is far below current — bottom still folded down (waiting),
        // top is unfolding from above-screen as offset approaches 0.5.
        pageOffset >= 0.5f -> {
            topAngle = smoothstep((pageOffset - 0.5f) * 2f) * 180f
            bottomAngle = 180f
        }
        // Page is between half-way-below and current — top half done
        // unfolding, bottom is unfolding into view (pager translation
        // puts it where it belongs).
        pageOffset >= 0f -> {
            topAngle = 0f
            bottomAngle = smoothstep(pageOffset * 2f) * 180f
        }
        // Current → halfway up — bottom half is folding up over the
        // hinge, top half stays put.
        pageOffset >= -0.5f -> {
            topAngle = 0f
            bottomAngle = -smoothstep(-pageOffset * 2f) * 180f
        }
        // Halfway up → fully gone — top half now folds up over the
        // top-edge hinge, bottom half stays at -180°.
        else -> {
            topAngle = -smoothstep((-pageOffset - 0.5f) * 2f) * 180f
            bottomAngle = -180f
        }
    }

    // Per-half fold "progress" — peaks at 90° (half edge-on), fades back
    // to 0 at both rest (0°) and fully-flipped (180°). Each half is
    // shaded by its own progress (so only the half that's actually
    // moving darkens). foldDepth is the max of the two, used for the
    // hinge band and edge lines that aren't tied to a single half.
    val topFold = foldProgress(topAngle)
    val bottomFold = foldProgress(bottomAngle)
    val foldDepth = max(topFold, bottomFold)

    Box(modifier = Modifier.fillMaxSize()) {
        // Top half — clipped, rotates about its top edge (screen top).
        // The dark line at y = midScreen is drawn AFTER the half's
        // content so it sits on the page edge and rotates with the
        // half, giving the rotating piece a visible silhouette. The
        // line's alpha is gated on foldDepth so it's invisible at rest
        // (no swipe) and fades in as the user starts to drag.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = TopHalfShape
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationX = topAngle
                    cameraDistance = 8f * density
                    alpha = if (abs(topAngle) > 90f) 0f else 1f
                }
                .drawWithContent {
                    drawContent()
                    val midY = size.height / 2f
                    // Self-shadow over the whole top half, darkest at the
                    // lifting (mid-screen) edge, scaled by how edge-on the
                    // half is. Rotates with the face — the cue that makes
                    // the fold visible on white pages.
                    if (topFold > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = FACE_SHADE_MAX * topFold),
                                ),
                                startY = 0f,
                                endY = midY,
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, midY),
                        )
                    }
                    if (foldDepth > 0f) {
                        val px = 1.5f.dp.toPx()
                        drawRect(
                            color = Color.Black.copy(alpha = FOLD_EDGE_MAX * foldDepth),
                            topLeft = Offset(0f, midY - px),
                            size = Size(size.width, px),
                        )
                    }
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
                    cameraDistance = 8f * density
                    alpha = if (abs(bottomAngle) > 90f) 0f else 1f
                }
                .drawWithContent {
                    drawContent()
                    val midY = size.height / 2f
                    // Self-shadow over the whole bottom half, darkest at the
                    // lifting (mid-screen) edge, scaled by how edge-on the
                    // half is. Rotates with the face.
                    if (bottomFold > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = FACE_SHADE_MAX * bottomFold),
                                    Color.Transparent,
                                ),
                                startY = midY,
                                endY = size.height,
                            ),
                            topLeft = Offset(0f, midY),
                            size = Size(size.width, size.height - midY),
                        )
                    }
                    if (foldDepth > 0f) {
                        val px = 1.5f.dp.toPx()
                        drawRect(
                            color = Color.Black.copy(alpha = FOLD_EDGE_MAX * foldDepth),
                            topLeft = Offset(0f, midY),
                            size = Size(size.width, px),
                        )
                    }
                }
        ) {
            content()
        }
        // Mid-screen shadow band — overlays both halves, fixed in
        // screen space (doesn't rotate). Vertical gradient
        // transparent → dark → transparent centred on the fold. Alpha
        // scales with foldDepth so the shadow appears, deepens, then
        // fades away during the swipe. Pure visual cue; the user
        // notices motion even on otherwise-white pages.
        if (foldDepth > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val midY = size.height / 2f
                        val bandH = size.height * 0.10f
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = HINGE_BAND_MAX * foldDepth),
                                    Color.Transparent,
                                ),
                                startY = midY - bandH,
                                endY = midY + bandH,
                            ),
                            topLeft = Offset(0f, midY - bandH),
                            size = Size(size.width, bandH * 2f),
                        )
                    }
            ) {}
        }
    }
}

/** 0 at rest (0°), 1 at edge-on (90°), 0 at fully-flipped (180°). */
private fun foldProgress(angle: Float): Float {
    val a = abs(angle).coerceIn(0f, 180f)
    return if (a <= 90f) a / 90f else (180f - a) / 90f
}

/**
 * Smoothstep easing — 3x²−2x³. Zero derivative at both ends so the
 * fold starts and stops without a velocity discontinuity, addressing
 * the linear-mapping jank.
 */
private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
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
