package com.hodowo.ui

import androidx.compose.ui.res.stringResource
import com.hodowo.R
import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hodowo.data.auth.GoogleSignInHelper
import com.hodowo.ui.components.HoDoWoLogo
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryContainer
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.viewmodel.AuthMode
import com.hodowo.viewmodel.AuthUiState
import com.hodowo.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Stateful entry point: wires [AuthViewModel] and the Google account picker
 * to the stateless [AuthScreen].
 */
@Composable
fun AuthRoute(
    viewModel: AuthViewModel,
    googleSignIn: GoogleSignInHelper,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    AuthScreen(
        state = state,
        modifier = modifier,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::submit,
        onModeChange = viewModel::setMode,
        googleConfigured = googleSignIn.isConfigured,
        onGoogleClick = {
            scope.launch {
                viewModel.signInWithGoogle { googleSignIn.getIdToken(context) }
            }
        },
    )
}

/**
 * Login or register form, depending on [AuthUiState.mode]. Stateless and previewable.
 *
 * Howdowo style: a purple header with the logo and what the app does, and a
 * white card with the form that overlaps it, like the banners of the other screens.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onGoogleClick: () -> Unit,
    googleConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHeader()
            AuthFormCard(
                state = state,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onConfirmPasswordChange = onConfirmPasswordChange,
                onSubmit = onSubmit,
                onModeChange = onModeChange,
                onGoogleClick = onGoogleClick,
                googleConfigured = googleConfigured,
                modifier = Modifier
                    .offset(y = (-HEADER_OVERLAP).dp)
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private const val HEADER_OVERLAP = 36

@Composable
private fun AuthHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.linearGradient(listOf(Primary, PrimaryContainer)))
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = (28 + HEADER_OVERLAP).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.16f)) {
                HoDoWoLogo(size = 72.dp, modifier = Modifier.padding(10.dp))
            }
            Text(
                stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FeaturePill(Icons.Default.PhotoCamera, stringResource(R.string.auth_feature_photo))
                FeaturePill(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.auth_feature_guides))
                FeaturePill(Icons.Default.Build, stringResource(R.string.auth_feature_parts))
            }
        }
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun AuthFormCard(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onGoogleClick: () -> Unit,
    googleConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    val isRegister = state.mode == AuthMode.REGISTER
    val focusManager = LocalFocusManager.current
    var showPassword by remember { mutableStateOf(false) }
    val passwordTransformation =
        if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    // Kept in local vals so the smart cast works inside the lambdas below.
    val emailError = state.emailError
    val passwordError = state.passwordError
    val confirmError = state.confirmError

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeSwitch(isRegister = isRegister, enabled = !state.isLoading, onModeChange = onModeChange)

            Text(
                if (isRegister) stringResource(R.string.auth_register_title) else stringResource(R.string.auth_login_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (isRegister) {
                    stringResource(R.string.auth_register_body)
                } else {
                    stringResource(R.string.auth_login_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.auth_email)) },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true,
                enabled = !state.isLoading,
                isError = emailError != null,
                supportingText = if (emailError != null) {
                    { Text(authMessage(emailError)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.auth_password)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                singleLine = true,
                enabled = !state.isLoading,
                isError = passwordError != null,
                supportingText = if (passwordError != null) {
                    { Text(authMessage(passwordError)) }
                } else {
                    null
                },
                visualTransformation = passwordTransformation,
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) stringResource(R.string.auth_hide_password) else stringResource(R.string.auth_show_password),
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isRegister) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { focusManager.clearFocus(); onSubmit() },
                ),
            )

            if (isRegister) {
                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.auth_confirm_password)) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    singleLine = true,
                    enabled = !state.isLoading,
                    isError = confirmError != null,
                    supportingText = if (confirmError != null) {
                        { Text(authMessage(confirmError)) }
                    } else {
                        null
                    },
                    visualTransformation = passwordTransformation,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onSubmit() }),
                )
            }

            state.formError?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        authMessage(it),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (isRegister) stringResource(R.string.auth_create_account) else stringResource(R.string.auth_log_in),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            OrDivider()

            OutlinedButton(
                onClick = onGoogleClick,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                // Official Google "G" shipped by Google Play services (play-services-base),
                // as the Google sign-in branding guidelines ask: never redrawn by hand.
                Image(
                    painter = painterResource(com.google.android.gms.base.R.drawable.googleg_standard_color_18),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.auth_google), style = MaterialTheme.typography.titleSmall)
            }

            if (!googleConfigured) {
                // The button stays tappable: the ViewModel shows the exact reason
                // (no web client id / provider disabled) instead of doing nothing.
                Text(
                    stringResource(R.string.auth_google_not_configured_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TextButton(
                onClick = { onModeChange(if (isRegister) AuthMode.LOGIN else AuthMode.REGISTER) },
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (isRegister) stringResource(R.string.auth_switch_to_login) else stringResource(R.string.auth_switch_to_register))
            }
        }
    }
}

/** "Log in | Register" pill switch at the top of the card. */
@Composable
private fun ModeSwitch(isRegister: Boolean, enabled: Boolean, onModeChange: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(SurfaceContainerLow)
            .padding(4.dp),
    ) {
        ModeTab(stringResource(R.string.auth_log_in), selected = !isRegister, enabled = enabled, modifier = Modifier.weight(1f)) {
            onModeChange(AuthMode.LOGIN)
        }
        ModeTab(stringResource(R.string.auth_register), selected = isRegister, enabled = enabled, modifier = Modifier.weight(1f)) {
            onModeChange(AuthMode.REGISTER)
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) Primary else Color.Transparent)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = OutlineVariant,
    focusedLeadingIconColor = Primary,
    focusedLabelColor = Primary,
    unfocusedContainerColor = PrimaryFixed.copy(alpha = 0.18f),
    focusedContainerColor = Color.White,
)

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = OutlineVariant)
        Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f), color = OutlineVariant)
    }
}
