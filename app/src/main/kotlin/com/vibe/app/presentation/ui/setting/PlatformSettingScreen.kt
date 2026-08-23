package com.vibe.app.presentation.ui.setting

import android.widget.Toast

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.vibe.app.R
import com.vibe.app.data.model.ClientType

import com.vibe.app.presentation.common.SettingItem

// ✅ الإضافات المهمة
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

            modifier = modifier,

            topBar = {

                PlatformTopAppBar(

                    title = platformData.name,

                    onNavigationClick =
                        onNavigationClick,

                    onDeleteClick =
                        settingViewModel::openDeleteDialog,

                    scrollBehavior =
                        scrollBehavior
                )
            }


        ) { innerPadding ->


            Column(

                Modifier
                    .padding(innerPadding)
                    .verticalScroll(scrollState)

            ) {


                PreferenceSwitchWithContainer(

                    title =
                        stringResource(
                            R.string.enable_api
                        ),

                    isChecked =
                        platformData.enabled

                ) {

                    settingViewModel.toggleEnabled()

                }



                SettingItem(

                    modifier =
                        Modifier.height(64.dp),

                    title = "Platform Name",

                    description =
                        platformData.name,

                    enabled =
                        platformData.enabled,

                    onItemClick =
                        settingViewModel::openPlatformNameDialog
                )



                SettingItem(

                    modifier =
                        Modifier.height(64.dp),

                    title = "API Provider",

                    description =
                        if (platformData.apiUrl.contains("openrouter"))
                            "OpenRouter"
                        else
                            "Custom API",

                    enabled =
                        platformData.enabled,

                    onItemClick =
                        settingViewModel::openApiUrlDialog
                )



                SettingItem(

                    modifier =
                        Modifier.height(64.dp),

                    title = "API Key",

                    description =
                        if (platformData.token.isNullOrEmpty())
                            "Not Set"
                        else
                            "Configured",

                    enabled =
                        platformData.enabled,

                    onItemClick =
                        settingViewModel::openApiTokenDialog
                )



                SettingItem(

                    modifier =
                        Modifier.height(64.dp),

                    title = "Model",

                    description =
                        platformData.model,

                    enabled =
                        platformData.enabled,

                    onItemClick =
                        settingViewModel::openApiModelDialog
                )



                val isReasoningDisabled =
                    platformData.compatibleType == ClientType.OPENAI &&
                            platformData.reasoning



                val notSetText =
                    stringResource(
                        R.string.not_set
                    )



                SettingItem(

                    modifier =
                        Modifier.height(64.dp),

                    title =
                        stringResource(
                            R.string.temperature
                        ),

                    description =
                        platformData.temperature?.toString()
                            ?: notSetText,

                    enabled =
                        platformData.enabled &&
                                !isReasoningDisabled,

                    onItemClick =
                        settingViewModel::openTemperatureDialog
                )



                SettingItem(

                    modifier =
                        Modifier.height(64.dp),

                    title =
                        stringResource(
                            R.string.top_p
                        ),

                    description =
                        platformData.topP?.toString()
                            ?: notSetText,

                    enabled =
                        platformData.enabled &&
                                !isReasoningDisabled,

                    onItemClick =
                        settingViewModel::openTopPDialog
                )



                PlatformNameDialog(
                    dialogState,
                    platformData.name,
                    settingViewModel
                )


                APIUrlDialog(
                    dialogState,
                    platformData.apiUrl,
                    settingViewModel
                )


                APIKeyDialog(
                    dialogState,
                    settingViewModel
                )


                ModelDialog(
                    dialogState,
                    platformData.model,
                    settingViewModel
                )


                TemperatureDialog(
                    dialogState,
                    platformData.temperature,
                    settingViewModel
                )


                TopPDialog(
                    dialogState,
                    platformData.topP,
                    settingViewModel
                )


                DeletePlatformDialog(
                    dialogState,
                    settingViewModel
                )
            }
        }
    }
}
