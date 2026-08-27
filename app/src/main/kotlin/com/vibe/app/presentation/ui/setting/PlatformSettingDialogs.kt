package com.vibe.app.presentation.ui.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.util.isValidUrl
import kotlin.math.roundToInt

@Composable
fun PlatformNameDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    initialValue: String,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isPlatformNameDialogOpen) {
        PlatformNameDialog(
            initialValue = initialValue,
            onDismissRequest = settingViewModel::closePlatformNameDialog,
            onConfirmRequest = settingViewModel::updatePlatformName
        )
    }
}

@Composable
fun APIUrlDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    initialValue: String,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isApiUrlDialogOpen) {
        APIUrlDialog(
            initialValue = initialValue,
            onDismissRequest = settingViewModel::closeApiUrlDialog,
            onConfirmRequest = settingViewModel::updateApiUrl
        )
    }
}

@Composable
fun APIKeyDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isApiTokenDialogOpen) {
        APIKeyDialog(
            onDismissRequest = settingViewModel::closeApiTokenDialog,
            onConfirmRequest = settingViewModel::updateApiToken
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    model: String,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isApiModelDialogOpen) {
        ModelDialog(
            initModel = model,
            settingViewModel = settingViewModel,
            onDismissRequest = settingViewModel::closeApiModelDialog,
            onConfirmRequest = settingViewModel::updateApiModel
        )
    }
}

@Composable
fun TemperatureDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    temperature: Float?,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isTemperatureDialogOpen) {
        TemperatureDialog(
            temperature = temperature,
            onDismissRequest = settingViewModel::closeTemperatureDialog,
            onConfirmRequest = settingViewModel::updateTemperature
        )
    }
}

@Composable
fun TopPDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    topP: Float?,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isTopPDialogOpen) {
        TopPDialog(
            topP = topP,
            onDismissRequest = settingViewModel::closeTopPDialog,
            onConfirmRequest = settingViewModel::updateTopP
        )
    }
}

@Composable
fun SystemPromptDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    systemPrompt: String,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isSystemPromptDialogOpen) {
        SystemPromptDialog(
            prompt = systemPrompt,
            onDismissRequest = settingViewModel::closeSystemPromptDialog,
            onConfirmRequest = settingViewModel::updateSystemPrompt
        )
    }
}

@Composable
private fun PlatformNameDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    var platformName by remember { mutableStateOf(initialValue) }

    AlertDialog(
        title = {
            Text(stringResource(R.string.platform_name))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = platformName,
                onValueChange = { platformName = it },
                label = {
                    Text(stringResource(R.string.platform_name))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                supportingText = {
                    Text(
                        stringResource(
                            R.string.platform_name_supporting
                        )
                    )
                }
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = platformName.isNotBlank(),
                onClick = {
                    onConfirmRequest(platformName)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun APIUrlDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    var apiUrl by remember { mutableStateOf(initialValue) }

    AlertDialog(
        title = {
            Text(stringResource(R.string.api_url))
        },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.api_url_cautions
                    )
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    value = apiUrl,
                    onValueChange = {
                        apiUrl = it
                    },
                    singleLine = true,
                    isError =
                        apiUrl.isNotBlank() &&
                            !apiUrl.isValidUrl(),
                    label = {
                        Text(
                            stringResource(
                                R.string.api_url
                            )
                        )
                    },
                    supportingText = {
                        if (
                            apiUrl.isNotBlank() &&
                            !apiUrl.isValidUrl()
                        ) {
                            Text(
                                stringResource(
                                    R.string.invalid_api_url
                                )
                            )
                        }
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled =
                    apiUrl.isNotBlank() &&
                        apiUrl.isValidUrl() &&
                        apiUrl.endsWith("/"),
                onClick = {
                    onConfirmRequest(apiUrl)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun APIKeyDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    var token by remember {
        mutableStateOf("")
    }

    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.api_key
                )
            )
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = token,
                onValueChange = {
                    token = it
                },
                label = {
                    Text(
                        stringResource(
                            R.string.api_key
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                )
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = token.isNotBlank(),
                onClick = {
                    onConfirmRequest(token)
                }
            ) {
                Text(
                    stringResource(
                        R.string.confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDialog(
    initModel: String,
    settingViewModel: PlatformSettingViewModel,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    var selectedModel by remember {
        mutableStateOf(initModel)
    }

    var isFreeOnly by remember {
        mutableStateOf(true)
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    val modelsList by
        settingViewModel.availableModels
            .collectAsStateWithLifecycle()

    val isLoading by
        settingViewModel.isLoadingModels
            .collectAsStateWithLifecycle()

    LaunchedEffect(isFreeOnly) {
        settingViewModel.loadModels(
            isFreeOnly
        )
    }

    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.api_model
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    FilterChip(
                        modifier =
                            Modifier.weight(1f),
                        selected = isFreeOnly,
                        onClick = {
                            isFreeOnly = true
                        },
                        label = {
                            Text("مجاني (Free)")
                        }
                    )

                    FilterChip(
                        modifier =
                            Modifier.weight(1f),
                        selected = !isFreeOnly,
                        onClick = {
                            isFreeOnly = false
                        },
                        label = {
                            Text("مدفوع (Paid)")
                        }
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        expanded = !expanded
                    }
                ) {
                    OutlinedTextField(
                        modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                stringResource(
                                    R.string.model_name
                                )
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults
                                .TrailingIcon(
                                    expanded = expanded
                                )
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        when {
                            isLoading -> {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier =
                                                    Modifier.size(
                                                        24.dp
                                                    )
                                            )
                                        }
                                    },
                                    onClick = {}
                                )
                            }

                            modelsList.isEmpty() -> {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "لا توجد نماذج متاحة"
                                        )
                                    },
                                    onClick = {}
                                )
                            }

                            else -> {
                                modelsList.forEach { model ->
                                    val priceLabel =
                                        if (isFreeOnly) {
                                            "مجاني"
                                        } else {
                                            "$${
                                                model.pricing
                                                    ?.averagePrice
                                                    ?: 0.0
                                            } / 1K"
                                        }

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier =
                                                    Modifier.fillMaxWidth(),
                                                horizontalArrangement =
                                                    Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text =
                                                        model.name
                                                            ?: model.id,
                                                    modifier =
                                                        Modifier.weight(
                                                            1f
                                                        )
                                                )

                                                Spacer(
                                                    modifier =
                                                        Modifier.width(
                                                            8.dp
                                                        )
                                                )

                                                Text(
                                                    text =
                                                        priceLabel,
                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodySmall
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedModel =
                                                model.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest =
            onDismissRequest,
        confirmButton = {
            TextButton(
                enabled =
                    selectedModel.isNotBlank(),
                onClick = {
                    onConfirmRequest(
                        selectedModel
                    )
                }
            ) {
                Text(
                    stringResource(
                        R.string.confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

@Composable
private fun TemperatureDialog(
    temperature: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Float?) -> Unit
) {
    var textFieldTemperature by
        remember {
            mutableStateOf(
                temperature?.let {
                    "%.1f".format(it)
                } ?: ""
            )
        }

    var sliderTemperature by
        remember {
            mutableFloatStateOf(
                temperature ?: 1F
            )
        }

    var isUnset by
        remember {
            mutableStateOf(
                temperature == null
            )
        }

    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.temperature_setting
                )
            )
        },
        text = {
            Column(
                modifier =
                    Modifier.verticalScroll(
                        rememberScrollState()
                    )
            ) {
                Text(
                    stringResource(
                        R.string.temperature_setting_description
                    )
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 16.dp
                            ),
                    value =
                        textFieldTemperature,
                    onValueChange = { value ->
                        textFieldTemperature =
                            value

                        if (value.isBlank()) {
                            isUnset = true
                        } else {
                            value.toFloatOrNull()?.let {
                                sliderTemperature =
                                    it.coerceIn(
                                        0F,
                                        2F
                                    )

                                isUnset = false
                            }
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number
                        ),
                    label = {
                        Text(
                            stringResource(
                                R.string.temperature
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                R.string.not_set
                            )
                        )
                    }
                )

                Slider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 16.dp
                            ),
                    value =
                        sliderTemperature,
                    valueRange =
                        0F..2F,
                    steps = 19,
                    enabled = !isUnset,
                    onValueChange = {
                        val rounded =
                            (
                                it * 10
                            )
                                .roundToInt()
                                .div(10F)

                        sliderTemperature =
                            rounded

                        textFieldTemperature =
                            "%.1f".format(
                                rounded
                            )

                        isUnset = false
                    }
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            textFieldTemperature =
                                ""

                            isUnset = true
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.reset
                            )
                        )
                    }
                }
            }
        },
        onDismissRequest =
            onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmRequest(
                        if (isUnset) {
                            null
                        } else {
                            sliderTemperature
                        }
                    )
                }
            ) {
                Text(
                    stringResource(
                        R.string.confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

@Composable
private fun TopPDialog(
    topP: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Float?) -> Unit
) {
    var textFieldTopP by
        remember {
            mutableStateOf(
                topP?.let {
                    "%.2f".format(it)
                } ?: ""
            )
        }

    var sliderTopP by
        remember {
            mutableFloatStateOf(
                topP ?: 1F
            )
        }

    var isUnset by
        remember {
            mutableStateOf(
                topP == null
            )
        }

    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.top_p_setting
                )
            )
        },
        text = {
            Column(
                modifier =
                    Modifier.verticalScroll(
                        rememberScrollState()
                    )
            ) {
                Text(
                    stringResource(
                        R.string.top_p_setting_description
                    )
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 16.dp
                            ),
                    value = textFieldTopP,
                    onValueChange = { value ->
                        textFieldTopP = value

                        if (value.isBlank()) {
                            isUnset = true
                        } else {
                            value.toFloatOrNull()?.let {
                                val rounded =
                                    (
                                        it.coerceIn(
                                            0.1F,
                                            1F
                                        ) * 100
                                    )
                                        .roundToInt()
                                        .div(100F)

                                sliderTopP =
                                    rounded

                                isUnset = false
                            }
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number
                        ),
                    label = {
                        Text(
                            stringResource(
                                R.string.top_p
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                R.string.not_set
                            )
                        )
                    }
                )

                Slider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 16.dp
                            ),
                    value = sliderTopP,
                    valueRange =
                        0.1F..1F,
                    steps = 89,
                    enabled = !isUnset,
                    onValueChange = {
                        val rounded =
                            (
                                it * 100
                            )
                                .roundToInt()
                                .div(100F)

                        sliderTopP =
                            rounded

                        textFieldTopP =
                            "%.2f".format(
                                rounded
                            )

                        isUnset = false
                    }
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            textFieldTopP =
                                ""

                            isUnset = true
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.reset
                            )
                        )
                    }
                }
            }
        },
        onDismissRequest =
            onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmRequest(
                        if (isUnset) {
                            null
                        } else {
                            sliderTopP
                        }
                    )
                }
            ) {
                Text(
                    stringResource(
                        R.string.confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

@Composable
private fun SystemPromptDialog(
    prompt: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    var textFieldPrompt by
        remember {
            mutableStateOf(prompt)
        }

    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.system_prompt_setting
                )
            )
        },
        text = {
            Column(
                modifier =
                    Modifier.verticalScroll(
                        rememberScrollState()
                    )
            ) {
                Text(
                    stringResource(
                        R.string.system_prompt_description
                    )
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 16.dp
                            ),
                    value = textFieldPrompt,
                    onValueChange = {
                        textFieldPrompt = it
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.system_prompt
                            )
                        )
                    }
                )
            }
        },
        onDismissRequest =
            onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmRequest(
                        textFieldPrompt
                    )
                }
            ) {
                Text(
                    stringResource(
                        R.string.confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

@Composable
fun DeletePlatformDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isDeleteDialogOpen) {
        DeletePlatformDialog(
            onDismissRequest =
                settingViewModel::closeDeleteDialog,
            onConfirmRequest =
                settingViewModel::deletePlatform
        )
    }
}

@Composable
private fun DeletePlatformDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit
) {
    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.delete_platform
                )
            )
        },
        text = {
            Text(
                stringResource(
                    R.string.delete_platform_confirmation
                )
            )
        },
        onDismissRequest =
            onDismissRequest,
        confirmButton = {
            TextButton(
                onClick =
                    onConfirmRequest
            ) {
                Text(
                    stringResource(
                        R.string.delete_platform
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick =
                    onDismissRequest
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}
