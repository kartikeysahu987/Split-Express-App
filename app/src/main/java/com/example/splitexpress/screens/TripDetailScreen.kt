package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.GetTransactionsRequest
import com.example.splitexpress.network.GetSettlementsRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.network.Transaction
import com.example.splitexpress.network.Settlement
import com.example.splitexpress.utils.TokenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    tripId: String
) {
    val context = LocalContext.current

    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var settlements by remember { mutableStateOf<List<Settlement>>(emptyList()) }
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
                                LazyColumn {
                                    items(transactions) { transaction ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Text(
                                                    text = "Amount: ₹${transaction.amount}",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "From: ${transaction.payer_name} → To: ${transaction.reciever_name}",
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                                if (!transaction.description.isNullOrBlank()) {
                                                    Text(
                                                        text = "Description: ${transaction.description}",
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Type: ${transaction.type}",
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                                Text(
                                                    text = "ID: ${transaction._id}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
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
                                LazyColumn {
                                    items(settlements) { settlement ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Text(
                                                    text = "Settlement Amount: ₹${settlement.amount}",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${settlement.from} owes ${settlement.to}",
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
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
    }
}