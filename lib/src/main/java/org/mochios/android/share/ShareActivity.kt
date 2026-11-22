// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.R
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.launcher.launcherComponentFor
import org.mochios.android.push.NonceStore
import org.mochios.android.push.notificationUri
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.theme.MochiTheme
import org.mochios.android.util.isWebUrl
import javax.inject.Inject

/**
 * Mochi as a destination in the system share sheet, for a page from a browser,
 * text, a photo from the gallery, or an item one of Mochi's own apps shares: a
 * small chooser of Post to a feed and Send in a chat, which opens Feeds' new
 * post or Chat's choice of chat filled in. The app it opens holds the share
 * until the user is signed in, as it holds any link.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = received(this, intent)
        if (shared == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val anchors by sessionManager.themeAnchors.collectAsState(initial = null)
            val preferences by preferencesManager.preferences.collectAsState()
            MochiTheme(themeAnchors = anchors, preferences = preferences) {
                ShareChooser(
                    onFeed = { open(shared, Destination.FEED) },
                    onChat = { open(shared, Destination.CHAT) },
                    onDismiss = ::finish,
                )
            }
        }
    }

    /** Whether a destination was chosen, so a second tap while the images copy opens nothing more. */
    private var chosen = false

    private fun open(shared: Received, destination: Destination) {
        if (chosen) return
        chosen = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { deliver(this@ShareActivity, shared, destination) }
            finish()
        }
    }
}

/** What another app, or one of Mochi's own, hands Mochi to share. */
data class Received(
    val text: String,
    val url: String = "",
    val images: List<Uri> = emptyList(),
)

/** Where Mochi takes a share. */
enum class Destination { FEED, CHAT }

/**
 * The share [intent] carries, or null when it holds nothing to share: the
 * words and address one of Mochi's own apps put apart; else its text, a page
 * address alone taken as the address, with its subject, such as a page's
 * title, as the first line where the text does not hold it already; and its
 * images, each a content URI another app gave this one to read.
 */
fun received(context: Context, intent: Intent): Received? {
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
    val images = images(context, intent)
    val (text, url) = if (intent.hasExtra(EXTRA_WORDS) || intent.hasExtra(EXTRA_ADDRESS)) {
        intent.getStringExtra(EXTRA_WORDS).orEmpty().trim() to intent.getStringExtra(EXTRA_ADDRESS).orEmpty().trim()
    } else {
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.trim().orEmpty()
        when {
            isWebUrl(body) && body.none { it.isWhitespace() } -> subject.takeIf { it != body }.orEmpty() to body
            subject.isEmpty() || body.contains(subject) -> body to ""
            body.isEmpty() -> subject to ""
            else -> "$subject\n$body" to ""
        }
    }
    if (text.isEmpty() && url.isEmpty() && images.isEmpty()) return null
    return Received(text, url, images)
}

/**
 * The images a share holds: its stream, or streams, and its clip's items,
 * each once. Only content URIs, and none of this app's own providers: the
 * activity is exported, so any app could name a file only Mochi may read and
 * have it posted.
 */
private fun images(context: Context, intent: Intent): List<Uri> {
    if (intent.type?.startsWith("image/") != true) return emptyList()
    val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }
    val clip = intent.clipData
    val clipped = if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri }
    return (streams + clipped).distinct().filter { uri -> foreign(context, uri) }
}

private fun foreign(context: Context, uri: Uri): Boolean {
    if (uri.scheme != "content") return false
    val authority = uri.authority ?: return false
    return context.packageManager.resolveContentProvider(authority, 0)?.packageName != context.packageName
}

/**
 * Open [received] at [destination]: Feeds' new post or Chat's choice of chat,
 * in that app's own task, through the nonce-gated link a tapped notification
 * opens. The images are copied first, while the grant to read them that came
 * with the share lasts: it ends with the activity it came to, and the copies
 * outlast the process. Reads files, so it runs off the main thread. Answers
 * whether the app was started.
 */
fun deliver(context: Context, received: Received, destination: Destination): Boolean {
    val images = SharedImages.hold(context, received.images)
    val discard = {
        SharedImages.take(context, images)
        SharedImages.release(context, images)
    }
    val (app, link) = when (destination) {
        Destination.FEED -> "feeds" to feedShareLink(received.text, received.url, images)
        Destination.CHAT -> "chat" to chatShareLink(received.text, received.url, images)
    }
    val component = launcherComponentFor(context, app)
    if (component == null) {
        discard()
        return false
    }
    val store = NonceStore(context)
    val nonce = store.issue()
    // launch-ok: a mochi: URI built here, addressed to this client's own activity
    val intent = Intent(Intent.ACTION_VIEW, notificationUri(link, null, nonce))
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        store.consume(nonce)
        discard()
        false
    } catch (_: SecurityException) {
        store.consume(nonce)
        discard()
        false
    }
}

/** The chooser Mochi's destination shows: Post to a feed, and Send in a chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareChooser(onFeed: () -> Unit, onChat: () -> Unit, onDismiss: () -> Unit) {
    MochiBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ChooserRow(Icons.Outlined.Newspaper, stringResource(R.string.share_feed), onFeed)
            ChooserRow(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.share_chat), onChat)
        }
    }
}

@Composable
private fun ChooserRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
