package com.vibe.app.presentation.ui.setting

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.vibe.app.R
import com.vibe.app.data.model.ClientType
import com.vibe.app.presentation.common.SettingItem
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


    val context = LocalContext.current

    val switchedHint =
        stringResource(R.string.switched_platform_hint)



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

            modifier = modifier
                .nestedScroll(
                    scrollBehavior.nestedScrollConnection
                ),

            topBar = {

                PlatformTopAppBar(
                    title = platformData.name,
                    onNavigationClick = onNavigationClick,
                    onDeleteClick = settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior
                )

            }

        ) { innerPadding ->


            Column(

                Modifier
                    .padding(innerPadding)
                    .verticalScroll(scrollState)

            ) {


                PreferenceSwitchWithContainer(

                    title = stringResource(R.string.enable_api),

                    isChecked = platformData.enabled

                ) {

                    settingViewModel.toggleEnabled()

                }



                SettingItem(

                    modifier = Modifier.height(64.dp),

                    title = "Platform Name",

                    description = platformData.name,

                    enabled = platformData.enabled,

                    onItemClick =
                        settingViewModel::openPlatformNameDialog,

                    showTrailingIcon = false,

                    showLeadingIcon = true,

                    leadingIcon = {

                        Icon(

                            imageVector =
                                Icons.AutoMirrored.Filled.Label,

                            contentDescription = null

                        )

                    }

                )
                              SettingItem(

                    modifier = Modifier.height(64.dp),

                    title = "API Provider",

                    description =
                        if (platformData.apiUrl.contains("openrouter"))
                            "OpenRouter"
                        else
                            "Custom API",

                    enabled = platformData.enabled,

                    onItemClick = {
                        settingViewModel.openApiUrlDialog()
                    },

                    showTrailingIcon = false,

                    showLeadingIcon = true,

                    leadingIcon = {

                        Icon(

                            imageVector =
                                ImageVector.vectorResource(
                                    id = R.drawable.ic_link
                                ),

                            contentDescription = null

                        )

                    }

                )



                SettingItem(

                    modifier = Modifier.height(64.dp),

                    title = "API Key",

                    description =
                        if (platformData.token.isNullOrEmpty()) {

                            "Not Set"

                        } else {

                            "Configured"

                        },

                    enabled = platformData.enabled,

                    onItemClick =
                        settingViewModel::openApiTokenDialog,

                    showTrailingIcon = false,

                    showLeadingIcon = true,

                    leadingIcon = {

                        Icon(

                            imageVector =
                                ImageVector.vectorResource(
                                    id = R.drawable.ic_key
                                ),

                            contentDescription = null

                        )

                    }

                )



                SettingItem(

                    modifier = Modifier.height(64.dp),

                    title = "Model",

                    description = platformData.model,

                    enabled = platformData.enabled,

                    onItemClick =
                        settingViewModel::openApiModelDialog,

                    showTrailingIcon = false,

                    showLeadingIcon = true,

                    leadingIcon = {

                        Icon(

                            imageVector =
                                ImageVector.vectorResource(
                                    id = R.drawable.ic_model
                                ),

                            contentDescription = null

                        )

                    }

                )



                val isReasoningDisabled =
                    platformData.compatibleType == ClientType.OPENAI &&
                        platformData.reasoning



                val notSetText =
                    stringResource(R.string.not_set)



                SettingItem(

                    modifier = Modifier.height(64.dp),

                    title = stringResource(R.string.temperature),

                    description =
                        platformData.temperature?.toString()
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
                                ImageVector.vectorResource(
                                    id = R.drawable.ic_temperature
                                ),

                            contentDescription = null

                        )

                    }

                )



                SettingItem(

                    modifier = Modifier.height(64.dp),

                    title = stringResource(R.string.top_p),

                    description =
                        platformData.topP?.toString()
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
                                ImageVector.vectorResource(
                                    id = R.drawable.ic_chart
                                ),

                            contentDescription = null

                        )

                    }

                )  
