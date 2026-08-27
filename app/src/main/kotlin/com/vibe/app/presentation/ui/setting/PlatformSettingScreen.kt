package com.vibe.app.presentation.ui.setting

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.vibe.app.presentation.common.SettingItem
import com.vibe.app.presentation.ui.components.ModelCatalogSelector
import com.vibe.app.presentation.ui.components.PlatformTopAppBar
import com.vibe.app.presentation.ui.components.PreferenceSwitchWithContainer
import com.vibe.app.util.pinnedExitUntilCollapsedScrollBehavior

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
                    .padding(bottom = 24.dp)
            ) {
                PreferenceSwitchWithContainer(
                    title = stringResource(R.string.enable_api),
                    isChecked = platformData.enabled,
                    onCheckedChange = { settingViewModel.toggleEnabled() },
                )

                SettingsCard {
                    SettingItem(
                        modifier = Modifier.height(72.dp),
                        title = stringResource(R.string.platform_name),
                        description = platformData.name,
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openPlatformNameDialog,
                        showTrailingIcon = false,
                    )
                    SettingsDivider()
                    SettingItem(
                        modifier = Modifier.height(72.dp),
                        title = stringResource(R.string.api_url),
                        description = platformData.apiUrl,
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openApiUrlDialog,
                        showTrailingIcon = false,
                    )
                    SettingsDivider()
                    SettingItem(
                        modifier = Modifier.height(72.dp),
                        title = if (isGoogleAIStudio) {
                            stringResource(R.string.google_ai_studio_api_key)
                        } else {
                            stringResource(R.string.api_key)
                        },
                        description = if (platformData.token.isNullOrBlank()) {
                            stringResource(R.string.not_set)
                        } else {
                            "${platformData.token!!.take(4)}*****"
                        },
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openApiTokenDialog,
                        showTrailingIcon = false,
                    )
                }

                SettingsCard {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (isGoogleAIStudio) {
                                stringResource(R.string.google_ai_studio_model_description)
                            } else {
                                stringResource(R.string.api_model)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )

                        Spacer(Modifier.height(12.dp))

                        if (isCatalogProvider) {
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
                                        settingViewModel.loadModels(
                                            isFreeOnly = isFree
                                        )
                                    }
                                },
                                onModelSelected = { modelInfo ->
                                    settingViewModel.updateApiModel(modelInfo.id)
                                },
                            )
                        } else {
                            SettingItem(
                                modifier = Modifier.height(72.dp),
                                title = stringResource(R.string.api_model),
                                description = displayedModel,
                                enabled = platformData.enabled,
                                onItemClick = settingViewModel::openApiModelDialog,
                                showTrailingIcon = false,
                            )
                        }
                    }
                }

                val isReasoningDisabled =
                    platformData.compatibleType == ClientType.OPENAI &&
                        platformData.reasoning
                val notSetText = stringResource(R.string.not_set)

                SettingsCard {
                    SettingItem(
                        modifier = Modifier.height(72.dp),
                        title = stringResource(R.string.temperature),
                        description = platformData.temperature?.toString() ?: notSetText,
                        enabled = platformData.enabled && !isReasoningDisabled,
                        onItemClick = settingViewModel::openTemperatureDialog,
                        showTrailingIcon = false,
                    )
                    SettingsDivider()
                    SettingItem(
                        modifier = Modifier.height(72.dp),
                        title = stringResource(R.string.top_p),
                        description = platformData.topP?.toString() ?: notSetText,
                        enabled = platformData.enabled && !isReasoningDisabled,
                        onItemClick = settingViewModel::openTopPDialog,
                        showTrailingIcon = false,
                    )
                }

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
                TemperatureDialog(
                    dialogState = dialogState,
                    temperature = platformData.temperature,
                    settingViewModel = settingViewModel,
                )
                TopPDialog(
                    dialogState = dialogState,
                    topP = platformData.topP,
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

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
