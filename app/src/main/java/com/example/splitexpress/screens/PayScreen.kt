package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    var currentUserCasualName by remember { mutableStateOf<String?>(null) } // Renamed for clarity
    var nameMappings by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // New state for name resolution
    var selectedMember by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showMemberSelection by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf("individual") }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var individualAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var includeCurrentUser by remember { mutableStateOf(true) }

    // Enhanced animations
    val contentAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(500)
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isProcessing) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Data loading logic with name resolution
    LaunchedEffect(tripId) {
        if (!isLoading) return@LaunchedEffect
        Log.d("PayScreen", "Loading data for trip: $tripId")
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Authentication required"
                isLoading = false
                return@LaunchedEffect
            }

            // Step 1: Fetch primary data concurrently
            val casualNameDeferred = async { RetrofitInstance.api.getCasualNameByUID(token, GetCasualNameRequest(tripId)) }
            val tripsDeferred = async { RetrofitInstance.api.getAllMyTrips(token) }

            val casualNameResponse = casualNameDeferred.await()
            val myTripsResponse = tripsDeferred.await()

            currentUserCasualName = casualNameResponse.body()?.casual_name
            tripDetails = myTripsResponse.body()?.user_items?.find { it.trip_id == tripId }

            // Step 2: Fetch members and collect all casual names
            val membersData = tripDetails?.invite_code?.let { inviteCode ->
                RetrofitInstance.api.getMembers(token, GetMembersRequest(inviteCode)).body()
            }
            freeMembers = membersData?.free_members ?: emptyList()
            notFreeMembers = membersData?.not_free_members ?: emptyList()
            allMembers = (freeMembers + notFreeMembers).filter { it != currentUserCasualName }

            val allCasualNames = (freeMembers + notFreeMembers + listOfNotNull(currentUserCasualName)).toSet()

            // Step 3: Fetch real names for all casual names concurrently
            if (allCasualNames.isNotEmpty()) {
                val nameMappingJobs = allCasualNames.map { casualName ->
                    async {
                        try {
                            val response = RetrofitInstance.api.getRealName(token, GetRealNameRequest(tripId, casualName))
                            if (response.isSuccessful && response.body() != null) {
                                casualName to response.body()!!.name
                            } else {
                                casualName to casualName // Fallback
                            }
                        } catch (e: Exception) {
                            casualName to casualName // Fallback on exception
                        }
                    }
                }
                nameMappings = nameMappingJobs.awaitAll().toMap()
            }

        } catch (e: Exception) {
            errorMessage = "Failed to load data: ${e.message}"
        } finally {
            isLoading = false
        }
    }


    // Payment submission logic (untouched) - uses casual names for API calls
    fun submitPayment() {
        when (splitMode) {
            "individual" -> {
                if (selectedMember == currentUserCasualName) {
                    errorMessage = "You cannot pay yourself."
                    return
                }
                if (selectedMember.isNullOrBlank() || amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0 || amount.toDouble() > 999999.99) {
                    errorMessage = "Please select a member and enter a valid amount (max ₹999,999.99)."
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        val payRequest = PayRequest(
                            trip_id = tripId,
                            payer_name = currentUserCasualName!!,
                            reciever_name = selectedMember!!,
                            amount = amount,
                            description = description.ifBlank { "Payment to ${nameMappings[selectedMember] ?: selectedMember}" }
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
                if (selectedMembers.isEmpty() || amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                    errorMessage = "Please select members and enter a valid total amount."
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        val totalAmount = amount.toDouble()
                        val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                        val amountPerPerson = totalAmount / totalPeople
                        var successCount = 0

                        for (member in selectedMembers) {
                            try {
                                val payRequest = PayRequest(
                                    trip_id = tripId,
                                    payer_name = currentUserCasualName!!,
                                    reciever_name = member,
                                    amount = String.format("%.2f", amountPerPerson),
                                    description = description.ifBlank { "Equally split expense" }
                                )
                                val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                                if (response.isSuccessful) successCount++
                            } catch (e: Exception) { /* A single failure shouldn't stop others */ }
                        }

                        if (successCount > 0) {
                            successMessage = "Split payment sent to $successCount member(s)."
                            selectedMembers = emptySet()
                            amount = ""
                            description = ""
                            kotlinx.coroutines.delay(2000)
                            navController.popBackStack()
                        } else {
                            errorMessage = "All split payments failed."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Split payment failed: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }
            "custom" -> {
                if (selectedMembers.isEmpty() || !selectedMembers.any { member ->
                        val memberAmount = individualAmounts[member]
                        memberAmount != null && memberAmount.isNotBlank() && memberAmount.toDoubleOrNull()?.let { it > 0 } == true
                    }) {
                    errorMessage = "Please select members and enter at least one valid amount."
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        var successCount = 0

                        for (member in selectedMembers) {
                            val memberAmount = individualAmounts[member]
                            if (memberAmount != null && memberAmount.isNotBlank() && memberAmount.toDoubleOrNull()?.let { it > 0 } == true) {
                                try {
                                    val payRequest = PayRequest(
                                        trip_id = tripId,
                                        payer_name = currentUserCasualName!!,
                                        reciever_name = member,
                                        amount = memberAmount,
                                        description = description.ifBlank { "Custom split expense" }
                                    )
                                    val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                                    if (response.isSuccessful) successCount++
                                } catch (e: Exception) { /* A single failure shouldn't stop others */ }
                            }
                        }

                        if (successCount > 0) {
                            successMessage = "Custom split successful! ($successCount payments sent)"
                            selectedMembers = emptySet()
                            individualAmounts = emptyMap()
                            description = ""
                            kotlinx.coroutines.delay(2000)
                            navController.popBackStack()
                        } else {
                            errorMessage = "All custom payments failed."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Custom split failed: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            ModernTopBar(title = "Add Expense", onBackClick = { navController.popBackStack() })
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).alpha(contentAlpha)
        ) {
            when {
                isLoading -> ModernLoadingScreen()
                errorMessage != null && tripDetails == null -> {
                    ModernErrorScreen(
                        message = errorMessage!!,
                        onRetry = {
                            errorMessage = null
                            isLoading = true
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            tripDetails?.let {
                                ModernTripCard(trip = it, memberCount = allMembers.size + 1)
                            }
                        }

                        item {
                            ModernPaymentModeSelector(
                                selectedMode = splitMode,
                                onModeChanged = { mode ->
                                    splitMode = mode
                                    errorMessage = null // Clear error on mode switch
                                }
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ModernPaymentForm(
                                        splitMode = splitMode,
                                        selectedMember = selectedMember,
                                        amount = amount,
                                        description = description,
                                        allMembers = allMembers,
                                        nameMappings = nameMappings, // Pass the map
                                        selectedMembers = selectedMembers,
                                        individualAmounts = individualAmounts,
                                        includeCurrentUser = includeCurrentUser,
                                        onMemberClick = { showMemberSelection = true },
                                        onAmountChange = { amount = it },
                                        onDescriptionChange = { description = it },
                                        onMemberSelectionChange = { selectedMembers = it },
                                        onIndividualAmountChange = { individualAmounts = it },
                                        onIncludeCurrentUserChange = { includeCurrentUser = it }
                                    )
                                }
                            }
                        }

                        // Success/Error Message display
                        item {
                            AnimatedVisibility(visible = errorMessage != null || successMessage != null) {
                                Column {
                                    errorMessage?.let { ModernMessageCard(message = it, isError = true, onDismiss = { errorMessage = null }) }
                                    successMessage?.let { ModernMessageCard(message = it, isError = false, onDismiss = { successMessage = null }) }
                                }
                            }
                        }

                        item {
                            ModernSubmitButton(
                                modifier = Modifier.padding(top = 8.dp).scale(buttonScale),
                                splitMode = splitMode,
                                isProcessing = isProcessing,
                                isEnabled = when (splitMode) {
                                    "individual" -> !selectedMember.isNullOrBlank() && amount.isNotBlank()
                                    "equal" -> selectedMembers.isNotEmpty() && amount.isNotBlank()
                                    "custom" -> selectedMembers.isNotEmpty() && individualAmounts.any { selectedMembers.contains(it.key) && it.value.isNotBlank() }
                                    else -> false
                                },
                                onClick = { submitPayment() }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (showMemberSelection && allMembers.isNotEmpty()) {
        ModernMemberSelectionDialog(
            freeMembers = freeMembers,
            notFreeMembers = notFreeMembers,
            nameMappings = nameMappings, // Pass the map
            onMemberSelected = { member ->
                selectedMember = member
                showMemberSelection = false
                errorMessage = null
            },
            onDismiss = { showMemberSelection = false }
        )
    }
}


// --- Production-Ready UI Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTopBar(title: String, onBackClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun ModernTripCard(trip: Trip, memberCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CardTravel,
                contentDescription = "Trip",
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = trip.trip_name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$memberCount members",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernPaymentModeSelector(selectedMode: String, onModeChanged: (String) -> Unit) {
    val modes = listOf("individual" to "Individual", "equal" to "Equal", "custom" to "Custom")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEach { (mode, label) ->
                Button(
                    onClick = { onModeChanged(mode) },
                    shape = RoundedCornerShape(8.dp),
                    colors = if (selectedMode == mode) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    elevation = if (selectedMode == mode) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
private fun ModernPaymentForm(
    splitMode: String,
    selectedMember: String?,
    amount: String,
    description: String,
    allMembers: List<String>,
    nameMappings: Map<String, String>, // Accept the map
    selectedMembers: Set<String>,
    individualAmounts: Map<String, String>,
    includeCurrentUser: Boolean,
    onMemberClick: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMemberSelectionChange: (Set<String>) -> Unit,
    onIndividualAmountChange: (Map<String, String>) -> Unit,
    onIncludeCurrentUserChange: (Boolean) -> Unit,
) {
    // This component will now live inside a parent Card, so it's a Column.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(visible = splitMode == "individual", enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModernMemberSelector(selectedMember = selectedMember, nameMappings = nameMappings, onMemberClick = onMemberClick)
                ModernAmountField(amount = amount, onAmountChange = onAmountChange, label = "Amount")
            }
        }
        AnimatedVisibility(visible = splitMode == "equal", enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModernSwitchRow(label = "Include myself in split", checked = includeCurrentUser, onCheckedChange = onIncludeCurrentUserChange)
                ModernMemberSelectionList(members = allMembers, nameMappings = nameMappings, selectedMembers = selectedMembers, onSelectionChange = onMemberSelectionChange)
                val totalAmount = amount.toDoubleOrNull() ?: 0.0
                val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                val supportingText = if (totalPeople > 0 && totalAmount > 0) "₹${"%.2f".format(totalAmount / totalPeople)} per person" else null
                ModernAmountField(amount = amount, onAmountChange = onAmountChange, label = "Total Amount", supportingText = supportingText)
            }
        }
        AnimatedVisibility(visible = splitMode == "custom", enter = fadeIn(), exit = fadeOut()) {
            ModernCustomSplitList(
                members = allMembers,
                nameMappings = nameMappings,
                selectedMembers = selectedMembers,
                individualAmounts = individualAmounts,
                onMemberSelectionChange = onMemberSelectionChange,
                onAmountChange = onIndividualAmountChange
            )
        }

        ModernDescriptionField(description = description, onDescriptionChange = onDescriptionChange)
    }
}

@Composable
private fun ModernMemberSelector(selectedMember: String?, nameMappings: Map<String, String>, onMemberClick: () -> Unit) {
    // Look up the real name for display, fallback to casual name, then to empty
    val displayName = nameMappings[selectedMember] ?: selectedMember ?: ""
    OutlinedTextField(
        value = displayName,
        onValueChange = {},
        label = { Text("Pay To") },
        placeholder = { Text("Select a member") },
        readOnly = true,
        modifier = Modifier.fillMaxWidth().clickable { onMemberClick() },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Member") },
        interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect {
                    if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                        onMemberClick()
                    }
                }
            }
        }
    )
}

@Composable
private fun ModernAmountField(amount: String, onAmountChange: (String) -> Unit, label: String, supportingText: String? = null) {
    OutlinedTextField(
        value = amount,
        onValueChange = { newValue ->
            // Enhanced decimal validation
            val filtered = newValue.filter { char -> char.isDigit() || char == '.' }
            val decimalCount = filtered.count { it == '.' }
            if (decimalCount <= 1) {
                if (filtered.startsWith(".")) onAmountChange("0$filtered") else onAmountChange(filtered)
            }
        },
        label = { Text(label) },
        prefix = { Text("₹ ", fontWeight = FontWeight.SemiBold) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = supportingText?.let { { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) } }
    )
}
@Composable
private fun ModernDescriptionField(description: String, onDescriptionChange: (String) -> Unit) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Description (Optional)") },
        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )
}

@Composable
private fun ModernSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ModernSubmitButton(modifier: Modifier = Modifier, splitMode: String, isProcessing: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    val buttonText = when(splitMode) {
        "individual" -> "Send Payment"
        "equal" -> "Split Equally"
        "custom" -> "Confirm Split"
        else -> "Submit"
    }

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = isEnabled && !isProcessing,
        shape = RoundedCornerShape(16.dp)
    ) {
        AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        }
        AnimatedVisibility(visible = !isProcessing, enter = fadeIn(), exit = fadeOut()) {
            Text(buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModernLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Preparing screen...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModernErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("An Error Occurred", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try Again") }
    }
}

@Composable
private fun ModernMessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val backgroundColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val icon = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onDismiss() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(12.dp))
            Text(text = message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = contentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModernMemberSelectionList(members: List<String>, nameMappings: Map<String, String>, selectedMembers: Set<String>, onSelectionChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Split with", style = MaterialTheme.typography.bodyLarge)
        members.forEach { member ->
            val displayName = nameMappings[member] ?: member // Resolve name for display
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                    val newSelection = if (selectedMembers.contains(member)) selectedMembers - member else selectedMembers + member
                    onSelectionChange(newSelection)
                }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedMembers.contains(member),
                    onCheckedChange = { checked ->
                        val newSelection = if (checked) selectedMembers + member else selectedMembers - member
                        onSelectionChange(newSelection)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(displayName, style = MaterialTheme.typography.bodyLarge) // Display real name
            }
        }
    }
}

@Composable
private fun ModernCustomSplitList(
    members: List<String>,
    nameMappings: Map<String, String>,
    selectedMembers: Set<String>,
    individualAmounts: Map<String, String>,
    onMemberSelectionChange: (Set<String>) -> Unit,
    onAmountChange: (Map<String, String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        members.forEach { member ->
            val isSelected = selectedMembers.contains(member)
            val displayName = nameMappings[member] ?: member // Resolve name for display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        val newSelection = if (checked) selectedMembers + member else selectedMembers - member
                        onMemberSelectionChange(newSelection)
                    }
                )
                Text(
                    text = displayName, // Display real name
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if(isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = if(isSelected) individualAmounts[member] ?: "" else "",
                    onValueChange = { newAmount ->
                        val filtered = newAmount.filter { it.isDigit() || it == '.' }
                        val decimalCount = filtered.count { it == '.' }
                        if (decimalCount <= 1) {
                            val finalAmount = if (filtered.startsWith(".")) "0$filtered" else filtered
                            val newAmounts = individualAmounts.toMutableMap()
                            newAmounts[member] = finalAmount
                            onAmountChange(newAmounts)
                        }
                    },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp),
                    enabled = isSelected,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        val totalAmount = individualAmounts.filterKeys { selectedMembers.contains(it) }.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
        AnimatedVisibility(visible = totalAmount > 0) {
            Text(
                text = "Total: ₹${"%.2f".format(totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            )
        }
    }
}


@Composable
private fun ModernMemberSelectionDialog(
    freeMembers: List<String>,
    notFreeMembers: List<String>,
    nameMappings: Map<String, String>, // Accept the map
    onMemberSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentUserName = remember { TokenManager.getCurrentUserName(context) }

    val filteredFreemembers = freeMembers.filter { it != currentUserName }
    val filteredNotFreeMembers = notFreeMembers.filter { it != currentUserName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Member", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filteredNotFreeMembers.isNotEmpty()) {
                    item { Text("Active Members", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(filteredNotFreeMembers) { member -> MemberDialogItem(member = member, nameMappings = nameMappings, onClick = { onMemberSelected(member) }) }
                }
                if (filteredFreemembers.isNotEmpty()) {
                    item { Text("Pending Members (Not Joined)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    items(filteredFreemembers) { member -> MemberDialogItem(member = member, isPending = true, nameMappings = nameMappings, onClick = { onMemberSelected(member) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun MemberDialogItem(member: String, isPending: Boolean = false, nameMappings: Map<String, String>, onClick: () -> Unit) {
    val displayName = nameMappings[member] ?: member // Resolve name for display
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isPending) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?", // Use real name for initial
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) // Display real name
        }
    }
}