package com.vibe.app.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {


    private val preferences =
        context.getSharedPreferences(
            "language_settings",
            Context.MODE_PRIVATE
        )


    private val _language =
        MutableStateFlow(
            getLanguage()
        )


    val language: StateFlow<String> =
        _language



    fun setLanguage(
        language: String
    ) {

        preferences.edit()
            .putString(
                "language",
                language
            )
            .apply()


        _language.value = language


        AppCompatDelegate
            .setApplicationLocales(
                LocaleListCompat.forLanguageTags(
                    language
                )
            )
    }



    fun getCurrentLanguage(): String {
        return _language.value
    }



    private fun getLanguage(): String {

        return preferences
            .getString(
                "language",
                "ar"
            )
            ?: "ar"
    }

}
