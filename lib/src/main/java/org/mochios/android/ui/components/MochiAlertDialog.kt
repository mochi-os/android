// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.mochios.android.ui.theme.LocalEntityRadius

/**
 * Narrowest corner a dialog may round to, matching [MochiDropdownMenu]'s floor.
 * A dialog is the largest floating surface the app draws, so it should never read
 * sharper than the menus that open over the same pages.
 */
private val DialogMinRadius = 16.dp

/** Diameter of the spinner a submitting confirm button shows before its label. */
private val ConfirmSpinnerSize = 16.dp

/** Stroke of that spinner. Material's default is far too heavy at this size. */
private val ConfirmSpinnerStroke = 2.dp

/** Space between a heading and the supporting line under it. */
private val SubtitleGap = 4.dp

/**
 * Every dialog in the app. Use it instead of `AlertDialog` everywhere, the same way
 * [MochiBottomSheet] and [MochiDropdownMenu] stand in for their Material originals.
 *
 * Two values depart from `DialogTokens` on purpose, so leave them be rather than
 * restoring the Material defaults:
 *
 *  - **Corners** follow [LocalEntityRadius], floored at [DialogMinRadius], against
 *    the spec's fixed 28dp. The theme sets no `Shapes`, so a plain `AlertDialog` was
 *    the one surface in the app that ignored the user's radius preference outright.
 *  - **Elevation** 0dp against the spec's 6dp — the app draws floating surfaces flat
 *    and leans on tone for separation. The container stays `surfaceContainerHigh`,
 *    which is what holds the dialog apart from the page now that it casts no shadow,
 *    so change the two together or not at all.
 *
 * The buttons are labels, not slots. That is the whole point of the wrapper: a
 * dialog cannot quietly grow a filled confirm, a hand-tinted label or a spinner of
 * its own size, because there is nowhere to put one. What varies between dialogs —
 * the label, whether the button is there at all, whether it is disabled, submitting
 * or destructive — varies through the parameters below.
 *
 * A null [confirmText] or [dismissText] leaves that button out, so an acknowledge-only
 * dialog passes only a confirm, and a dialog whose body carries the choices (a picker,
 * a list of options) passes only a dismiss.
 *
 * @param onDismissRequest Called when the user taps the scrim or presses back.
 * @param modifier Applied to the dialog container.
 * @param icon Content above the title. Material centres the title under one.
 * @param title The dialog's heading; null leaves it out.
 * @param titleStyle Type for the heading. Null keeps the dialog headline, which is
 *   what almost every dialog wants; pass one only for a heading that has to sit
 *   quieter than the rest, such as a picker whose grid is the real subject.
 * @param titleAlign Alignment of the heading's own lines. Material already centres
 *   the heading block under an [icon]; this decides how a heading that wraps to two
 *   lines sets those lines within it.
 * @param subtitle A supporting line under the heading, in the body type. For a
 *   heading that needs a sentence of context before the body proper begins.
 * @param text The dialog's message. Ignored when [content] is given.
 * @param confirmText Label for the affirmative button; null leaves it out.
 * @param onConfirm Called when the affirmative button is tapped.
 * @param confirmEnabled Whether the affirmative button accepts taps. Turn it off
 *   while a form is incomplete. Submitting is [confirmLoading]'s job, not this one.
 * @param confirmLoading Whether the action is in flight. Shows a spinner ahead of the
 *   label and disables the button, so a dialog cannot be submitted twice.
 * @param destructive Whether confirming destroys something. Tints the confirm label
 *   with the error colour. The dismiss label is never tinted — it is the safe way
 *   out, and colouring it would compete with the button that carries the warning.
 * @param dismissText Label for the dismissive button; null leaves it out.
 * @param onDismiss Called when the dismissive button is tapped. Defaults to
 *   [onDismissRequest], since a cancel and a tap outside almost always do the same
 *   thing; pass it only when they differ.
 * @param dismissEnabled Whether the dismissive button accepts taps.
 * @param shape Container shape; defaults to the user's radius, floored as above.
 * @param containerColor Dialog background.
 * @param properties Platform dialog behaviour, chiefly the dismissal rules.
 * @param content The dialog's body, for anything a single [text] cannot say — a form,
 *   a list of options, a message with an error line under it.
 */
@Composable
fun MochiAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: String? = null,
    titleStyle: TextStyle? = null,
    titleAlign: TextAlign? = null,
    subtitle: String? = null,
    text: String? = null,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    destructive: Boolean = false,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    dismissEnabled: Boolean = true,
    shape: Shape = RoundedCornerShape(
        LocalEntityRadius.current.coerceAtLeast(DialogMinRadius)
    ),
    containerColor: Color = AlertDialogDefaults.containerColor,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            if (confirmText != null) {
                MochiTextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled && !confirmLoading,
                    tone = if (destructive) {
                        MochiButtonTone.Destructive
                    } else {
                        MochiButtonTone.Primary
                    },
                ) {
                    if (confirmLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ConfirmSpinnerSize),
                            strokeWidth = ConfirmSpinnerStroke,
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    }
                    Text(confirmText)
                }
            }
        },
        modifier = modifier,
        dismissButton = dismissText?.let {
            {
                MochiTextButton(
                    onClick = onDismiss,
                    enabled = dismissEnabled,
                    // Neutral on purpose, which is this button's default: beside a
                    // tinted confirm - primary, or error on a destructive one - two
                    // coloured labels would say nothing about which is which.
                    tone = MochiButtonTone.Neutral,
                ) {
                    Text(it)
                }
            }
        },
        icon = icon,
        title = title?.let {
            {
                if (subtitle == null) {
                    Text(it, style = titleStyle ?: LocalTextStyle.current, textAlign = titleAlign)
                } else {
                    Column {
                        Text(it, style = titleStyle ?: LocalTextStyle.current, textAlign = titleAlign)
                        Spacer(Modifier.height(SubtitleGap))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = content ?: text?.let { { Text(it) } },
        shape = shape,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        properties = properties,
    )
}
