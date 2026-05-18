package org.mochios.crm.ui.design

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.Template
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

data class DesignUiState(
    val crmDetails: CrmDetails? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val selectedClassId: String? = null,
    val selectedFieldId: String? = null,
    val isSaving: Boolean = false,
    val exportedJson: String? = null,
    val templates: List<Template> = emptyList(),
    val isLoadingTemplates: Boolean = false,
    val importSuccess: Boolean = false
)

@HiltViewModel
class DesignViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository
) : ViewModel() {

    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    private val _uiState = MutableStateFlow(DesignUiState())
    val uiState: StateFlow<DesignUiState> = _uiState.asStateFlow()

    init {
        loadCrm()
    }

    fun loadCrm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val details = repository.getCrmInfo(crmId)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val details = repository.getCrmInfo(crmId)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun selectClass(classId: String?) {
        _uiState.value = _uiState.value.copy(selectedClassId = classId, selectedFieldId = null)
    }

    fun selectField(fieldId: String?) {
        _uiState.value = _uiState.value.copy(selectedFieldId = fieldId)
    }

    // ---- Classes ----

    fun createClass(name: String) {
        viewModelScope.launch {
            try {
                repository.createClass(crmId, name)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateClass(classId: String, name: String? = null, title: String? = null) {
        viewModelScope.launch {
            try {
                repository.updateClass(crmId, classId, name, title)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            try {
                repository.deleteClass(crmId, classId)
                if (_uiState.value.selectedClassId == classId) {
                    _uiState.value = _uiState.value.copy(selectedClassId = null)
                }
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Hierarchy ----

    fun setHierarchy(classId: String, parents: List<String>) {
        viewModelScope.launch {
            try {
                repository.setHierarchy(crmId, classId, parents.joinToString(","))
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Fields ----

    fun createField(classId: String, name: String, fieldtype: String, flags: String?, multi: Boolean?) {
        viewModelScope.launch {
            try {
                repository.createField(crmId, classId, name, fieldtype, flags, multi)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateField(
        classId: String,
        fieldId: String,
        name: String?,
        fieldtype: String?,
        flags: String?,
        multi: Boolean?,
        card: Boolean?,
        position: String?,
        rows: Int?
    ) {
        viewModelScope.launch {
            try {
                repository.updateField(crmId, classId, fieldId, name, fieldtype, flags, multi, card, position, rows)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteField(classId: String, fieldId: String) {
        viewModelScope.launch {
            try {
                repository.deleteField(crmId, classId, fieldId)
                if (_uiState.value.selectedFieldId == fieldId) {
                    _uiState.value = _uiState.value.copy(selectedFieldId = null)
                }
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reorderFields(classId: String, order: String) {
        viewModelScope.launch {
            try {
                repository.reorderFields(crmId, classId, order)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Options ----

    fun createOption(classId: String, fieldId: String, name: String, colour: String?, icon: String? = null) {
        viewModelScope.launch {
            try {
                repository.createOption(crmId, classId, fieldId, name, colour, icon)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateOption(classId: String, fieldId: String, optionId: String, name: String?, colour: String?, icon: String?) {
        viewModelScope.launch {
            try {
                repository.updateOption(crmId, classId, fieldId, optionId, name, colour, icon)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteOption(classId: String, fieldId: String, optionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteOption(crmId, classId, fieldId, optionId)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reorderOptions(classId: String, fieldId: String, order: String) {
        viewModelScope.launch {
            try {
                repository.reorderOptions(crmId, classId, fieldId, order)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Views ----

    fun createView(
        name: String,
        viewtype: String,
        columns: String?,
        rows: String?,
        filter: String?,
        sort: String?,
        direction: String?,
        classes: String?,
        border: String?
    ) {
        viewModelScope.launch {
            try {
                repository.createView(crmId, name, viewtype, columns, rows, filter, sort, direction, classes, border)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateView(
        viewId: String,
        name: String?,
        viewtype: String?,
        columns: String?,
        rows: String?,
        filter: String?,
        sort: String?,
        direction: String?,
        classes: String?,
        border: String?
    ) {
        viewModelScope.launch {
            try {
                repository.updateView(crmId, viewId, name, viewtype, columns, rows, filter, sort, direction, classes, border)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteView(viewId: String) {
        viewModelScope.launch {
            try {
                repository.deleteView(crmId, viewId)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reorderViews(order: String) {
        viewModelScope.launch {
            try {
                repository.reorderViews(crmId, order)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Design Export / Import ----

    fun exportDesign() {
        viewModelScope.launch {
            try {
                val json = repository.exportDesign(crmId)
                _uiState.value = _uiState.value.copy(exportedJson = json.toString())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearExportedJson() {
        _uiState.value = _uiState.value.copy(exportedJson = null)
    }

    fun loadTemplates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTemplates = true)
            try {
                val templates = repository.getTemplates()
                _uiState.value = _uiState.value.copy(
                    templates = templates,
                    isLoadingTemplates = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingTemplates = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun importFromTemplate(templateId: String, templateVersion: Int) {
        viewModelScope.launch {
            try {
                repository.importDesign(crmId, template = templateId, templateVersion = templateVersion)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun importFromJson(jsonText: String) {
        viewModelScope.launch {
            try {
                repository.importDesign(crmId, data = jsonText)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearImportSuccess() {
        _uiState.value = _uiState.value.copy(importSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
