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


    private val _language =
        MutableStateFlow(
            getLanguage()
        )


    val language: StateFlow<String> =
        _language



    fun changeLanguage(
        language: String
    ) {

        _language.value = language


        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(
                language
            )
        )


        context
            .getSharedPreferences(
                "settings",
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                "language",
                language
            )
            .apply()

    }



    private fun getLanguage(): String {

        return context
            .getSharedPreferences(
                "settings",
                Context.MODE_PRIVATE
            )
            .getString(
                "language",
                "en"
            )
            ?: "en"

    }

}
