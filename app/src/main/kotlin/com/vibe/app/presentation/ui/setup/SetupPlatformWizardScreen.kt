package com.vibe.app.presentation.ui.setup

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.data.model.ClientType
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_API_KEY
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_BASICS
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_MODEL
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_TOTAL_STEPS
import java.util.Locale

@Composable
fun SetupPlatformWizardScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onComplete: () -> Unit,
    onBackAction: () -> Unit,
) {
    val wizardStep by
        setupViewModel.wizardStep.collectAsStateWithLifecycle()

    val selectedClientType by
        setupViewModel.selectedClientType.collectAsStateWithLifecycle()

    val platformName by
        setupViewModel.platformName.collectAsStateWithLifecycle()

    val apiUrl by
        setupViewModel.apiUrl.collectAsStateWithLifecycle()

    val apiKey by
        setupViewModel.apiKey.collectAsStateWithLifecycle()

    val model by
        setupViewModel.model.collectAsStateWithLifecycle()

    val isFreePlan by
        setupViewModel.isFreePlan.collectAsStateWithLifecycle()

    val modelsFetchStatus by
        setupViewModel.modelsFetchStatus.collectAsStateWithLifecycle()

    val saveStatus by
        setupViewModel.saveStatus.collectAsStateWithLifecycle()

    val context =
        LocalContext.current

    val switchedHint =
        stringResource(
            R.string.switched_platform_hint
        )

    val savePlatformFailedText =
        stringResource(
            R.string.save_platform_failed
        )

    /*
     * Provider switch notification.
     */
    LaunchedEffect(Unit) {
        setupViewModel
            .switchedPlatformEvent
            .collect { name ->
                Toast.makeText(
                    context,
                    switchedHint.format(name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
    }

    /*
     * Wait for database save before leaving
     * the setup wizard.
     */
    LaunchedEffect(saveStatus) {
        when (
            val status = saveStatus
        ) {
            SaveStatus.Success -> {
                setupViewModel.clearSaveStatus()
                onComplete()
            }

            is SaveStatus.Error -> {
                Toast.makeText(
                    context,
                    status.message.ifBlank {
                        savePlatformFailedText
                    },
                    Toast.LENGTH_LONG,
                ).show()

                setupViewModel.clearSaveStatus()
            }

            else -> Unit
        }
    }

    val isSaving =
        saveStatus is SaveStatus.Saving

    /*
     * Custom OpenAI-compatible endpoints can work
     * without authentication, so API key is optional
     * for ClientType.CUSTOM.
     */
    val canProceed by
        remember(
            wizardStep,
            selectedClientType,
            platformName,
            apiUrl,
            apiKey,
            model,
        ) {
            derivedStateOf {
                when (wizardStep) {
                    WIZARD_STEP_BASICS ->
                        platformName.isNotBlank() &&
                            apiUrl.isNotBlank()

                    WIZARD_STEP_API_KEY ->
                        selectedClientType == ClientType.CUSTOM ||
                            apiKey.isNotBlank()

                    WIZARD_STEP_MODEL ->
                        model.isNotBlank()

                    else ->
                        false
                }
            }
        }

    BackHandler {
        if (wizardStep > 0) {
            setupViewModel.previousWizardStep()
        } else {
            setupViewModel.resetWizard()
            onBackAction()
        }
    }

    Scaffold(
        modifier =
            modifier.fillMaxSize(),

        topBar = {
            SetupAppBar(
                backAction = {
                    if (wizardStep > 0) {
                        setupViewModel.previousWizardStep()
                    } else {
                        setupViewModel.resetWizard()
                        onBackAction()
                    }
                }
            )
        },
    ) { innerPadding ->

        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .imePadding()
        ) {

            WizardProgressIndicator(
                currentStep =
                    wizardStep,

                totalSteps =
                    WIZARD_TOTAL_STEPS,
            )

            AnimatedContent(
                targetState =
                    wizardStep,

                transitionSpec = {
                    if (
                        targetState >
                        initialState
                    ) {
                        (
                            slideInHorizontally {
                                it
                            } + fadeIn()
                            ) togetherWith (
                            slideOutHorizontally {
                                -it
                            } + fadeOut()
                            )
                    } else {
                        (
                            slideInHorizontally {
                                -it
                            } + fadeIn()
                            ) togetherWith (
                            slideOutHorizontally {
                                it
                            } + fadeOut()
                            )
                    }
                },

                label =
                    "wizard_step_animation",

                modifier =
                    Modifier.weight(1f),
            ) { step ->

                when (step) {
                    WIZARD_STEP_BASICS -> {
                        BasicsStep(
                            clientType =
                                selectedClientType,

                            platformName =
                                platformName,

                            onPlatformNameChange =
                                setupViewModel::updatePlatformName,

                            apiUrl =
                                apiUrl,

                            onApiUrlChange =
                                setupViewModel::updateApiUrl,
                        )
                    }

                    WIZARD_STEP_API_KEY -> {
                        ApiKeyStep(
                            clientType =
                                selectedClientType,

                            apiKey =
                                apiKey,

                            onApiKeyChange =
                                setupViewModel::updateApiKey,
                        )
                    }

                    WIZARD_STEP_MODEL -> {
                        ModelStep(
                            clientType =
                                selectedClientType,

                            model =
                                model,

                            onModelChange =
                                setupViewModel::updateModel,

                            isFreePlan =
                                isFreePlan,

                            onPlanTypeChange =
                                setupViewModel::updatePlanType,

                            modelsFetchStatus =
                                modelsFetchStatus,
                        )
                    }
                }
            }

            WizardNavigationButtons(
                currentStep =
                    wizardStep,

                canProceed =
                    canProceed,

                isSaving =
                    isSaving,

                onBack = {
                    if (wizardStep > 0) {
                        setupViewModel.previousWizardStep()
                    } else {
                        setupViewModel.resetWizard()
                        onBackAction()
                    }
                },

                onNext = {
                    if (
                        wizardStep <
                        WIZARD_TOTAL_STEPS - 1
                    ) {
                        setupViewModel.nextWizardStep()
                    } else {
                        setupViewModel.savePlatform()
                    }
                },

                isLastStep =
                    wizardStep ==
                        WIZARD_TOTAL_STEPS - 1,
            )
        }
    }
}

@Composable
private fun WizardProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 16.dp,
                )
    ) {
        Text(
            text =
                stringResource(
                    R.string.step_x_of_y,
                    currentStep + 1,
                    totalSteps,
                ),

            style =
                MaterialTheme.typography.labelMedium,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        LinearProgressIndicator(
            progress = {
                (currentStep + 1)
                    .toFloat() /
                    totalSteps
            },

            modifier =
                Modifier.fillMaxWidth(),
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,
        ) {
            StepLabel(
                text =
                    stringResource(
                        R.string.step_basics
                    ),

                isCompleted =
                    currentStep >
                        WIZARD_STEP_BASICS,

                isCurrent =
                    currentStep ==
                        WIZARD_STEP_BASICS,
            )

            StepLabel(
                text =
                    stringResource(
                        R.string.step_api_key
                    ),

                isCompleted =
                    currentStep >
                        WIZARD_STEP_API_KEY,

                isCurrent =
                    currentStep ==
                        WIZARD_STEP_API_KEY,
            )

            StepLabel(
                text =
                    stringResource(
                        R.string.step_model
                    ),

                isCompleted =
                    currentStep >
                        WIZARD_STEP_MODEL,

                isCurrent =
                    currentStep ==
                        WIZARD_STEP_MODEL,
            )
        }
    }
}

@Composable
private fun StepLabel(
    text: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier,

        verticalAlignment =
            Alignment.CenterVertically,

        horizontalArrangement =
            Arrangement.spacedBy(
                4.dp
            ),
    ) {
        if (isCompleted) {
            Icon(
                imageVector =
                    Icons.Default.Check,

                contentDescription =
                    null,

                tint =
                    MaterialTheme
                        .colorScheme
                        .primary,

                modifier =
                    Modifier.size(14.dp),
            )
        }

        Text(
            text =
                text,

            style =
                MaterialTheme.typography.labelSmall,

            color =
                when {
                    isCurrent ->
                        MaterialTheme
                            .colorScheme
                            .primary

                    isCompleted ->
                        MaterialTheme
                            .colorScheme
                            .primary

                    else ->
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                },
        )
    }
}

@Composable
private fun BasicsStep(
    clientType: ClientType?,
    platformName: String,
    onPlatformNameChange: (String) -> Unit,
    apiUrl: String,
    onApiUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 20.dp
                )
    ) {
        Text(
            modifier =
                Modifier.semantics {
                    heading()
                },

            text =
                stringResource(
                    R.string.step_basics
                ),

            style =
                MaterialTheme.typography.headlineSmall,
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                stringResource(
                    R.string.platform_basics_description
                ),

            style =
                MaterialTheme.typography.bodyMedium,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        OutlinedTextField(
            value =
                platformName,

            onValueChange =
                onPlatformNameChange,

            label = {
                Text(
                    stringResource(
                        R.string.platform_name
                    )
                )
            },

            placeholder = {
                Text(
                    stringResource(
                        R.string.platform_name_hint
                    )
                )
            },

            modifier =
                Modifier.fillMaxWidth(),

            singleLine =
                true,

            supportingText = {
                Text(
                    stringResource(
                        R.string.platform_name_supporting
                    )
                )
            },
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        OutlinedTextField(
            value =
                apiUrl,

            onValueChange =
                onApiUrlChange,

            label = {
                Text(
                    stringResource(
                        R.string.api_url
                    )
                )
            },

            placeholder = {
                Text(
                    stringResource(
                        R.string.api_url_hint
                    )
                )
            },

            modifier =
                Modifier.fillMaxWidth(),

            singleLine =
                true,

            supportingText = {
                if (
                    clientType ==
                    ClientType.GOOGLE_AI_STUDIO
                ) {
                    Text(
                        stringResource(
                            R.string.google_ai_studio_api_url_supporting
                        )
                    )
                } else {
                    Text(
                        stringResource(
                            R.string.api_url_cautions
                        )
                    )
                }
            },
        )
    }
}

@Composable
private fun ApiKeyStep(
    clientType: ClientType?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler =
        LocalUriHandler.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 20.dp
                )
    ) {
        Text(
            modifier =
                Modifier.semantics {
                    heading()
                },

            text =
                stringResource(
                    R.string.step_api_key
                ),

            style =
                MaterialTheme.typography.headlineSmall,
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                when (clientType) {
                    ClientType.GOOGLE_AI_STUDIO ->
                        stringResource(
                            R.string.google_ai_studio_api_key_description
                        )

                    ClientType.CUSTOM ->
                        stringResource(
                            R.string.custom_api_key_optional_description
                        )

                    else ->
                        stringResource(
                            R.string.api_key_description
                        )
                },

            style =
                MaterialTheme.typography.bodyMedium,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        OutlinedTextField(
            value =
                apiKey,

            onValueChange =
                onApiKeyChange,

            label = {
                Text(
                    when (clientType) {
                        ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(
                                R.string.google_ai_studio_api_key
                            )

                        ClientType.CUSTOM ->
                            stringResource(
                                R.string.custom_api_key_optional_label
                            )

                        else ->
                            stringResource(
                                R.string.api_key
                            )
                    }
                )
            },

            placeholder = {
                Text(
                    stringResource(
                        R.string.api_key_hint
                    )
                )
            },

            modifier =
                Modifier.fillMaxWidth(),

            singleLine =
                true,

            visualTransformation =
                PasswordVisualTransformation(),

            supportingText = {
                Text(
                    when (clientType) {
                        ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(
                                R.string.google_ai_studio_api_key_supporting
                            )

                        ClientType.CUSTOM ->
                            stringResource(
                                R.string.custom_api_key_optional_supporting
                            )

                        else ->
                            stringResource(
                                R.string.api_key_supporting
                            )
                    }
                )
            },
        )

        clientType?.let { type ->
            val helpUrl =
                getApiHelpUrl(type)

            if (helpUrl != null) {
                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text =
                        stringResource(
                            R.string.need_help
                        ),

                    style =
                        MaterialTheme.typography.labelLarge,
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        helpUrl,

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                            .copy(
                                textDecoration =
                                    TextDecoration.Underline
                            ),

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    modifier =
                        Modifier.clickable {
                            uriHandler.openUri(
                                helpUrl
                            )
                        },
                )
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
private fun ModelStep(
    clientType: ClientType?,
    model: String,
    onModelChange: (String) -> Unit,
    isFreePlan: Boolean,
    onPlanTypeChange: (Boolean) -> Unit,
    modelsFetchStatus: ModelsFetchStatus,
    modifier: Modifier = Modifier,
) {
    var isDropdownExpanded by
        remember(clientType) {
            mutableStateOf(false)
        }

    var modelSearchQuery by
        remember(clientType) {
            mutableStateOf("")
        }

    val context =
        LocalContext.current

    val chatOnlyWarningText =
        stringResource(
            R.string.model_chat_only_warning
        )

    val isLoading =
        modelsFetchStatus is
            ModelsFetchStatus.Loading

    val availableModels =
        if (
            modelsFetchStatus is
            ModelsFetchStatus.Success
        ) {
            modelsFetchStatus.models
        } else {
            emptyList()
        }

    val filteredModels =
        remember(
            availableModels,
            modelSearchQuery,
        ) {
            val query =
                modelSearchQuery.trim()

            if (query.isBlank()) {
                availableModels
            } else {
                availableModels.filter { modelInfo ->
                    modelInfo.id.contains(
                        other = query,
                        ignoreCase = true,
                    ) ||
                        modelInfo.name
                            ?.contains(
                                other = query,
                                ignoreCase = true,
                            ) == true
                }
            }
        }

    val isOpenRouter =
        clientType ==
            ClientType.OPEN_ROUTER

    val isGoogleAIStudio =
        clientType ==
            ClientType.GOOGLE_AI_STUDIO

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 20.dp
                )
    ) {
        Text(
            modifier =
                Modifier.semantics {
                    heading()
                },

            text =
                stringResource(
                    R.string.step_model
                ),

            style =
                MaterialTheme.typography.headlineSmall,
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                if (isGoogleAIStudio) {
                    stringResource(
                        R.string.google_ai_studio_model_description
                    )
                } else {
                    stringResource(
                        R.string.model_description
                    )
                },

            style =
                MaterialTheme.typography.bodyMedium,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        if (isOpenRouter) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    ),
            ) {
                FilterChip(
                    selected =
                        isFreePlan,

                    onClick = {
                        onPlanTypeChange(true)

                        modelSearchQuery =
                            ""

                        isDropdownExpanded =
                            false
                    },

                    label = {
                        Text(
                            stringResource(
                                R.string.model_plan_free
                            )
                        )
                    },
                )

                FilterChip(
                    selected =
                        !isFreePlan,

                    onClick = {
                        onPlanTypeChange(false)

                        modelSearchQuery =
                            ""

                        isDropdownExpanded =
                            false
                    },

                    label = {
                        Text(
                            stringResource(
                                R.string.model_plan_paid
                            )
                        )
                    },
                )
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            OutlinedTextField(
                value =
                    modelSearchQuery,

                onValueChange = { query ->
                    modelSearchQuery =
                        query

                    if (!isLoading) {
                        isDropdownExpanded =
                            true
                    }
                },

                label = {
                    Text(
                        stringResource(
                            R.string.openrouter_model_search
                        )
                    )
                },

                placeholder = {
                    Text(
                        stringResource(
                            R.string.openrouter_model_search_hint
                        )
                    )
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine =
                    true,
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            ExposedDropdownMenuBox(
                expanded =
                    isDropdownExpanded,

                onExpandedChange = {
                    if (!isLoading) {
                        isDropdownExpanded =
                            !isDropdownExpanded
                    }
                },
            ) {
                OutlinedTextField(
                    value =
                        model,

                    onValueChange = {},

                    readOnly =
                        true,

                    label = {
                        Text(
                            stringResource(
                                R.string.model
                            )
                        )
                    },

                    placeholder = {
                        Text(
                            stringResource(
                                R.string.model_name
                            )
                        )
                    },

                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(20.dp),

                                strokeWidth =
                                    2.dp,
                            )
                        } else {
                            ExposedDropdownMenuDefaults
                                .TrailingIcon(
                                    expanded =
                                        isDropdownExpanded
                                )
                        }
                    },

                    modifier =
                        Modifier
                            .menuAnchor()
                            .fillMaxWidth(),

                    singleLine =
                        true,

                    supportingText = {
                        Text(
                            stringResource(
                                R.string.model_supporting
                            )
                        )
                    },
                )

                ExposedDropdownMenu(
                    expanded =
                        isDropdownExpanded,

                    onDismissRequest = {
                        isDropdownExpanded =
                            false
                    },
                ) {
                    when {
                        isLoading -> {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),

                                        horizontalArrangement =
                                            Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier =
                                                Modifier.size(24.dp)
                                        )
                                    }
                                },

                                onClick = {},
                            )
                        }

                        filteredModels.isEmpty() -> {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (
                                            modelSearchQuery
                                                .isBlank()
                                        ) {
                                            stringResource(
                                                R.string.models_not_available
                                            )
                                        } else {
                                            stringResource(
                                                R.string.models_no_matches
                                            )
                                        }
                                    )
                                },

                                onClick = {
                                    isDropdownExpanded =
                                        false
                                },
                            )
                        }

                        else -> {
                            filteredModels
                                .forEach { modelInfo ->

                                    val pricing =
                                        modelInfo.pricing

                                    val isFree =
                                        pricing?.isFree ==
                                            true

                                    val priceText =
                                        if (isFree) {
                                            stringResource(
                                                R.string.model_price_free
                                            )
                                        } else {
                                            val pricePer1K =
                                                pricing
                                                    ?.averagePricePer1K

                                            if (
                                                pricePer1K != null
                                            ) {
                                                stringResource(
                                                    R.string.model_price_per_1k,
                                                    String.format(
                                                        Locale.US,
                                                        "%.6f",
                                                        pricePer1K,
                                                    ),
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.model_price_unavailable
                                                )
                                            }
                                        }

                                    val capabilityText =
                                        if (
                                            modelInfo.supportsTools
                                        ) {
                                            stringResource(
                                                R.string.model_supports_tools
                                            )
                                        } else {
                                            stringResource(
                                                R.string.model_chat_only
                                            )
                                        }

                                    DropdownMenuItem(
                                        text = {
                                            Column(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                            ) {
                                                Text(
                                                    text =
                                                        modelInfo.name
                                                            ?: modelInfo.id,

                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodyLarge,
                                                )

                                                if (
                                                    modelInfo.name !=
                                                    null
                                                ) {
                                                    Text(
                                                        text =
                                                            modelInfo.id,

                                                        style =
                                                            MaterialTheme
                                                                .typography
                                                                .bodySmall,

                                                        color =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .onSurfaceVariant,
                                                    )
                                                }

                                                Text(
                                                    text =
                                                        capabilityText,

                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodySmall,

                                                    color =
                                                        if (
                                                            modelInfo
                                                                .supportsTools
                                                        ) {
                                                            MaterialTheme
                                                                .colorScheme
                                                                .primary
                                                        } else {
                                                            MaterialTheme
                                                                .colorScheme
                                                                .onSurfaceVariant
                                                        },
                                                )

                                                Text(
                                                    text =
                                                        priceText,

                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodySmall,

                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurfaceVariant,
                                                )
                                            }
                                        },

                                        onClick = {
                                            onModelChange(
                                                modelInfo.id
                                            )

                                            if (
                                                !modelInfo
                                                    .supportsTools
                                            ) {
                                                Toast.makeText(
                                                    context,
                                                    chatOnlyWarningText,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }

                                            isDropdownExpanded =
                                                false
                                        },
                                    )
                                }
                        }
                    }
                }
            }

        } else {
            OutlinedTextField(
                value =
                    model,

                onValueChange =
                    onModelChange,

                label = {
                    Text(
                        stringResource(
                            R.string.model
                        )
                    )
                },

                placeholder = {
                    Text(
                        if (isGoogleAIStudio) {
                            "gemini-2.5-flash"
                        } else {
                            stringResource(
                                R.string.model_name
                            )
                        }
                    )
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine =
                    true,

                supportingText = {
                    Text(
                        if (isGoogleAIStudio) {
                            stringResource(
                                R.string.google_ai_studio_model_supporting
                            )
                        } else {
                            stringResource(
                                R.string.model_supporting
                            )
                        }
                    )
                },
            )
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text =
                if (isGoogleAIStudio) {
                    stringResource(
                        R.string.google_ai_studio_model_examples
                    )
                } else {
                    stringResource(
                        R.string.model_examples
                    )
                },

            style =
                MaterialTheme.typography.bodySmall,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardNavigationButtons(
    currentStep: Int,
    canProceed: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    isLastStep: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    20.dp
                ),

        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),
    ) {
        OutlinedButton(
            onClick =
                onBack,

            modifier =
                Modifier.weight(1f),

            enabled =
                !isSaving,
        ) {
            Text(
                text =
                    if (
                        currentStep == 0
                    ) {
                        stringResource(
                            R.string.cancel
                        )
                    } else {
                        stringResource(
                            R.string.back
                        )
                    }
            )
        }

        Button(
            onClick =
                onNext,

            modifier =
                Modifier.weight(1f),

            enabled =
                canProceed &&
                    !isSaving,
        ) {
            if (
                isLastStep &&
                isSaving
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(20.dp),

                    strokeWidth =
                        2.dp,
                )
            } else {
                Text(
                    text =
                        if (isLastStep) {
                            stringResource(
                                R.string.finish
                            )
                        } else {
                            stringResource(
                                R.string.next
                            )
                        }
                )
            }
        }
    }
}

private fun getApiHelpUrl(
    clientType: ClientType,
): String? =
    when (clientType) {
        ClientType.GOOGLE_AI_STUDIO ->
            "https://aistudio.google.com/apikey"

        ClientType.OPEN_ROUTER ->
            "https://openrouter.ai/keys"

        ClientType.CUSTOM ->
            null

        else ->
            null
    }
