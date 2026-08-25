package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import com.vibe.app.data.preferences.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languageManager: LanguageManager
) : ViewModel() {


    val language =
        languageManager.language



    fun changeLanguage(
        language: String
    ) {

        languageManager
            .setLanguage(language)

    }

}
