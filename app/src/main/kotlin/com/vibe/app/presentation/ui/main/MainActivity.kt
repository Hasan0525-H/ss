package com.vibe.app.presentation.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.presentation.common.LocalDynamicTheme
import com.vibe.app.presentation.common.LocalThemeMode
import com.vibe.app.presentation.common.SetupNavGraph
import com.vibe.app.presentation.common.ThemeSettingProvider
import com.vibe.app.presentation.theme.VibeAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var languageManager: LanguageManager

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                !mainViewModel.isReady.value
            }
        }

        enableEdgeToEdge()

        super.onCreate(
            savedInstanceState
        )

        // Prevent keyboard from pushing the entire view up.
        // Composables handle IME insets via imePadding().
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_NOTHING
        )

        setContent {

            val navController =
                rememberNavController()

            val currentLanguage by
                languageManager.language
                    .collectAsStateWithLifecycle()

            val layoutDirection =
                if (
                    currentLanguage == "ar"
                ) {
                    LayoutDirection.Rtl
                } else {
                    LayoutDirection.Ltr
                }

            CompositionLocalProvider(
                LocalLayoutDirection provides
                    layoutDirection
            ) {

                ThemeSettingProvider {

                    VibeAppTheme(
                        dynamicTheme =
                            LocalDynamicTheme.current,

                        themeMode =
                            LocalThemeMode.current
                    ) {

                        SetupNavGraph(
                            navController =
                                navController
                        )
                    }
                }
            }
        }
    }
}
