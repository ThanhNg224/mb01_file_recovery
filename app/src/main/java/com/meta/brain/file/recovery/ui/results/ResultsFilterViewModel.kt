package com.meta.brain.file.recovery.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ResultsFilterViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _filterSpec = MutableStateFlow(
        savedStateHandle.get<ResultsFilterSpec>(KEY_FILTER_SPEC) ?: ResultsFilterSpec.DEFAULT
    )
    val filterSpec: StateFlow<ResultsFilterSpec> = _filterSpec.asStateFlow()

    fun updateFilterSpec(spec: ResultsFilterSpec) {
        _filterSpec.value = spec
        savedStateHandle[KEY_FILTER_SPEC] = spec
    }

    fun resetToDefaults() {
        updateFilterSpec(ResultsFilterSpec.DEFAULT)
    }

    companion object {
        private const val KEY_FILTER_SPEC = "filter_spec"
    }
}

