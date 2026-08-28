package com.vibe.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.vibe.app.presentation.ui.auth.WelcomeSignInScreen
import com.vibe.app.presentation.ui.setting.LanguageViewModel

/**
 * App entry point that requires a real Google account session before exposing
 * the rest of the application. GoogleSignIn persists the selected account via
 * Google Play services, so returning users do not have to choose an account on
 * every launch.
 */
@Composable
fun AuthenticatedAppRoot(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val languageViewModel: LanguageViewModel = hiltViewModel()

    var googleSignedIn by remember {
        mutableStateOf(
            GoogleSignIn.getLastSignedInAccount(context) != null
        )
    }

    val languageSelected = languageViewModel.isLanguageSelected()

    if (googleSignedIn && languageSelected) {
        SetupNavGraph(navController = navController)
    } else {
        WelcomeSignInScreen(
            languageViewModel = languageViewModel,
            onSignedIn = {
                languageViewModel.confirmLanguage()
                googleSignedIn = true
            },
        )
    }
}
