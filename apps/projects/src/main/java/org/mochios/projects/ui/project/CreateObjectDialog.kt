package org.mochios.projects.ui.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.projects.R
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateObjectDialog(
    classes: List<ProjectClass>,
    /** className/parentClassIds map from [ProjectDetails.hierarchy]. */
    hierarchy: Map<String, List<String>>,
    /** All objects in the project — used to populate the parent picker. */
    objects: List<ProjectObject>,
    /**
     * When non-null, the dialog opens with this object as the pre-selected
     * parent (and the class auto-set to a class that allows that parent).
     * Set by `viewModel.showCreateObjectDialog(parent = ...)` from an
     * "Add child" affordance on an existing object.
     */
    presetParent: String?,
    isCreating: Boolean,
    activeView: ProjectView?,
    viewModel: ProjectViewModel,
    onDismiss: () -> Unit,
    onCreate: (classId: String, title: String, parent: String?, initialValues: Map<String, String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val presetParentObj = remember(presetParent, objects) {
        presetParent?.let { p -> objects.firstOrNull { it.id == p } }
    }
    val initialClassId = remember(presetParentObj, activeView, classes, hierarchy) {
        when {
            // If pre-selected from "Add child", pick a class that permits
            // the parent's class as a parent (first match).
            presetParentObj != null -> {
                classes.firstOrNull { cls ->
                    (hierarchy[cls.id] ?: emptyList()).contains(presetParentObj.objectClass)
                }?.id ?: classes.firstOrNull()?.id ?: ""
            }
            activeView != null && activeView.classes.isNotEmpty() -> activeView.classes.first()
            else -> classes.firstOrNull()?.id ?: ""
        }
    }
    var selectedClassId by remember { mutableStateOf(initialClassId) }
    var classExpanded by remember { mutableStateOf(false) }

    // Parent picker state. Derived from the selected class's allowed
    // parent classes (hierarchy[selectedClassId]) intersected with the
    // project's existing objects.
    val allowedParentClasses = hierarchy[selectedClassId] ?: emptyList()
    val parentCandidates = remember(objects, allowedParentClasses) {
        if (allowedParentClasses.isEmpty()) emptyList()
        else objects.filter { it.objectClass in allowedParentClasses }
    }
    var selectedParentId by remember(initialClassId) {
        mutableStateOf(presetParent.takeIf { presetParentObj != null })
    }
    var parentExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.projects_create_object_title)) },
        text = {
            Column {
                if (classes.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = classExpanded,
                        onExpandedChange = { classExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = classes.find { it.id == selectedClassId }?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.projects_create_object_type)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = classExpanded,
                            onDismissRequest = { classExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.name) },
                                    onClick = {
                                        selectedClassId = cls.id
                                        // Reset parent when class changes; old
                                        // selection may no longer be allowed.
                                        selectedParentId = null
                                        classExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Parent picker — only shown when the selected class has
                // allowed parent classes per project.hierarchy. "None" is
                // always an option so root-level objects can still be
                // created from the dialog.
                if (parentCandidates.isNotEmpty()) {
                    val selectedParentLabel = selectedParentId?.let { id ->
                        objects.firstOrNull { it.id == id }?.let { o ->
                            o.readable.ifBlank { o.id }
                        } ?: id
                    } ?: stringResource(R.string.projects_create_object_parent_none)
                    ExposedDropdownMenuBox(
                        expanded = parentExpanded,
                        onExpandedChange = { parentExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedParentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.projects_create_object_parent)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = parentExpanded,
                            onDismissRequest = { parentExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_create_object_parent_none)) },
                                onClick = {
                                    selectedParentId = null
                                    parentExpanded = false
                                }
                            )
                            parentCandidates.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.readable.ifBlank { p.id }) },
                                    onClick = {
                                        selectedParentId = p.id
                                        parentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.projects_create_object_title_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val initialValues = mutableMapOf<String, String>()
                    // Pre-fill column value from current view context if board view
                    if (activeView?.viewtype == "board" && activeView.columns.isNotBlank()) {
                        val options = viewModel.getAllOptionsForField(activeView.columns)
                        if (options.isNotEmpty()) {
                            initialValues[activeView.columns] = options.first().id
                        }
                    }
                    onCreate(selectedClassId, title, selectedParentId, initialValues)
                },
                enabled = title.isNotBlank() && selectedClassId.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.projects_create_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
