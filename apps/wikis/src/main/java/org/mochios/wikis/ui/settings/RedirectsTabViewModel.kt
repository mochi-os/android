// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Marker view model for the Redirects tab. The standalone
 * [org.mochios.wikis.ui.redirects.RedirectsViewModel] already owns the
 * redirects list + create/delete flows, and the [RedirectsTab] composable
 * embeds the shared `RedirectsBody` directly — there's no additional
 * tab-local state to keep here.
 *
 * This class exists so the tab follows the same `<Tab>` / `<Tab>ViewModel`
 * pairing as the other tabs and so callers can inject Hilt-scoped tab
 * state into it later (e.g. analytics or per-tab UI preferences) without
 * refactoring the tab signature.
 */
@HiltViewModel
class RedirectsTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
}
