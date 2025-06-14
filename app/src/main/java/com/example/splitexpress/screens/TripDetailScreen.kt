package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
//import androidx.compose.material.icons.filled.Receipt
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    tripId: String
) {
    val context = LocalContext.current

    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var settlements by remember { mutableStateOf<List<Settlement>>(emptyList()) }
    var currentUserName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

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

            // First, get current user's casual name
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
                transactions = transactionsBody?.transactions ?: emptyList()
                Log.d("TripDetailScreen", "Loaded ${transactions.size} transactions")
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
                        "Trip Details",
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
                .padding(16.dp)
        ) {
            Text(
                text = "Trip ID: $tripId",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                            // Transactions Tab - Splitwise Style
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
                                        SettlementCard(settlement = settlement)
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

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                        .background(getCategoryColor(transaction.description ?: ""))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(transaction.description ?: ""),
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
                    text = transaction.description?.takeIf { it.isNotBlank() }
                        ?: "Transaction",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Payer info or involvement status
                Text(
                    text = when (involvement.status) {
                        TransactionStatus.YOU_PAID -> "You paid ₹${transaction.amount}"
                        TransactionStatus.YOU_BORROWED -> "${transaction.payer_name} paid ₹${transaction.amount}"
                        TransactionStatus.NOT_INVOLVED -> "You are not involved"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Right side - Status and Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
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

@Composable
fun SettlementCard(settlement: Settlement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settlement icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↔",
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
                    text = "Settlement",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${settlement.from} owes ${settlement.to}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Amount
            Text(
                text = "₹${settlement.amount}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
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

fun getCategoryIcon(description: String): ImageVector {
    return when {
        description.contains("food", ignoreCase = true) ||
                description.contains("restaurant", ignoreCase = true) ||
                description.contains("momo", ignoreCase = true) ||
                description.contains("kebab", ignoreCase = true) ||
                description.contains("sweets", ignoreCase = true) ||
                description.contains("lassi", ignoreCase = true) -> Icons.Default.Favorite

        description.contains("auto", ignoreCase = true) ||
                description.contains("car", ignoreCase = true) ||
                description.contains("transport", ignoreCase = true) -> Icons.Default.ShoppingCart

        description.contains("ice cream", ignoreCase = true) ||
                description.contains("kulfi", ignoreCase = true) ||
                description.contains("drink", ignoreCase = true) ||
                description.contains("water", ignoreCase = true) ||
                description.contains("bottle", ignoreCase = true) -> Icons.Default.Info

        description.contains("shop", ignoreCase = true) ||
                description.contains("store", ignoreCase = true) ||
                description.contains("pharmacy", ignoreCase = true) ||
                description.contains("stuff", ignoreCase = true) -> Icons.Default.ShoppingCart

        else -> Icons.Default.Info
    }
}

fun getCategoryColor(description: String): Color {
    return when {
        description.contains("food", ignoreCase = true) ||
                description.contains("restaurant", ignoreCase = true) ||
                description.contains("momo", ignoreCase = true) ||
                description.contains("kebab", ignoreCase = true) ||
                description.contains("sweets", ignoreCase = true) ||
                description.contains("lassi", ignoreCase = true) -> Color(0xFFFF6B6B)

        description.contains("auto", ignoreCase = true) ||
                description.contains("car", ignoreCase = true) ||
                description.contains("transport", ignoreCase = true) -> Color(0xFF4ECDC4)

        description.contains("ice cream", ignoreCase = true) ||
                description.contains("kulfi", ignoreCase = true) ||
                description.contains("drink", ignoreCase = true) ||
                description.contains("water", ignoreCase = true) ||
                description.contains("bottle", ignoreCase = true) -> Color(0xFF45B7D1)

        description.contains("shop", ignoreCase = true) ||
                description.contains("store", ignoreCase = true) ||
                description.contains("pharmacy", ignoreCase = true) ||
                description.contains("stuff", ignoreCase = true) -> Color(0xFF96CEB4)

        else -> Color(0xFF6C5CE7)
    }
}