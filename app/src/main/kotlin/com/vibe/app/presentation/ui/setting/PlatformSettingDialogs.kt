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
import com.vibe.app.R
import com.vibe.app.data.dto.OpenRouterModel
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
            onConfirmRequest = { name ->
                settingViewModel.updatePlatformName(name)
            }
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
            onConfirmRequest = { apiUrl ->
                settingViewModel.updateApiUrl(apiUrl)
            }
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
            onDismissRequest = settingViewModel::closeApiTokenDialog
        ) { apiToken ->
            settingViewModel.updateApiToken(apiToken)
        }
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
            onDismissRequest = settingViewModel::closeApiModelDialog
        ) { m ->
            settingViewModel.updateApiModel(m)
        }
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
            onDismissRequest = settingViewModel::closeTemperatureDialog
        ) { temp ->
            settingViewModel.updateTemperature(temp)
        }
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
            onDismissRequest = settingViewModel::closeTopPDialog
        ) { p ->
            settingViewModel.updateTopP(p)
        }
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
            onDismissRequest = settingViewModel::closeSystemPromptDialog
        ) {
            settingViewModel.updateSystemPrompt(it)
        }
    }
}

@Composable
private fun PlatformNameDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (name: String) -> Unit
) {
    var platformName by remember { mutableStateOf(initialValue) }
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.platform_name)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = platformName,
                onValueChange = { platformName = it },
                label = { Text(stringResource(R.string.platform_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = {
                    Text(stringResource(R.string.platform_name_supporting))
                }
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = platformName.isNotBlank(),
                onClick = { onConfirmRequest(platformName) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun APIUrlDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (url: String) -> Unit
) {
    var apiUrl by remember { mutableStateOf(initialValue) }
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.api_url)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.api_url_cautions)
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    value = apiUrl,
                    singleLine = true,
                    isError = apiUrl.isValidUrl().not(),
                    onValueChange = { apiUrl = it },
                    label = {
                        Text(stringResource(R.string.api_url))
                    },
                    supportingText = {
                        if (apiUrl.isValidUrl().not()) {
                            Text(text = stringResource(R.string.invalid_api_url))
                        }
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = apiUrl.isNotBlank() && apiUrl.isValidUrl() && apiUrl.endsWith("/"),
                onClick = { onConfirmRequest(apiUrl) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun APIKeyDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (token: String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.api_key)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.api_key)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(token) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDialog(
    initModel: String,
    settingViewModel: PlatformSettingViewModel,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (model: String) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }

    var selectedModel by remember { mutableStateOf(initModel) }
    var isFreeOnly by remember { mutableStateOf(true) }
    var modelsList by remember { mutableStateOf<List<OpenRouterModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(isFreeOnly) {
        isLoading = true
        modelsList = settingViewModel.fetchOpenRouterModels(isFreeOnly)
        isLoading = false
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.api_model)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isFreeOnly,
                        onClick = { isFreeOnly = true },
                        label = { Text("مجاني (Free)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isFreeOnly,
                        onClick = { isFreeOnly = false },
                        label = { Text("مدفوع (Paid)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.model_name)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        supportingText = {
                            Text(stringResource(R.string.model_supporting))
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (isLoading) {
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                },
                                onClick = {}
                            )
                        } else if (modelsList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("لا توجد نماذج متاحة") },
                                onClick = {}
                            )
                        } else {
                            modelsList.forEach { model ->
                                val priceLabel = if (isFreeOnly) {
                                    "مجاني"
                                } else {
                                    "$${model.pricing?.averagePrice ?: 0.0}/1K"
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = model.id, modifier = Modifier.weight(1f))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = priceLabel, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        selectedModel = model.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = selectedModel.isNotBlank(),
                onClick = { onConfirmRequest(selectedModel) }
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
private fun TemperatureDialog(
    temperature: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (temp: Float?) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var textFieldTemperature by remember { mutableStateOf(temperature?.let { "%.1f".format(it) } ?: "") }
    var sliderTemperature by remember { mutableFloatStateOf(temperature ?: 1F) }
    var isUnset by remember { mutableStateOf(temperature == null) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.temperature_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.temperature_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTemperature,
                    onValueChange = { t ->
                        textFieldTemperature = t
                        if (t.isBlank()) {
                            isUnset = true
                        } else {
                            val converted = t.toFloatOrNull()
                            converted?.let {
                                sliderTemperature = it.coerceIn(0F, 2F)
                                isUnset = false
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.temperature))
                    },
                    placeholder = {
                        Text(stringResource(R.string.not_set))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTemperature,
                    valueRange = 0F..2F,
                    steps = 19,
                    enabled = !isUnset,
                    onValueChange = { t ->
                        val rounded = (t * 10).roundToInt() / 10F
                        sliderTemperature = rounded
                        textFieldTemperature = "%.1f".format(rounded)
                        isUnset = false
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            textFieldTemperature = ""
                            isUnset = true
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(if (isUnset) null else sliderTemperature) }
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
private fun TopPDialog(
    topP: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (topP: Float?) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var textFieldTopP by remember { mutableStateOf(topP?.let { "%.1f".format(it) } ?: "") }
    var sliderTopP by remember { mutableFloatStateOf(topP ?: 1F) }
    var isUnset by remember { mutableStateOf(topP == null) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.top_p_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.top_p_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTopP,
                    onValueChange = { p ->
                        textFieldTopP = p
                        if (p.isBlank()) {
                            isUnset = true
                        } else {
                            p.toFloatOrNull()?.let {
                                val rounded = (it.coerceIn(0.1F, 1F) * 100).roundToInt() / 100F
                                sliderTopP = rounded
                                isUnset = false
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.top_p))
                    },
                    placeholder = {
                        Text(stringResource(R.string.not_set))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTopP,
                    valueRange = 0.1F..1F,
                    steps = 89,
                    enabled = !isUnset,
                    onValueChange = { t ->
                        val rounded = (t * 100).roundToInt() / 100F
                        sliderTopP = rounded
                        textFieldTopP = "%.2f".format(rounded)
                        isUnset = false
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            textFieldTopP = ""
                            isUnset = true
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(if (isUnset) null else sliderTopP) }
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
private fun SystemPromptDialog(
    prompt: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (text: String) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var textFieldPrompt by remember { mutableStateOf(prompt) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.system_prompt_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.system_prompt_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldPrompt,
                    onValueChange = { textFieldPrompt = it },
                    label = {
                        Text(stringResource(R.string.system_prompt))
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(textFieldPrompt) }
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
fun DeletePlatformDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    settingViewModel: PlatformSettingViewModel
) {
    if (dialogState.isDeleteDialogOpen) {
        DeletePlatformDialog(
            onDismissRequest = settingViewModel::closeDeleteDialog,
            onConfirmRequest = settingViewModel::deletePlatform
        )
    }
}

@Composable
private fun DeletePlatformDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = stringResource(R.string.delete_platform)) },
        text = {
            Text(stringResource(R.string.delete_platform_confirmation))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirmRequest) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
