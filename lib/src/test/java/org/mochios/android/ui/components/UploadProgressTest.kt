// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.Upload
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.UserPreferences
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What an upload in flight looks like: the bytes sent of the total while they
 * go, "Processing…" once the server has them all, a bare bar while the files
 * are read, and the files themselves in the composer, which takes no more
 * until the send ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadProgressTest {

    @get:Rule
    val rule = createComposeRule()

    private val format = Format(UserPreferences())

    private val sending = Upload(
        listOf(
            Upload.File("report.pdf", 2048, sent = 1024),
            Upload.File("photo.png", 2048),
        )
    )

    private fun show(upload: Upload?) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                UploadFiles(upload)
            }
        }
    }

    @Test
    fun `while bytes go, the bar says how many of how many`() {
        show(sending)
        val label = "${format.formatFileSize(1024)} of ${format.formatFileSize(4096)}"
        rule.onNodeWithText(label).assertExists()
        rule.onNodeWithContentDescription("Upload progress").assertExists()
        rule.onNodeWithText("report.pdf").assertExists()
        rule.onNodeWithText("photo.png").assertExists()
    }

    @Test
    fun `once every byte is out, the server is processing`() {
        show(
            Upload(
                listOf(
                    Upload.File("report.pdf", 2048, sent = 2048),
                    Upload.File("photo.png", 2048, sent = 2048),
                )
            )
        )
        rule.onNodeWithText("Processing…").assertExists()
    }

    @Test
    fun `while the files are read, the bar runs with no label`() {
        show(Upload())
        rule.onNodeWithContentDescription("Upload progress").assertExists()
        rule.onNodeWithText("Processing…").assertDoesNotExist()
    }

    @Test
    fun `no upload draws nothing`() {
        show(null)
        rule.onNodeWithContentDescription("Upload progress").assertDoesNotExist()
    }

    @Test
    fun `the composer shows the files going and takes no more`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                ComposeBar(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    sendLabel = "Send",
                    attachments = ComposeBarAttachments(
                        pending = emptyList(),
                        onAdd = {},
                        onRemove = {},
                        resolveFileName = { "" },
                        addLabel = "Attach",
                        fallbackLabel = "File",
                        removeLabel = "Remove",
                    ),
                    upload = sending,
                )
            }
        }
        rule.onNodeWithText("report.pdf").assertExists()
        rule.onNodeWithText("photo.png").assertExists()
        rule.onNodeWithContentDescription("Upload progress").assertExists()
        rule.onNodeWithContentDescription("Attach").assertIsNotEnabled()
    }
}
