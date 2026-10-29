package com.MateuszW.aigrowthtracker

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.MateuszW.aigrowthtracker.data.AuthManager
import com.MateuszW.aigrowthtracker.ui.theme.BearOrange

//  ── Validation helpers ──────────────────────────────────────────────────────

private fun validateEmail(email: String): String? {
    if (email.isBlank()) return "Podaj adres email"
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Nieprawidlowy format email"
    return null
}

private fun validatePassword(password: String, isRegistration: Boolean): String? {
    if (password.isBlank()) return "Podaj haslo"
    if (password.length < 6) return "Haslo musi miec minimum 6 znakow"
    if (isRegistration && !password.any { it.isDigit() }) return "Haslo musi zawierac cyfre"
    if (isRegistration && !password.any { it.isUpperCase() }) return "Haslo musi zawierac wielka litere"
    return null
}

private fun mapFirebaseError(message: String?): String {
    return when {
        message == null -> "Wystapil nieoczekiwany blad"
        "INVALID_LOGIN_CREDENTIALS" in message || "INVALID_EMAIL" in message ->
            "Bledny email lub haslo"
        "USER_NOT_FOUND" in message -> "Nie znaleziono konta z tym adresem email"
        "WRONG_PASSWORD" in message -> "Bledne haslo"
        "EMAIL_EXISTS" in message || "already in use" in message ->
            "Konto z tym adresem email juz istnieje"
        "TOO_MANY_ATTEMPTS" in message || "BLOCKED" in message ->
            "Zbyt wiele prob. Sprobuj ponownie pozniej"
        "WEAK_PASSWORD" in message -> "Haslo jest zbyt slabe"
        "network" in message.lowercase() -> "Brak polaczenia z internetem"
        else -> "Blad logowania: $message"
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        //Clear previous errors
        generalError = null
        emailError = validateEmail(email)
        passwordError = validatePassword(password, !isLogin)

        if (emailError != null || passwordError != null) return

        isLoading = true
        val trimmedEmail = email.trim()

        val task = if (isLogin) {
            AuthManager.auth.signInWithEmailAndPassword(trimmedEmail, password)
        } else {
            AuthManager.auth.createUserWithEmailAndPassword(trimmedEmail, password)
        }

        task.addOnSuccessListener {
            isLoading = false
        }.addOnFailureListener { exception ->
            isLoading = false
            generalError = mapFirebaseError(exception.message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BearOrange.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))

            // ── Logo / branding area ─────────────────────────────────
            Text(
                "AI Growth\nTracker",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Twoj asystent codziennego rozwoju",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            //  ── Mode toggle ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    listOf(true to "Logowanie", false to "Rejestracja").forEach { (loginMode, label) ->
                        val selected = isLogin == loginMode
                        Button(
                            onClick = {
                                isLogin = loginMode
                                emailError = null
                                passwordError = null
                                generalError = null
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (selected) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) else ButtonDefaults.textButtonColors(),
                            elevation = if (selected) ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp
                            ) else null
                        ) {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            //── General error ────────────────────────────────────────
            AnimatedVisibility(
                visible = generalError != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        generalError ?: "",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Email field ──────────────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (emailError != null) emailError = validateEmail(it)
                },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null,
                         tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            //── Password field ───────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (passwordError != null) passwordError = validatePassword(it, !isLogin)
                },
                label = { Text("Haslo") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null,
                         tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Ukryj haslo" else "Pokaz haslo"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        submit()
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            //  ── Submit button ────────────────────────────────────────
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    if (isLogin) "Zaloguj sie" else "Zaloz konto",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            //  ── Switch mode text ─────────────────────────────────────
            TextButton(
                onClick = {
                    isLogin = !isLogin
                    emailError = null
                    passwordError = null
                    generalError = null
                }
            ) {
                Text(
                    if (isLogin) "Nie masz konta? Zarejestruj sie"
                    else "Masz juz konto? Zaloguj sie",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
