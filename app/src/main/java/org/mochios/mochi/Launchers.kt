// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.mochi

import dagger.hilt.android.AndroidEntryPoint

// One activity class per launcher icon. Each is MainActivity under another
// name so its manifest entry can carry its own task affinity - an
// <activity-alias> cannot, it inherits its target's - which gives every Mochi
// app a task of its own: a tap on its icon resumes that task behind that
// task's snapshot, or starts one behind the splash, and never shows another
// app's last frame. The class name is the launcher component other code
// addresses: notification badges, the staff icon toggle, pinned shortcuts.

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
