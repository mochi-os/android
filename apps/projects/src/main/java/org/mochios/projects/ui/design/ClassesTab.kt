// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.ClassListItem
import org.mochios.android.ui.components.ClassListLabels
import org.mochios.android.ui.components.ClassListTab
import org.mochios.projects.R
import org.mochios.projects.model.ProjectClass

@Composable
fun ClassesTab(
    classes: List<ProjectClass>,
    viewModel: DesignViewModel,
    onClassClick: (String) -> Unit
) {
    ClassListTab(
        classes = classes.map { cls -> ClassListItem(cls.id, cls.name, cls.rank) },
        labels = ClassListLabels(
            empty = stringResource(R.string.projects_classes_empty),
            addAction = stringResource(R.string.projects_classes_add),
            addDialogTitle = stringResource(R.string.projects_classes_add_dialog_title),
            nameLabel = stringResource(R.string.projects_class_name),
            createAction = stringResource(R.string.projects_classes_create)
        ),
        onCreateClass = { name -> viewModel.createClass(name) },
        onClassClick = onClassClick
    )
}
