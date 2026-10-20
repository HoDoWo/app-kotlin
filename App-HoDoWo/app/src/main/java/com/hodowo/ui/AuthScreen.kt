package com.hodowo.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hodowo.data.auth.GoogleSignInHelper
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
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
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
        onGoogleClick = {
            scope.launch {
                viewModel.signInWithGoogle { googleSignIn.getIdToken(context) }
            }
        },
    )
}

/** Login or register form, depending on [AuthUiState.mode]. Stateless and previewable. */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onGoogleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRegister = state.mode == AuthMode.REGISTER
    val focusManager = LocalFocusManager.current
    var showPassword by remember { mutableStateOf(false) }
    val passwordTransformation =
        if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("HoDoWo", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                if (isRegister) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                enabled = !state.isLoading,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.isLoading,
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { { Text(it) } },
                visualTransformation = passwordTransformation,
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show")
                    }
                },
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
                    label = { Text("Confirm password") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    isError = state.confirmError != null,
                    supportingText = state.confirmError?.let { { Text(it) } },
                    visualTransformation = passwordTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onSubmit() }),
                )
            }

            state.formError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isRegister) "Create account" else "Log in")
                }
            }

            OrDivider()

            OutlinedButton(
                onClick = onGoogleClick,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Continue with Google")
            }

            TextButton(
                onClick = { onModeChange(if (isRegister) AuthMode.LOGIN else AuthMode.REGISTER) },
                enabled = !state.isLoading,
            ) {
                Text(if (isRegister) "Already have an account? Log in" else "No account yet? Register")
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text("or", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
