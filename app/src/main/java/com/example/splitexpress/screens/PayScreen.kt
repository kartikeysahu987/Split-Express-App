package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // Add these new state variables after the existing ones
    var splitEqually by remember { mutableStateOf(false) }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var individualAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var includeCurrentUser by remember { mutableStateOf(true) }



    // Load trip details and members
    LaunchedEffect(tripId) {
        Log.d("PayScreen", "Loading data for trip: $tripId")

        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "No token found"
                isLoading = false
                return@LaunchedEffect
            }

            // Step 1: Get current user's casual name for this trip
            try {
                val casualNameResponse = RetrofitInstance.api.getCasualNameByUID(
                    token = token,
                    request = GetCasualNameRequest(trip_id = tripId)
                )

                if (casualNameResponse.isSuccessful) {
                    currentUserName = casualNameResponse.body()?.casual_name
                    Log.d("PayScreen", "Current user name: $currentUserName")
                } else {
                    Log.w("PayScreen", "Failed to get casual name: ${casualNameResponse.code()}")
                }
            } catch (e: Exception) {
                Log.e("PayScreen", "Error getting casual name", e)
            }

            // Step 2: Get trip details from user's trips to find invite code
            try {
                val myTripsResponse = RetrofitInstance.api.getAllMyTrips(token)

                if (myTripsResponse.isSuccessful) {
                    val userTrips = myTripsResponse.body()?.user_items ?: emptyList()
                    tripDetails = userTrips.find { it.trip_id == tripId }

                    Log.d("PayScreen", "Found trip details: ${tripDetails?.trip_name}")
                    Log.d("PayScreen", "Trip invite code: ${tripDetails?.invite_code}")
                } else {
                    Log.e("PayScreen", "Failed to get user trips: ${myTripsResponse.code()}")
                }
            } catch (e: Exception) {
                Log.e("PayScreen", "Error getting trip details", e)
            }

            // Step 3: Get trip members using invite code
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
                        allMembers = freeMembers + notFreeMembers

                        // Remove current user from the list of selectable members
                        allMembers = allMembers.filter { it != currentUserName }

                        Log.d("PayScreen", "Free members: $freeMembers")
                        Log.d("PayScreen", "Not free members: $notFreeMembers")
                        Log.d("PayScreen", "All selectable members: $allMembers")
                    } else {
                        Log.e("PayScreen", "Failed to get members: ${membersResponse.code()}")
                        errorMessage = "Failed to load trip members"
                    }
                } catch (e: Exception) {
                    Log.e("PayScreen", "Error getting members", e)
                    errorMessage = "Error loading trip members: ${e.message}"
                }
            } ?: run {
                errorMessage = "Could not find trip invite code"
                Log.e("PayScreen", "Trip invite code not found")
            }

        } catch (e: Exception) {
            errorMessage = "Failed to load trip data: ${e.message}"
            Log.e("PayScreen", "Exception occurred", e)
        } finally {
            isLoading = false
        }
    }

    // Handle payment submission
    fun submitPayment() {
        if (splitEqually) {
            // Validation for split equally
            if (selectedMembers.isEmpty()) {
                errorMessage = "Please select at least one member to split with"
                return
            }

            if (amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                errorMessage = "Please enter a valid total amount"
                return
            }

            if (currentUserName.isNullOrBlank()) {
                errorMessage = "Unable to identify current user"
                return
            }

            scope.launch {
                isProcessing = true
                errorMessage = null

                try {
                    val token = TokenManager.getToken(context)
                    if (token.isNullOrBlank()) {
                        errorMessage = "Authentication failed"
                        return@launch
                    }

                    val totalAmount = amount.toDouble()
                    val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                    val perPersonAmount = totalAmount / totalPeople
                    val formattedAmount = String.format("%.2f", perPersonAmount)

                    var successCount = 0
                    var failCount = 0
                    val errors = mutableListOf<String>()

                    // Send payment to each selected member
                    for (member in selectedMembers) {
                        try {
                            val payRequest = PayRequest(
                                trip_id = tripId,
                                payer_name = currentUserName!!,
                                reciever_name = member,
                                amount = formattedAmount,
                                description = description.ifBlank {
                                    "Split payment: ₹$formattedAmount each from ₹$amount total (${if (includeCurrentUser) "including" else "excluding"} yourself)"
                                }
                            )

                            val response = RetrofitInstance.api.pay(
                                token = token,
                                request = payRequest
                            )

                            if (response.isSuccessful) {
                                successCount++
                            } else {
                                failCount++
                                errors.add("Failed to pay $member: ${response.message()}")
                            }

                        } catch (e: Exception) {
                            failCount++
                            errors.add("Error paying $member: ${e.message}")
                        }
                    }

                    if (successCount > 0) {
                        if (failCount == 0) {
                            val totalPeopleText = if (includeCurrentUser) "${totalPeople} people" else "${selectedMembers.size} members"
                            successMessage = "Successfully split ₹$amount among $totalPeopleText (₹$formattedAmount each)"
                        } else {
                            successMessage = "Partially successful: $successCount payments sent, $failCount failed"
                            errorMessage = "Some payments failed: ${errors.joinToString(", ")}"
                        }

                        // Clear form on success
                        if (failCount == 0) {
                            selectedMembers = emptySet()
                            amount = ""
                            description = ""
                            includeCurrentUser = true

                            kotlinx.coroutines.delay(3000)
                            navController.popBackStack()
                        }
                    } else {
                        errorMessage = "All payments failed: ${errors.joinToString(", ")}"
                    }

                } catch (e: Exception) {
                    errorMessage = "Split payment failed: ${e.message}"
                    Log.e("PayScreen", "Split payment exception", e)
                } finally {
                    isProcessing = false
                }
            }
        } else {
            // Individual payment logic (keep your existing code here)
            if (selectedMember.isNullOrBlank()) {
                errorMessage = "Please select a member to pay"
                return
            }

            if (amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                errorMessage = "Please enter a valid amount"
                return
            }

            if (currentUserName.isNullOrBlank()) {
                errorMessage = "Unable to identify current user"
                return
            }

            scope.launch {
                isProcessing = true
                errorMessage = null

                try {
                    val token = TokenManager.getToken(context)
                    if (token.isNullOrBlank()) {
                        errorMessage = "Authentication failed"
                        return@launch
                    }

                    val payRequest = PayRequest(
                        trip_id = tripId,
                        payer_name = currentUserName!!,
                        reciever_name = selectedMember!!,
                        amount = amount.toString(),
                        description = description.ifBlank { "Payment to $selectedMember" }
                    )

                    val response = RetrofitInstance.api.pay(
                        token = token,
                        request = payRequest
                    )

                    if (response.isSuccessful) {
                        successMessage = "Payment of ₹$amount to $selectedMember was successful!"
                        selectedMember = null
                        amount = ""
                        description = ""

                        kotlinx.coroutines.delay(2000)
                        navController.popBackStack()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        errorMessage = "Payment failed: ${response.code()} - ${response.message()}"
                        Log.e("PayScreen", "Payment API Error: ${response.code()}, Body: $errorBody")
                    }

                } catch (e: Exception) {
                    errorMessage = "Payment failed: ${e.message}"
                    Log.e("PayScreen", "Payment exception", e)
                } finally {
                    isProcessing = false
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp), // Give it some height
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Loading trip data...",
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }

                errorMessage != null && allMembers.isEmpty() -> {
                    item {
                        // Error state when loading failed
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Error Loading Trip Data",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = errorMessage!!,
                                        modifier = Modifier.padding(top = 8.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            isLoading = true
                                            errorMessage = null
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    item {
                        // Payment Form
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Payment Details",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Trip Info
                                tripDetails?.let { trip ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = trip.trip_name,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${allMembers.size + 1} members total",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                // Current User Info
                                currentUserName?.let { userName ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Paying as: $userName",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                // Split Equally Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Split Equally",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Switch(
                                        checked = splitEqually,
                                        onCheckedChange = {
                                            splitEqually = it
                                            if (it) {
                                                selectedMember = null
                                                individualAmounts = emptyMap()
                                            } else {
                                                selectedMembers = emptySet()
                                            }
                                            errorMessage = null
                                        }
                                    )
                                }

                                if (splitEqually) {
                                    // Include Current User Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Include myself in split",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Switch(
                                            checked = includeCurrentUser,
                                            onCheckedChange = { includeCurrentUser = it }
                                        )
                                    }

                                    // Multiple Member Selection for Split Equally
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Select Members to Split With",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )

                                            // Use a Column instead of LazyColumn inside LazyColumn
                                            Column {
                                                allMembers.forEach { member ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = selectedMembers.contains(member),
                                                            onCheckedChange = { isChecked ->
                                                                selectedMembers = if (isChecked) {
                                                                    selectedMembers + member
                                                                } else {
                                                                    selectedMembers - member
                                                                }
                                                            }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = member,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }

                                            if (selectedMembers.isNotEmpty()) {
                                                val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                                                Text(
                                                    text = "Selected: ${selectedMembers.size} members${if (includeCurrentUser) " + you" else ""} = $totalPeople people total",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 8.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Total Amount Input for Split Equally
                                    OutlinedTextField(
                                        value = amount,
                                        onValueChange = { amount = it },
                                        label = { Text("Total Amount to Split (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        prefix = { Text("₹") },
                                        supportingText = {
                                            if (selectedMembers.isNotEmpty() && amount.isNotBlank()) {
                                                val totalAmount = amount.toDoubleOrNull() ?: 0.0
                                                val totalPeople = selectedMembers.size + if (includeCurrentUser) 1 else 0
                                                if (totalPeople > 0) {
                                                    val perPerson = totalAmount / totalPeople
                                                    Text("₹${String.format("%.2f", perPerson)} per person ($totalPeople people)")
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    // Individual Member Selection
                                    OutlinedButton(
                                        onClick = {
                                            if (allMembers.isNotEmpty()) {
                                                showMemberSelection = true
                                            } else {
                                                errorMessage = "No other members found in this trip"
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = allMembers.isNotEmpty()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when {
                                                    allMembers.isEmpty() -> "No members available"
                                                    selectedMember != null -> selectedMember!!
                                                    else -> "Select member to pay"
                                                },
                                                fontSize = 16.sp,
                                                color = if (selectedMember != null)
                                                    MaterialTheme.colorScheme.onSurface
                                                else
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (allMembers.isNotEmpty())
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }

                                    // Amount Input for Individual Payment
                                    OutlinedTextField(
                                        value = amount,
                                        onValueChange = { amount = it },
                                        label = { Text("Amount (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        prefix = { Text("₹") }
                                    )
                                }

                                // Description Input
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    label = { Text("Description (Optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    maxLines = 3,
                                    placeholder = { Text("What's this payment for?") }
                                )

                                // Error and Success Messages
                                errorMessage?.let { error ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Text(
                                            text = error,
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }

                                successMessage?.let { success ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = success,
                                                color = Color(0xFF4CAF50),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Submit Button
                                Button(
                                    onClick = { submitPayment() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = !isProcessing &&
                                            amount.isNotBlank() &&
                                            allMembers.isNotEmpty() &&
                                            if (splitEqually) selectedMembers.isNotEmpty() else selectedMember != null,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = if (splitEqually) "Split Payment" else "Make Payment",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Member Selection Dialog
    if (showMemberSelection && allMembers.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showMemberSelection = false },
            title = {
                Text(
                    text = "Select Member to Pay",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn {
                    // Show free members first
                    if (freeMembers.isNotEmpty()) {
                        item {
                            Text(
                                text = "Available Members",
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(freeMembers.filter { it != currentUserName }) { member ->
                            MemberSelectionItem(
                                member = member,
                                isAvailable = true,
                                onSelect = {
                                    selectedMember = member
                                    showMemberSelection = false
                                    errorMessage = null
                                }
                            )
                        }
                    }

                    // Show not free members
                    if (notFreeMembers.isNotEmpty()) {
                        item {
                            Text(
                                text = "Other Members",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(notFreeMembers.filter { it != currentUserName }) { member ->
                            MemberSelectionItem(
                                member = member,
                                isAvailable = false,
                                onSelect = {
                                    selectedMember = member
                                    showMemberSelection = false
                                    errorMessage = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showMemberSelection = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MemberSelectionItem(
    member: String,
    isAvailable: Boolean,
    onSelect: () -> Unit
) {
    TextButton(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isAvailable)
                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.first().toString(),
                    color = if (isAvailable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = member,
                    fontSize = 16.sp,
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