package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
//import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.network.LinkMemberRequest
import com.example.splitexpress.network.GetMembersRequest
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch

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

    // Load members when invite code changes
    LaunchedEffect(inviteCode) {
        if (inviteCode.length >= 6) { // Assuming invite codes are at least 6 characters
            loadAvailableMembers(
                inviteCode = inviteCode,
                context = context,
                onLoading = { isLoadingMembers = it },
                onSuccess = { members, name ->
                    availableMembers = members
                    tripName = name
                    selectedMember = "" // Reset selection
                    errorMessage = null
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
        }
    }

    // Clear messages when user starts typing
    LaunchedEffect(inviteCode, selectedMember) {
        if (successMessage != null) {
            successMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Join Trip",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (tripName.isNotEmpty()) "Join \"$tripName\"" else "Join a Trip",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Enter the invite code and select your name from available members",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Input Fields
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Invite Code Input
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it }, // Removed .uppercase() to allow lowercase input
                        label = { Text("Invite Code") },
                        placeholder = { Text("Enter invite code") },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        trailingIcon = {
                            if (isLoadingMembers) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Member Selection Dropdown
                    ExposedDropdownMenuBox(
                        expanded = showDropdown,
                        onExpandedChange = { showDropdown = !showDropdown && availableMembers.isNotEmpty() }
                    ) {
                        OutlinedTextField(
                            value = selectedMember,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Select Your Name") },
                            placeholder = {
                                Text(
                                    if (availableMembers.isEmpty() && inviteCode.isNotEmpty())
                                        "Enter invite code first"
                                    else if (availableMembers.isEmpty())
                                        "No available members"
                                    else
                                        "Choose your name"
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            trailingIcon = {
                                if (availableMembers.isNotEmpty()) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = availableMembers.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            availableMembers.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member) },
                                    onClick = {
                                        selectedMember = member
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Show available members count
                    if (availableMembers.isNotEmpty()) {
                        Text(
                            text = "${availableMembers.size} available member(s) to join",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Error Message
                    errorMessage?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Success Message
                    successMessage?.let { success ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = success,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Join Button
                    Button(
                        onClick = {
                            scope.launch {
                                joinTrip(
                                    inviteCode = inviteCode,
                                    memberName = selectedMember,
                                    context = context,
                                    onLoading = { isJoining = it },
                                    onError = { errorMessage = it },
                                    onSuccess = { message ->
                                        successMessage = message
                                        // Navigate back to home after a short delay
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = "Join Trip",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Important Notes:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Only available (unlinked) members are shown in the dropdown\n" +
                                "• The invite code will automatically load available members\n" +
                                "• You can only join trips where you've been added as a member\n" +
                                "• Each user can only link to one member per trip",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

private suspend fun loadAvailableMembers(
    inviteCode: String,
    context: android.content.Context,
    onLoading: (Boolean) -> Unit,
    onSuccess: (List<String>, String) -> Unit,
    onError: (String) -> Unit
) {
    onLoading(true)

    try {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) {
            onError("Authentication token not found. Please login again.")
            return
        }

        Log.d("LoadMembers", "Loading members for invite code: $inviteCode")

        val request = GetMembersRequest(invite_code = inviteCode.trim())
        val response = RetrofitInstance.api.getMembers(token, request)

        Log.d("LoadMembers", "API response - Success: ${response.isSuccessful}, Code: ${response.code()}")

        if (response.isSuccessful) {
            val responseBody = response.body()
            val freeMembers = responseBody?.free_members ?: emptyList()
            val tripName = responseBody?.trip_name ?: "Unknown Trip"

            Log.d("LoadMembers", "Free members: $freeMembers, Trip: $tripName")
            onSuccess(freeMembers, tripName)
        } else {
            val errorMsg = when (response.code()) {
                400 -> "Invalid invite code format"
                404 -> "Trip not found with this invite code"
                401 -> "Authentication failed. Please login again."
                else -> "Failed to load trip members"
            }
            Log.e("LoadMembers", "API Error: ${response.code()}")
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
    context: android.content.Context,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    onLoading(true)

    try {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) {
            onError("Authentication token not found. Please login again.")
            return
        }

        Log.d("JoinTrip", "Attempting to join trip with code: $inviteCode, name: $memberName")

        val request = LinkMemberRequest(
            invite_code = inviteCode.trim(),
            name = memberName.trim()
        )

        val response = RetrofitInstance.api.linkMember(token, request)

        Log.d("JoinTrip", "API response - Success: ${response.isSuccessful}, Code: ${response.code()}")

        if (response.isSuccessful) {
            val responseBody = response.body()
            val tripName = responseBody?.trip_name ?: "Unknown Trip"
            onSuccess("Successfully joined '$tripName'!")
            Log.d("JoinTrip", "Successfully joined trip: $tripName")
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("JoinTrip", "API Error: ${response.code()} - $errorBody")

            val errorMsg = when (response.code()) {
                400 -> "Invalid invite code or member name. Please check your details."
                401 -> "Authentication failed. Please login again."
                404 -> "Trip not found. Please check the invite code."
                else -> "Failed to join trip. Please try again."
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