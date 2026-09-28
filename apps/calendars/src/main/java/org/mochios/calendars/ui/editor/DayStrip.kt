// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.calendars.model.Hours
import org.mochios.calendars.ui.calendar.toColour

/** The width of an hour on the strip. */
private val HOUR = 48.dp

private fun x(minutes: Int): Dp = HOUR * (minutes / 60f)

/**
 * The event's day on one line beneath its times: the user's other events that
 * day drawn faintly, the event itself a block that drags to move and stretches
 * from its end, and a tap on the rest of the day moving it there, all on
 * five-minute steps. It opens on the working hours. The fields above it are
 * the way to the same times for TalkBack, so the strip is hidden from it. It
 * reads left to right in every language, as a ruler does, and as the web
 * editor's strip does.
 */
@Composable
fun DayStrip(
    span: Span,
    colour: Color,
    others: List<Block>,
    hours: Hours,
    onMove: (Int) -> Unit,
    onResize: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = LocalFormat.current
    val hourPx = with(LocalDensity.current) { HOUR.toPx() }
    val current by rememberUpdatedState(span)
    val move by rememberUpdatedState(onMove)
    val resize by rememberUpdatedState(onResize)
    val scroll = rememberScrollState()
    val shade = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val line = MaterialTheme.colorScheme.outlineVariant

    // Open on the working hours, or on the event where it starts before them.
    LaunchedEffect(Unit) {
        val first = minOf(hours.start * 60, maxOf(0, span.start - 60))
        scroll.scrollTo((hourPx * first / 60f).toInt())
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, line, RoundedCornerShape(8.dp))
                .horizontalScroll(scroll)
                .clearAndSetSemantics {}
                .testTag("day-strip"),
        ) {
            Box(Modifier.width(HOUR * 24).fillMaxHeight()) {
                Box(Modifier.width(x(hours.start * 60)).fillMaxHeight().background(shade))
                Box(
                    Modifier.offset(x = x(hours.finish * 60)).width(x((24 - hours.finish) * 60))
                        .fillMaxHeight().background(shade),
                )
                for (hour in 0 until 24) {
                    Box(Modifier.offset(x = x(hour * 60)).width(1.dp).fillMaxHeight().background(line))
                    Text(
                        text = format.formatHour(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.offset(x = x(hour * 60) + 3.dp, y = 2.dp),
                    )
                }
                // The day beside the event: a tap moves the event there.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 4.dp)
                        .fillMaxHeight()
                        .pointerInput(hourPx) {
                            detectTapGestures { offset ->
                                val held = current
                                move(Strip.placed(held.start, held.finish, offset.x / hourPx * 60.0))
                            }
                        },
                ) {
                    others.forEach { block ->
                        Box(
                            Modifier
                                .offset(x = x(block.start))
                                .width(x(block.finish - block.start))
                                .fillMaxHeight()
                                .background(
                                    block.colour.toColour(MaterialTheme.colorScheme.primary).copy(alpha = 0.25f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = block.summary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .offset(x = x(span.start))
                            .width(maxOf(x(span.finish - span.start), 6.dp))
                            .fillMaxHeight()
                            .background(colour, RoundedCornerShape(4.dp))
                            .testTag("day-strip-event")
                            .pointerInput(hourPx) {
                                var origin = current
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        origin = current
                                        total = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    total += amount
                                    move(Strip.moved(origin.start, origin.finish, total / hourPx * 60.0))
                                }
                            },
                    ) {
                        // The end, which stretches rather than moves.
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(16.dp)
                                .fillMaxHeight()
                                .testTag("day-strip-end")
                                .pointerInput(hourPx) {
                                    var origin = current
                                    var total = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            origin = current
                                            total = 0f
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        total += amount
                                        resize(Strip.resized(origin.start, origin.finish, total / hourPx * 60.0))
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
