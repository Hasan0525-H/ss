package com.vibe.app.presentation.ui.setting

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    val scrollBehavior =
        pinnedExitUntilCollapsedScrollBehavior(
            canScroll = {
                scrollState.canScrollForward ||
                        scrollState.canScrollBackward
            }
        )

    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()

    val availableModels by settingViewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by settingViewModel.isLoadingModels.collectAsStateWithLifecycle()

    var isFreeFilter by remember { mutableStateOf(true) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)

    LaunchedEffect(isFreeFilter, platform?.token) {
        if (!platform?.token.isNullOrBlank()) {
            settingViewModel.loadModels(isFreeFilter)
        }
    }

    LaunchedEffect(Unit) {
        settingViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(
                context,
                switchedHint.format(name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            onNavigationClick()
        }
    }

    platform?.let { platformData ->
        Scaffold(
            modifier = modifier,
            topBar = {
                PlatformTopAppBar(
                    title = platformData.name,
                    onBackClick = onNavigationClick,
                    onDeleteClick = { settingViewModel.openDeleteDialog() }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(scrollState)
            ) {
                PreferenceSwitchWithContainer(
                    title = stringResource(R.string.enable_api),
                    isChecked = platformData.enabled,
                    onCheckedChange = {
                        settingViewModel.toggleEnabled()
                    }
                )

                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.platform_name),
                    description = platformData.name,
                    enabled = platformData.enabled,
                    onItemClick = { settingViewModel.openPlatformNameDialog() }
                )

                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_url),
                    description = platformData.apiUrl,
                    enabled = platformData.enabled,
                    onItemClick = { settingViewModel.openApiUrlDialog() }
                )

                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_key),
                    description = if (platformData.token.isNullOrBlank())
                        stringResource(R.string.not_set)
                    else
                        "Configured",
                    enabled = platformData.enabled,
                    onItemClick = { settingViewModel.openApiTokenDialog() }
                )

                // --- قائمة اختيار الموديل مع التصفية التلقائية ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.model),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isFreeFilter,
                            onClick = { isFreeFilter = true },
                            label = { Text("مجاني (Free)") },
                            enabled = platformData.enabled
                        )
                        FilterChip(
                            selected = !isFreeFilter,
                            onClick = { isFreeFilter = false },
                            label = { Text("مدفوع (Paid)") },
                            enabled = platformData.enabled
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded && platformData.enabled,
                        onExpandedChange = {
                            if (platformData.enabled) isDropdownExpanded = !isDropdownExpanded
                        }
                    ) {
                        OutlinedTextField(
                            value = platformData.model,
                            onValueChange = {},
                            readOnly = true,
                            enabled = platformData.enabled,
                            label = { Text(stringResource(R.string.model)) },
                            trailingIcon = {
                                if (isLoadingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                                }
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded && platformData.enabled,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            if (availableModels.isEmpty() && !isLoadingModels) {
                                DropdownMenuItem(
                                    text = { Text("لا توجد نماذج متاحة") },
                                    onClick = { isDropdownExpanded = false }
                                )
                            } else {
                                availableModels.forEach { model ->
                                    val priceText = if (model.pricing?.isFree == true) {
                                        "مجاني"
                                    } else {
                                        "\$${String.format("%.6f", model.pricing?.averagePrice)} / 1K tokens"
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.name ?: model.id,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = priceText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            settingViewModel.updateApiModel(model.id)
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                val reasoningDisabled =
                    platformData.compatibleType == ClientType.OPENAI &&
                            platformData.reasoning

                val notSet = stringResource(R.string.not_set)

                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.temperature),
                    description = platformData.temperature?.toString() ?: notSet,
                    enabled = platformData.enabled && !reasoningDisabled,
                    onItemClick = { settingViewModel.openTemperatureDialog() }
                )

                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.top_p),
                    description = platformData.topP?.toString() ?: notSet,
                    enabled = platformData.enabled && !reasoningDisabled,
                    onItemClick = { settingViewModel.openTopPDialog() }
                )

                PlatformNameDialog(
                    dialogState = dialogState,
                    initialValue = platformData.name,
                    settingViewModel = settingViewModel
                )

                APIUrlDialog(
                    dialogState = dialogState,
                    initialValue = platformData.apiUrl,
                    settingViewModel = settingViewModel
                )

                APIKeyDialog(
                    dialogState = dialogState,
                    settingViewModel = settingViewModel
                )

                TemperatureDialog(
                    dialogState = dialogState,
                    temperature = platformData.temperature,
                    settingViewModel = settingViewModel
                )

                TopPDialog(
                    dialogState = dialogState,
                    topP = platformData.topP,
                    settingViewModel = settingViewModel
                )

                DeletePlatformDialog(
                    dialogState = dialogState,
                    settingViewModel = settingViewModel
                )
            }
        }
    }
}
