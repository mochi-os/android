// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Empty by design: [RedirectsTab] drives the shared
 * [org.mochios.wikis.ui.redirects.RedirectsViewModel].
 */
@HiltViewModel
class RedirectsTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
}
