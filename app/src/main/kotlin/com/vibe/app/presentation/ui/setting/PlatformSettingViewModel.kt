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
        checkNotNull(
            savedStateHandle["platformUid"]
        )

    /*
     * Platform state
     */
    private val _platformState =
        MutableStateFlow<PlatformV2?>(null)

    val platformState: StateFlow<PlatformV2?> =
        _platformState.asStateFlow()

    /*
     * Dialog state
     */
    private val _dialogState =
        MutableStateFlow(DialogState())

    val dialogState: StateFlow<DialogState> =
        _dialogState.asStateFlow()

    /*
     * Delete state
     */
    private val _isDeleted =
        MutableStateFlow(false)

    val isDeleted: StateFlow<Boolean> =
        _isDeleted.asStateFlow()

    /*
     * Platform switching event
     */
    private val _switchedPlatformEvent =
        MutableSharedFlow<String>()

    val switchedPlatformEvent: SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()

    /*
     * OpenRouter models
     */
    private val _availableModels =
        MutableStateFlow<List<OpenRouterModel>>(
            emptyList()
        )

    val availableModels: StateFlow<List<OpenRouterModel>> =
        _availableModels.asStateFlow()

    /*
     * Loading state
     */
    private val _isLoadingModels =
        MutableStateFlow(false)

    val isLoadingModels: StateFlow<Boolean> =
        _isLoadingModels.asStateFlow()

    /*
     * Current Free / Paid filter
     */
    private var currentIsFreeFilter = true

    init {
        loadPlatform()
    }

    /*
     * Load the platform from the database.
     */
    private fun loadPlatform() {
        viewModelScope.launch {

            try {

                val platforms =
                    settingRepository.fetchPlatformV2s()

                val platform =
                    platforms.firstOrNull {
                        it.uid == platformUid
                    }

                _platformState.value = platform

                if (!platform?.token.isNullOrBlank()) {
                    loadModels(
                        isFreeOnly = currentIsFreeFilter
                    )
                }

            } catch (_: Exception) {

                _platformState.value = null
            }
        }
    }

    /*
     * Load models from OpenRouter.
     */
    fun loadModels(
        isFreeOnly: Boolean
    ) {
        currentIsFreeFilter = isFreeOnly

        viewModelScope.launch {

            _isLoadingModels.value = true

            try {

                val models =
                    fetchOpenRouterModels(
                        isFreeOnly = isFreeOnly
                    )

                _availableModels.value = models

            } catch (_: Exception) {

                _availableModels.value =
                    emptyList()

            } finally {

                _isLoadingModels.value = false
            }
        }
    }

    /*
     * Fetch models using the current platform API key.
     */
    suspend fun fetchOpenRouterModels(
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {

        var apiKey =
            _platformState.value?.token

        /*
         * If the state has not been updated yet,
         * fetch the platform directly from the repository.
         */
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

        return openRouterModelsAPI
            .fetchOpenRouterModels(
                apiKey = apiKey,
                isFreeOnly = isFreeOnly
            )
    }

    /*
     * Enable / disable the platform.
     *
     * Only one platform can be enabled at a time.
     */
    fun toggleEnabled() {

        val platform =
            _platformState.value
                ?: return

        val enable =
            !platform.enabled

        if (!enable) {

            updatePlatform(
                platform.copy(
                    enabled = false
                )
            )

            return
        }

        viewModelScope.launch {

            try {

                val allPlatforms =
                    settingRepository
                        .fetchPlatformV2s()

                val otherEnabledPlatforms =
                    allPlatforms.filter {
                        it.enabled &&
                            it.id != platform.id
                    }

                /*
                 * Disable all other platforms.
                 */
                otherEnabledPlatforms.forEach {
                    settingRepository
                        .updatePlatformV2(
                            it.copy(
                                enabled = false
                            )
                        )
                }

                /*
                 * Enable the selected platform.
                 */
                val updatedPlatform =
                    platform.copy(
                        enabled = true
                    )

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.update {
                    updatedPlatform
                }

                /*
                 * Notify the UI if another platform
                 * was disabled.
                 */
                if (otherEnabledPlatforms.isNotEmpty()) {

                    _switchedPlatformEvent.emit(
                        platform.name
                    )
                }

            } catch (_: Exception) {
                // Keep the current state if the update fails.
            }
        }
    }

    /*
     * Enable / disable reasoning.
     */
    fun toggleReasoning() {

        _platformState.value?.let { platform ->

            updatePlatform(
                platform.copy(
                    reasoning = !platform.reasoning
                )
            )
        }
    }

    /*
     * Update complete platform object.
     */
    fun updatePlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        platform
                    )

                _platformState.update {
                    platform
                }

            } catch (_: Exception) {
                // Keep the current UI state if persistence fails.
            }
        }
    }

    /*
     * API Key
     */
    fun updateApiToken(
        token: String
    ) {
        _platformState.value?.let { platform ->

            val newToken =
                token
                    .trim()
                    .takeIf {
                        it.isNotEmpty()
                    }

            updatePlatform(
                platform.copy(
                    token = newToken
                )
            )

            closeApiTokenDialog()

            if (!newToken.isNullOrBlank()) {

                loadModels(
                    currentIsFreeFilter
                )

            } else {

                _availableModels.value =
                    emptyList()
            }
        }
    }

    /*
     * API Model
     */
    fun updateApiModel(
        model: String
    ) {
        _platformState.value?.let { platform ->

            updatePlatform(
                platform.copy(
                    model = model.trim()
                )
            )

            closeApiModelDialog()
        }
    }

    /*
     * API URL
     */
    fun updateApiUrl(
        url: String
    ) {
        _platformState.value?.let { platform ->

            updatePlatform(
                platform.copy(
                    apiUrl = url.trim()
                )
            )

            closeApiUrlDialog()
        }
    }

    /*
     * Platform name
     */
    fun updatePlatformName(
        name: String
    ) {
        _platformState.value?.let { platform ->

            val cleanedName =
                name.trim()

            if (cleanedName.isEmpty()) {
                return
            }

            updatePlatform(
                platform.copy(
                    name = cleanedName
                )
            )

            closePlatformNameDialog()
        }
    }

    /*
     * Temperature
     *
     * Allowed range:
     * 0.0 .. 2.0
     */
    fun updateTemperature(
        temperature: Float?
    ) {
        _platformState.value?.let { platform ->

            val normalizedTemperature =
                temperature?.coerceIn(
                    0f,
                    2f
                )

            updatePlatform(
                platform.copy(
                    temperature =
                        normalizedTemperature
                )
            )

            closeTemperatureDialog()
        }
    }

    /*
     * Top P
     *
     * Allowed range:
     * 0.1 .. 1.0
     */
    fun updateTopP(
        topP: Float?
    ) {
        _platformState.value?.let { platform ->

            val normalizedTopP =
                topP?.coerceIn(
                    0.1f,
                    1f
                )

            updatePlatform(
                platform.copy(
                    topP = normalizedTopP
                )
            )

            closeTopPDialog()
        }
    }

    /*
     * System prompt
     */
    fun updateSystemPrompt(
        prompt: String
    ) {
        _platformState.value?.let { platform ->

            updatePlatform(
                platform.copy(
                    systemPrompt =
                        prompt.trim()
                )
            )

            closeSystemPromptDialog()
        }
    }

    /*
     * Platform name dialog
     */
    fun openPlatformNameDialog() {
        _dialogState.update {
            it.copy(
                isPlatformNameDialogOpen = true
            )
        }
    }

    fun closePlatformNameDialog() {
        _dialogState.update {
            it.copy(
                isPlatformNameDialogOpen = false
            )
        }
    }

    /*
     * API URL dialog
     */
    fun openApiUrlDialog() {
        _dialogState.update {
            it.copy(
                isApiUrlDialogOpen = true
            )
        }
    }

    fun closeApiUrlDialog() {
        _dialogState.update {
            it.copy(
                isApiUrlDialogOpen = false
            )
        }
    }

    /*
     * API Key dialog
     */
    fun openApiTokenDialog() {
        _dialogState.update {
            it.copy(
                isApiTokenDialogOpen = true
            )
        }
    }

    fun closeApiTokenDialog() {
        _dialogState.update {
            it.copy(
                isApiTokenDialogOpen = false
            )
        }
    }

    /*
     * Model dialog
     */
    fun openApiModelDialog() {
        _dialogState.update {
            it.copy(
                isApiModelDialogOpen = true
            )
        }
    }

    fun closeApiModelDialog() {
        _dialogState.update {
            it.copy(
                isApiModelDialogOpen = false
            )
        }
    }

    /*
     * Temperature dialog
     */
    fun openTemperatureDialog() {
        _dialogState.update {
            it.copy(
                isTemperatureDialogOpen = true
            )
        }
    }

    fun closeTemperatureDialog() {
        _dialogState.update {
            it.copy(
                isTemperatureDialogOpen = false
            )
        }
    }

    /*
     * Top P dialog
     */
    fun openTopPDialog() {
        _dialogState.update {
            it.copy(
                isTopPDialogOpen = true
            )
        }
    }

    fun closeTopPDialog() {
        _dialogState.update {
            it.copy(
                isTopPDialogOpen = false
            )
        }
    }

    /*
     * System prompt dialog
     */
    fun openSystemPromptDialog() {
        _dialogState.update {
            it.copy(
                isSystemPromptDialogOpen = true
            )
        }
    }

    fun closeSystemPromptDialog() {
        _dialogState.update {
            it.copy(
                isSystemPromptDialogOpen = false
            )
        }
    }

    /*
     * Delete dialog
     */
    fun openDeleteDialog() {
        _dialogState.update {
            it.copy(
                isDeleteDialogOpen = true
            )
        }
    }

    fun closeDeleteDialog() {
        _dialogState.update {
            it.copy(
                isDeleteDialogOpen = false
            )
        }
    }

    /*
     * Delete platform
     */
    fun deletePlatform() {

        val platform =
            _platformState.value
                ?: return

        viewModelScope.launch {

            try {

                settingRepository
                    .deletePlatformV2(
                        platform
                    )

                closeDeleteDialog()

                _isDeleted.update {
                    true
                }

            } catch (_: Exception) {
                // Do not navigate away if deletion fails.
            }
        }
    }

    /*
     * Dialog state
     */
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
