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

    data object Idle :
        SaveStatus()

    data object Saving :
        SaveStatus()

    data object Success :
        SaveStatus()

    data class Error(
        val message: String,
    ) : SaveStatus()
}

sealed class ModelsFetchStatus {

    data object Idle :
        ModelsFetchStatus()

    data object Loading :
        ModelsFetchStatus()

    data class Success(
        val models: List<OpenRouterModel>,
    ) : ModelsFetchStatus()

    data class Error(
        val message: String,
    ) : ModelsFetchStatus()
}

@HiltViewModel
class SetupViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
) : ViewModel() {

    private val _platforms =
        MutableStateFlow<List<PlatformV2>>(
            emptyList()
        )

    val platforms:
        StateFlow<List<PlatformV2>> =
        _platforms.asStateFlow()

    private val _wizardStep =
        MutableStateFlow(
            WIZARD_STEP_BASICS
        )

    val wizardStep:
        StateFlow<Int> =
        _wizardStep.asStateFlow()

    private val _selectedClientType =
        MutableStateFlow<ClientType?>(
            null
        )

    val selectedClientType:
        StateFlow<ClientType?> =
        _selectedClientType.asStateFlow()

    private val _platformName =
        MutableStateFlow("")

    val platformName:
        StateFlow<String> =
        _platformName.asStateFlow()

    private val _apiUrl =
        MutableStateFlow("")

    val apiUrl:
        StateFlow<String> =
        _apiUrl.asStateFlow()

    private val _apiKey =
        MutableStateFlow("")

    val apiKey:
        StateFlow<String> =
        _apiKey.asStateFlow()

    private val _model =
        MutableStateFlow("")

    val model:
        StateFlow<String> =
        _model.asStateFlow()

    /*
     * OpenRouter only.
     */
    private val _isFreePlan =
        MutableStateFlow(true)

    val isFreePlan:
        StateFlow<Boolean> =
        _isFreePlan.asStateFlow()

    /*
     * OpenRouter dynamic model loading only.
     */
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

    val saveStatus:
        StateFlow<SaveStatus> =
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

            try {

                _platforms.value =
                    settingRepository
                        .fetchPlatformV2s()

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Failed to load platforms",
                    e,
                )
            }
        }
    }

    /*
     * =========================================================
     * PROVIDER SELECTION
     * =========================================================
     *
     * Visible setup providers:
     *
     * OPEN_ROUTER
     * GOOGLE_AI_STUDIO
     * CUSTOM
     *
     * Other enum values remain for compatibility.
     */
    fun selectClientType(
        clientType: ClientType,
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

        _apiKey.value =
            ""

        _model.value =
            getDefaultModel(
                clientType
            )

        /*
         * Free/Paid filtering belongs only
         * to OpenRouter.
         */
        _isFreePlan.value =
            clientType ==
                ClientType.OPEN_ROUTER

        _modelsFetchStatus.value =
            ModelsFetchStatus.Idle

        _saveStatus.value =
            SaveStatus.Idle

        _wizardStep.value =
            WIZARD_STEP_BASICS
    }

    fun updatePlatformName(
        name: String,
    ) {

        _platformName.value =
            name
    }

    fun updateApiUrl(
        url: String,
    ) {

        _apiUrl.value =
            url
    }

    fun updateApiKey(
        key: String,
    ) {

        _apiKey.value =
            key
    }

    fun updateModel(
        modelName: String,
    ) {

        _model.value =
            modelName
    }

    /*
     * =========================================================
     * OPENROUTER FREE / PAID
     * =========================================================
     */
    fun updatePlanType(
        isFree: Boolean,
    ) {

        /*
         * Ignore for Google AI Studio
         * and Custom.
         */
        if (
            _selectedClientType.value !=
            ClientType.OPEN_ROUTER
        ) {

            return
        }

        _isFreePlan.value =
            isFree

        if (
            _apiKey.value
                .isNotBlank()
        ) {

            fetchModels()
        }
    }

    /*
     * =========================================================
     * OPENROUTER MODELS
     * =========================================================
     */
    fun fetchModels() {

        /*
         * Never fetch OpenRouter models for
         * Google AI Studio or Custom.
         */
        if (
            _selectedClientType.value !=
            ClientType.OPEN_ROUTER
        ) {

            _modelsFetchStatus.value =
                ModelsFetchStatus.Idle

            return
        }

        val currentApiKey =
            normalizeApiKey(
                _apiKey.value
            )

        if (
            currentApiKey
                .isNullOrBlank()
        ) {

            _modelsFetchStatus.value =
                ModelsFetchStatus.Error(
                    "OpenRouter API key is required"
                )

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
                                _isFreePlan.value,
                        )

                _modelsFetchStatus.value =
                    ModelsFetchStatus.Success(
                        fetchedModels
                    )

                /*
                 * =================================================
                 * IMPORTANT:
                 * Prefer a Tools-capable model automatically.
                 * =================================================
                 *
                 * Keep the user's current selection if it still
                 * exists in the newly fetched Free/Paid list.
                 *
                 * If the current model is no longer available:
                 *
                 * 1. Prefer the first model whose
                 *    supported_parameters contains "tools".
                 *
                 * 2. If there is no Tools-capable model in the
                 *    current list, use the first available model
                 *    so normal text chat remains usable.
                 *
                 * We do NOT silently replace an existing valid
                 * user selection.
                 */
                if (
                    fetchedModels.isNotEmpty()
                ) {

                    val currentModel =
                        _model.value
                            .trim()

                    val currentModelExists =
                        fetchedModels.any {
                            it.id ==
                                currentModel
                        }

                    if (
                        !currentModelExists
                    ) {

                        val preferredModel =
                            fetchedModels
                                .firstOrNull {
                                    it.supportsTools
                                }
                                ?: fetchedModels
                                    .first()

                        _model.value =
                            preferredModel.id
                    }
                }

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Failed to fetch OpenRouter models",
                    e,
                )

                _modelsFetchStatus.value =
                    ModelsFetchStatus.Error(
                        e.message
                            ?: "Failed to fetch model list"
                    )
            }
        }
    }

    /*
     * =========================================================
     * WIZARD NAVIGATION
     * =========================================================
     */
    fun nextWizardStep() {

        if (
            !canProceedFromStep(
                _wizardStep.value
            )
        ) {

            return
        }

        /*
         * Only OpenRouter fetches a dynamic
         * list of models.
         *
         * Google AI Studio:
         * manual Gemini model ID.
         *
         * Custom:
         * manual model ID.
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
                it + 1,
            )
        }
    }

    fun previousWizardStep() {

        _wizardStep.update {

            maxOf(
                WIZARD_STEP_BASICS,
                it - 1,
            )
        }
    }

    fun resetWizard() {

        _wizardStep.value =
            WIZARD_STEP_BASICS

        _selectedClientType.value =
            null

        _platformName.value =
            ""

        _apiUrl.value =
            ""

        _apiKey.value =
            ""

        _model.value =
            ""

        _isFreePlan.value =
            true

        _modelsFetchStatus.value =
            ModelsFetchStatus.Idle

        /*
         * IMPORTANT:
         *
         * Do not reset SaveStatus here.
         *
         * SetupPlatformWizardScreen waits for
         * SaveStatus.Success and then calls
         * clearSaveStatus().
         */
    }

    /*
     * =========================================================
     * SAVE PLATFORM
     * =========================================================
     */
    fun savePlatform() {

        /*
         * Prevent duplicate save requests.
         */
        if (
            _saveStatus.value ==
            SaveStatus.Saving
        ) {

            return
        }

        val clientType =
            _selectedClientType.value
                ?: return

        val cleanName =
            _platformName.value
                .trim()

        val cleanApiUrl =
            _apiUrl.value
                .trim()
                .trimEnd('/')

        val cleanModel =
            _model.value
                .trim()

        val cleanApiKey =
            normalizeApiKey(
                _apiKey.value
            )

        /*
         * Validate required fields.
         */
        if (
            cleanName.isBlank()
        ) {

            _saveStatus.value =
                SaveStatus.Error(
                    "Platform name is required"
                )

            return
        }

        if (
            cleanApiUrl.isBlank()
        ) {

            _saveStatus.value =
                SaveStatus.Error(
                    "API URL is required"
                )

            return
        }

        if (
            cleanModel.isBlank()
        ) {

            _saveStatus.value =
                SaveStatus.Error(
                    "Model ID is required"
                )

            return
        }

        /*
         * OpenRouter and Google AI Studio
         * require an API key.
         *
         * Custom OpenAI-compatible APIs may
         * work without authentication.
         */
        if (
            clientType !=
            ClientType.CUSTOM &&
            cleanApiKey.isNullOrBlank()
        ) {

            _saveStatus.value =
                SaveStatus.Error(
                    "API key is required"
                )

            return
        }

        viewModelScope.launch {

            _saveStatus.value =
                SaveStatus.Saving

            try {

                val platform =
                    PlatformV2(

                        name =
                            cleanName,

                        compatibleType =
                            clientType,

                        enabled =
                            true,

                        apiUrl =
                            cleanApiUrl,

                        /*
                         * Stored without "Bearer ".
                         *
                         * The networking layer adds
                         * Authorization: Bearer itself.
                         */
                        token =
                            cleanApiKey,

                        /*
                         * Exact selected model ID.
                         */
                        model =
                            cleanModel,

                        /*
                         * Free/Paid applies only
                         * to OpenRouter.
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
                            30,
                    )

                val allPlatforms =
                    settingRepository
                        .fetchPlatformV2s()

                val othersEnabled =
                    allPlatforms
                        .filter {
                            it.enabled
                        }

                /*
                 * Only one provider can be active.
                 */
                othersEnabled
                    .forEach {
                        existingPlatform ->

                        settingRepository
                            .updatePlatformV2(
                                existingPlatform.copy(
                                    enabled =
                                        false
                                )
                            )
                    }

                settingRepository
                    .addPlatformV2(
                        platform
                    )

                if (
                    othersEnabled
                        .isNotEmpty()
                ) {

                    _switchedPlatformEvent.emit(
                        platform.name
                    )
                }

                /*
                 * Refresh immediately before
                 * reporting Success.
                 */
                _platforms.value =
                    settingRepository
                        .fetchPlatformV2s()

                /*
                 * SetupPlatformWizardScreen
                 * observes this value.
                 */
                _saveStatus.value =
                    SaveStatus.Success

                /*
                 * Reset form fields.
                 *
                 * SaveStatus remains Success until
                 * clearSaveStatus() is called.
                 */
                resetWizard()

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Failed to save platform",
                    e,
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
        platform: PlatformV2,
    ) {

        viewModelScope.launch {

            try {

                settingRepository
                    .deletePlatformV2(
                        platform
                    )

                _platforms.value =
                    settingRepository
                        .fetchPlatformV2s()

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Failed to delete platform",
                    e,
                )
            }
        }
    }

    /*
     * =========================================================
     * WIZARD VALIDATION
     * =========================================================
     */
    fun canProceedFromStep(
        step: Int,
    ): Boolean =

        when (step) {

            WIZARD_STEP_BASICS ->

                _platformName.value
                    .isNotBlank() &&
                    _apiUrl.value
                        .isNotBlank()

            /*
             * Custom API key is optional.
             */
            WIZARD_STEP_API_KEY ->

                _selectedClientType.value ==
                    ClientType.CUSTOM ||
                    _apiKey.value
                        .isNotBlank()

            WIZARD_STEP_MODEL ->

                _model.value
                    .isNotBlank()

            else ->

                false
        }

    fun isSetupComplete(): Boolean =

        _platforms.value
            .isNotEmpty()

    /*
     * =========================================================
     * DEFAULT PLATFORM NAME
     * =========================================================
     */
    private fun getDefaultPlatformName(
        clientType: ClientType,
    ): String =

        when (clientType) {

            ClientType.OPEN_ROUTER ->

                "OpenRouter"

            ClientType.GOOGLE_AI_STUDIO ->

                "Google AI Studio"

            ClientType.CUSTOM ->

                "Custom API"

            /*
             * Compatibility with old saved types.
             */
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
        }

    /*
     * =========================================================
     * DEFAULT API URL
     * =========================================================
     */
    private fun getDefaultApiUrl(
        clientType: ClientType,
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

    /*
     * =========================================================
     * DEFAULT MODEL
     * =========================================================
     */
    private fun getDefaultModel(
        clientType: ClientType,
    ): String =

        when (clientType) {

            /*
             * When the OpenRouter model list
             * loads, fetchModels() replaces this
             * if it is not available.
             *
             * The replacement now prefers a
             * Tools-capable model.
             */
            ClientType.OPEN_ROUTER ->

                "google/gemini-2.5-pro"

            ClientType.GOOGLE_AI_STUDIO ->

                "gemini-2.5-flash"

            /*
             * Custom requires manual model ID.
             */
            ClientType.CUSTOM ->

                ""

            /*
             * Compatibility only.
             */
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
        }

    /*
     * Remove an optional "Bearer " prefix.
     *
     * The networking layer adds the Bearer
     * prefix when sending the request.
     */
    private fun normalizeApiKey(
        rawApiKey: String,
    ): String? {

        val trimmed =
            rawApiKey
                .trim()

        if (
            trimmed.isBlank()
        ) {

            return null
        }

        val normalized =

            if (
                trimmed.startsWith(
                    prefix =
                        "Bearer ",

                    ignoreCase =
                        true,
                )
            ) {

                trimmed
                    .substring(
                        "Bearer ".length
                    )
                    .trim()

            } else {

                trimmed
            }

        return normalized
            .takeIf {
                it.isNotBlank()
            }
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
