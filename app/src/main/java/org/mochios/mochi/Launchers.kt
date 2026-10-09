// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.mochi

import dagger.hilt.android.AndroidEntryPoint

// One activity class per Mochi app. Each is MainActivity under another name
// so its manifest entry can carry its own task affinity - an <activity-alias>
// cannot, it inherits its target's - which gives every app a task of its own:
// opening it resumes that task behind that task's snapshot, or starts one
// behind the splash, and never shows another app's last frame. Only
// MochiHomeLauncher is a launcher entry; the others are opened from the home
// grid, notifications, forwarded links and pinned shortcuts, all of which
// address them by class name (lib's LAUNCHER_ACTIVITIES).

@AndroidEntryPoint class MochiHomeLauncher : MainActivity()
@AndroidEntryPoint class MochiFeedsLauncher : MainActivity()
@AndroidEntryPoint class MochiChatLauncher : MainActivity()
@AndroidEntryPoint class MochiForumsLauncher : MainActivity()
@AndroidEntryPoint class MochiProjectsLauncher : MainActivity()
@AndroidEntryPoint class MochiCrmLauncher : MainActivity()
@AndroidEntryPoint class MochiPeopleLauncher : MainActivity()
@AndroidEntryPoint class MochiSettingsLauncher : MainActivity()
@AndroidEntryPoint class MochiWikisLauncher : MainActivity()
@AndroidEntryPoint class MochiChessLauncher : MainActivity()
@AndroidEntryPoint class MochiGoLauncher : MainActivity()
@AndroidEntryPoint class MochiWordsLauncher : MainActivity()
@AndroidEntryPoint class MochiMarketLauncher : MainActivity()
@AndroidEntryPoint class MochiStaffLauncher : MainActivity()
@AndroidEntryPoint class MochiCalendarsLauncher : MainActivity()
