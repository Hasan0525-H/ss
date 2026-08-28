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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
 * First-run account screen.
 *
 * Google Play services owns the account chooser. That means the email list shown
 * to the user comes from real Google accounts available on the Android device;
 * the app never reads the device account list directly and never receives the
 * user's Google password.
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
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            errorMessage = if (isArabic) {
                "تم إلغاء تسجيل الدخول. يمكنك المحاولة مرة أخرى."
            } else {
                "Sign-in was cancelled. You can try again."
            }
            return@rememberLauncherForActivityResult
        }

        try {
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)

            if (account.email.isNullOrBlank()) {
                errorMessage = if (isArabic) {
                    "تعذر قراءة البريد الإلكتروني من حساب Google المحدد."
                } else {
                    "Google did not return an email address for this account."
                }
            } else {
                errorMessage = null
                onSignedIn(account)
            }
        } catch (error: ApiException) {
            errorMessage = if (isArabic) {
                "تعذر تسجيل الدخول إلى Google. رمز الخطأ: ${error.statusCode}"
            } else {
                "Google sign-in failed. Error code: ${error.statusCode}"
            }
        }
    }

    fun launchGoogleAccountChooser() {
        if (isSigningIn) return
        isSigningIn = true
        errorMessage = null

        // Clear only this app's previous Google session before opening the chooser.
        // The Google accounts remain on the device. This makes Google show the
        // account picker instead of silently reusing the last account.
        googleSignInClient.signOut().addOnCompleteListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(42.dp))

            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (isArabic) "مرحبًا بك في Vibe" else "Welcome to Vibe",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isArabic) {
                    "اختر اللغة ثم سجّل الدخول بحساب Google للمتابعة"
                } else {
                    "Choose your language, then sign in with Google to continue"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(34.dp))

            Text(
                text = if (isArabic) "لغة التطبيق" else "App language",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(18.dp),
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

            Spacer(Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = if (isArabic) "تسجيل الدخول" else "Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = ::launchGoogleAccountChooser,
                enabled = !isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    GoogleMark()
                }
                Spacer(Modifier.size(12.dp))
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

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isArabic) {
                    "سيعرض Google الحسابات الموجودة على جهازك لتختار الحساب الذي تريد الدخول به."
                } else {
                    "Google will show the accounts available on your device so you can choose which one to use."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isArabic) {
                        "تسجيل الدخول يتم عبر Google Play Services. التطبيق لا يرى كلمة مرور حسابك ولا يخزنها."
                    } else {
                        "Sign-in is handled by Google Play services. Vibe never sees or stores your Google password."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
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
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(7.dp))
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

/**
 * Small Google-style mark for the sign-in button. The account chooser and
 * authentication itself are provided by Google Play services.
 */
@Composable
private fun GoogleMark() {
    Surface(
        modifier = Modifier.size(26.dp),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE1E5EA)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "G",
                color = Color(0xFF4285F4),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
