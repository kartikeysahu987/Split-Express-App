package com.example.splitexpress.screens

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.splitexpress.network.GetMembersRequest
import com.example.splitexpress.network.LinkMemberRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinTripScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inviteCode by remember { mutableStateOf("") }
    var selectedMember by remember { mutableStateOf("") }
    var availableMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    var tripName by remember { mutableStateOf("") }
    var isLoadingMembers by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(inviteCode) {
        if (inviteCode.length >= 6) {
            errorMessage = null
            loadAvailableMembers(
                inviteCode = inviteCode,
                context = context,
                onLoading = { isLoadingMembers = it },
                onSuccess = { members, name ->
                    availableMembers = members
                    tripName = name
                    selectedMember = ""
                },
                onError = { error ->
                    availableMembers = emptyList()
                    tripName = ""
                    selectedMember = ""
                    errorMessage = error
                }
            )
        } else {
            availableMembers = emptyList()
            tripName = ""
            selectedMember = ""
            errorMessage = null
        }
    }

    LaunchedEffect(inviteCode, selectedMember) {
        if (successMessage != null) {
            successMessage = null
        }
    }

    SplitExpressTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Join a Trip", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (tripName.isNotEmpty()) "You're joining \"$tripName\"" else "Join a New Trip",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Enter the invite code, then pick your name.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { inviteCode = it },
                            label = { Text("Invite Code") },
                            placeholder = { Text("e.g., ABC-123") },
                            leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                            trailingIcon = {
                                if (isLoadingMembers) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )

                        ExposedDropdownMenuBox(
                            expanded = showDropdown,
                            onExpandedChange = { showDropdown = !showDropdown && availableMembers.isNotEmpty() }
                        ) {
                            OutlinedTextField(
                                value = selectedMember,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Your Name") },
                                placeholder = { Text(if (availableMembers.isEmpty()) "Waiting for code..." else "Choose your name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = availableMembers.isNotEmpty(),
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false }
                            ) {
                                availableMembers.forEach { member ->
                                    DropdownMenuItem(
                                        text = { Text(member, fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            selectedMember = member
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // FIXED: Use a single AnimatedVisibility with an if/else block inside.
                        // This resolves the compiler error and cleans up the logic.
                        AnimatedVisibility(
                            visible = errorMessage != null || successMessage != null,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            // Error messages take priority over success messages.
                            if (errorMessage != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (successMessage != null) {
                                val successContainerColor = if(isSystemInDarkTheme()) Color(0xFF003823) else Color(0xFFC8E6C9)
                                val successContentColor = if(isSystemInDarkTheme()) Color(0xFFB9F6CA) else Color(0xFF002113)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = successContainerColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = successMessage ?: "",
                                        color = successContentColor,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    errorMessage = null
                                    successMessage = null
                                    joinTrip(
                                        inviteCode = inviteCode,
                                        memberName = selectedMember,
                                        context = context,
                                        onLoading = { isJoining = it },
                                        onError = { errorMessage = it },
                                        onSuccess = { message ->
                                            successMessage = message
                                            scope.launch {
                                                kotlinx.coroutines.delay(1500)
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isJoining && !isLoadingMembers && inviteCode.isNotBlank() && selectedMember.isNotBlank(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isJoining) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(text = "Link My Account & Join", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}


private suspend fun loadAvailableMembers(
    inviteCode: String,
    context: Context,
    onLoading: (Boolean) -> Unit,
    onSuccess: (List<String>, String) -> Unit,
    onError: (String) -> Unit
) {
    onLoading(true)
    try {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) {
            onError("Authentication token not found. Please log in again.")
            return
        }
        val request = GetMembersRequest(invite_code = inviteCode.trim())
        val response = RetrofitInstance.api.getMembers(token, request)

        if (response.isSuccessful) {
            val responseBody = response.body()
            val freeMembers = responseBody?.free_members ?: emptyList()
            val tripName = responseBody?.trip_name ?: "Unknown Trip"
            if (freeMembers.isEmpty()) {
                onError("No available members to join in this trip.")
            } else {
                onSuccess(freeMembers, tripName)
            }
        } else {
            val errorMsg = when (response.code()) {
                404 -> "Trip not found. Please double-check the invite code."
                else -> "Failed to load trip members. Invalid code or network issue."
            }
            onError(errorMsg)
        }
    } catch (e: Exception) {
        Log.e("LoadMembers", "Exception while loading members", e)
        onError("Network error. Please check your connection.")
    } finally {
        onLoading(false)
    }
}

private suspend fun joinTrip(
    inviteCode: String,
    memberName: String,
    context: Context,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    onLoading(true)
    try {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) {
            onError("Authentication token not found. Please log in again.")
            return
        }
        val request = LinkMemberRequest(invite_code = inviteCode.trim(), name = memberName.trim())
        val response = RetrofitInstance.api.linkMember(token, request)

        if (response.isSuccessful) {
            val tripName = response.body()?.trip_name ?: "the trip"
            onSuccess("Success! You have joined '$tripName'.")
        } else {
            val errorMsg = when (response.code()) {
                409 -> "This member is already linked to a user."
                else -> "Failed to join trip. The member might be taken."
            }
            onError(errorMsg)
        }
    } catch (e: Exception) {
        Log.e("JoinTrip", "Exception while joining trip", e)
        onError("Network error. Please check your connection and try again.")
    } finally {
        onLoading(false)
    }
}