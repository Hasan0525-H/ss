package com.vibe.app.presentation.ui.auth

import android.accounts.AccountManager
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.AccountPicker
import com.vibe.app.presentation.ui.setting.LanguageViewModel

/**
 * Minimal first-run screen.
 *
 * Account selection is delegated to Google's system account picker. The app
 * only receives the email address of the account the user explicitly chooses.
 */
@Composable
fun WelcomeSignInScreen(
    languageViewModel: LanguageViewModel,
    onSignedIn: (String) -> Unit,
) {
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isArabic = selectedLanguage == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            // Cancellation is a normal user action. Do not show a large error card.
            return@rememberLauncherForActivityResult
        }

        if (result.resultCode != Activity.RESULT_OK) {
            errorMessage = if (isArabic) {
                "تعذر فتح حساب Google. حاول مرة أخرى."
            } else {
                "Could not open your Google account. Please try again."
            }
            return@rememberLauncherForActivityResult
        }

        val email = result.data
            ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?.trim()

        if (email.isNullOrBlank()) {
            errorMessage = if (isArabic) {
                "لم يتم تحديد حساب Google."
            } else {
                "No Google account was selected."
            }
        } else {
            errorMessage = null
            onSignedIn(email)
        }
    }

    fun openGoogleAccountPicker() {
        errorMessage = null

        val intent = AccountPicker.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            true,
            null,
            null,
            null,
            null,
        )

        accountPickerLauncher.launch(intent)
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(66.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(31.dp),
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
                    "اختر اللغة ثم حساب Google للمتابعة"
                } else {
                    "Choose your language and Google account to continue"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

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

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = ::openGoogleAccountPicker,
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
                GoogleMark()
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

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (isArabic) {
                    "ستظهر حسابات Google الموجودة على جهازك"
                } else {
                    "Your Google accounts on this device will appear"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
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
                    modifier = Modifier.size(18.dp),
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
