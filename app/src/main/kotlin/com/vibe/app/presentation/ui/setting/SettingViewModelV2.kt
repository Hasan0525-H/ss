package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


@HiltViewModel
class SettingViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val openRouterModelsAPI: OpenRouterModelsAPI
) : ViewModel() {


    private val _platformState =
        MutableStateFlow(listOf<PlatformV2>())

    val platformState: StateFlow<List<PlatformV2>> =
        _platformState.asStateFlow()



    private val _dialogState =
        MutableStateFlow(DialogState())

    val dialogState: StateFlow<DialogState> =
        _dialogState.asStateFlow()



    private val _switchedPlatformEvent =
        MutableSharedFlow<String>()

    val switchedPlatformEvent: SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()



    private val _debugMode =
        MutableStateFlow(false)

    val debugMode: StateFlow<Boolean> =
        _debugMode.asStateFlow()



    private val _apiProvider =
        MutableStateFlow("OPEN_ROUTER")

    val apiProvider: StateFlow<String> =
        _apiProvider.asStateFlow()



    private val _apiKey =
        MutableStateFlow("")

    val apiKey: StateFlow<String> =
        _apiKey.asStateFlow()



    private val _customApiUrl =
        MutableStateFlow("")

    val customApiUrl: StateFlow<String> =
        _customApiUrl.asStateFlow()



    // ============================
    // OpenRouter Models
    // ============================


    private val _models =
        MutableStateFlow<List<OpenRouterModel>>(emptyList())

    val models: StateFlow<List<OpenRouterModel>> =
        _models.asStateFlow()



    private val _isLoading =
        MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()



    private val _isFreeFilter =
        MutableStateFlow(true)

    val isFreeFilter: StateFlow<Boolean> =
        _isFreeFilter.asStateFlow()



    init {

        fetchPlatforms()

        fetchDebugMode()

    }



    fun setApiProvider(
        provider: String
    ) {

        _apiProvider.value = provider

    }



    fun setApiKey(
        key: String
    ) {

        _apiKey.value = key

    }



    fun setCustomApiUrl(
        url: String
    ) {

        _customApiUrl.value = url

    }



    fun fetchModels(
        apiKey: String,
        isFreeOnly: Boolean
    ) {

        viewModelScope.launch {


            _isLoading.value = true

            _isFreeFilter.value = isFreeOnly



            try {

                val result =
                    openRouterModelsAPI.fetchOpenRouterModels(
                        apiKey = apiKey,
                        isFreeOnly = isFreeOnly
                    )


                _models.value = result


            } catch (e: Exception) {


                _models.value =
                    emptyList()


            } finally {


                _isLoading.value = false


            }

        }

    }
        fun saveApiSettings() {

        viewModelScope.launch {

            settingRepository.saveApiSettings(
                provider = _apiProvider.value,
                apiKey = _apiKey.value,
                customUrl = _customApiUrl.value
            )

        }

    }



    fun fetchPlatforms() {

        viewModelScope.launch {

            val platforms =
                settingRepository.fetchPlatformV2s()

            _platformState.update {
                platforms
            }

        }

    }



    fun addPlatform(
        platform: PlatformV2
    ) {

        viewModelScope.launch {


            if (platform.enabled) {


                val allPlatforms =
                    settingRepository.fetchPlatformV2s()


                val othersEnabled =
                    allPlatforms.filter {
                        it.enabled
                    }



                othersEnabled.forEach {

                    settingRepository.updatePlatformV2(
                        it.copy(
                            enabled = false
                        )
                    )

                }



                if (othersEnabled.isNotEmpty()) {

                    _switchedPlatformEvent.emit(
                        platform.name
                    )

                }

            }



            settingRepository.addPlatformV2(
                platform
            )


            fetchPlatforms()

        }

    }




    fun updatePlatform(
        platform: PlatformV2
    ) {

        viewModelScope.launch {


            settingRepository.updatePlatformV2(
                platform
            )


            fetchPlatforms()

        }

    }




    fun deletePlatform(
        platform: PlatformV2
    ) {

        viewModelScope.launch {


            settingRepository.deletePlatformV2(
                platform
            )


            fetchPlatforms()

        }

    }




    fun togglePlatformEnabled(
        platformId: Int
    ) {


        val platform =
            _platformState.value.find {

                it.id == platformId

            } ?: return



        val enable =
            !platform.enabled



        if (enable) {


            viewModelScope.launch {


                val others =
                    _platformState.value.filter {

                        it.enabled &&
                        it.id != platformId

                    }



                others.forEach {

                    settingRepository.updatePlatformV2(
                        it.copy(
                            enabled = false
                        )
                    )

                }



                settingRepository.updatePlatformV2(
                    platform.copy(
                        enabled = true
                    )
                )



                if (others.isNotEmpty()) {

                    _switchedPlatformEvent.emit(
                        platform.name
                    )

                }



                fetchPlatforms()

            }


        } else {


            updatePlatform(
                platform.copy(
                    enabled = false
                )
            )

        }

    }




    fun openThemeDialog() {

        _dialogState.update {

            it.copy(
                isThemeDialogOpen = true
            )

        }

    }




    fun closeThemeDialog() {

        _dialogState.update {

            it.copy(
                isThemeDialogOpen = false
            )

        }

    }




    fun openDeleteDialog(
        platformId: Int
    ) {

        _dialogState.update {

            it.copy(
                isDeleteDialogOpen = true,
                platformToDelete = platformId
            )

        }

    }




    fun closeDeleteDialog() {

        _dialogState.update {

            it.copy(
                isDeleteDialogOpen = false,
                platformToDelete = null
            )

        }

    }




    fun confirmDelete() {


        _dialogState.value.platformToDelete?.let { id ->


            _platformState.value
                .find {

                    it.id == id

                }
                ?.let {

                    deletePlatform(it)

                }

        }



        closeDeleteDialog()

    }




    fun toggleDebugMode() {


        val value =
            !_debugMode.value



        _debugMode.update {

            value

        }



        viewModelScope.launch {


            settingRepository.updateDebugMode(
                value
            )

        }

    }




    private fun fetchDebugMode() {


        viewModelScope.launch {


            _debugMode.update {


                settingRepository.getDebugMode()


            }

        }

    }




    data class DialogState(

        val isThemeDialogOpen: Boolean = false,

        val isDeleteDialogOpen: Boolean = false,

        val platformToDelete: Int? = null

    )

}
