package com.vibe.app.presentation.ui.setting

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.vibe.app.presentation.common.SettingItem
import com.vibe.app.presentation.ui.components.PlatformTopAppBar
import com.vibe.app.presentation.ui.components.PreferenceSwitchWithContainer
import com.vibe.app.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel =
        hiltViewModel(),
    onNavigationClick: () -> Unit,
) {

    val scrollState =
        rememberScrollState()

    val scrollBehavior =
        pinnedExitUntilCollapsedScrollBehavior(
            canScroll = {
                scrollState.canScrollForward ||
                    scrollState.canScrollBackward
            }
        )

    val platform by
        settingViewModel.platformState
            .collectAsStateWithLifecycle()

    val dialogState by
        settingViewModel.dialogState
            .collectAsStateWithLifecycle()

    val isDeleted by
        settingViewModel.isDeleted
            .collectAsStateWithLifecycle()

    val availableModels by
        settingViewModel.availableModels
            .collectAsStateWithLifecycle()

    val isLoadingModels by
        settingViewModel.isLoadingModels
            .collectAsStateWithLifecycle()

    var isFreeFilter by
        remember(
            platform?.uid
        ) {
            mutableStateOf(
                platform?.isFree
                    ?: true
            )
        }

    var isDropdownExpanded by
        remember(
            platform?.uid
        ) {
            mutableStateOf(false)
        }

    val context =
        LocalContext.current

    val switchedHint =
        stringResource(
            R.string.switched_platform_hint
        )

    /*
     * Only OpenRouter loads its model list
     * dynamically.
     *
     * Google AI Studio and all other providers
     * remain independent from OpenRouter.
     */
    LaunchedEffect(
        platform?.compatibleType,
        isFreeFilter,
        platform?.token,
    ) {

        if (
            platform?.compatibleType ==
            ClientType.OPEN_ROUTER &&
            !platform?.token.isNullOrBlank()
        ) {

            settingViewModel
                .loadModels(
                    isFreeOnly =
                        isFreeFilter
                )

        }
    }

    /*
     * Show provider-switch notification.
     */
    LaunchedEffect(Unit) {

        settingViewModel
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
     * Navigate back after successful deletion.
     */
    LaunchedEffect(isDeleted) {

        if (isDeleted) {

            onNavigationClick()
        }
    }

    platform?.let { platformData ->

        val isGoogleAIStudio =
            platformData.compatibleType ==
                ClientType.GOOGLE_AI_STUDIO

        val isOpenRouter =
            platformData.compatibleType ==
                ClientType.OPEN_ROUTER

        val displayedModel =
            if (
                platformData.model.isBlank()
            ) {

                stringResource(
                    R.string.not_set
                )

            } else {

                platformData.model
            }

        Scaffold(
            modifier =
                modifier,

            topBar = {

                PlatformTopAppBar(
                    title =
                        platformData.name,

                    onBackClick =
                        onNavigationClick,

                    onDeleteClick = {

                        settingViewModel
                            .openDeleteDialog()
                    },

                    scrollBehavior =
                        scrollBehavior,
                )
            },
        ) { paddingValues ->

            Column(
                modifier =
                    Modifier
                        .padding(
                            paddingValues
                        )
                        .verticalScroll(
                            scrollState
                        )
            ) {

                /*
                 * Enable / disable provider.
                 */
                PreferenceSwitchWithContainer(
                    title =
                        stringResource(
                            R.string.enable_api
                        ),

                    isChecked =
                        platformData.enabled,

                    onCheckedChange = {

                        settingViewModel
                            .toggleEnabled()
                    },
                )

                /*
                 * Provider name.
                 */
                SettingItem(
                    modifier =
                        Modifier.height(
                            64.dp
                        ),

                    title =
                        stringResource(
                            R.string.platform_name
                        ),

                    description =
                        platformData.name,

                    enabled =
                        platformData.enabled,

                    onItemClick = {

                        settingViewModel
                            .openPlatformNameDialog()
                    },

                    showTrailingIcon =
                        false,
                )

                /*
                 * API URL.
                 */
                SettingItem(
                    modifier =
                        Modifier.height(
                            64.dp
                        ),

                    title =
                        stringResource(
                            R.string.api_url
                        ),

                    description =
                        platformData.apiUrl,

                    enabled =
                        platformData.enabled,

                    onItemClick = {

                        settingViewModel
                            .openApiUrlDialog()
                    },

                    showTrailingIcon =
                        false,
                )

                /*
                 * API key.
                 */
                SettingItem(
                    modifier =
                        Modifier.height(
                            64.dp
                        ),

                    title =
                        if (
                            isGoogleAIStudio
                        ) {

                            stringResource(
                                R.string.google_ai_studio_api_key
                            )

                        } else {

                            stringResource(
                                R.string.api_key
                            )
                        },

                    description =
                        if (
                            platformData.token
                                .isNullOrBlank()
                        ) {

                            stringResource(
                                R.string.not_set
                            )

                        } else {

                            "${platformData.token!!.take(4)}*****"
                        },

                    enabled =
                        platformData.enabled,

                    onItemClick = {

                        settingViewModel
                            .openApiTokenDialog()
                    },

                    showTrailingIcon =
                        false,
                )

                /*
                 * =================================================
                 * MODEL
                 * =================================================
                 */
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    16.dp,

                                vertical =
                                    8.dp,
                            )
                ) {

                    Text(
                        text =
                            if (
                                isGoogleAIStudio
                            ) {

                                stringResource(
                                    R.string.google_ai_studio_model_description
                                )

                            } else {

                                stringResource(
                                    R.string.api_model
                                )
                            },

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        modifier =
                            Modifier.padding(
                                bottom =
                                    8.dp
                            ),
                    )

                    /*
                     * =================================================
                     * OPENROUTER
                     * =================================================
                     */
                    if (isOpenRouter) {

                        /*
                         * Free / Paid filters.
                         */
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                ),
                        ) {

                            FilterChip(
                                selected =
                                    isFreeFilter,

                                onClick = {

                                    isFreeFilter =
                                        true

                                    isDropdownExpanded =
                                        false
                                },

                                label = {

                                    Text(
                                        "مجاني (Free)"
                                    )
                                },

                                enabled =
                                    platformData.enabled,
                            )

                            FilterChip(
                                selected =
                                    !isFreeFilter,

                                onClick = {

                                    isFreeFilter =
                                        false

                                    isDropdownExpanded =
                                        false
                                },

                                label = {

                                    Text(
                                        "مدفوع (Paid)"
                                    )
                                },

                                enabled =
                                    platformData.enabled,
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        /*
                         * Dynamic OpenRouter model selector.
                         */
                        ExposedDropdownMenuBox(
                            expanded =
                                isDropdownExpanded &&
                                    platformData.enabled,

                            onExpandedChange = {

                                if (
                                    platformData.enabled &&
                                    !isLoadingModels
                                ) {

                                    isDropdownExpanded =
                                        !isDropdownExpanded
                                }
                            },
                        ) {

                            OutlinedTextField(
                                value =
                                    platformData.model,

                                onValueChange = {},

                                readOnly =
                                    true,

                                enabled =
                                    platformData.enabled,

                                label = {

                                    Text(
                                        stringResource(
                                            R.string.api_model
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

                                    if (
                                        isLoadingModels
                                    ) {

                                        CircularProgressIndicator(
                                            modifier =
                                                Modifier.size(
                                                    20.dp
                                                ),

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
                            )

                            ExposedDropdownMenu(
                                expanded =
                                    isDropdownExpanded &&
                                        platformData.enabled,

                                onDismissRequest = {

                                    isDropdownExpanded =
                                        false
                                },
                            ) {

                                when {

                                    /*
                                     * Loading.
                                     */
                                    isLoadingModels -> {

                                        DropdownMenuItem(
                                            text = {

                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth(),

                                                    horizontalArrangement =
                                                        Arrangement.Center,
                                                ) {

                                                    CircularProgressIndicator(
                                                        modifier =
                                                            Modifier.size(
                                                                24.dp
                                                            )
                                                    )
                                                }
                                            },

                                            onClick = {},
                                        )
                                    }

                                    /*
                                     * No models.
                                     */
                                    availableModels
                                        .isEmpty() -> {

                                        DropdownMenuItem(
                                            text = {

                                                Text(
                                                    "لا توجد نماذج متاحة"
                                                )
                                            },

                                            onClick = {

                                                isDropdownExpanded =
                                                    false
                                            },
                                        )
                                    }

                                    /*
                                     * Models.
                                     */
                                    else -> {

                                        availableModels
                                            .forEach { modelInfo ->

                                                val isFree =
                                                    modelInfo
                                                        .pricing
                                                        ?.isFree ==
                                                        true

                                                /*
                                                 * OpenRouter pricing values
                                                 * are USD per token.
                                                 *
                                                 * OpenRouterPricing converts
                                                 * them here to USD / 1K tokens.
                                                 */
                                                val priceText =
                                                    if (isFree) {

                                                        "مجاني"

                                                    } else {

                                                        val pricePer1K =
                                                            modelInfo
                                                                .pricing
                                                                ?.averagePricePer1K

                                                        if (
                                                            pricePer1K !=
                                                            null
                                                        ) {

                                                            "$" +
                                                                String.format(
                                                                    "%.6f",
                                                                    pricePer1K,
                                                                ) +
                                                                " / 1K tokens"

                                                        } else {

                                                            "السعر غير متاح"
                                                        }
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
                                                                    modelInfo
                                                                        .name
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

                                                        /*
                                                         * Save exact model ID.
                                                         *
                                                         * No fallback.
                                                         */
                                                        settingViewModel
                                                            .updateApiModel(
                                                                modelInfo.id
                                                            )

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

                        /*
                         * =================================================
                         * OTHER PROVIDERS
                         * =================================================
                         *
                         * Google AI Studio
                         * OpenAI
                         * Anthropic
                         * Qwen
                         * Kimi
                         * MiniMax
                         * DeepSeek
                         * Custom
                         *
                         * use manually entered model IDs.
                         */
                        SettingItem(
                            modifier =
                                Modifier.height(
                                    64.dp
                                ),

                            title =
                                if (
                                    isGoogleAIStudio
                                ) {

                                    stringResource(
                                        R.string.google_ai_studio_model_description
                                    )

                                } else {

                                    stringResource(
                                        R.string.api_model
                                    )
                                },

                            description =
                                displayedModel,

                            enabled =
                                platformData.enabled,

                            onItemClick = {

                                settingViewModel
                                    .openApiModelDialog()
                            },

                            showTrailingIcon =
                                false,
                        )
                    }
                }

                /*
                 * =================================================
                 * PARAMETERS
                 * =================================================
                 */

                val isReasoningDisabled =
                    platformData.compatibleType ==
                        ClientType.OPENAI &&
                        platformData.reasoning

                val notSetText =
                    stringResource(
                        R.string.not_set
                    )

                /*
                 * Temperature.
                 */
                SettingItem(
                    modifier =
                        Modifier.height(
                            64.dp
                        ),

                    title =
                        stringResource(
                            R.string.temperature
                        ),

                    description =
                        platformData.temperature
                            ?.toString()
                            ?: notSetText,

                    enabled =
                        platformData.enabled &&
                            !isReasoningDisabled,

                    onItemClick = {

                        settingViewModel
                            .openTemperatureDialog()
                    },

                    showTrailingIcon =
                        false,
                )

                /*
                 * Top P.
                 */
                SettingItem(
                    modifier =
                        Modifier.height(
                            64.dp
                        ),

                    title =
                        stringResource(
                            R.string.top_p
                        ),

                    description =
                        platformData.topP
                            ?.toString()
                            ?: notSetText,

                    enabled =
                        platformData.enabled &&
                            !isReasoningDisabled,

                    onItemClick = {

                        settingViewModel
                            .openTopPDialog()
                    },

                    showTrailingIcon =
                        false,
                )

                /*
                 * =================================================
                 * DIALOGS
                 * =================================================
                 */

                PlatformNameDialog(
                    dialogState =
                        dialogState,

                    initialValue =
                        platformData.name,

                    settingViewModel =
                        settingViewModel,
                )

                APIUrlDialog(
                    dialogState =
                        dialogState,

                    initialValue =
                        platformData.apiUrl,

                    settingViewModel =
                        settingViewModel,
                )

                APIKeyDialog(
                    dialogState =
                        dialogState,

                    settingViewModel =
                        settingViewModel,
                )

                ModelDialog(
                    dialogState =
                        dialogState,

                    model =
                        platformData.model,

                    settingViewModel =
                        settingViewModel,
                )

                TemperatureDialog(
                    dialogState =
                        dialogState,

                    temperature =
                        platformData.temperature,

                    settingViewModel =
                        settingViewModel,
                )

                TopPDialog(
                    dialogState =
                        dialogState,

                    topP =
                        platformData.topP,

                    settingViewModel =
                        settingViewModel,
                )

                DeletePlatformDialog(
                    dialogState =
                        dialogState,

                    settingViewModel =
                        settingViewModel,
                )
            }
        }
    }
}
