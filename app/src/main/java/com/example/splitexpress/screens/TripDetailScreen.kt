package com.example.splitexpress.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(navController: NavController, tripId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State management
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var settlements by remember { mutableStateOf<List<Settlement>>(emptyList()) }
    var members by remember { mutableStateOf<MembersResponse?>(null) }
    var currentUserName by remember { mutableStateOf<String?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var tripName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var isSettling by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Data loading functions
    suspend fun loadTripData(token: String) {
        try {
            val myTripsResponse = RetrofitInstance.api.getAllMyTrips(token = token)
            if (myTripsResponse.isSuccessful) {
                val currentTrip = myTripsResponse.body()?.user_items?.find {
                    it.trip_id == tripId || it._id == tripId
                }
                currentTrip?.let {
                    inviteCode = it.invite_code
                    tripName = it.trip_name
                }
            }
        } catch (e: Exception) {
            Log.e("TripDetailScreen", "Error loading trip data", e)
        }
    }

    suspend fun loadMembers(token: String, code: String) {
        try {
            val response = RetrofitInstance.api.getMembers(
                token = token,
                request = GetMembersRequest(invite_code = code)
            )
            if (response.isSuccessful) {
                members = response.body()
            }
        } catch (e: Exception) {
            Log.e("TripDetailScreen", "Error loading members", e)
        }
    }

    fun refreshData() {
        scope.launch {
            isRefreshing = true
            try {
                val token = TokenManager.getToken(context) ?: return@launch

                loadTripData(token)
                inviteCode?.let { loadMembers(token, it) }

                // Load transactions
                val transactionsResponse = RetrofitInstance.api.getAllTransactions(
                    token = token,
                    request = GetTransactionsRequest(trip_id = tripId)
                )
                if (transactionsResponse.isSuccessful) {
                    transactions = transactionsResponse.body()?.transactions?.sortedByDescending {
                        parseDate(it.created_at)
                    } ?: emptyList()
                }

                // Load settlements
                val settlementsResponse = RetrofitInstance.api.getSettlements(
                    token = token,
                    request = GetSettlementsRequest(trip_id = tripId)
                )
                if (settlementsResponse.isSuccessful) {
                    settlements = settlementsResponse.body()?.settlements ?: emptyList()
                }

                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Failed to refresh: ${e.message}"
            } finally {
                isRefreshing = false
            }
        }
    }

    // Initial data load
    LaunchedEffect(tripId) {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) {
            errorMessage = "No token found"
            isLoading = false
            return@LaunchedEffect
        }

        try {
            loadTripData(token)

            // Get current user name
            val casualNameResponse = RetrofitInstance.api.getCasualNameByUID(
                token = token,
                request = GetCasualNameRequest(trip_id = tripId)
            )
            if (casualNameResponse.isSuccessful) {
                currentUserName = casualNameResponse.body()?.casual_name
            }

            refreshData()
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Refresh on tab change
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 || selectedTab == 2) refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tripName ?: "Trip Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
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
                onClick = { navController.navigate("payScreen/$tripId") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add Payment", tint = Color.White)
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
            InviteCodeCard(
                inviteCode = inviteCode,
                onCopyClick = { code ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", code))
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            )

            when {
                isLoading -> LoadingScreen()
                errorMessage != null -> ErrorScreen(errorMessage!!) { refreshData() }
                else -> {
                    // Tab Navigation
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("Transactions (${transactions.size})", modifier = Modifier.padding(16.dp))
                        }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                            Text("Settlements (${settlements.size})", modifier = Modifier.padding(16.dp))
                        }
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                            Text("Members (${members?.total_members ?: "?"})", modifier = Modifier.padding(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content based on selected tab
                    when (selectedTab) {
                        0 -> TransactionsContent(transactions, currentUserName)
                        1 -> SettlementsContent(settlements, currentUserName, isSettling) { settlement ->
                            val canSettle = settlement.from.equals(currentUserName, ignoreCase = true)
                            if (canSettle) {
                                val settlementKey = "${settlement.from}-${settlement.to}-${settlement.amount}"
                                isSettling = settlementKey

                                scope.launch {
                                    try {
                                        val token = TokenManager.getToken(context) ?: return@launch
                                        val response = RetrofitInstance.api.settle(
                                            token = token,
                                            request = SettleRequest(
                                                trip_id = tripId,
                                                payer_name = settlement.to,
                                                reciever_name = settlement.from,
                                                amount = settlement.amount,
                                                description = "Settlement payment"
                                            )
                                        )

                                        if (response.isSuccessful) {
                                            refreshData()
                                        } else {
                                            errorMessage = "Settlement failed"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Settlement error: ${e.message}"
                                    } finally {
                                        isSettling = null
                                    }
                                }
                            }
                        }
                        2 -> MembersContent(members)
                    }
                }
            }
        }
    }
}

@Composable
fun InviteCodeCard(inviteCode: String?, onCopyClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { inviteCode?.let(onCopyClick) },
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
                Text("Invite Code", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))

                if (inviteCode != null) {
                    Text(inviteCode, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Tap to copy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading...", fontSize = 16.sp)
                    }
                }
            }
            Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

//@Composable
//fun LoadingScreen() {
//    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            CircularProgressIndicator()
//            Text("Loading data...", modifier = Modifier.padding(top = 8.dp))
//        }
//    }
//}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun TransactionsContent(transactions: List<Transaction>, currentUserName: String?) {
    if (transactions.isEmpty()) {
        EmptyState("No transactions found")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions) { transaction ->
                TransactionCard(transaction, currentUserName)
            }
        }
    }
}

@Composable
fun SettlementsContent(
    settlements: List<Settlement>,
    currentUserName: String?,
    isSettling: String?,
    onSettleClick: (Settlement) -> Unit
) {
    if (settlements.isEmpty()) {
        EmptyState("No settlements required")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(settlements) { settlement ->
                SettlementCard(settlement, currentUserName, isSettling, onSettleClick)
            }
        }
    }
}

@Composable
fun MembersContent(members: MembersResponse?) {
    members?.let { membersData ->
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Trip Summary", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatItem("Total", "${membersData.total_members}", Color.Gray)
                            StatItem("Joined", "${membersData.total_not_free}", Color(0xFF4CAF50))
                            StatItem("Pending", "${membersData.total_free}", Color(0xFFFF9800))
                        }
                    }
                }
            }

            // Joined Members
            if (!membersData.not_free_members.isNullOrEmpty()) {
                item {
                    Text("Joined Members", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
                items(membersData.not_free_members) { member ->
                    MemberCard(member, true)
                }
            }

            // Pending Members
            if (!membersData.free_members.isNullOrEmpty()) {
                item {
                    Text("Pending Members", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
                items(membersData.free_members) { member ->
                    MemberCard(member, false)
                }
            }
        }
    } ?: LoadingScreen()
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, color = color)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun MemberCard(memberName: String, hasJoined: Boolean) {
    val color = if (hasJoined) Color(0xFF4CAF50) else Color(0xFFFF9800)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = color)
                Spacer(modifier = Modifier.width(12.dp))
                Text(memberName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (hasJoined) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (hasJoined) "Joined" else "Pending",
                    fontSize = 12.sp,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TransactionCard(transaction: Transaction, currentUserName: String?) {
    val involvement = getTransactionInvolvement(transaction, currentUserName)
    val isSettlement = transaction.type.equals("settle", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSettlement)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date and Icon
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                Text(
                    formatDate(transaction.created_at),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(getCategoryColor(transaction.description ?: ""))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        getCategoryIcon(transaction.description ?: ""),
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSettlement) "Settlement" else (transaction.description ?: "Transaction"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (isSettlement)
                        "${transaction.payer_name} settled with ${transaction.reciever_name}"
                    else involvement.statusText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isSettlement) "settled" else involvement.statusText,
                    fontSize = 12.sp,
                    color = involvement.statusColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "₹${transaction.amount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = involvement.amountColor
                )
            }
        }
    }
}

@Composable
fun SettlementCard(
    settlement: Settlement,
    currentUserName: String?,
    isSettling: String?,
    onSettleClick: (Settlement) -> Unit
) {
    val canSettle = settlement.from.equals(currentUserName, ignoreCase = true)
    val isCurrentUserInvolved = settlement.from.equals(currentUserName, ignoreCase = true) ||
            settlement.to.equals(currentUserName, ignoreCase = true)
    val settlementKey = "${settlement.from}-${settlement.to}-${settlement.amount}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                canSettle -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                isCurrentUserInvolved -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSettle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (canSettle) "↑" else "↓",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            canSettle -> "You owe"
                            isCurrentUserInvolved -> "You are owed"
                            else -> "Settlement Required"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("${settlement.from} → ${settlement.to}", fontSize = 14.sp)
                }

                Text(
                    "₹${settlement.amount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSettle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onSettleClick(settlement) },
                enabled = canSettle && isSettling != settlementKey,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSettle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            ) {
                if (isSettling == settlementKey) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Settling...")
                } else {
                    Text(
                        when {
                            canSettle -> "Settle Payment"
                            isCurrentUserInvolved -> "Waiting for ${settlement.from}"
                            else -> "Not your settlement"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

// Helper Functions
data class TransactionInvolvement(
    val statusText: String,
    val statusColor: Color,
    val amountColor: Color
)

@Composable
fun getTransactionInvolvement(transaction: Transaction, currentUserName: String?): TransactionInvolvement {
    return when {
        transaction.payer_name.equals(currentUserName, ignoreCase = true) -> TransactionInvolvement(
            "you lent", Color(0xFF4CAF50), Color(0xFF4CAF50)
        )
        transaction.reciever_name.equals(currentUserName, ignoreCase = true) -> TransactionInvolvement(
            "you borrowed", Color(0xFFFF8A50), Color(0xFFFF8A50)
        )
        else -> TransactionInvolvement(
            "not involved", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), Color.Gray
        )
    }
}

fun parseDate(dateString: String): Long {
    return try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateString)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun formatDate(dateString: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateString)
        SimpleDateFormat("MMM\ndd", Locale.getDefault()).format(date ?: Date())
    } catch (e: Exception) {
        "---"
    }
}

@Composable
fun getCategoryColor(description: String): Color {
    return when {
        description.contains("food", ignoreCase = true) -> Color(0xFFFF6B35)
        description.contains("fuel", ignoreCase = true) -> Color(0xFF4285F4)
        description.contains("transport", ignoreCase = true) -> Color(0xFF34A853)
        description.contains("hotel", ignoreCase = true) -> Color(0xFF9C27B0)
        description.contains("entertainment", ignoreCase = true) -> Color(0xFFE91E63)
        description.contains("travel", ignoreCase = true) -> Color(0xFF00BCD4)
        description.contains("medical", ignoreCase = true) -> Color(0xFFFF5722)
        description.contains("shopping", ignoreCase = true) -> Color(0xFF795548)
        else -> Color(0xFF607D8B)
    }
}

fun getCategoryIcon(description: String): ImageVector {
    return when {
        description.contains("food", ignoreCase = true) -> Icons.Default.Restaurant
        description.contains("fuel", ignoreCase = true) -> Icons.Default.LocalGasStation
        description.contains("transport", ignoreCase = true) -> Icons.Default.DirectionsCar
        description.contains("hotel", ignoreCase = true) -> Icons.Default.Hotel
        description.contains("entertainment", ignoreCase = true) -> Icons.Default.Movie
        description.contains("travel", ignoreCase = true) -> Icons.Default.Flight
        description.contains("medical", ignoreCase = true) -> Icons.Default.LocalHospital
        description.contains("shopping", ignoreCase = true) -> Icons.Default.ShoppingCart
        else -> Icons.Default.Receipt
    }
}