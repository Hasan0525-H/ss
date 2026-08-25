package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val openRouterModelsAPI: OpenRouterModelsAPI,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val platformUid: String =
        checkNotNull(savedStateHandle["platformUid"])

    private val _platformState =
        MutableStateFlow<PlatformV2?>(null)

    val platformState: StateFlow<PlatformV2?> =
        _platformState.asStateFlow()

    private val _dialogState =
        MutableStateFlow(DialogState())

    val dialogState: StateFlow<DialogState> =
        _dialogState.asStateFlow()

    private val _isDeleted =
        MutableStateFlow(false)

    val isDeleted: StateFlow<Boolean> =
        _isDeleted.asStateFlow()

    private val _switchedPlatformEvent =
        MutableSharedFlow<String>()

    val switchedPlatformEvent: SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()

    // --- حالة النماذج المتاحة وحالة التحميل ---
    private val _availableModels =
        MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val availableModels: StateFlow<List<OpenRouterModel>> =
        _availableModels.asStateFlow()

    private val _isLoadingModels =
        MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> =
        _isLoadingModels.asStateFlow()

    private var currentIsFreeFilter: Boolean = true

    init {
        loadPlatform()
    }

    private fun loadPlatform() {
        viewModelScope.launch {
            val platforms =
                settingRepository.fetchPlatformV2s()

            val platform =
                platforms.firstOrNull {
                    it.uid == platformUid
                }

            _platformState.update {
                platform
            }

            // جلب النماذج تلقائياً عند فتح الشاشة في حال وجود توكن
            if (!platform?.token.isNullOrBlank()) {
                loadModels(currentIsFreeFilter)
            }
        }
    }

    // --- دالة جلب وتصفية وترتيب النماذج ---
    fun loadModels(isFreeOnly: Boolean) {
        currentIsFreeFilter = isFreeOnly
        viewModelScope.launch {
            _isLoadingModels.value = true
            try {
                // جلب جميع النماذج من API مع تمرير isFreeOnly كـ false لجلب الكل وتصفيته محلياً
                val models = fetchOpenRouterModels(isFreeOnly = false)
                val filteredAndSorted = if (isFreeOnly) {
                    models.filter { it.pricing?.isFree == true }
                } else {
                    models
                        .filter { it.pricing?.isFree == false }
                        .sortedBy { it.pricing?.averagePrice ?: Double.MAX_VALUE }
                }
                _availableModels.value = filteredAndSorted
            } catch (e: Exception) {
                _availableModels.value = emptyList()
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    suspend fun fetchOpenRouterModels(
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {
        var apiKey =
            _platformState.value?.token

        if (apiKey.isNullOrBlank()) {
            val platforms =
                settingRepository.fetchPlatformV2s()

            apiKey =
                platforms
                    .firstOrNull {
                        it.uid == platformUid
                    }
                    ?.token
        }

        if (apiKey.isNullOrBlank()) {
            return emptyList()
        }

        return openRouterModelsAPI.fetchOpenRouterModels(
            apiKey = apiKey,
            isFreeOnly = isFreeOnly
        )
    }

    fun toggleEnabled() {
        _platformState.value?.let { platform ->
            val enable = !platform.enabled

            if (enable) {
                viewModelScope.launch {
                    val allPlatforms =
                        settingRepository.fetchPlatformV2s()

                    val others =
                        allPlatforms.filter {
                            it.enabled && it.id != platform.id
                        }

                    others.forEach {
                        settingRepository.updatePlatformV2(
                            it.copy(enabled = false)
                        )
                    }

                    val updated =
                        platform.copy(enabled = true)

                    settingRepository.updatePlatformV2(updated)

                    _platformState.update { updated }

                    if (others.isNotEmpty()) {
                        _switchedPlatformEvent.emit(platform.name)
                    }
                }
            } else {
                updatePlatform(
                    platform.copy(enabled = false)
                )
            }
        }
    }

    fun toggleReasoning() {
        _platformState.value?.let {
            updatePlatform(
                it.copy(reasoning = !it.reasoning)
            )
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
            _platformState.update { platform }
        }
    }

    fun updateApiToken(token: String) {
        _platformState.value?.let {
            val newToken = token.trim().takeIf { value -> value.isNotEmpty() }
            updatePlatform(
                it.copy(token = newToken)
            )
            closeApiTokenDialog()
            
            // إعادة جلب النماذج فور إدخال أو تحديث مفتاح API
            if (!newToken.isNullOrBlank()) {
                loadModels(currentIsFreeFilter)
            }
        }
    }

    fun updateApiModel(model: String) {
        _platformState.value?.let {
            updatePlatform(
                it.copy(model = model.trim())
            )
            closeApiModelDialog()
        }
    }

    fun updateApiUrl(url: String) {
        _platformState.value?.let {
            updatePlatform(
                it.copy(apiUrl = url.trim())
            )
            closeApiUrlDialog()
        }
    }

    fun updatePlatformName(name: String) {
        _platformState.value?.let {
            updatePlatform(
                it.copy(name = name.trim())
            )
            closePlatformNameDialog()
        }
    }

    fun updateTemperature(temperature: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(
                platform.copy(temperature = temperature)
            )
            closeTemperatureDialog()
        }
    }

    fun updateTopP(topP: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(
                platform.copy(topP = topP)
            )
            closeTopPDialog()
        }
    }

    fun updateSystemPrompt(prompt: String) {
        _platformState.value?.let { platform ->
            updatePlatform(
                platform.copy(systemPrompt = prompt.trim())
            )
            closeSystemPromptDialog()
        }
    }

    fun openPlatformNameDialog() =
        _dialogState.update { it.copy(isPlatformNameDialogOpen = true) }

    fun closePlatformNameDialog() =
        _dialogState.update { it.copy(isPlatformNameDialogOpen = false) }

    fun openApiUrlDialog() =
        _dialogState.update { it.copy(isApiUrlDialogOpen = true) }

    fun closeApiUrlDialog() =
        _dialogState.update { it.copy(isApiUrlDialogOpen = false) }

    fun openApiTokenDialog() =
        _dialogState.update { it.copy(isApiTokenDialogOpen = true) }

    fun closeApiTokenDialog() =
        _dialogState.update { it.copy(isApiTokenDialogOpen = false) }

    fun openApiModelDialog() =
        _dialogState.update { it.copy(isApiModelDialogOpen = true) }

    fun closeApiModelDialog() =
        _dialogState.update { it.copy(isApiModelDialogOpen = false) }

    fun openTemperatureDialog() =
        _dialogState.update { it.copy(isTemperatureDialogOpen = true) }

    fun closeTemperatureDialog() =
        _dialogState.update { it.copy(isTemperatureDialogOpen = false) }

    fun openTopPDialog() =
        _dialogState.update { it.copy(isTopPDialogOpen = true) }

    fun closeTopPDialog() =
        _dialogState.update { it.copy(isTopPDialogOpen = false) }

    fun openSystemPromptDialog() =
        _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }

    fun closeSystemPromptDialog() =
        _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }

    fun openDeleteDialog() =
        _dialogState.update { it.copy(isDeleteDialogOpen = true) }

    fun closeDeleteDialog() =
        _dialogState.update { it.copy(isDeleteDialogOpen = false) }

    fun deletePlatform() {
        _platformState.value?.let { platform ->
            viewModelScope.launch {
                settingRepository.deletePlatformV2(platform)
                closeDeleteDialog()
                _isDeleted.update { true }
            }
        }
    }

    data class DialogState(
        val isPlatformNameDialogOpen: Boolean = false,
        val isApiUrlDialogOpen: Boolean = false,
        val isApiTokenDialogOpen: Boolean = false,
        val isApiModelDialogOpen: Boolean = false,
        val isTemperatureDialogOpen: Boolean = false,
        val isTopPDialogOpen: Boolean = false,
        val isSystemPromptDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false
    )
}
