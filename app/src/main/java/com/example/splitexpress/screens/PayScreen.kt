package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.*
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen(
    navController: NavController,
    tripId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State variables
    var tripDetails by remember { mutableStateOf<Trip?>(null) }
    var allMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    var freeMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    var notFreeMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentUserName by remember { mutableStateOf<String?>(null) }
    var selectedMember by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showMemberSelection by remember { mutableStateOf(false) }

    // Enhanced split options
    var splitMode by remember { mutableStateOf("individual") }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var individualAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var includeCurrentUser by remember { mutableStateOf(true) }

    // Animation states
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.3f else 1f,
        animationSpec = tween(500)
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isProcessing) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    // Load trip details and members
    LaunchedEffect(tripId) {
        Log.d("PayScreen", "Loading data for trip: $tripId")
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Authentication required"
                isLoading = false
                return@LaunchedEffect
            }

            // Get current user's casual name
            try {
                val casualNameResponse = RetrofitInstance.api.getCasualNameByUID(
                    token = token,
                    request = GetCasualNameRequest(trip_id = tripId)
                )
                if (casualNameResponse.isSuccessful) {
                    currentUserName = casualNameResponse.body()?.casual_name
                }
            } catch (e: Exception) {
                Log.e("PayScreen", "Error getting casual name", e)
            }

            // Get trip details
            try {
                val myTripsResponse = RetrofitInstance.api.getAllMyTrips(token)
                if (myTripsResponse.isSuccessful) {
                    val userTrips = myTripsResponse.body()?.user_items ?: emptyList()
                    tripDetails = userTrips.find { it.trip_id == tripId }
                }
            } catch (e: Exception) {
                Log.e("PayScreen", "Error getting trip details", e)
            }

            // Get trip members
            tripDetails?.invite_code?.let { inviteCode ->
                try {
                    val membersResponse = RetrofitInstance.api.getMembers(
                        token = token,
                        request = GetMembersRequest(invite_code = inviteCode)
                    )
                    if (membersResponse.isSuccessful) {
                        val membersData = membersResponse.body()
                        freeMembers = membersData?.free_members ?: emptyList()
                        notFreeMembers = membersData?.not_free_members ?: emptyList()
                        allMembers = (freeMembers + notFreeMembers).filter { it != currentUserName }
                    } else {
                        errorMessage = "Failed to load members"
                    }
                } catch (e: Exception) {
                    errorMessage = "Network error: ${e.message}"
                }
            } ?: run {
                errorMessage = "Trip not found"
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load data: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Handle payment submission
    fun submitPayment() {
        when (splitMode) {
            "individual" -> {
                if (selectedMember.isNullOrBlank() || amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                    errorMessage = "Please select member and enter valid amount"
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        val payRequest = PayRequest(
                            trip_id = tripId,
                            payer_name = currentUserName!!,
                            reciever_name = selectedMember!!,
                            amount = amount,
                            description = description.ifBlank { "Payment to $selectedMember" }
                        )
                        val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                        if (response.isSuccessful) {
                            successMessage = "Payment successful!"
                            selectedMember = null
                            amount = ""
                            description = ""
                            kotlinx.coroutines.delay(2000)
                            navController.popBackStack()
                        } else {
                            errorMessage = "Payment failed: ${response.message()}"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Payment failed: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }

            "equal" -> {
                if (selectedMembers.isEmpty() || amount.isBlank() || amount.toDoubleOrNull() == null) {
                    errorMessage = "Please select members and enter valid amount"
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        val totalAmount = amount.toDouble()
                        val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                        val perPersonAmount = String.format("%.2f", totalAmount / totalPeople)

                        var successCount = 0
                        for (member in selectedMembers) {
                            try {
                                val payRequest = PayRequest(
                                    trip_id = tripId,
                                    payer_name = currentUserName!!,
                                    reciever_name = member,
                                    amount = perPersonAmount,
                                    description = description.ifBlank { "Split payment: ₹$perPersonAmount" }
                                )
                                val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                                if (response.isSuccessful) successCount++
                            } catch (e: Exception) {
                                Log.e("PayScreen", "Error paying $member", e)
                            }
                        }

                        if (successCount > 0) {
                            successMessage = "Split payment successful!"
                            selectedMembers = emptySet()
                            amount = ""
                            description = ""
                            kotlinx.coroutines.delay(2000)
                            navController.popBackStack()
                        } else {
                            errorMessage = "All payments failed"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Split payment failed: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }

            "custom" -> {
                val validAmounts = individualAmounts.filter { (member, amt) ->
                    selectedMembers.contains(member) && amt.isNotBlank() && amt.toDoubleOrNull() != null && amt.toDouble() > 0
                }

                if (validAmounts.isEmpty()) {
                    errorMessage = "Please enter valid amounts for selected members"
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        var successCount = 0

                        for ((member, memberAmount) in validAmounts) {
                            try {
                                val payRequest = PayRequest(
                                    trip_id = tripId,
                                    payer_name = currentUserName!!,
                                    reciever_name = member,
                                    amount = memberAmount,
                                    description = description.ifBlank { "Custom payment: ₹$memberAmount" }
                                )
                                val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                                if (response.isSuccessful) successCount++
                            } catch (e: Exception) {
                                Log.e("PayScreen", "Error paying $member", e)
                            }
                        }

                        if (successCount > 0) {
                            successMessage = "Custom payments successful!"
                            selectedMembers = emptySet()
                            individualAmounts = emptyMap()
                            description = ""
                            kotlinx.coroutines.delay(2000)
                            navController.popBackStack()
                        } else {
                            errorMessage = "All payments failed"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Custom payment failed: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Make Payment",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        AnimatedVisibility(
            visible = !isLoading,
            enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
                    .alpha(animatedAlpha),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 20.dp)
            ) {
                when {
                    errorMessage != null && allMembers.isEmpty() -> {
                        item {
                            ErrorCard(
                                message = errorMessage!!,
                                onRetry = {
                                    isLoading = true
                                    errorMessage = null
                                }
                            )
                        }
                    }

                    else -> {
                        // Trip Info Card
                        item {
                            AnimatedVisibility(
                                visible = tripDetails != null,
                                enter = slideInVertically() + fadeIn()
                            ) {
                                tripDetails?.let { trip ->
                                    TripInfoCard(trip = trip, memberCount = allMembers.size + 1)
                                }
                            }
                        }

                        // Payment Mode Selection
                        item {
                            PaymentModeSelector(
                                selectedMode = splitMode,
                                onModeChanged = { mode ->
                                    splitMode = mode
                                    selectedMember = null
                                    selectedMembers = emptySet()
                                    individualAmounts = emptyMap()
                                    amount = ""
                                    errorMessage = null
                                }
                            )
                        }

                        // Payment Form
                        item {
                            PaymentForm(
                                splitMode = splitMode,
                                selectedMember = selectedMember,
                                amount = amount,
                                description = description,
                                allMembers = allMembers,
                                selectedMembers = selectedMembers,
                                individualAmounts = individualAmounts,
                                includeCurrentUser = includeCurrentUser,
                                onMemberClick = { showMemberSelection = true },
                                onAmountChange = { amount = it },
                                onDescriptionChange = { description = it },
                                onMemberSelectionChange = { selectedMembers = it },
                                onIndividualAmountChange = { individualAmounts = it },
                                onIncludeCurrentUserChange = { includeCurrentUser = it },
                                modifier = Modifier.scale(cardScale)
                            )
                        }

                        // Messages
                        errorMessage?.let { message ->
                            item {
                                MessageCard(
                                    message = message,
                                    isError = true,
                                    onDismiss = { errorMessage = null }
                                )
                            }
                        }

                        successMessage?.let { message ->
                            item {
                                MessageCard(
                                    message = message,
                                    isError = false,
                                    onDismiss = { successMessage = null }
                                )
                            }
                        }

                        // Submit Button
                        item {
                            SubmitButton(
                                splitMode = splitMode,
                                isProcessing = isProcessing,
                                isEnabled = when (splitMode) {
                                    "individual" -> selectedMember != null && amount.isNotBlank()
                                    "equal" -> selectedMembers.isNotEmpty() && amount.isNotBlank()
                                    "custom" -> selectedMembers.isNotEmpty() && individualAmounts.any {
                                        selectedMembers.contains(it.key) && it.value.isNotBlank()
                                    }
                                    else -> false
                                },
                                onClick = { submitPayment() }
                            )
                        }
                    }
                }
            }
        }

        // Loading State
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingScreen()
        }
    }

    // Member Selection Dialog
    if (showMemberSelection && allMembers.isNotEmpty()) {
        MemberSelectionDialog(
            freeMembers = freeMembers.filter { it != currentUserName },
            notFreeMembers = notFreeMembers.filter { it != currentUserName },
            onMemberSelected = { member ->
                selectedMember = member
                showMemberSelection = false
                errorMessage = null
            },
            onDismiss = { showMemberSelection = false }
        )
    }
}

@Composable
private fun TripInfoCard(trip: Trip, memberCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trip.trip_name.first().toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = trip.trip_name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$memberCount members",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PaymentModeSelector(
    selectedMode: String,
    onModeChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Payment Mode",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf(
                "individual" to "Individual",
                "equal" to "Split Equal",
                "custom" to "Custom"
            )

            modes.forEach { (mode, label) ->
                FilterChip(
                    onClick = { onModeChanged(mode) },
                    label = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedMode == mode) FontWeight.Medium else FontWeight.Normal
                        )
                    },
                    selected = selectedMode == mode,
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun PaymentForm(
    splitMode: String,
    selectedMember: String?,
    amount: String,
    description: String,
    allMembers: List<String>,
    selectedMembers: Set<String>,
    individualAmounts: Map<String, String>,
    includeCurrentUser: Boolean,
    onMemberClick: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMemberSelectionChange: (Set<String>) -> Unit,
    onIndividualAmountChange: (Map<String, String>) -> Unit,
    onIncludeCurrentUserChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (splitMode) {
                "individual" -> {
                    OutlinedButton(
                        onClick = onMemberClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedMember ?: "Select member to pay")
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("₹") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                "equal" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include myself in split")
                        Switch(
                            checked = includeCurrentUser,
                            onCheckedChange = onIncludeCurrentUserChange
                        )
                    }

                    // Member selection for equal split
                    MemberSelectionList(
                        members = allMembers,
                        selectedMembers = selectedMembers,
                        onSelectionChange = onMemberSelectionChange
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = { Text("Total Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("₹") },
                        supportingText = {
                            if (selectedMembers.isNotEmpty() && amount.isNotBlank()) {
                                val totalAmount = amount.toDoubleOrNull() ?: 0.0
                                val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                                if (totalPeople > 0) {
                                    val perPerson = totalAmount / totalPeople
                                    Text("₹${String.format("%.2f", perPerson)} per person")
                                }
                            }
                        }
                    )
                }

                "custom" -> {
                    CustomSplitList(
                        members = allMembers,
                        selectedMembers = selectedMembers,
                        individualAmounts = individualAmounts,
                        onMemberSelectionChange = onMemberSelectionChange,
                        onAmountChange = onIndividualAmountChange
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun MemberSelectionList(
    members: List<String>,
    selectedMembers: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Select Members", fontWeight = FontWeight.Medium)
            members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedMembers.contains(member),
                        onCheckedChange = { isChecked ->
                            onSelectionChange(
                                if (isChecked) selectedMembers + member
                                else selectedMembers - member
                            )
                        }
                    )
                    Text(member, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomSplitList(
    members: List<String>,
    selectedMembers: Set<String>,
    individualAmounts: Map<String, String>,
    onMemberSelectionChange: (Set<String>) -> Unit,
    onAmountChange: (Map<String, String>) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Custom Split", fontWeight = FontWeight.Medium)

            members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedMembers.contains(member),
                        onCheckedChange = { isChecked ->
                            onMemberSelectionChange(
                                if (isChecked) selectedMembers + member
                                else selectedMembers - member
                            )
                            if (!isChecked) {
                                onAmountChange(individualAmounts - member)
                            }
                        }
                    )

                    Text(
                        member,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    if (selectedMembers.contains(member)) {
                        OutlinedTextField(
                            value = individualAmounts[member] ?: "",
                            onValueChange = { value ->
                                onAmountChange(individualAmounts + (member to value))
                            },
                            label = { Text("₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError)
                MaterialTheme.colorScheme.errorContainer
            else
                Color(0xFF4CAF50).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Warning else Icons.Default.Check,
                contentDescription = null,
                tint = if (isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    Color(0xFF4CAF50)
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                color = if (isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
private fun SubmitButton(
    splitMode: String,
    isProcessing: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        enabled = isEnabled && !isProcessing,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = when (splitMode) {
                    "individual" -> "Make Payment"
                    "equal" -> "Split Payment"
                    "custom" -> "Send Payments"
                    else -> "Pay"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Something went wrong",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun MemberSelectionDialog(
    freeMembers: List<String>,
    notFreeMembers: List<String>,
    onMemberSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Member", fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (freeMembers.isNotEmpty()) {
                    item {
                        Text(
                            "Available",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(freeMembers) { member ->
                        MemberItem(member, true) { onMemberSelected(member) }
                    }
                }
                if (notFreeMembers.isNotEmpty()) {
                    item {
                        Text(
                            "Other Members",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(notFreeMembers) { member ->
                        MemberItem(member, false) { onMemberSelected(member) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MemberItem(
    member: String,
    isAvailable: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable)
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAvailable)
                            Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.first().toString().uppercase(),
                    color = if (isAvailable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = member,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isAvailable) {
                    Text(
                        text = "May have pending transactions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
@Composable
public fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )

            Text(
                text = "Loading payment details...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}