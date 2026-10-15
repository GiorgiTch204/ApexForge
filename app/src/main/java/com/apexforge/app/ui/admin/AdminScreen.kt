package com.apexforge.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apexforge.app.data.cloud.AdminRepository
import com.apexforge.app.data.cloud.AdminUser
import com.apexforge.app.data.cloud.AuthRepository
import com.apexforge.app.data.cloud.AuthState
import com.apexforge.app.data.cloud.friendlyCloudError
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.ApexCard
import com.apexforge.app.ui.components.ApexIconButton
import com.apexforge.app.ui.components.ApexTextField
import com.apexforge.app.ui.components.ButtonStyle
import com.apexforge.app.ui.components.EmptyState
import com.apexforge.app.ui.components.LoadingBox
import com.apexforge.app.ui.components.ScreenHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUi(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val users: List<AdminUser> = emptyList(),
    val nextToken: String? = null,
    val query: String = "",
    val error: String? = null,
    val notice: String? = null,
    val selfUid: String? = null,
    val allowed: Boolean = false,
) {
    val visible: List<AdminUser>
        get() = query.trim().lowercase().let { q -> if (q.isEmpty()) users else users.filter { it.email.orEmpty().lowercase().contains(q) } }
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val admin: AdminRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminUi())
    val ui: StateFlow<AdminUi> = _ui

    init {
        val me = auth.state.value as? AuthState.SignedIn
        _ui.update { it.copy(selfUid = me?.uid, allowed = me?.isAdmin == true) }
        if (me?.isAdmin == true) reload() else _ui.update { it.copy(loading = false) }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q.take(80)) }
    fun dismiss() = _ui.update { it.copy(error = null, notice = null) }

    fun reload() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            admin.listUsers()
                .onSuccess { page -> _ui.update { it.copy(loading = false, users = page.users, nextToken = page.nextPageToken) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyCloudError(e)) } }
        }
    }

    fun loadMore() {
        val token = _ui.value.nextToken ?: return
        if (_ui.value.loadingMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            admin.listUsers(token)
                .onSuccess { page -> _ui.update { it.copy(loadingMore = false, users = it.users + page.users, nextToken = page.nextPageToken) } }
                .onFailure { e -> _ui.update { it.copy(loadingMore = false, error = friendlyCloudError(e)) } }
        }
    }

    private fun act(success: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block()
                .onSuccess { _ui.update { it.copy(notice = success) }; reload() }
                .onFailure { e -> _ui.update { it.copy(error = friendlyCloudError(e)) } }
        }
    }

    fun setSuspended(user: AdminUser, suspended: Boolean) =
        act(if (suspended) "Account suspended." else "Account reactivated.") { admin.setSuspended(user.uid, suspended) }

    fun setAdmin(user: AdminUser, makeAdmin: Boolean) =
        act(if (makeAdmin) "Admin role granted. They need to refresh permissions or sign in again." else "Admin role removed.") {
            admin.setAdmin(user.uid, makeAdmin)
        }

    fun delete(user: AdminUser) = act("User and their cloud data deleted.") { admin.deleteUser(user.uid) }
}

private sealed interface Pending {
    data class Suspend(val user: AdminUser, val suspend: Boolean) : Pending
    data class Role(val user: AdminUser, val makeAdmin: Boolean) : Pending
    data class Delete(val user: AdminUser) : Pending
}

@Composable
fun AdminScreen(onBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pending?>(null) }

    pending?.let { p ->
        val (title, body, label, action) = when (p) {
            is Pending.Suspend -> Quad(
                if (p.suspend) "Suspend ${p.user.email}?" else "Reactivate ${p.user.email}?",
                if (p.suspend) "They are signed out and can't sign in until reactivated." else "They will be able to sign in again.",
                if (p.suspend) "Suspend" else "Reactivate",
            ) { viewModel.setSuspended(p.user, p.suspend) }
            is Pending.Role -> Quad(
                if (p.makeAdmin) "Make ${p.user.email} an admin?" else "Remove admin from ${p.user.email}?",
                if (p.makeAdmin) "Admins can view all users and suspend or delete accounts." else "They lose access to the admin dashboard.",
                if (p.makeAdmin) "Make admin" else "Remove admin",
            ) { viewModel.setAdmin(p.user, p.makeAdmin) }
            is Pending.Delete -> Quad(
                "Delete ${p.user.email}?",
                "This permanently deletes the account and all of its cloud data. It can't be undone.",
                "Delete",
            ) { viewModel.delete(p.user) }
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { pending = null; action() }) { Text(label) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
    (ui.error ?: ui.notice)?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(if (ui.error != null) "Something went wrong" else "Done") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("OK") } },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Admin", subtitle = "${ui.users.size} users", onBack = onBack, actions = {
            ApexIconButton(Icons.Rounded.Refresh, "Reload users", viewModel::reload, enabled = ui.allowed)
        })
        when {
            !ui.allowed && !ui.loading -> EmptyState(
                Icons.Rounded.AdminPanelSettings, "Admins only",
                "Your account doesn't have the admin role. If you were just promoted, use Refresh permissions on the Account screen.",
            )
            ui.loading -> LoadingBox()
            else -> Column(Modifier.fillMaxSize()) {
                ApexTextField(
                    ui.query, viewModel::setQuery, "Search by email",
                    leadingIcon = Icons.Rounded.Search, modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.visible, key = { it.uid }) { user ->
                        UserRow(user, isSelf = user.uid == ui.selfUid) { pending = it }
                    }
                    if (ui.nextToken != null) {
                        item {
                            ApexButton(
                                if (ui.loadingMore) "Loading..." else "Load more", viewModel::loadMore,
                                Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = !ui.loadingMore,
                            )
                        }
                    }
                    if (ui.visible.isEmpty()) {
                        item { Text("No users match.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private data class Quad(val title: String, val body: String, val label: String, val run: () -> Unit)

@Composable
private fun UserRow(user: AdminUser, isSelf: Boolean, onAction: (Pending) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.email ?: user.uid, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val tags = listOfNotNull(
                    if (user.isAdmin) "Admin" else null,
                    if (user.disabled) "Suspended" else null,
                    if (isSelf) "You" else null,
                )
                if (tags.isNotEmpty()) {
                    Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                user.lastSignIn?.let {
                    Text("Last sign-in: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The server refuses self-suspend, self-demotion is blocked, and deleting yourself belongs on the Account screen.
            if (!isSelf) {
                Box {
                    ApexIconButton(Icons.Rounded.MoreVert, "Actions for ${user.email ?: user.uid}", { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (user.disabled) "Reactivate" else "Suspend") },
                            onClick = { menu = false; onAction(Pending.Suspend(user, !user.disabled)) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (user.isAdmin) "Remove admin" else "Make admin") },
                            onClick = { menu = false; onAction(Pending.Role(user, !user.isAdmin)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete user and data") },
                            onClick = { menu = false; onAction(Pending.Delete(user)) },
                        )
                    }
                }
            }
        }
    }
}
