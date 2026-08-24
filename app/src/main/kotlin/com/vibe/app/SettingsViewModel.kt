package com.vibe.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.network.OpenRouterModelsAPI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val openRouterModelsAPI: OpenRouterModelsAPI
) : ViewModel() {

    private val _models = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val models: StateFlow<List<OpenRouterModel>> = _models.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isFreeFilter = MutableStateFlow(true)
    val isFreeFilter: StateFlow<Boolean> = _isFreeFilter.asStateFlow()

    fun fetchModels(apiKey: String, isFreeOnly: Boolean) {
        _isFreeFilter.value = isFreeOnly
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedModels = openRouterModelsAPI.fetchOpenRouterModels(apiKey, isFreeOnly)
            _models.value = fetchedModels
            _isLoading.value = false
        }
    }
}
