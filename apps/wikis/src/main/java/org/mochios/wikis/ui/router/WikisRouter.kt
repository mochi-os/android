// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.router

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.wikis.navigation.WikisApp

/** [LastViewedStore] key for the wiki last viewed. */
const val WIKIS_FEATURE = "wikis"

/** [LastViewedStore] key for the page last viewed in wiki [wikiId]. */
fun wikiPageFeature(wikiId: String): String = "${WIKIS_FEATURE}_page_$wikiId"

/**
 * Start destination: resolves the page last viewed, as the web reopens it,
 * or null when the wiki list was the last thing shown; the spinner masks the
 * one-frame handoff.
 */
@Composable
fun WikisRouter(onResolve: (page: String?) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onResolve(lastWikiPage(context))
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * The route of the page last viewed: the wiki's own last page, or its home
 * page through the resolver when none was kept. Null when no wiki is kept.
 */
internal fun lastWikiPage(context: Context): String? {
    val wiki = LastViewedStore.get(context, WIKIS_FEATURE) ?: return null
    val page = LastViewedStore.get(context, wikiPageFeature(wiki))
    return if (page == null) WikisApp.wikiHome(wiki) else WikisApp.pageView(wiki, page)
}

/** Records page [slug] of wiki [wikiId] as the one the next launch reopens. */
@Composable
fun RememberWikiPage(wikiId: String, slug: String) {
    val context = LocalContext.current
    LaunchedEffect(wikiId, slug) {
        if (wikiId.isNotBlank() && slug.isNotBlank()) {
            LastViewedStore.set(context, WIKIS_FEATURE, wikiId)
            LastViewedStore.set(context, wikiPageFeature(wikiId), slug)
        }
    }
}

/**
 * Records the wiki list as what the next launch opens, as the web's all-wikis
 * view does. On resume, not on composition: a router or a link passes through
 * the list on its way to a page, composing it for a frame without showing it.
 */
@Composable
fun RememberWikiList() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) LastViewedStore.clear(context, WIKIS_FEATURE)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
