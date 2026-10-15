package com.apexforge.app.ui.account

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.cloud.AuthRepository
import com.apexforge.app.data.cloud.AuthState
import com.apexforge.app.data.cloud.SyncRepository
import com.apexforge.app.data.cloud.friendlyCloudError
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import com.apexforge.app.ui.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class AuthMode { SIGN_IN, REGISTER, RESET }

data class AccountForm(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val emailValid: Boolean get() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val canSubmit: Boolean
        get() = !busy && emailValid && when (mode) {
            AuthMode.SIGN_IN -> password.isNotEmpty()
            AuthMode.REGISTER -> password.length >= 8 && name.isNotBlank()
            AuthMode.RESET -> true
        }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = auth.state
    val syncing: StateFlow<Boolean> = sync.running
    val lastSyncedAt: StateFlow<Long?> = sync.lastSyncedAt

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun update(transform: (AccountForm) -> AccountForm) = _form.update { transform(it).copy(error = null, info = null) }
    fun setMode(mode: AuthMode) = _form.update { it.copy(mode = mode, error = null, info = null, password = "") }
    fun dismissMessage() { _message.value = null }

    fun submit() {
        val f = _form.value
        if (!f.canSubmit) return
        _form.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val result = when (f.mode) {
                AuthMode.SIGN_IN -> auth.signIn(f.email, f.password)
                AuthMode.REGISTER -> auth.register(f.email, f.password, f.name)
                AuthMode.RESET -> auth.sendPasswordReset(f.email)
            }
            result
                .onSuccess {
                    if (f.mode == AuthMode.RESET) {
                        _form.update { it.copy(busy = false, info = "If an account exists for that email, a reset link is on its way.") }
                    } else {
                        _form.update { it.copy(busy = false, password = "") }
                        // Pull the user's cloud data as soon as the sign-in has propagated.
                        withTimeoutOrNull(8_000) { auth.state.first { it is AuthState.SignedIn } }
                        runSync()
                    }
                }
                .onFailure { e -> _form.update { it.copy(busy = false, error = friendlyCloudError(e)) } }
        }
    }

    private suspend fun runSync() {
        sync.syncNow()
            .onSuccess { _message.value = "Synced. Sent ${it.pushed} item(s), received ${it.pulled}." }
            .onFailure { _message.value = "Sync failed: ${friendlyCloudError(it)}" }
    }

    fun syncNow() { viewModelScope.launch { runSync() } }

    fun refreshPermissions() {
        viewModelScope.launch {
            auth.refreshClaims()
            _message.value = "Permissions refreshed."
        }
    }

    fun signOut() = auth.signOut()

    fun deleteAccount() {
        viewModelScope.launch {
            auth.deleteAccount().onFailure { _message.value = friendlyCloudError(it) }
        }
    }
}

@Composable
fun AccountScreen(onBack: () -> Unit, onOpenAdmin: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("ApexForge") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("This permanently deletes your account and everything synced to the cloud. Data on this phone is kept. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader("Account & sync", onBack = onBack)
        when (val s = state) {
            AuthState.NotConfigured -> EmptyState(
                Icons.Rounded.CloudOff, "Cloud features are off",
                "This build has no Firebase project attached, so everything stays on this phone. To enable sign-in, sync and the admin dashboard, follow backend/SETUP_FIREBASE.md, put google-services.json in the app folder and rebuild.",
            )
            AuthState.Loading -> LoadingBox()
            AuthState.SignedOut -> SignedOutContent(form, viewModel)
            is AuthState.SignedIn -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                ApexCard(Modifier.fillMaxWidth()) {
                    Text("Signed in as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.email, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (s.isAdmin) "Role: administrator" else "Role: member",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ApexCard(Modifier.fillMaxWidth()) {
                    Text("Cloud sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        lastSynced?.let { "Last synced ${formatDateTime(it)}" } ?: "Not synced yet",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Workouts, routines, water, body weight and favorites are merged between this phone and the cloud. Deleting something on one device doesn't delete it elsewhere.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    ApexButton(if (syncing) "Syncing..." else "Sync now", viewModel::syncNow, Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudSync, enabled = !syncing)
                }
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.isAdmin) {
                        ApexButton("Admin dashboard", onOpenAdmin, Modifier.fillMaxWidth(), icon = Icons.Rounded.AdminPanelSettings)
                    }
                    ApexButton("Refresh permissions", viewModel::refreshPermissions, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    ApexButton("Sign out", viewModel::signOut, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    ApexButton("Delete account", { confirmDelete = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SignedOutContent(form: AccountForm, vm: AccountViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(
            when (form.mode) {
                AuthMode.SIGN_IN -> "Sign in"
                AuthMode.REGISTER -> "Create an account"
                AuthMode.RESET -> "Reset your password"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "An account is optional. It lets you back up and sync your training across phones.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (form.mode == AuthMode.REGISTER) {
            ApexTextField(form.name, { v -> vm.update { it.copy(name = v.take(40)) } }, "Name", leadingIcon = Icons.Rounded.Person)
            Spacer(Modifier.height(10.dp))
        }
        ApexTextField(
            form.email, { v -> vm.update { it.copy(email = v.take(120)) } }, "Email",
            keyboardType = KeyboardType.Email, leadingIcon = Icons.Rounded.Email,
            isError = form.email.isNotBlank() && !form.emailValid,
            supportingText = if (form.email.isNotBlank() && !form.emailValid) "Enter a valid email" else null,
        )
        if (form.mode != AuthMode.RESET) {
            Spacer(Modifier.height(10.dp))
            ApexTextField(
                form.password, { v -> vm.update { it.copy(password = v.take(128)) } }, "Password",
                keyboardType = KeyboardType.Password, leadingIcon = Icons.Rounded.Lock,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (form.mode == AuthMode.REGISTER) "At least 8 characters" else null,
            )
        }
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
        form.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(16.dp))
        ApexButton(
            when (form.mode) {
                AuthMode.SIGN_IN -> if (form.busy) "Signing in..." else "Sign in"
                AuthMode.REGISTER -> if (form.busy) "Creating..." else "Create account"
                AuthMode.RESET -> if (form.busy) "Sending..." else "Send reset link"
            },
            vm::submit, Modifier.fillMaxWidth(), enabled = form.canSubmit,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (form.mode == AuthMode.SIGN_IN) {
                TextButton(onClick = { vm.setMode(AuthMode.REGISTER) }) { Text("Create account") }
                TextButton(onClick = { vm.setMode(AuthMode.RESET) }) { Text("Forgot password?") }
            } else {
                TextButton(onClick = { vm.setMode(AuthMode.SIGN_IN) }) { Text("Back to sign in") }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
