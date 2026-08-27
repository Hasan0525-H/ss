package com.vibe.app.presentation.ui.setup

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.data.model.ClientType
import com.vibe.app.presentation.ui.components.ModelCatalogSelector
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_API_KEY
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_BASICS
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_MODEL
import com.vibe.app.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_TOTAL_STEPS

@Composable
fun SetupPlatformWizardScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onComplete: () -> Unit,
    onBackAction: () -> Unit,
) {
    val wizardStep by setupViewModel.wizardStep.collectAsStateWithLifecycle()
    val selectedClientType by setupViewModel.selectedClientType.collectAsStateWithLifecycle()
    val platformName by setupViewModel.platformName.collectAsStateWithLifecycle()
    val apiUrl by setupViewModel.apiUrl.collectAsStateWithLifecycle()
    val apiKey by setupViewModel.apiKey.collectAsStateWithLifecycle()
    val model by setupViewModel.model.collectAsStateWithLifecycle()
    val isFreePlan by setupViewModel.isFreePlan.collectAsStateWithLifecycle()
    val modelsFetchStatus by setupViewModel.modelsFetchStatus.collectAsStateWithLifecycle()
    val saveStatus by setupViewModel.saveStatus.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)
    val savePlatformFailedText = stringResource(R.string.save_platform_failed)

    LaunchedEffect(Unit) {
        setupViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(
                context,
                switchedHint.format(name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(saveStatus) {
        when (val status = saveStatus) {
            SaveStatus.Success -> {
                setupViewModel.clearSaveStatus()
                onComplete()
            }

            is SaveStatus.Error -> {
                Toast.makeText(
                    context,
                    status.message.ifBlank { savePlatformFailedText },
                    Toast.LENGTH_LONG,
                ).show()
                setupViewModel.clearSaveStatus()
            }

            else -> Unit
        }
    }

    val isSaving = saveStatus is SaveStatus.Saving
    val canProceed by remember(
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
                    platformName.isNotBlank() && apiUrl.isNotBlank()

                WIZARD_STEP_API_KEY ->
                    selectedClientType == ClientType.CUSTOM || apiKey.isNotBlank()

                WIZARD_STEP_MODEL ->
                    model.isNotBlank()

                else -> false
            }
        }
    }

    fun goBack() {
        if (wizardStep > WIZARD_STEP_BASICS) {
            setupViewModel.previousWizardStep()
        } else {
            setupViewModel.resetWizard()
            onBackAction()
        }
    }

    BackHandler { goBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SetupAppBar(backAction = ::goBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding(),
        ) {
            WizardProgressIndicator(
                currentStep = wizardStep,
                totalSteps = WIZARD_TOTAL_STEPS,
            )

            when (wizardStep) {
                WIZARD_STEP_BASICS -> BasicsStep(
                    clientType = selectedClientType,
                    platformName = platformName,
                    onPlatformNameChange = setupViewModel::updatePlatformName,
                    apiUrl = apiUrl,
                    onApiUrlChange = setupViewModel::updateApiUrl,
                    modifier = Modifier.weight(1f),
                )

                WIZARD_STEP_API_KEY -> ApiKeyStep(
                    clientType = selectedClientType,
                    apiKey = apiKey,
                    onApiKeyChange = setupViewModel::updateApiKey,
                    modifier = Modifier.weight(1f),
                )

                WIZARD_STEP_MODEL -> ModelStep(
                    clientType = selectedClientType,
                    model = model,
                    onModelChange = setupViewModel::updateModel,
                    isFreePlan = isFreePlan,
                    onPlanTypeChange = setupViewModel::updatePlanType,
                    modelsFetchStatus = modelsFetchStatus,
                    modifier = Modifier.weight(1f),
                )
            }

            WizardNavigationButtons(
                currentStep = wizardStep,
                canProceed = canProceed,
                isSaving = isSaving,
                onBack = ::goBack,
                onNext = {
                    if (wizardStep < WIZARD_TOTAL_STEPS - 1) {
                        setupViewModel.nextWizardStep()
                    } else {
                        setupViewModel.savePlatform()
                    }
                },
                isLastStep = wizardStep == WIZARD_TOTAL_STEPS - 1,
            )
        }
    }
}

@Composable
private fun WizardProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(
                R.string.step_x_of_y,
                currentStep + 1,
                totalSteps,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth(),
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
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.step_basics),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.platform_basics_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = platformName,
            onValueChange = onPlatformNameChange,
            label = { Text(stringResource(R.string.platform_name)) },
            placeholder = { Text(stringResource(R.string.platform_name_hint)) },
            supportingText = { Text(stringResource(R.string.platform_name_supporting)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = apiUrl,
            onValueChange = onApiUrlChange,
            label = { Text(stringResource(R.string.api_url)) },
            placeholder = { Text(stringResource(R.string.api_url_hint)) },
            supportingText = {
                Text(
                    if (clientType == ClientType.GOOGLE_AI_STUDIO) {
                        stringResource(R.string.google_ai_studio_api_url_supporting)
                    } else {
                        stringResource(R.string.api_url_cautions)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.step_api_key),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = when (clientType) {
                ClientType.GOOGLE_AI_STUDIO ->
                    stringResource(R.string.google_ai_studio_api_key_description)

                ClientType.CUSTOM ->
                    stringResource(R.string.custom_api_key_optional_description)

                else ->
                    stringResource(R.string.api_key_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = {
                Text(
                    when (clientType) {
                        ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(R.string.google_ai_studio_api_key)

                        ClientType.CUSTOM ->
                            stringResource(R.string.custom_api_key_optional_label)

                        else ->
                            stringResource(R.string.api_key)
                    }
                )
            },
            placeholder = { Text(stringResource(R.string.api_key_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text(
                    when (clientType) {
                        ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(R.string.google_ai_studio_api_key_supporting)

                        ClientType.CUSTOM ->
                            stringResource(R.string.custom_api_key_optional_supporting)

                        else ->
                            stringResource(R.string.api_key_supporting)
                    }
                )
            },
        )

        val helpUrl = clientType?.let(::getApiHelpUrl)
        if (helpUrl != null) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.need_help),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = helpUrl,
                style = MaterialTheme.typography.bodySmall.copy(
                    textDecoration = TextDecoration.Underline
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri(helpUrl) },
            )
        }
    }
}

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
    val isCatalogProvider =
        clientType == ClientType.OPEN_ROUTER ||
            clientType == ClientType.GOOGLE_AI_STUDIO

    val models = (modelsFetchStatus as? ModelsFetchStatus.Success)
        ?.models
        .orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.step_model),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (clientType == ClientType.GOOGLE_AI_STUDIO) {
                stringResource(R.string.google_ai_studio_model_description)
            } else {
                stringResource(R.string.model_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isCatalogProvider && clientType != null) {
            ModelCatalogSelector(
                providerType = clientType,
                selectedModel = model,
                isFreePlan = isFreePlan,
                models = models,
                isLoading = modelsFetchStatus is ModelsFetchStatus.Loading,
                onPlanTypeChange = onPlanTypeChange,
                onModelSelected = { onModelChange(it.id) },
            )

            if (modelsFetchStatus is ModelsFetchStatus.Error) {
                Text(
                    text = modelsFetchStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text(stringResource(R.string.model)) },
                placeholder = { Text(stringResource(R.string.model_name)) },
                supportingText = { Text(stringResource(R.string.model_supporting)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
        ) {
            Text(
                if (currentStep == WIZARD_STEP_BASICS) {
                    stringResource(R.string.cancel)
                } else {
                    stringResource(R.string.back)
                }
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            enabled = canProceed && !isSaving,
        ) {
            if (isLastStep && isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    if (isLastStep) {
                        stringResource(R.string.finish)
                    } else {
                        stringResource(R.string.next)
                    }
                )
            }
        }
    }
}

private fun getApiHelpUrl(clientType: ClientType): String? =
    when (clientType) {
        ClientType.GOOGLE_AI_STUDIO -> "https://aistudio.google.com/apikey"
        ClientType.OPEN_ROUTER -> "https://openrouter.ai/keys"
        ClientType.CUSTOM -> null
        else -> null
    }
