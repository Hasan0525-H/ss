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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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

    val platform by settingViewModel.platformState
        .collectAsStateWithLifecycle()

    val dialogState by settingViewModel.dialogState
        .collectAsStateWithLifecycle()

    val isDeleted by settingViewModel.isDeleted
        .collectAsStateWithLifecycle()

    val availableModels by settingViewModel.availableModels
        .collectAsStateWithLifecycle()

    val isLoadingModels by settingViewModel.isLoadingModels
        .collectAsStateWithLifecycle()

    var isFreeFilter by remember {
        mutableStateOf(true)
    }

    var isDropdownExpanded by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current

    val switchedHint =
        stringResource(R.string.switched_platform_hint)

    /*
     * تحميل الموديلات عند وجود API Key
     * وعند تغيير نوع الخطة Free / Paid.
     */
    LaunchedEffect(
        isFreeFilter,
        platform?.token
    ) {
        if (!platform?.token.isNullOrBlank()) {
            settingViewModel.loadModels(isFreeFilter)
        }
    }

    /*
     * إشعار عند تبديل المنصة.
     */
    LaunchedEffect(Unit) {
        settingViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(
                context,
                switchedHint.format(name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /*
     * الرجوع تلقائيًا بعد حذف المنصة.
     */
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
                    onNavigationClick = onNavigationClick,
                    onDeleteClick =
                        settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->

            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
            ) {

                /*
                 * Enable / Disable API
                 */
                PreferenceSwitchWithContainer(
                    title = stringResource(
                        R.string.enable_api
                    ),
                    isChecked = platformData.enabled,
                    onCheckedChange = {
                        settingViewModel.toggleEnabled()
                    }
                )

                /*
                 * Platform Name
                 */
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(
                        R.string.platform_name
                    ),
                    description = platformData.name,
                    enabled = platformData.enabled,
                    onItemClick =
                        settingViewModel::openPlatformNameDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Label,
                            contentDescription =
                                stringResource(
                                    R.string.platform_name_icon
                                )
                        )
                    }
                )

                /*
                 * API URL
                 */
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(
                        R.string.api_url
                    ),
                    description = platformData.apiUrl,
                    enabled = platformData.enabled,
                    onItemClick =
                        settingViewModel::openApiUrlDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription =
                                stringResource(
                                    R.string.url_icon
                                )
                        )
                    }
                )

                /*
                 * API Key
                 */
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(
                        R.string.api_key
                    ),
                    description =
                        if (platformData.token.isNullOrBlank()) {
                            stringResource(
                                R.string.not_set
                            )
                        } else {
                            stringResource(
                                R.string.token_set,
                                "*"
                            )
                        },
                    enabled = platformData.enabled,
                    onItemClick =
                        settingViewModel::openApiTokenDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription =
                                stringResource(
                                    R.string.key_icon
                                )
                        )
                    }
                )

                /*
                 * Model selector
                 */
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                ) {

                    Text(
                        text = stringResource(
                            R.string.model
                        ),
                        style =
                            MaterialTheme.typography.titleMedium,
                        modifier =
                            Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        FilterChip(
                            selected = isFreeFilter,
                            onClick = {
                                isFreeFilter = true
                            },
                            label = {
                                Text("مجاني (Free)")
                            },
                            enabled = platformData.enabled
                        )

                        FilterChip(
                            selected = !isFreeFilter,
                            onClick = {
                                isFreeFilter = false
                            },
                            label = {
                                Text("مدفوع (Paid)")
                            },
                            enabled = platformData.enabled
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded =
                            isDropdownExpanded &&
                                platformData.enabled,

                        onExpandedChange = {
                            if (platformData.enabled) {
                                isDropdownExpanded =
                                    !isDropdownExpanded
                            }
                        }
                    ) {

                        OutlinedTextField(
                            value = platformData.model,
                            onValueChange = {},
                            readOnly = true,
                            enabled = platformData.enabled,

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

                            leadingIcon = {
                                Icon(
                                    imageVector =
                                        Icons.Outlined.Memory,
                                    contentDescription =
                                        stringResource(
                                            R.string.model_icon
                                        )
                                )
                            },

                            trailingIcon = {

                                if (isLoadingModels) {

                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(
                                                20.dp
                                            ),
                                        strokeWidth = 2.dp
                                    )

                                } else {

                                    ExposedDropdownMenuDefaults
                                        .TrailingIcon(
                                            expanded =
                                                isDropdownExpanded
                                        )
                                }
                            },

                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),

                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded =
                                isDropdownExpanded &&
                                    platformData.enabled,

                            onDismissRequest = {
                                isDropdownExpanded = false
                            }
                        ) {

                            if (
                                availableModels.isEmpty() &&
                                !isLoadingModels
                            ) {

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "لا توجد نماذج متاحة"
                                        )
                                    },
                                    onClick = {
                                        isDropdownExpanded =
                                            false
                                    }
                                )

                            } else {

                                availableModels.forEach { modelInfo ->

                                    val priceText =
                                        if (
                                            modelInfo.pricing
                                                ?.isFree == true
                                        ) {
                                            "مجاني"
                                        } else {
                                            "\$${String.format(
                                                "%.6f",
                                                modelInfo.pricing
                                                    ?.averagePrice
                                                    ?: 0.0
                                            )} / 1K tokens"
                                        }

                                    DropdownMenuItem(

                                        text = {

                                            Column {

                                                Text(
                                                    text =
                                                        modelInfo.name
                                                            ?: modelInfo.id,

                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodyLarge
                                                )

                                                Text(
                                                    text = priceText,

                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodySmall,

                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurfaceVariant
                                                )
                                            }
                                        },

                                        onClick = {

                                            settingViewModel
                                                .updateApiModel(
                                                    modelInfo.id
                                                )

                                            isDropdownExpanded =
                                                false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                /*
                 * Reasoning models:
                 * Temperature و Top P يتم تعطيلهما
                 * عندما يكون reasoning مفعّلًا لـ OpenAI.
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
                 * Temperature
                 */
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(
                        R.string.temperature
                    ),
                    description =
                        platformData.temperature
                            ?.toString()
                            ?: notSetText,

                    enabled =
                        platformData.enabled &&
                            !isReasoningDisabled,

                    onItemClick =
                        settingViewModel::openTemperatureDialog,

                    showTrailingIcon = false,
                    showLeadingIcon = true,

                    leadingIcon = {
                        Icon(
                            imageVector =
                                Icons.Outlined.Tune,
                            contentDescription =
                                stringResource(
                                    R.string.temperature_icon
                                )
                        )
                    }
                )

                /*
                 * Top P
                 */
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(
                        R.string.top_p
                    ),
                    description =
                        platformData.topP
                            ?.toString()
                            ?: notSetText,

                    enabled =
                        platformData.enabled &&
                            !isReasoningDisabled,

                    onItemClick =
                        settingViewModel::openTopPDialog,

                    showTrailingIcon = false,
                    showLeadingIcon = true,

                    leadingIcon = {
                        Icon(
                            imageVector =
                                Icons.Outlined.Tune,
                            contentDescription =
                                stringResource(
                                    R.string.top_p_icon
                                )
                        )
                    }
                )

                /*
                 * Dialogs
                 */
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

                ModelDialog(
                    dialogState = dialogState,
                    model = platformData.model,
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
