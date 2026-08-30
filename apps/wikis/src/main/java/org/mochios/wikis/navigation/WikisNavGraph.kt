// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.wikis.ui.attachments.AttachmentsScreen
import org.mochios.wikis.ui.changes.ChangesListScreen
import org.mochios.wikis.ui.comments.CommentsScreen
import org.mochios.wikis.ui.editor.PageEditorScreen
import org.mochios.wikis.ui.find.FindWikisScreen
import org.mochios.wikis.ui.history.PageHistoryScreen
import org.mochios.wikis.ui.history.RevisionViewScreen
import org.mochios.wikis.ui.join.JoinWikiScreen
import org.mochios.wikis.ui.list.CreateWikiScreen
import org.mochios.wikis.ui.list.WikiListScreen
import org.mochios.wikis.ui.page.PageViewScreen
import org.mochios.wikis.ui.page.WikiHomeScreen
import org.mochios.wikis.ui.redirects.RedirectsScreen
import org.mochios.wikis.ui.search.SearchScreen
import org.mochios.wikis.ui.settings.WikiSettingsScreen
import org.mochios.wikis.ui.tags.TagPagesScreen
import org.mochios.wikis.ui.tags.TagsListScreen

object WikisApp {
    // ---- Class-level routes (no wikiId) ----
    const val HOME = "wikis"
    const val FIND = "wikis/find"
    const val JOIN = "wikis/join"
    const val CREATE = "wikis/create"

    // ---- Builders for entity-context routes ----
    fun wikiHome(wikiId: String) = "wikis/$wikiId"
    fun pageView(wikiId: String, page: String) = "wikis/$wikiId/$page"
    fun pageEdit(wikiId: String, page: String) = "wikis/$wikiId/$page/edit"
    fun newPage(wikiId: String, slug: String? = null) =
        if (slug.isNullOrBlank()) "wikis/$wikiId/new" else "wikis/$wikiId/new?slug=$slug"
    fun pageHistory(wikiId: String, page: String) = "wikis/$wikiId/$page/history"
    fun pageRevision(wikiId: String, page: String, version: Int) =
        "wikis/$wikiId/$page/history/$version"
    fun comments(wikiId: String, page: String) = "wikis/$wikiId/$page/comments"
    fun settings(wikiId: String, tab: String = "settings") =
        "wikis/$wikiId/settings?tab=$tab"
    fun redirects(wikiId: String) = "wikis/$wikiId/redirects"
    fun tags(wikiId: String) = "wikis/$wikiId/tags"
    fun tagPages(wikiId: String, tag: String) = "wikis/$wikiId/tag/$tag"
    fun search(wikiId: String, q: String? = null) =
        if (q.isNullOrBlank()) "wikis/$wikiId/search" else "wikis/$wikiId/search?q=$q"
    fun changes(wikiId: String) = "wikis/$wikiId/changes"
    fun attachments(wikiId: String, page: String) = "wikis/$wikiId/$page/attachments"

    // ---- Pattern constants for composable() route definitions ----
    const val WIKI_HOME = "wikis/{wikiId}"
    const val PAGE_VIEW = "wikis/{wikiId}/{page}"
    const val PAGE_EDIT = "wikis/{wikiId}/{page}/edit"
    const val NEW_PAGE = "wikis/{wikiId}/new?slug={slug}"
    const val PAGE_HISTORY = "wikis/{wikiId}/{page}/history"
    const val PAGE_REVISION = "wikis/{wikiId}/{page}/history/{version}"
    const val COMMENTS = "wikis/{wikiId}/{page}/comments"
    const val SETTINGS = "wikis/{wikiId}/settings?tab={tab}"
    const val REDIRECTS = "wikis/{wikiId}/redirects"
    const val TAGS = "wikis/{wikiId}/tags"
    const val TAG_PAGES = "wikis/{wikiId}/tag/{tag}"
    const val SEARCH = "wikis/{wikiId}/search?q={q}"
    const val CHANGES = "wikis/{wikiId}/changes"
    const val ATTACHMENTS = "wikis/{wikiId}/{page}/attachments"
}

fun NavGraphBuilder.wikisNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    composable(WikisApp.HOME) {
        WikiListScreen(navController)
    }

    composable(WikisApp.FIND) {
        FindWikisScreen(
            onBack = { navController.popBackStack() },
            onSubscribed = { wikiId ->
                navController.navigate(WikisApp.wikiHome(wikiId)) {
                    popUpTo(WikisApp.HOME)
                }
            },
        )
    }

    composable(WikisApp.CREATE) {
        CreateWikiScreen(
            onBack = { navController.popBackStack() },
            // Straight into the editor for the new wiki's home page. A wiki
            // is created empty, so opening its home would land on "page not
            // found" and make the first act of a new wiki a dead end. Drop the
            // create screen on the way, so Back from the editor returns to the
            // wiki list rather than the form.
            onCreated = { wikiId, home ->
                navController.navigate(WikisApp.newPage(wikiId, home)) {
                    popUpTo(WikisApp.CREATE) { inclusive = true }
                }
            },
        )
    }

    composable(WikisApp.JOIN) {
        JoinWikiScreen(
            onBack = { navController.popBackStack() },
            onSearch = { navController.navigate(WikisApp.FIND) },
            onJoined = { wikiId ->
                navController.navigate(WikisApp.wikiHome(wikiId)) {
                    popUpTo(WikisApp.HOME)
                }
            },
        )
    }

    composable(
        route = WikisApp.WIKI_HOME,
        arguments = listOf(navArgument("wikiId") { type = NavType.StringType }),
    ) {
        WikiHomeScreen(navController)
    }

    composable(
        route = WikisApp.PAGE_VIEW,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("page") { type = NavType.StringType },
        ),
    ) {
        PageViewScreen(navController)
    }

    composable(
        route = WikisApp.PAGE_EDIT,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("page") { type = NavType.StringType },
        ),
    ) {
        PageEditorScreen(navController)
    }

    composable(
        route = WikisApp.NEW_PAGE,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            // A suggested slug the editor seeds its field with — the wiki's
            // home when this is the wiki's first page. Empty for a plain
            // "New page", where the user names it themselves.
            navArgument("slug") {
                type = NavType.StringType
                defaultValue = ""
                nullable = false
            },
        ),
    ) {
        PageEditorScreen(navController)
    }

    composable(
        route = WikisApp.PAGE_HISTORY,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("page") { type = NavType.StringType },
        ),
    ) {
        PageHistoryScreen(navController)
    }

    composable(
        route = WikisApp.PAGE_REVISION,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("page") { type = NavType.StringType },
            navArgument("version") { type = NavType.IntType },
        ),
    ) {
        RevisionViewScreen(navController)
    }

    composable(
        route = WikisApp.COMMENTS,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("page") { type = NavType.StringType },
        ),
    ) {
        CommentsScreen(navController)
    }

    composable(
        route = WikisApp.SETTINGS,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("tab") {
                type = NavType.StringType
                defaultValue = "settings"
                nullable = false
            },
        ),
    ) {
        WikiSettingsScreen(navController)
    }

    composable(
        route = WikisApp.REDIRECTS,
        arguments = listOf(navArgument("wikiId") { type = NavType.StringType }),
    ) {
        RedirectsScreen(navController)
    }

    composable(
        route = WikisApp.TAGS,
        arguments = listOf(navArgument("wikiId") { type = NavType.StringType }),
    ) {
        TagsListScreen(navController)
    }

    composable(
        route = WikisApp.TAG_PAGES,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("tag") { type = NavType.StringType },
        ),
    ) {
        TagPagesScreen(navController)
    }

    composable(
        route = WikisApp.SEARCH,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("q") {
                type = NavType.StringType
                defaultValue = ""
                nullable = false
            },
        ),
    ) {
        SearchScreen(navController)
    }

    composable(
        route = WikisApp.CHANGES,
        arguments = listOf(navArgument("wikiId") { type = NavType.StringType }),
    ) {
        ChangesListScreen(navController)
    }

    composable(
        route = WikisApp.ATTACHMENTS,
        arguments = listOf(
            navArgument("wikiId") { type = NavType.StringType },
            navArgument("page") { type = NavType.StringType },
        ),
    ) {
        AttachmentsScreen(navController)
    }
}

