package com.vibe.app.presentation.ui.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.ModelConstants
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.model.ClientType
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

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object Saving : SaveStatus()
    data object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

sealed class ModelsFetchStatus {
    data object Idle : ModelsFetchStatus()
    data object Loading : ModelsFetchStatus()

    data class Success(
        val models: List<OpenRouterModel>
    ) : ModelsFetchStatus()

    data class Error(
        val message: String
    ) : ModelsFetchStatus()
}

@HiltViewModel
class SetupViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _platforms =
        MutableStateFlow<List<PlatformV2>>(emptyList())

    val platforms: StateFlow<List<PlatformV2>> =
        _platforms.asStateFlow()

    private val _wizardStep =
        MutableStateFlow(0)

    val wizardStep: StateFlow<Int> =
        _wizardStep.asStateFlow()

    private val _selectedClientType =
        MutableStateFlow<ClientType?>(null)

    val selectedClientType: StateFlow<ClientType?> =
        _selectedClientType.asStateFlow()

    private val _platformName =
        MutableStateFlow("")

    val platformName: StateFlow<String> =
        _platformName.asStateFlow()

    private val _apiUrl =
        MutableStateFlow("")

    val apiUrl: StateFlow<String> =
        _apiUrl.asStateFlow()

    private val _apiKey =
        MutableStateFlow("")

    val apiKey: StateFlow<String> =
        _apiKey.asStateFlow()

    private val _model =
        MutableStateFlow("")

    val model: StateFlow<String> =
        _model.asStateFlow()

    private val _isFreePlan =
        MutableStateFlow(true)

    val isFreePlan: StateFlow<Boolean> =
        _isFreePlan.asStateFlow()

    private val _modelsFetchStatus =
        MutableStateFlow<ModelsFetchStatus>(
            ModelsFetchStatus.Idle
        )

    val modelsFetchStatus:
        StateFlow<ModelsFetchStatus> =
        _modelsFetchStatus.asStateFlow()

    private val _saveStatus =
        MutableStateFlow<SaveStatus>(
            SaveStatus.Idle
        )

    val saveStatus: StateFlow<SaveStatus> =
        _saveStatus.asStateFlow()

    private val _switchedPlatformEvent =
        MutableSharedFlow<String>()

    val switchedPlatformEvent:
        SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()

    init {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            _platforms.value =
                settingRepository.fetchPlatformV2s()
        }
    }

    fun selectClientType(
        clientType: ClientType
    ) {
        _selectedClientType.value =
            clientType

        _platformName.value =
            getDefaultPlatformName(
                clientType
            )

        _apiUrl.value =
            getDefaultApiUrl(
                clientType
            )

        _apiKey.value = ""

        _model.value =
            getDefaultModel(
                clientType
            )

        /*
         * Free/Paid is meaningful only for
         * OpenRouter.
         */
        _isFreePlan.value =
            clientType == ClientType.OPEN_ROUTER

        _modelsFetchStatus.value =
            ModelsFetchStatus.Idle

        _wizardStep.value =
            WIZARD_STEP_BASICS
    }

    fun updatePlatformName(
        name: String
    ) {
        _platformName.value = name
    }

    fun updateApiUrl(
        url: String
    ) {
        _apiUrl.value = url
    }

    fun updateApiKey(
        key: String
    ) {
        _apiKey.value = key
    }

    fun updateModel(
        modelName: String
    ) {
        _model.value = modelName
    }

    fun updatePlanType(
        isFree: Boolean
    ) {
        _isFreePlan.value = isFree

        /*
         * OpenRouter only has dynamic
         * free/paid model filtering.
         *
         * Google AI Studio is completely
         * independent from OpenRouter.
         */
        if (
            _selectedClientType.value ==
            ClientType.OPEN_ROUTER &&
            _apiKey.value.isNotBlank()
        ) {
            fetchModels()
        }
    }

    fun fetchModels() {

        val currentApiKey =
            _apiKey.value.trim()

        if (currentApiKey.isBlank()) {
            return
        }

        /*
         * Only OpenRouter can use this
         * model-fetching operation.
         */
        if (
            _selectedClientType.value !=
            ClientType.OPEN_ROUTER
        ) {
            _modelsFetchStatus.value =
                ModelsFetchStatus.Idle

            return
        }

        viewModelScope.launch {

            _modelsFetchStatus.value =
                ModelsFetchStatus.Loading

            try {

                val fetchedModels =
                    settingRepository
                        .fetchOpenRouterModels(
                            apiKey =
                                currentApiKey,

                            isFreeOnly =
                                _isFreePlan.value
                        )

                _modelsFetchStatus.value =
                    ModelsFetchStatus.Success(
                        fetchedModels
                    )

                /*
                 * Automatically select the first
                 * OpenRouter model when available.
                 */
                if (
                    fetchedModels.isNotEmpty()
                ) {
                    _model.value =
                        fetchedModels
                            .first()
                            .id
                }

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Failed to fetch OpenRouter models",
                    e
                )

                _modelsFetchStatus.value =
                    ModelsFetchStatus.Error(
                        e.message
                            ?: "Failed to fetch model list"
                    )
            }
        }
    }

    fun nextWizardStep() {

        if (
            !canProceedFromStep(
                _wizardStep.value
            )
        ) {
            return
        }

        /*
         * OpenRouter fetches models after
         * the API key step.
         *
         * Google AI Studio does not.
         */
        if (
            _wizardStep.value ==
            WIZARD_STEP_API_KEY &&
            _selectedClientType.value ==
            ClientType.OPEN_ROUTER
        ) {
            fetchModels()
        }

        _wizardStep.update {
            minOf(
                WIZARD_TOTAL_STEPS - 1,
                it + 1
            )
        }
    }

    fun previousWizardStep() {
        _wizardStep.update {
            maxOf(
                0,
                it - 1
            )
        }
    }

    fun resetWizard() {

        _wizardStep.value = 0

        _selectedClientType.value =
            null

        _platformName.value = ""

        _apiUrl.value = ""

        _apiKey.value = ""

        _model.value = ""

        _isFreePlan.value = true

        _modelsFetchStatus.value =
            ModelsFetchStatus.Idle
    }

    fun savePlatform() {

        val clientType =
            _selectedClientType.value
                ?: return

        viewModelScope.launch {

            _saveStatus.value =
                SaveStatus.Saving

            try {

                val platform =
                    PlatformV2(

                        name =
                            _platformName.value
                                .trim(),

                        compatibleType =
                            clientType,

                        enabled =
                            true,

                        apiUrl =
                            _apiUrl.value
                                .trim(),

                        token =
                            _apiKey.value
                                .trim()
                                .takeIf {
                                    it.isNotEmpty()
                                },

                        model =
                            _model.value
                                .trim(),

                        /*
                         * Only OpenRouter stores
                         * free/paid state.
                         *
                         * Google AI Studio gets null.
                         */
                        isFree =
                            if (
                                clientType ==
                                ClientType.OPEN_ROUTER
                            ) {
                                _isFreePlan.value
                            } else {
                                null
                            },

                        temperature =
                            1.0f,

                        topP =
                            1.0f,

                        systemPrompt =
                            null,

                        stream =
                            true,

                        reasoning =
                            false,

                        timeout =
                            30
                    )

                val allPlatforms =
                    settingRepository
                        .fetchPlatformV2s()

                val othersEnabled =
                    allPlatforms.filter {
                        it.enabled
                    }

                /*
                 * Only one platform is active
                 * at a time.
                 */
                othersEnabled.forEach {
                    existingPlatform ->

                    settingRepository
                        .updatePlatformV2(
                            existingPlatform.copy(
                                enabled = false
                            )
                        )
                }

                settingRepository
                    .addPlatformV2(
                        platform
                    )

                if (
                    othersEnabled.isNotEmpty()
                ) {

                    _switchedPlatformEvent.emit(
                        platform.name
                    )
                }

                loadPlatforms()

                _saveStatus.value =
                    SaveStatus.Success

                resetWizard()

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Failed to save platform",
                    e
                )

                _saveStatus.value =
                    SaveStatus.Error(
                        e.message
                            ?: "Unknown error"
                    )
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value =
            SaveStatus.Idle
    }

    fun deletePlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {

            settingRepository
                .deletePlatformV2(
                    platform
                )

            loadPlatforms()
        }
    }

    fun canProceedFromStep(
        step: Int
    ): Boolean =
        when (step) {

            WIZARD_STEP_BASICS ->

                _platformName.value
                    .isNotBlank() &&

                _apiUrl.value
                    .isNotBlank()

            WIZARD_STEP_API_KEY ->

                _apiKey.value
                    .isNotBlank()

            WIZARD_STEP_MODEL ->

                _model.value
                    .isNotBlank()

            else ->
                false
        }

    fun isSetupComplete(): Boolean =
        _platforms.value.isNotEmpty()

    private fun getDefaultPlatformName(
        clientType: ClientType
    ): String =
        when (clientType) {

            ClientType.OPENAI ->
                "OpenAI"

            ClientType.ANTHROPIC ->
                "Anthropic"

            ClientType.QWEN ->
                "Qwen"

            ClientType.KIMI ->
                "Kimi"

            ClientType.MINIMAX ->
                "MiniMax"

            ClientType.DEEPSEEK ->
                "DeepSeek"

            ClientType.GOOGLE_AI_STUDIO ->
                "Google AI Studio"

            ClientType.OPEN_ROUTER ->
                "OpenRouter"

            ClientType.CUSTOM ->
                "Custom API"
        }

    private fun getDefaultApiUrl(
        clientType: ClientType
    ): String =
        when (clientType) {

            ClientType.OPEN_ROUTER ->
                ModelConstants
                    .OPENROUTER_API_URL

            ClientType.GOOGLE_AI_STUDIO ->
                ModelConstants
                    .GOOGLE_AI_STUDIO_API_URL

            ClientType.CUSTOM ->
                ModelConstants
                    .CUSTOM_API_URL

            else ->
                ""
        }

    private fun getDefaultModel(
        clientType: ClientType
    ): String =
        when (clientType) {

            ClientType.OPENAI ->
                "gpt-4o"

            ClientType.ANTHROPIC ->
                "claude-3-5-sonnet"

            ClientType.QWEN ->
                "qwen-max"

            ClientType.KIMI ->
                "moonshot-v1-8k"

            ClientType.MINIMAX ->
                "abab6.5s-chat"

            ClientType.DEEPSEEK ->
                "deepseek-chat"

            ClientType.GOOGLE_AI_STUDIO ->
                "gemini-2.5-flash"

            ClientType.OPEN_ROUTER ->
                "google/gemini-2.5-pro"

            ClientType.CUSTOM ->
                ""
        }

    companion object {

        private const val TAG =
            "SetupViewModelV2"

        const val WIZARD_STEP_BASICS =
            0

        const val WIZARD_STEP_API_KEY =
            1

        const val WIZARD_STEP_MODEL =
            2

        const val WIZARD_TOTAL_STEPS =
            3
    }
}
