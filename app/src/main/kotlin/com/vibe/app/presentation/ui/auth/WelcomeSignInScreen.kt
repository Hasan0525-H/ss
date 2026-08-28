package com.vibe.app.presentation.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.vibe.app.presentation.ui.setting.LanguageViewModel

/**
 * Minimal first-run screen: choose app language, then authenticate with a
 * Google account already available through Google Play services.
 */
@Composable
fun WelcomeSignInScreen(
    languageViewModel: LanguageViewModel,
    onSignedIn: (GoogleSignInAccount) -> Unit,
) {
    val context = LocalContext.current
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isArabic = selectedLanguage == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }

    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isSigningIn = false

        /*
         * A user closing Google's account chooser is not an application error.
         * Do not show a red error card for a normal cancellation.
         */
        if (result.resultCode == Activity.RESULT_CANCELED && result.data == null) {
            errorMessage = null
            return@rememberLauncherForActivityResult
        }

        val resultIntent = result.data
        if (resultIntent == null) {
            errorMessage = if (isArabic) {
                "تعذر بدء تسجيل الدخول إلى Google. حاول مرة أخرى."
            } else {
                "Google sign-in could not be started. Please try again."
            }
            return@rememberLauncherForActivityResult
        }

        try {
            /*
             * Parse Google's result even when Activity.resultCode is not OK.
             * Google can return a useful ApiException/status inside the intent;
             * checking RESULT_OK first used to hide real failures as
             * "sign-in cancelled".
             */
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(resultIntent)
                .getResult(ApiException::class.java)

            if (account.email.isNullOrBlank()) {
                errorMessage = if (isArabic) {
                    "لم يُرجع Google بريدًا إلكترونيًا لهذا الحساب."
                } else {
                    "Google did not return an email address for this account."
                }
            } else {
                errorMessage = null
                onSignedIn(account)
            }
        } catch (error: ApiException) {
            /* Google status 12501 means the user cancelled the chooser. */
            if (error.statusCode == 12501) {
                errorMessage = null
            } else {
                errorMessage = googleSignInErrorMessage(
                    statusCode = error.statusCode,
                    isArabic = isArabic,
                )
            }
        }
    }

    fun launchGoogleAccountChooser() {
        if (isSigningIn) return

        isSigningIn = true
        errorMessage = null

        /*
         * Launch directly. Signing out before every attempt is unnecessary and
         * can add delay/race conditions before the account chooser appears.
         */
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = if (isArabic) "مرحبًا بك في Vibe" else "Welcome to Vibe",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isArabic) {
                    "اختر اللغة ثم تابع بحساب Google"
                } else {
                    "Choose a language, then continue with Google"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(30.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LanguageSegment(
                    text = "العربية",
                    selected = selectedLanguage == "ar",
                    modifier = Modifier.weight(1f),
                    onClick = { languageViewModel.selectLanguage("ar") },
                )
                LanguageSegment(
                    text = "English",
                    selected = selectedLanguage == "en",
                    modifier = Modifier.weight(1f),
                    onClick = { languageViewModel.selectLanguage("en") },
                )
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = ::launchGoogleAccountChooser,
                enabled = !isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    GoogleMark()
                }

                Spacer(Modifier.size(10.dp))

                Text(
                    text = if (isArabic) {
                        "المتابعة باستخدام Google"
                    } else {
                        "Continue with Google"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun googleSignInErrorMessage(
    statusCode: Int,
    isArabic: Boolean,
): String {
    return when (statusCode) {
        7 -> if (isArabic) {
            "تعذر الاتصال بـ Google. تحقق من الإنترنت ثم حاول مرة أخرى."
        } else {
            "Could not connect to Google. Check your internet connection and try again."
        }

        10 -> if (isArabic) {
            "إعداد تسجيل الدخول عبر Google غير صحيح في التطبيق. رمز الخطأ: 10"
        } else {
            "Google sign-in is not configured correctly for this app. Error code: 10"
        }

        12500 -> if (isArabic) {
            "فشل تسجيل الدخول إلى Google. حاول مرة أخرى."
        } else {
            "Google sign-in failed. Please try again."
        }

        else -> if (isArabic) {
            "تعذر تسجيل الدخول إلى Google. رمز الخطأ: $statusCode"
        } else {
            "Google sign-in failed. Error code: $statusCode"
        }
    }
}

@Composable
private fun LanguageSegment(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.size(6.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun GoogleMark() {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE1E5EA)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "G",
                color = Color(0xFF4285F4),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
