package com.example.splitexpress.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.*
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TripDetailScreen(navController: NavController, tripId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(TripDetailUiState()) }

    fun refreshData() {
        scope.launch {
            uiState = uiState.copy(isRefreshing = true)
            try {
                val token = TokenManager.getToken(context) ?: return@launch
                val myTripsResponse = RetrofitInstance.api.getAllMyTrips(token = token)
                val currentTrip = myTripsResponse.body()?.user_items?.find { it.trip_id == tripId }

                // Fetch all transactions, including deleted ones, for display
                val transactionsResponse = RetrofitInstance.api.getAllTransactions(
                    token = token, request = GetTransactionsRequest(trip_id = tripId)
                )

                // Fetch settlements. The backend should correctly calculate this by ignoring deleted transactions.
                val settlementsResponse = RetrofitInstance.api.getSettlements(
                    token = token, request = GetSettlementsRequest(trip_id = tripId)
                )
                val membersResponse = currentTrip?.invite_code?.let { code ->
                    RetrofitInstance.api.getMembers(token = token, request = GetMembersRequest(invite_code = code))
                }
                val casualNameResponse = RetrofitInstance.api.getCasualNameByUID(
                    token = token, request = GetCasualNameRequest(trip_id = tripId)
                )

                uiState = uiState.copy(
                    tripName = currentTrip?.trip_name,
                    inviteCode = currentTrip?.invite_code,
                    transactions = transactionsResponse.body()?.transactions?.sortedByDescending {
                        parseDate(it.created_at)
                    } ?: emptyList(),
                    settlements = settlementsResponse.body()?.settlements ?: emptyList(),
                    members = membersResponse?.body(),
                    currentUserName = casualNameResponse.body()?.casual_name,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        scope.launch {
            uiState = uiState.copy(isDeleting = true)
            try {
                val token = TokenManager.getToken(context) ?: throw IllegalStateException("Token not found")
                val request = DeleteTransactionRequest(trip_id = tripId, _id = transaction._id)
                val response = RetrofitInstance.api.deleteTransaction(token, request)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Transaction deleted successfully", Toast.LENGTH_SHORT).show()
                    uiState = uiState.copy(showDeleteConfirmDialogFor = null, isDeleting = false)
                    refreshData() // Refresh data to reflect the deletion and update settlements
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Deletion failed"
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    uiState = uiState.copy(isDeleting = false, errorMessage = errorMsg)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                uiState = uiState.copy(isDeleting = false, errorMessage = "Error: ${e.message}")
            } finally {
                // Ensure dialog is closed even on failure
                uiState = uiState.copy(showDeleteConfirmDialogFor = null, isDeleting = false)
            }
        }
    }


    LaunchedEffect(tripId) {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) {
            uiState = uiState.copy(isLoading = false, errorMessage = "Authentication required")
            return@LaunchedEffect
        }
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.tripName ?: "Trip Details",
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }, enabled = !uiState.isRefreshing) {
                        AnimatedContent(
                            targetState = uiState.isRefreshing,
                            transitionSpec = { fadeIn() with fadeOut() }
                        ) { isRefreshing ->
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("payScreen/$tripId") },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("Add Payment") }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = { refreshData() }
                )
                else -> TripContent(
                    uiState = uiState,
                    onTabChange = { newTab -> uiState = uiState.copy(selectedTab = newTab) },
                    onInviteCodeCopy = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", code))
                        Toast.makeText(context, "Invite code copied!", Toast.LENGTH_SHORT).show()
                    },
                    onSettleClick = { settlement ->
                        // RESTORED from previous code
                        scope.launch {
                            try {
                                val token = TokenManager.getToken(context) ?: return@launch
                                uiState = uiState.copy(isSettling = "${settlement.from}-${settlement.to}")
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
                                    uiState = uiState.copy(errorMessage = "Settlement failed")
                                }
                            } catch (e: Exception) {
                                uiState = uiState.copy(errorMessage = "Settlement error: ${e.message}")
                            } finally {
                                uiState = uiState.copy(isSettling = null)
                            }
                        }
                    },
                    onDeleteRequest = { transaction ->
                        uiState = uiState.copy(showDeleteConfirmDialogFor = transaction)
                    }
                )
            }
        }

        // Conditionally show the dialog at the screen level
        uiState.showDeleteConfirmDialogFor?.let { transactionToDelete ->
            DeleteConfirmationDialog(
                transaction = transactionToDelete,
                onConfirm = { deleteTransaction(transactionToDelete) },
                onDismiss = { uiState = uiState.copy(showDeleteConfirmDialogFor = null) },
                isDeleting = uiState.isDeleting
            )
        }
    }
}

data class TripDetailUiState(
    val tripName: String? = null,
    val inviteCode: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val members: MembersResponse? = null,
    val currentUserName: String? = null,
    val selectedTab: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSettling: String? = null,
    val errorMessage: String? = null,
    val showDeleteConfirmDialogFor: Transaction? = null,
    val isDeleting: Boolean = false
)

@Composable
private fun DeleteConfirmationDialog(
    transaction: Transaction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Transaction") },
        text = { Text("Are you sure you want to delete this transaction? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Deleting...")
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading trip details...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun TripContent(
    uiState: TripDetailUiState,
    onTabChange: (Int) -> Unit,
    onInviteCodeCopy: (String) -> Unit,
    onSettleClick: (Settlement) -> Unit,
    onDeleteRequest: (Transaction) -> Unit
) {
    val filteredTransactions = uiState.transactions.filter { transaction ->
        transaction.payer_name.equals(uiState.currentUserName, ignoreCase = true) ||
                transaction.reciever_name.equals(uiState.currentUserName, ignoreCase = true)
    }
    // Count only active transactions for the tab badge
    val activeTransactionCount = filteredTransactions.count { it.isDeleted != true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InviteCodeCard(inviteCode = uiState.inviteCode, onCopyClick = onInviteCodeCopy)
        }

        item {
            TabNavigation(
                selectedTab = uiState.selectedTab,
                onTabChange = onTabChange,
                transactionCount = activeTransactionCount, // Show count of active transactions
                settlementCount = uiState.settlements.size,
                memberCount = uiState.members?.total_members ?: 0
            )
        }

        when (uiState.selectedTab) {
            0 -> items(filteredTransactions, key = { it._id }) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    currentUserName = uiState.currentUserName,
                    onDeleteRequest = { onDeleteRequest(transaction) }
                )
            }
            1 -> items(uiState.settlements) { settlement ->
                SettlementCard(
                    settlement = settlement,
                    currentUserName = uiState.currentUserName,
                    isSettling = uiState.isSettling?.contains("${settlement.from}-${settlement.to}") == true,
                    onSettleClick = onSettleClick
                )
            }
            2 -> {
                uiState.members?.let { members ->
                    item { MembersSummaryCard(members) }
                    items(members.not_free_members ?: emptyList()) { member -> MemberCard(member, true) }
                    items(members.free_members ?: emptyList()) { member -> MemberCard(member, false) }
                }
            }
        }

        if (uiState.selectedTab == 0 && activeTransactionCount == 0) {
            item { EmptyStateCard("No transactions yet", "Add your first payment to get started") }
        }
        if (uiState.selectedTab == 1 && uiState.settlements.isEmpty()) {
            item { EmptyStateCard("All settled up!", "No outstanding settlements. Deleting a transaction may have cleared all debts.") }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun InviteCodeCard(inviteCode: String?, onCopyClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Invite Code:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                if (inviteCode != null) {
                    Text(inviteCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text("••••••", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { inviteCode?.let(onCopyClick) }, enabled = inviteCode != null, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy invite code", tint = if (inviteCode != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TabNavigation(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    transactionCount: Int,
    settlementCount: Int,
    memberCount: Int
) {
    val tabs = listOf("Transactions" to transactionCount, "Settlements" to settlementCount, "Members" to memberCount)
    TabRow(selectedTabIndex = selectedTab) {
        tabs.forEachIndexed { index, (title, count) ->
            Tab(selected = selectedTab == index, onClick = { onTabChange(index) }, text = { Text("$title ($count)", style = MaterialTheme.typography.labelLarge) })
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    currentUserName: String?,
    onDeleteRequest: () -> Unit
) {
    val isSettlement = transaction.type.equals("settle", ignoreCase = true)
    val isPayer = transaction.payer_name.equals(currentUserName, ignoreCase = true)
    val isReceiver = transaction.reciever_name.equals(currentUserName, ignoreCase = true)
    val isDeleted = transaction.isDeleted == true
    var menuExpanded by remember { mutableStateOf(false) }

    val cardAlpha = if (isDeleted) 0.6f else 1f
    val textDecoration = if (isDeleted) TextDecoration.LineThrough else TextDecoration.None

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(cardAlpha),
        colors = CardDefaults.cardColors(
            containerColor = if (isSettlement) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDeleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val dateComponents = formatDateComponents(transaction.created_at)
                    Text(dateComponents.day, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    Text(dateComponents.month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(brush = androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(getCategoryColor(transaction.description ?: ""), getCategoryColor(transaction.description ?: "").copy(alpha = 0.8f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(getCategoryIcon(transaction.description ?: ""), null, tint = Color.White, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Transaction details with strikethrough
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSettlement) "Settlement Payment" else (transaction.description ?: "Transaction"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = textDecoration
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isSettlement -> "${transaction.payer_name} → ${transaction.reciever_name}"
                        isPayer -> "You paid ${transaction.reciever_name}"
                        isReceiver -> "${transaction.payer_name} paid you"
                        else -> "Not involved"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Amount section with strikethrough
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isPayer -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        isReceiver -> Color(0xFFFF8A50).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = "₹${transaction.amount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isPayer -> Color(0xFF4CAF50)
                            isReceiver -> Color(0xFFFF8A50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        textDecoration = textDecoration
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Icon(
                        imageVector = when {
                            isPayer -> Icons.Default.TrendingUp
                            isReceiver -> Icons.Default.TrendingDown
                            else -> Icons.Default.Remove
                        }, null, modifier = Modifier.size(12.dp),
                        tint = when {
                            isPayer -> Color(0xFF4CAF50)
                            isReceiver -> Color(0xFFFF8A50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isPayer -> "Lent"
                            isReceiver -> "Borrowed"
                            else -> "N/A"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isPayer -> Color(0xFF4CAF50)
                            isReceiver -> Color(0xFFFF8A50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // More options menu for deletion (only if not already deleted)
            if (isPayer && !isSettlement && !isDeleted) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDeleteRequest()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, "Delete transaction", tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            } else {
                // Add a spacer to maintain alignment when the icon is not present
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

data class DateComponents(val day: String, val month: String, val year: String)

fun formatDateComponents(dateString: String): DateComponents {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateString)
        if (date != null) {
            val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
            DateComponents(dayFormat.format(date), monthFormat.format(date).uppercase(), yearFormat.format(date))
        } else {
            DateComponents("--", "---", "----")
        }
    } catch (e: Exception) {
        DateComponents("--", "---", "----")
    }
}

// RESTORED from previous code
@Composable
private fun SettlementCard(
    settlement: Settlement,
    currentUserName: String?,
    isSettling: Boolean,
    onSettleClick: (Settlement) -> Unit
) {
    val canSettle = settlement.from.equals(currentUserName, ignoreCase = true)
    val isReceiver = settlement.to.equals(currentUserName, ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                canSettle -> MaterialTheme.colorScheme.primaryContainer
                isReceiver -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top // Changed from CenterVertically to Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f) // Added weight to prevent overflow
                ) {
                    Icon(
                        imageVector = when {
                            canSettle -> Icons.Default.TrendingUp
                            isReceiver -> Icons.Default.TrendingDown
                            else -> Icons.Default.SwapHoriz
                        },
                        contentDescription = null,
                        tint = when {
                            canSettle -> MaterialTheme.colorScheme.error
                            isReceiver -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            canSettle -> "${settlement.to} owes you"
                            isReceiver -> "You owe ${settlement.from}"
                            else -> "${settlement.from} owes ${settlement.to}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2, // Allow up to 2 lines for long names
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp)) // Reduced spacing

                // Amount text with better overflow handling
                Text(
                    text = "₹${"%.2f".format(settlement.amount.toDoubleOrNull() ?: 0.0)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        canSettle -> MaterialTheme.colorScheme.error
                        isReceiver -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(max = 120.dp) // Limit maximum width
                        .wrapContentWidth(Alignment.End)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (canSettle) {
                Button(
                    onClick = { onSettleClick(settlement) },
                    enabled = !isSettling,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (isSettling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settling...")
                    } else {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settle Payment")
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isReceiver) "Waiting for payment" else "Not your settlement",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


@Composable
private fun MembersSummaryCard(members: MembersResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Trip Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Total", "${members.total_members}", MaterialTheme.colorScheme.onPrimaryContainer)
                StatItem("Active", "${members.total_not_free}", Color(0xFF4CAF50))
                StatItem("Pending", "${members.total_free}", Color(0xFFFF9800))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun MemberCard(memberName: String, hasJoined: Boolean) {
    val statusColor = if (hasJoined) Color(0xFF4CAF50) else Color(0xFFFF9800)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memberName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasJoined) Icons.Default.CheckCircle else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasJoined) "Active" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

fun parseDate(dateString: String): Long {
    return try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateString)?.time ?: 0L
    } catch (e: Exception) { 0L }
}

fun formatDate(dateString: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateString)
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date ?: Date())
    } catch (e: Exception) { "Date unknown" }
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