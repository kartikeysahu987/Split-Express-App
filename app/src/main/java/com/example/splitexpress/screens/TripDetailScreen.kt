package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AccountBox
//import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.*
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    tripId: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var settlements by remember { mutableStateOf<List<Settlement>>(emptyList()) }
    var currentUserName by remember { mutableStateOf<String?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var tripName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var isSettling by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Function to fetch trip details including invite code
    suspend fun fetchTripDetails(token: String) {
        try {
            Log.d("TripDetailScreen", "Fetching trip details for tripId: $tripId")

            // Try getAllMyTrips first
            val myTripsResponse = RetrofitInstance.api.getAllMyTrips(token = token)
            if (myTripsResponse.isSuccessful) {
                val trips = myTripsResponse.body()?.user_items ?: emptyList()
                Log.d("TripDetailScreen", "Found ${trips.size} trips in getAllMyTrips")

                val currentTrip = trips.find { trip ->
                    trip.trip_id == tripId || trip._id == tripId
                }

                if (currentTrip != null) {
                    inviteCode = currentTrip.invite_code
                    tripName = currentTrip.trip_name
                    Log.d("TripDetailScreen", "Found trip in getAllMyTrips - Invite code: $inviteCode, Name: $tripName")
                } else {
                    Log.d("TripDetailScreen", "Trip not found in getAllMyTrips, trying getAllTrips")

                    // If not found in my trips, try all trips
                    val allTripsResponse = RetrofitInstance.api.getAllTrips(token = token)
                    if (allTripsResponse.isSuccessful) {
                        val allTrips = allTripsResponse.body()?.user_items ?: emptyList()
                        Log.d("TripDetailScreen", "Found ${allTrips.size} trips in getAllTrips")

                        val foundTrip = allTrips.find { trip ->
                            trip.trip_id == tripId || trip._id == tripId
                        }

                        if (foundTrip != null) {
                            inviteCode = foundTrip.invite_code
                            tripName = foundTrip.trip_name
                            Log.d("TripDetailScreen", "Found trip in getAllTrips - Invite code: $inviteCode, Name: $tripName")
                        } else {
                            Log.e("TripDetailScreen", "Trip not found in any API call")
                        }
                    } else {
                        Log.e("TripDetailScreen", "getAllTrips failed: ${allTripsResponse.code()}")
                    }
                }
            } else {
                Log.e("TripDetailScreen", "getAllMyTrips failed: ${myTripsResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e("TripDetailScreen", "Exception fetching trip details", e)
        }
    }

    // Function to refresh data
    fun refreshData() {
        coroutineScope.launch {
            isRefreshing = true
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "No token found"
                    return@launch
                }

                // Refresh trip details including invite code
                fetchTripDetails(token)

                // Fetch transactions
                val transactionsResponse = RetrofitInstance.api.getAllTransactions(
                    token = token,
                    request = GetTransactionsRequest(trip_id = tripId)
                )

                if (transactionsResponse.isSuccessful) {
                    val transactionsBody = transactionsResponse.body()
                    val fetchedTransactions = transactionsBody?.transactions ?: emptyList()

                    // Sort transactions by date - newest first
                    transactions = fetchedTransactions.sortedByDescending { transaction ->
                        try {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                            dateFormat.parse(transaction.created_at)?.time ?: 0L
                        } catch (e: Exception) {
                            Log.e("TripDetailScreen", "Error parsing date: ${transaction.created_at}", e)
                            0L
                        }
                    }

                    Log.d("TripDetailScreen", "Loaded and sorted ${transactions.size} transactions")
                }

                // Fetch settlements
                val settlementsResponse = RetrofitInstance.api.getSettlements(
                    token = token,
                    request = GetSettlementsRequest(trip_id = tripId)
                )

                if (settlementsResponse.isSuccessful) {
                    val settlementsBody = settlementsResponse.body()
                    settlements = settlementsBody?.settlements ?: emptyList()
                    Log.d("TripDetailScreen", "Loaded ${settlements.size} settlements")
                }

                // Clear any previous error message on successful refresh
                errorMessage = null

            } catch (e: Exception) {
                Log.e("TripDetailScreen", "Exception during refresh", e)
                errorMessage = "Failed to refresh data: ${e.message}"
            } finally {
                isRefreshing = false
            }
        }
    }

    // Refresh settlements when switching to settlements tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) { // Settlements tab
            refreshData()
        }
    }

    LaunchedEffect(tripId) {
        Log.d("TripDetailScreen", "Starting to fetch data for trip: $tripId")

        try {
            val token = TokenManager.getToken(context)
            Log.d("TripDetailScreen", "Token: ${if (token.isNullOrBlank()) "NULL/BLANK" else "EXISTS"}")

            if (token.isNullOrBlank()) {
                errorMessage = "No token found"
                isLoading = false
                return@LaunchedEffect
            }

            Log.d("TripDetailScreen", "Making API calls with trip_id: $tripId")

            // Fetch trip details including invite code
            fetchTripDetails(token)

            // Get current user's casual name
            val casualNameResponse = RetrofitInstance.api.getCasualNameByUID(
                token = token,
                request = GetCasualNameRequest(trip_id = tripId)
            )

            if (casualNameResponse.isSuccessful) {
                currentUserName = casualNameResponse.body()?.casual_name
                Log.d("TripDetailScreen", "Current user name: $currentUserName")
            } else {
                Log.e("TripDetailScreen", "Failed to get casual name: ${casualNameResponse.code()}")
            }

            // Fetch transactions
            val transactionsResponse = RetrofitInstance.api.getAllTransactions(
                token = token,
                request = GetTransactionsRequest(trip_id = tripId)
            )

            Log.d("TripDetailScreen", "Transactions response code: ${transactionsResponse.code()}")

            if (transactionsResponse.isSuccessful) {
                val transactionsBody = transactionsResponse.body()
                val fetchedTransactions = transactionsBody?.transactions ?: emptyList()

                // Sort transactions by date - newest first
                transactions = fetchedTransactions.sortedByDescending { transaction ->
                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        dateFormat.parse(transaction.created_at)?.time ?: 0L
                    } catch (e: Exception) {
                        Log.e("TripDetailScreen", "Error parsing date: ${transaction.created_at}", e)
                        0L
                    }
                }

                Log.d("TripDetailScreen", "Loaded and sorted ${transactions.size} transactions")
            } else {
                Log.e("TripDetailScreen", "Transactions API Error: ${transactionsResponse.code()} - ${transactionsResponse.message()}")
            }

            // Fetch settlements
            val settlementsResponse = RetrofitInstance.api.getSettlements(
                token = token,
                request = GetSettlementsRequest(trip_id = tripId)
            )

            Log.d("TripDetailScreen", "Settlements response code: ${settlementsResponse.code()}")

            if (settlementsResponse.isSuccessful) {
                val settlementsBody = settlementsResponse.body()
                settlements = settlementsBody?.settlements ?: emptyList()
                Log.d("TripDetailScreen", "Loaded ${settlements.size} settlements")
            } else {
                Log.e("TripDetailScreen", "Settlements API Error: ${settlementsResponse.code()} - ${settlementsResponse.message()}")
            }

            // Show error only if both requests failed
            if (!transactionsResponse.isSuccessful && !settlementsResponse.isSuccessful) {
                errorMessage = "Failed to load data: ${transactionsResponse.code()}"
            }

        } catch (e: Exception) {
            errorMessage = "Exception: ${e.message}"
            Log.e("TripDetailScreen", "Exception occurred", e)
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tripName ?: "Trip Details",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate("payScreen/$tripId")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Make Payment"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Invite Code Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable {
                        inviteCode?.let { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Invite Code", code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Invite code copied to clipboard!", Toast.LENGTH_SHORT).show()
                        } ?: run {
                            Toast.makeText(context, "Invite code not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Invite Code",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (inviteCode != null) {
                            Text(
                                text = inviteCode!!,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap to copy",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading...",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Copy invite code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Debug information (remove in production)
            if (isLoading) {
                Text(
                    text = "Debug: tripId = $tripId",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading data...",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error: $errorMessage",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp
                            )
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMessage = null
                                    refreshData()
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                else -> {
                    // Tab Row for switching between Transactions and Settlements
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Transactions (${transactions.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Settlements (${settlements.size})") }
                        )
                    }

                    // Content based on selected tab
                    when (selectedTab) {
                        0 -> {
                            // Transactions Tab
                            if (transactions.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No transactions found for this trip",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(transactions) { transaction ->
                                        TransactionCard(
                                            transaction = transaction,
                                            currentUserName = currentUserName
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Settlements Tab
                            if (settlements.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No settlements found for this trip",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(settlements) { settlement ->
                                        SettlementCard(
                                            settlement = settlement,
                                            currentUserName = currentUserName,
                                            isSettling = isSettling == "${settlement.from}-${settlement.to}-${settlement.amount}",
                                            onSettleClick = {
                                                val settlementKey = "${settlement.from}-${settlement.to}-${settlement.amount}"
                                                isSettling = settlementKey

                                                coroutineScope.launch {
                                                    try {
                                                        val token = TokenManager.getToken(context)
                                                        if (token != null) {
                                                            val settleRequest = SettleRequest(
                                                                trip_id = tripId,
                                                                payer_name = settlement.to,
                                                                reciever_name = settlement.from,
                                                                amount = settlement.amount,
                                                                description = "Settlement payment"
                                                            )

                                                            val response = RetrofitInstance.api.settle(
                                                                token = token,
                                                                request = settleRequest
                                                            )

                                                            if (response.isSuccessful) {
                                                                Log.d("TripDetailScreen", "Settlement successful")
                                                                // Refresh data to show updated transactions and settlements
                                                                refreshData()
                                                            } else {
                                                                Log.e("TripDetailScreen", "Settlement failed: ${response.code()}")
                                                                errorMessage = "Settlement failed. Please try again."
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("TripDetailScreen", "Settlement exception", e)
                                                        errorMessage = "Settlement error: ${e.message}"
                                                    } finally {
                                                        isSettling = null
                                                    }
                                                }
                                            }
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
}

@Composable
fun TransactionCard(
    transaction: Transaction,
    currentUserName: String?
) {
    // Determine involvement and calculate amounts
    val involvement = getTransactionInvolvement(transaction, currentUserName)
    val isSettlement = transaction.type.equals("settle", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSettlement)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Date and Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                // Date
                Text(
                    text = formatTransactionDate(transaction.created_at),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Category Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSettlement)
                                MaterialTheme.colorScheme.tertiary
                            else
                                getCategoryColor(transaction.description ?: "")
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSettlement)
                            Icons.Default.AccountBox
                        else
                            getCategoryIcon(transaction.description ?: ""),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Middle - Transaction Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Description/Title
                Text(
                    text = if (isSettlement) {
                        "Settlement"
                    } else {
                        transaction.description?.takeIf { it.isNotBlank() } ?: "Transaction"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Payer info or involvement status
                Text(
                    text = if (isSettlement) {
                        "${transaction.payer_name} settled with ${transaction.reciever_name}"
                    } else {
                        when (involvement.status) {
                            TransactionStatus.YOU_PAID -> "You paid ₹${transaction.amount}"
                            TransactionStatus.YOU_BORROWED -> "${transaction.payer_name} paid ₹${transaction.amount}"
                            TransactionStatus.NOT_INVOLVED -> "You are not involved"
                        }
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Right side - Status and Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (isSettlement) {
                    Text(
                        text = "settled",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${transaction.amount}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    // Status text
                    Text(
                        text = involvement.statusText,
                        fontSize = 12.sp,
                        color = involvement.statusColor,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Amount (only show if involved)
                    if (involvement.status != TransactionStatus.NOT_INVOLVED) {
                        Text(
                            text = "₹${involvement.displayAmount}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = involvement.amountColor
                        )
                    } else {
                        Text(
                            text = "not involved",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementCard(
    settlement: Settlement,
    currentUserName: String?,
    isSettling: Boolean,
    onSettleClick: () -> Unit
) {
    val canSettle = settlement.from.equals(currentUserName, ignoreCase = true)
    val isCurrentUserInvolved = settlement.from.equals(currentUserName, ignoreCase = true) ||
            settlement.to.equals(currentUserName, ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                canSettle -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) // You owe
                isCurrentUserInvolved -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) // You're owed
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) // Not involved
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settlement icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                canSettle -> MaterialTheme.colorScheme.error
                                isCurrentUserInvolved -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (canSettle) "↑" else if (isCurrentUserInvolved) "↓" else "↔",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Settlement details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            canSettle -> "You owe"
                            isCurrentUserInvolved -> "You are owed"
                            else -> "Settlement Required"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            canSettle -> MaterialTheme.colorScheme.error
                            isCurrentUserInvolved -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = "${settlement.from} → ${settlement.to}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Amount
                Text(
                    text = "₹${settlement.amount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        canSettle -> MaterialTheme.colorScheme.error
                        isCurrentUserInvolved -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // Settle button (show for all settlements, but only enable if current user can settle)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSettleClick,
                enabled = canSettle && !isSettling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSettle)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            ) {
                when {
                    isSettling -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Settling...")
                        }
                    }
                    canSettle -> {
                        Text("Settle Payment")
                    }
                    isCurrentUserInvolved -> {
                        Text("Waiting for ${settlement.from}")
                    }
                    else -> {
                        Text("Not your settlement")
                    }
                }
            }
        }
    }
}

// Data classes for transaction involvement
enum class TransactionStatus {
    YOU_PAID,
    YOU_BORROWED,
    NOT_INVOLVED
}

data class TransactionInvolvement(
    val status: TransactionStatus,
    val statusText: String,
    val statusColor: Color,
    val displayAmount: String,
    val amountColor: Color
)

// Helper functions
@Composable
fun getTransactionInvolvement(
    transaction: Transaction,
    currentUserName: String?
): TransactionInvolvement {
    return when {
        // User is the payer
        transaction.payer_name.equals(currentUserName, ignoreCase = true) -> {
            TransactionInvolvement(
                status = TransactionStatus.YOU_PAID,
                statusText = "you lent",
                statusColor = Color(0xFF4CAF50), // Green
                displayAmount = transaction.amount,
                amountColor = Color(0xFF4CAF50)
            )
        }

        // User is the receiver
        transaction.reciever_name.equals(currentUserName, ignoreCase = true) -> {
            TransactionInvolvement(
                status = TransactionStatus.YOU_BORROWED,
                statusText = "you borrowed",
                statusColor = Color(0xFFFF8A50), // Orange
                displayAmount = transaction.amount,
                amountColor = Color(0xFFFF8A50)
            )
        }

        // User is not involved
        else -> {
            TransactionInvolvement(
                status = TransactionStatus.NOT_INVOLVED,
                statusText = "not involved",
                statusColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                displayAmount = "0.00",
                amountColor = Color.Gray
            )
        }
    }
}
fun formatTransactionDate(dateString: String): String {
    return try {
        // Assuming the date format from API, adjust as needed
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM\ndd", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        // Fallback to current date if parsing fails
        val currentDate = Date()
        val calendar = Calendar.getInstance()
        calendar.time = currentDate

        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val dayFormat = SimpleDateFormat("dd", Locale.getDefault())

        "${monthFormat.format(currentDate)}\n${dayFormat.format(currentDate)}"
    }
}

@Composable
fun getCategoryColor(description: String): Color {
    return when {
        description.contains("food", ignoreCase = true) ||
                description.contains("restaurant", ignoreCase = true) ||
                description.contains("meal", ignoreCase = true) ||
                description.contains("dinner", ignoreCase = true) ||
                description.contains("lunch", ignoreCase = true) ||
                description.contains("breakfast", ignoreCase = true) -> Color(0xFFFF6B35) // Orange-red for food

        description.contains("fuel", ignoreCase = true) ||
                description.contains("gas", ignoreCase = true) ||
                description.contains("petrol", ignoreCase = true) -> Color(0xFF4285F4) // Blue for fuel

        description.contains("transport", ignoreCase = true) ||
                description.contains("taxi", ignoreCase = true) ||
                description.contains("uber", ignoreCase = true) ||
                description.contains("car", ignoreCase = true) ||
                description.contains("bus", ignoreCase = true) -> Color(0xFF34A853) // Green for transport

        description.contains("hotel", ignoreCase = true) ||
                description.contains("accommodation", ignoreCase = true) ||
                description.contains("stay", ignoreCase = true) ||
                description.contains("room", ignoreCase = true) -> Color(0xFF9C27B0) // Purple for accommodation

        description.contains("movie", ignoreCase = true) ||
                description.contains("entertainment", ignoreCase = true) ||
                description.contains("game", ignoreCase = true) ||
                description.contains("show", ignoreCase = true) -> Color(0xFFE91E63) // Pink for entertainment

        description.contains("flight", ignoreCase = true) ||
                description.contains("ticket", ignoreCase = true) ||
                description.contains("travel", ignoreCase = true) -> Color(0xFF00BCD4) // Cyan for travel

        description.contains("medical", ignoreCase = true) ||
                description.contains("hospital", ignoreCase = true) ||
                description.contains("doctor", ignoreCase = true) ||
                description.contains("medicine", ignoreCase = true) -> Color(0xFFFF5722) // Deep orange for medical

        description.contains("shopping", ignoreCase = true) ||
                description.contains("grocery", ignoreCase = true) ||
                description.contains("market", ignoreCase = true) -> Color(0xFF795548) // Brown for shopping

        else -> Color(0xFF607D8B) // Blue-grey for others
    }
}

fun getCategoryIcon(description: String): ImageVector {
    return when {
        description.contains("food", ignoreCase = true) ||
                description.contains("restaurant", ignoreCase = true) ||
                description.contains("meal", ignoreCase = true) ||
                description.contains("dinner", ignoreCase = true) ||
                description.contains("lunch", ignoreCase = true) ||
                description.contains("breakfast", ignoreCase = true) -> Icons.Default.Favorite

        description.contains("fuel", ignoreCase = true) ||
                description.contains("gas", ignoreCase = true) ||
                description.contains("petrol", ignoreCase = true) -> Icons.Default.Add

        description.contains("transport", ignoreCase = true) ||
                description.contains("taxi", ignoreCase = true) ||
                description.contains("uber", ignoreCase = true) ||
                description.contains("car", ignoreCase = true) ||
                description.contains("bus", ignoreCase = true) -> Icons.Default.ArrowBack

        description.contains("hotel", ignoreCase = true) ||
                description.contains("accommodation", ignoreCase = true) ||
                description.contains("stay", ignoreCase = true) ||
                description.contains("room", ignoreCase = true) -> Icons.Default.AccountBox

        description.contains("movie", ignoreCase = true) ||
                description.contains("entertainment", ignoreCase = true) ||
                description.contains("game", ignoreCase = true) ||
                description.contains("show", ignoreCase = true) -> Icons.Default.Favorite

        description.contains("flight", ignoreCase = true) ||
                description.contains("ticket", ignoreCase = true) ||
                description.contains("travel", ignoreCase = true) -> Icons.Default.ArrowBack

        description.contains("medical", ignoreCase = true) ||
                description.contains("hospital", ignoreCase = true) ||
                description.contains("doctor", ignoreCase = true) ||
                description.contains("medicine", ignoreCase = true) -> Icons.Default.Add

        description.contains("shopping", ignoreCase = true) ||
                description.contains("grocery", ignoreCase = true) ||
                description.contains("market", ignoreCase = true) -> Icons.Default.ShoppingCart

        description.contains("education", ignoreCase = true) ||
                description.contains("school", ignoreCase = true) ||
                description.contains("course", ignoreCase = true) -> Icons.Default.AccountBox

        description.contains("work", ignoreCase = true) ||
                description.contains("office", ignoreCase = true) ||
                description.contains("business", ignoreCase = true) -> Icons.Default.AccountBox

        else -> Icons.Default.Info // Default icon for unrecognized categories
    }
}