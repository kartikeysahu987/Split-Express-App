package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.text.style.TextForegroundStyle.Unspecified.alpha
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

    // State variables (keeping original logic)
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
    var splitMode by remember { mutableStateOf("individual") }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var individualAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var includeCurrentUser by remember { mutableStateOf(true) }

    // Enhanced animations
    val screenTransition = remember { Animatable(0f) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.3f else 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing)
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isProcessing) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    LaunchedEffect(Unit) {
        screenTransition.animateTo(
            targetValue = 1f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing)
        )
    }

    // Original data loading logic remains the same
    LaunchedEffect(tripId) {
        Log.d("PayScreen", "Loading data for trip: $tripId")
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Authentication required"
                isLoading = false
                return@LaunchedEffect
            }

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

            try {
                val myTripsResponse = RetrofitInstance.api.getAllMyTrips(token)
                if (myTripsResponse.isSuccessful) {
                    val userTrips = myTripsResponse.body()?.user_items ?: emptyList()
                    tripDetails = userTrips.find { it.trip_id == tripId }
                }
            } catch (e: Exception) {
                Log.e("PayScreen", "Error getting trip details", e)
            }

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

    // Payment submission logic (keeping original)
    // Replace your existing submitPayment() function with this complete version
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
                if (selectedMembers.isEmpty() || amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
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
                        val amountPerPerson = totalAmount / totalPeople

                        var successCount = 0
                        var failureCount = 0

                        // Send payments to selected members
                        for (member in selectedMembers) {
                            try {
                                val payRequest = PayRequest(
                                    trip_id = tripId,
                                    payer_name = currentUserName!!,
                                    reciever_name = member,
                                    amount = String.format("%.2f", amountPerPerson),
                                    description = description.ifBlank { "Equal split payment" }
                                )
                                val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                                if (response.isSuccessful) {
                                    successCount++
                                } else {
                                    failureCount++
                                }
                            } catch (e: Exception) {
                                failureCount++
                            }
                        }

                        if (successCount > 0) {
                            successMessage = "Split payment successful! ($successCount/${selectedMembers.size} payments sent)"
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
                if (selectedMembers.isEmpty() || !selectedMembers.any { member ->
                        val memberAmount = individualAmounts[member]
                        memberAmount != null && memberAmount.isNotBlank() && memberAmount.toDoubleOrNull() != null && memberAmount.toDouble() > 0
                    }) {
                    errorMessage = "Please select members and enter valid amounts"
                    return
                }

                scope.launch {
                    isProcessing = true
                    errorMessage = null
                    try {
                        val token = TokenManager.getToken(context)
                        var successCount = 0
                        var failureCount = 0

                        for (member in selectedMembers) {
                            val memberAmount = individualAmounts[member]
                            if (memberAmount != null && memberAmount.isNotBlank() && memberAmount.toDoubleOrNull() != null && memberAmount.toDouble() > 0) {
                                try {
                                    val payRequest = PayRequest(
                                        trip_id = tripId,
                                        payer_name = currentUserName!!,
                                        reciever_name = member,
                                        amount = memberAmount,
                                        description = description.ifBlank { "Custom split payment" }
                                    )
                                    val response = RetrofitInstance.api.pay(token = token.toString(), request = payRequest)
                                    if (response.isSuccessful) {
                                        successCount++
                                    } else {
                                        failureCount++
                                    }
                                } catch (e: Exception) {
                                    failureCount++
                                }
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
                            errorMessage = "All payments failed"
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
            ModernTopBar(
                title = "Make Payment",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = (1f - screenTransition.value) * 50f
                }
        ) {
            when {
                isLoading -> ModernLoadingScreen()
                errorMessage != null && allMembers.isEmpty() -> {
                    ModernErrorScreen(
                        message = errorMessage!!,
                        onRetry = {
                            isLoading = true
                            errorMessage = null
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item {
                            AnimatedVisibility(
                                visible = tripDetails != null,
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = tween(600, delayMillis = 200)
                                ) + fadeIn(tween(600, delayMillis = 200))
                            ) {
                                tripDetails?.let { trip ->
                                    ModernTripCard(
                                        trip = trip,
                                        memberCount = allMembers.size + 1
                                    )
                                }
                            }
                        }

                        item {
                            ModernPaymentModeSelector(
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

                        item {
                            ModernPaymentForm(
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
                                modifier = Modifier.scale(buttonScale)
                            )
                        }

                        errorMessage?.let { message ->
                            item {
                                ModernMessageCard(
                                    message = message,
                                    isError = true,
                                    onDismiss = { errorMessage = null }
                                )
                            }
                        }

                        successMessage?.let { message ->
                            item {
                                ModernMessageCard(
                                    message = message,
                                    isError = false,
                                    onDismiss = { successMessage = null }
                                )
                            }
                        }

                        item {
                            ModernSubmitButton(
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
    }

    if (showMemberSelection && allMembers.isNotEmpty()) {
        ModernMemberSelectionDialog(
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
private fun ModernTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModernTripCard(
    trip: Trip,
    memberCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trip.trip_name.firstOrNull()?.toString()?.uppercase() ?: "T",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = trip.trip_name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "$memberCount members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernPaymentModeSelector(
    selectedMode: String,
    onModeChanged: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Payment Mode",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val modes = listOf(
                "individual" to "Individual",
                "equal" to "Split Equal",
                "custom" to "Custom"
            )

            modes.forEach { (mode, label) ->
                ModernFilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeChanged(mode) },
                    label = label,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModernFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        shadowElevation = if (selected) 8.dp else 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
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
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (splitMode) {
                "individual" -> {
                    ModernMemberSelector(
                        selectedMember = selectedMember,
                        onMemberClick = onMemberClick
                    )

                    ModernAmountField(
                        amount = amount,
                        onAmountChange = onAmountChange,
                        label = "Amount"
                    )
                }

                "equal" -> {
                    ModernSwitchRow(
                        label = "Include myself in split",
                        checked = includeCurrentUser,
                        onCheckedChange = onIncludeCurrentUserChange
                    )

                    ModernMemberSelectionList(
                        members = allMembers,
                        selectedMembers = selectedMembers,
                        onSelectionChange = onMemberSelectionChange
                    )

                    ModernAmountField(
                        amount = amount,
                        onAmountChange = onAmountChange,
                        label = "Total Amount",
                        supportingText = if (selectedMembers.isNotEmpty() && amount.isNotBlank()) {
                            val totalAmount = amount.toDoubleOrNull() ?: 0.0
                            val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                            if (totalPeople > 0) {
                                "₹${String.format("%.2f", totalAmount / totalPeople)} per person"
                            } else null
                        } else null
                    )
                }

                "custom" -> {
                    ModernCustomSplitList(
                        members = allMembers,
                        selectedMembers = selectedMembers,
                        individualAmounts = individualAmounts,
                        onMemberSelectionChange = onMemberSelectionChange,
                        onAmountChange = onIndividualAmountChange
                    )
                }
            }

            ModernDescriptionField(
                description = description,
                onDescriptionChange = onDescriptionChange
            )
        }
    }
}

@Composable
private fun ModernMemberSelector(
    selectedMember: String?,
    onMemberClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onMemberClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedMember ?: "Select member to pay",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selectedMember != null) FontWeight.Medium else FontWeight.Normal
                ),
                color = if (selectedMember != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )

            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ModernAmountField(
    amount: String,
    onAmountChange: (String) -> Unit,
    label: String,
    supportingText: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            label = { Text(label) },
            prefix = {
                Text(
                    "₹",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            )
        )

        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ModernDescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit
) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Description (Optional)") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        maxLines = 3,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ModernSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun ModernSubmitButton(
    splitMode: String,
    isProcessing: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = isEnabled && !isProcessing,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = when (splitMode) {
                    "individual" -> "Make Payment"
                    "equal" -> "Split Payment"
                    "custom" -> "Send Payments"
                    else -> "Pay"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun ModernLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 5.dp
            )

            Text(
                text = "Loading payment details...",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ModernErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ModernMessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Warning else Icons.Default.Check,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    Color(0xFF4CAF50)
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    Color(0xFF4CAF50)
                }
            )
        }
    }
}

// Add these functions at the end of your PayScreen.kt file, before the closing brace

@Composable
private fun ModernMemberSelectionList(
    members: List<String>,
    selectedMembers: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Select Members",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val newSelection = if (selectedMembers.contains(member)) {
                                    selectedMembers - member
                                } else {
                                    selectedMembers + member
                                }
                                onSelectionChange(newSelection)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedMembers.contains(member),
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    selectedMembers + member
                                } else {
                                    selectedMembers - member
                                }
                                onSelectionChange(newSelection)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = member,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernCustomSplitList(
    members: List<String>,
    selectedMembers: Set<String>,
    individualAmounts: Map<String, String>,
    onMemberSelectionChange: (Set<String>) -> Unit,
    onAmountChange: (Map<String, String>) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Custom Split",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedMembers.contains(member),
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    selectedMembers + member
                                } else {
                                    selectedMembers - member
                                }
                                onMemberSelectionChange(newSelection)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = member,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = individualAmounts[member] ?: "",
                            onValueChange = { newAmount ->
                                val newAmounts = individualAmounts.toMutableMap()
                                newAmounts[member] = newAmount
                                onAmountChange(newAmounts)
                            },
                            label = { Text("Amount") },
                            prefix = { Text("₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp),
                            shape = RoundedCornerShape(8.dp),
                            enabled = selectedMembers.contains(member),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // Show total amount
        val totalAmount = individualAmounts.values.mapNotNull { it.toDoubleOrNull() }.sum()
        if (totalAmount > 0) {
            Text(
                text = "Total: ₹${String.format("%.2f", totalAmount)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ModernMemberSelectionDialog(
    freeMembers: List<String>,
    notFreeMembers: List<String>,
    onMemberSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Member",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show notFreeMembers as "Available Members" (already joined)
                if (notFreeMembers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Available Members",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(notFreeMembers) { member ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMemberSelected(member) },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = member,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Show freeMembers as "May not be available" (not joined yet)
                if (freeMembers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Not Joined Yet",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    items(freeMembers) { member ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMemberSelected(member) },
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = member,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "Not joined yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}