package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    // حالة الحوارات (Dialog States)
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

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    // حالة بيانات المنصة الحالية
    // (يتم جلبها من المستودع أو كود المنصة الفعلي لديك)
    private val _platformState = MutableStateFlow<PlatformData?>(null)
    val platformState: StateFlow<PlatformData?> = _platformState.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    private val _switchedPlatformEvent = MutableSharedFlow<String>()
    val switchedPlatformEvent: SharedFlow<String> = _switchedPlatformEvent.asSharedFlow()

    // جلب نماذج OpenRouter بناءً على الفلتر (مجاني أو مدفوع)
    suspend fun fetchOpenRouterModels(isFreeOnly: Boolean): List<OpenRouterModel> {
        return try {
            // استبدل هذا الاستدعاء بالدالة الموجودة لديك في الـ Repository لجلب النماذج
            settingRepository.getOpenRouterModels(isFreeOnly)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // دوال فتح وإغلاق الحوارات
    fun openPlatformNameDialog() { _dialogState.update { it.copy(isPlatformNameDialogOpen = true) } }
    fun closePlatformNameDialog() { _dialogState.update { it.copy(isPlatformNameDialogOpen = false) } }

    fun openApiUrlDialog() { _dialogState.update { it.copy(isApiUrlDialogOpen = true) } }
    fun closeApiUrlDialog() { _dialogState.update { it.copy(isApiUrlDialogOpen = false) } }

    fun openApiTokenDialog() { _dialogState.update { it.copy(isApiTokenDialogOpen = true) } }
    fun closeApiTokenDialog() { _dialogState.update { it.copy(isApiTokenDialogOpen = false) } }

    fun openApiModelDialog() { _dialogState.update { it.copy(isApiModelDialogOpen = true) } }
    fun closeApiModelDialog() { _dialogState.update { it.copy(isApiModelDialogOpen = false) } }

    fun openTemperatureDialog() { _dialogState.update { it.copy(isTemperatureDialogOpen = true) } }
    fun closeTemperatureDialog() { _dialogState.update { it.copy(isTemperatureDialogOpen = false) } }

    fun openTopPDialog() { _dialogState.update { it.copy(isTopPDialogOpen = true) } }
    fun closeTopPDialog() { _dialogState.update { it.copy(isTopPDialogOpen = false) } }

    fun openSystemPromptDialog() { _dialogState.update { it.copy(isSystemPromptDialogOpen = true) } }
    fun closeSystemPromptDialog() { _dialogState.update { it.copy(isSystemPromptDialogOpen = false) } }

    fun openDeleteDialog() { _dialogState.update { it.copy(isDeleteDialogOpen = true) } }
    fun closeDeleteDialog() { _dialogState.update { it.copy(isDeleteDialogOpen = false) } }

    // دوال تحديث البيانات
    fun toggleEnabled() {
        _platformState.update { current ->
            current?.copy(enabled = !current.enabled)
        }
    }

    fun updatePlatformName(name: String) {
        _platformState.update { it?.copy(name = name) }
        closePlatformNameDialog()
    }

    fun updateApiUrl(url: String) {
        _platformState.update { it?.copy(apiUrl = url) }
        closeApiUrlDialog()
    }

    fun updateApiToken(token: String) {
        _platformState.update { it?.copy(token = token) }
        closeApiTokenDialog()
    }

    fun updateApiModel(model: String) {
        _platformState.update { it?.copy(model = model) }
        closeApiModelDialog()
    }

    fun updateTemperature(temp: Float?) {
        _platformState.update { it?.copy(temperature = temp) }
        closeTemperatureDialog()
    }

    fun updateTopP(topP: Float?) {
        _platformState.update { it?.copy(topP = topP) }
        closeTopPDialog()
    }

    fun updateSystemPrompt(prompt: String) {
        _platformState.update { it?.copy(systemPrompt = prompt) }
        closeSystemPromptDialog()
    }

    fun deletePlatform() {
        _isDeleted.value = true
        closeDeleteDialog()
    }
}
