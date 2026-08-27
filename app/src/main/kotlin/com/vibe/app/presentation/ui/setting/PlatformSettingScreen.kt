package com.vibe.app.presentation.ui.setting

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.model.GoogleAiStudioModelCatalog
import com.vibe.app.presentation.ui.components.ModelCatalogSelector
import com.vibe.app.presentation.ui.components.PlatformTopAppBar
import com.vibe.app.presentation.ui.components.PreferenceSwitchWithContainer
import com.vibe.app.presentation.ui.components.ReferenceCard
import com.vibe.app.presentation.ui.components.ReferenceDivider
import com.vibe.app.presentation.ui.components.ReferenceInfoBanner
import com.vibe.app.presentation.ui.components.ReferenceSectionLabel
import com.vibe.app.presentation.ui.components.ReferenceSettingRow
import com.vibe.app.presentation.ui.components.ReferenceSliderCard
import com.vibe.app.util.pinnedExitUntilCollapsedScrollBehavior
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = {
            scrollState.canScrollForward || scrollState.canScrollBackward
        }
    )

    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()
    val openRouterModels by settingViewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingOpenRouterModels by
        settingViewModel.isLoadingModels.collectAsStateWithLifecycle()

    var isFreeFilter by remember(platform?.uid) {
        mutableStateOf(platform?.isFree ?: true)
    }
    var temperatureValue by remember(platform?.uid) {
        mutableFloatStateOf(platform?.temperature ?: 1f)
    }
    var topPValue by remember(platform?.uid) {
        mutableFloatStateOf(platform?.topP ?: 1f)
    }

    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)

    LaunchedEffect(
        platform?.compatibleType,
        isFreeFilter,
        platform?.token,
    ) {
        if (
            platform?.compatibleType == ClientType.OPEN_ROUTER &&
            !platform?.token.isNullOrBlank()
        ) {
            settingViewModel.loadModels(isFreeOnly = isFreeFilter)
        }
    }

    LaunchedEffect(Unit) {
        settingViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(
                context,
                switchedHint.format(name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) onNavigationClick()
    }

    platform?.let { platformData ->
        val isGoogleAIStudio =
            platformData.compatibleType == ClientType.GOOGLE_AI_STUDIO
        val isOpenRouter =
            platformData.compatibleType == ClientType.OPEN_ROUTER
        val isCatalogProvider = isGoogleAIStudio || isOpenRouter
        val isReasoningDisabled =
            platformData.compatibleType == ClientType.OPENAI && platformData.reasoning

        val models = if (isGoogleAIStudio) {
            GoogleAiStudioModelCatalog.models(isFreeOnly = isFreeFilter)
        } else {
            openRouterModels
        }

        val displayedModel = platformData.model.ifBlank {
            stringResource(R.string.not_set)
        }

        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                PlatformTopAppBar(
                    title = platformData.name,
                    onBackClick = onNavigationClick,
                    onDeleteClick = settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(bottom = 30.dp)
            ) {
                PreferenceSwitchWithContainer(
                    title = stringResource(R.string.enable_api),
                    isChecked = platformData.enabled,
                    onCheckedChange = { settingViewModel.toggleEnabled() },
                )

                ReferenceSectionLabel(
                    text = stringResource(R.string.connection_settings),
                )

                ReferenceCard {
                    ReferenceSettingRow(
                        title = stringResource(R.string.platform_name),
                        value = platformData.name,
                        icon = Icons.Outlined.Person,
                        enabled = platformData.enabled,
                        onClick = settingViewModel::openPlatformNameDialog,
                    )
                    ReferenceDivider()
                    ReferenceSettingRow(
                        title = stringResource(R.string.api_url),
                        value = platformData.apiUrl,
                        icon = Icons.Outlined.Link,
                        enabled = platformData.enabled,
                        onClick = settingViewModel::openApiUrlDialog,
                    )
                    ReferenceDivider()
                    ReferenceSettingRow(
                        title = if (isGoogleAIStudio) {
                            stringResource(R.string.google_ai_studio_api_key)
                        } else {
                            stringResource(R.string.api_key)
                        },
                        value = if (platformData.token.isNullOrBlank()) {
                            stringResource(R.string.not_set)
                        } else {
                            "•••••${platformData.token!!.takeLast(4)}"
                        },
                        icon = Icons.Outlined.VpnKey,
                        enabled = platformData.enabled,
                        onClick = settingViewModel::openApiTokenDialog,
                    )
                }

                Spacer(Modifier.height(16.dp))

                ReferenceInfoBanner(
                    text = if (isGoogleAIStudio) {
                        stringResource(R.string.google_ai_studio_model_description)
                    } else {
                        stringResource(R.string.model_selector_hint)
                    },
                )

                Spacer(Modifier.height(14.dp))

                if (isCatalogProvider) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ModelCatalogSelector(
                            providerType = platformData.compatibleType,
                            selectedModel = platformData.model,
                            isFreePlan = isFreeFilter,
                            models = models,
                            isLoading = isOpenRouter && isLoadingOpenRouterModels,
                            enabled = platformData.enabled,
                            onPlanTypeChange = { isFree ->
                                isFreeFilter = isFree
                                settingViewModel.updatePlatform(
                                    platformData.copy(isFree = isFree)
                                )
                                if (isOpenRouter && !platformData.token.isNullOrBlank()) {
                                    settingViewModel.loadModels(isFreeOnly = isFree)
                                }
                            },
                            onModelSelected = { modelInfo ->
                                settingViewModel.updateApiModel(modelInfo.id)
                            },
                        )
                    }
                } else {
                    ReferenceCard {
                        ReferenceSettingRow(
                            title = stringResource(R.string.api_model),
                            value = displayedModel,
                            icon = Icons.Outlined.Tune,
                            enabled = platformData.enabled,
                            onClick = settingViewModel::openApiModelDialog,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                ReferenceSliderCard(
                    title = stringResource(R.string.temperature),
                    description = stringResource(R.string.temperature_description),
                    value = temperatureValue,
                    valueText = String.format(Locale.US, "%.1f", temperatureValue),
                    valueRange = 0f..2f,
                    onValueChange = { temperatureValue = it },
                    onValueChangeFinished = {
                        settingViewModel.updatePlatform(
                            platformData.copy(temperature = temperatureValue)
                        )
                    },
                    icon = Icons.Outlined.Thermostat,
                    enabled = platformData.enabled && !isReasoningDisabled,
                )

                Spacer(Modifier.height(12.dp))

                ReferenceSliderCard(
                    title = stringResource(R.string.top_p),
                    description = stringResource(R.string.top_p_description),
                    value = topPValue,
                    valueText = String.format(Locale.US, "%.2f", topPValue),
                    valueRange = 0f..1f,
                    onValueChange = { topPValue = it },
                    onValueChangeFinished = {
                        settingViewModel.updatePlatform(
                            platformData.copy(topP = topPValue)
                        )
                    },
                    icon = Icons.Outlined.Tune,
                    enabled = platformData.enabled && !isReasoningDisabled,
                )

                PlatformNameDialog(
                    dialogState = dialogState,
                    initialValue = platformData.name,
                    settingViewModel = settingViewModel,
                )
                APIUrlDialog(
                    dialogState = dialogState,
                    initialValue = platformData.apiUrl,
                    settingViewModel = settingViewModel,
                )
                APIKeyDialog(
                    dialogState = dialogState,
                    settingViewModel = settingViewModel,
                )
                ModelDialog(
                    dialogState = dialogState,
                    model = platformData.model,
                    settingViewModel = settingViewModel,
                )
                DeletePlatformDialog(
                    dialogState = dialogState,
                    settingViewModel = settingViewModel,
                )
            }
        }
    }
}
