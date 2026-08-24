package com.vibe.app.data.repository

import com.vibe.app.data.database.dao.ChatPlatformModelV2Dao
import com.vibe.app.data.database.dao.PlatformV2Dao
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.datastore.SettingDataSource
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.ThemeSetting
import com.vibe.app.data.model.DynamicTheme
import com.vibe.app.data.model.ThemeMode
import com.vibe.app.data.network.OpenRouterModelsAPI
import javax.inject.Inject


class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource,
    private val platformV2Dao: PlatformV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val openRouterModelsAPI: OpenRouterModelsAPI
) : SettingRepository {


    override suspend fun fetchPlatformV2s(): List<PlatformV2> =
        platformV2Dao.getPlatforms()



    override suspend fun fetchThemes(): ThemeSetting =
        ThemeSetting(
            dynamicTheme =
            settingDataSource.getDynamicTheme()
                ?: DynamicTheme.OFF,

            themeMode =
            settingDataSource.getThemeMode()
                ?: ThemeMode.SYSTEM
        )



    override suspend fun updateThemes(
        themeSetting: ThemeSetting
    ) {

        settingDataSource.updateDynamicTheme(
            themeSetting.dynamicTheme
        )

        settingDataSource.updateThemeMode(
            themeSetting.themeMode
        )
    }



    override suspend fun addPlatformV2(
        platform: PlatformV2
    ) {

        platformV2Dao.addPlatform(
            platform
        )
    }



    override suspend fun updatePlatformV2(
        platform: PlatformV2
    ) {

        platformV2Dao.editPlatform(
            platform
        )
    }



    override suspend fun deletePlatformV2(
        platform: PlatformV2
    ) {

        chatPlatformModelV2Dao.deleteByPlatformUid(
            platform.uid
        )

        platformV2Dao.deletePlatform(
            platform
        )
    }



    override suspend fun getPlatformV2ById(
        id: Int
    ): PlatformV2? =
        platformV2Dao.getPlatform(id)



    override suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {

        val authHeader =
            if (apiKey.startsWith("Bearer ")) {
                apiKey
            } else {
                "Bearer $apiKey"
            }

        val sortParam =
            if (!isFreeOnly) {
                "pricing-low-to-high"
            } else {
                null
            }

        val response =
            openRouterModelsAPI.getModels(
                token = authHeader,
                sort = sortParam
            )

        val models =
            response.data

        return if (isFreeOnly) {
            models.filter {
                it.pricing?.isFree == true
            }
        } else {
            models.filter {
                it.pricing?.isFree == false
            }
        }
    }



    override suspend fun getDebugMode(): Boolean =
        settingDataSource.getDebugMode()



    override suspend fun updateDebugMode(
        enabled: Boolean
    ) {

        settingDataSource.updateDebugMode(
            enabled
        )
    }



    override suspend fun saveApiSettings(
        provider: String,
        apiKey: String,
        customUrl: String
    ) {

        settingDataSource.updateApiProvider(
            provider
        )

        settingDataSource.updateApiKey(
            apiKey
        )

        settingDataSource.updateCustomApiUrl(
            customUrl
        )
    }



    override suspend fun getApiProvider(): String =
        settingDataSource.getApiProvider()



    override suspend fun getApiKey(): String =
        settingDataSource.getApiKey()



    override suspend fun getCustomApiUrl(): String =
        settingDataSource.getCustomApiUrl()

}
