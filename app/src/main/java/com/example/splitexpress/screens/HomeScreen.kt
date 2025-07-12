
package com.example.splitexpress.screens

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.DeleteTripRequest
import com.example.splitexpress.network.GetSettlementsRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.network.Settlement
import com.example.splitexpress.network.Trip
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Enhanced data class with display formatting
data class SettlementSummary(
    val personName: String,
    val amountOwedToMe: Double,
    val amountIOwe: Double,
    val netAmount: Double,
    val isPositive: Boolean = netAmount > 0,
    val formattedAmount: String = "₹${String.format("%.2f", kotlin.math.abs(netAmount))}"
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var settlementSummaries by remember { mutableStateOf<List<SettlementSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // State for delete confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    // Function to filter active trips (not deleted)
    fun getActiveTrips(allTrips: List<Trip>): List<Trip> {
        return allTrips.filter { trip ->
            // Filter out deleted trips - check both isDeleted flag and null safety
            trip.isDeleted != true
        }.sortedByDescending { it.created_at }
    }

    suspend fun updateSettlements(currentTrips: List<Trip>) {
        val rawToken = TokenManager.getToken(context) ?: return
        try {
            // Only calculate settlements for active (non-deleted) trips
            val activeTrips = getActiveTrips(currentTrips)

            if (activeTrips.isEmpty()) {
                settlementSummaries = emptyList()
                return
            }

            coroutineScope {
                val settlementTasks = activeTrips.map { trip ->
                    async {
                        try {
                            val settlementResponse = RetrofitInstance.api.getSettlements(
                                token = rawToken,
                                request = GetSettlementsRequest(trip.trip_id)
                            )
                            if (settlementResponse.isSuccessful) {
                                settlementResponse.body()?.settlements ?: emptyList()
                            } else {
                                Log.w("GetSettlements", "Failed for trip ${trip.trip_id}: ${settlementResponse.message()}")
                                emptyList<Settlement>()
                            }
                        } catch (e: Exception) {
                            Log.e("GetSettlements", "Failed for trip ${trip.trip_id}: ${e.message}")
                            emptyList<Settlement>()
                        }
                    }
                }
                val allSettlements = settlementTasks.awaitAll().flatten()
                settlementSummaries = processSettlements(allSettlements, context)
            }
        } catch (e: Exception) {
            Log.e("UpdateSettlements", "Error recalculating settlements: ${e.message}")
            settlementSummaries = emptyList()
        }
    }

    // Function to refresh all data
    suspend fun refreshData() {
        try {
            val rawToken = TokenManager.getToken(context)
            if (rawToken.isNullOrBlank()) {
                errorMessage = "Authentication required. Please log in again."
                return
            }

            val response = RetrofitInstance.api.getAllMyTrips(rawToken)
            if (response.isSuccessful) {
                val allTrips = response.body()?.user_items ?: emptyList()
                val activeTrips = getActiveTrips(allTrips)

                trips = activeTrips
                updateSettlements(activeTrips)
                errorMessage = null
            } else {
                errorMessage = "Unable to load your trips. Please try again."
                Log.e("RefreshData", "API Error: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            errorMessage = "Connection error. Please check your internet and try again."
            Log.e("RefreshData", "Exception: ${e.message}")
        }
    }

    // Load data on screen launch
    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "Loading data...")
        isLoading = true
        refreshData()
        delay(300)
        isLoading = false
    }

    Scaffold(
        topBar = {
            CompactTopBar(onLogout = {
                TokenManager.clearTokens(context)
                navController.navigate("login") { popUpTo("home") { inclusive = true } }
            })
        },
        floatingActionButton = {
            EnhancedFAB(onClick = { navController.navigate("createtrip") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading) {
            LoadingScreen()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick Actions Section
                item { QuickActionsSection(navController) }

                // Settlement Overview (only if there are active trips with settlements)
                if (settlementSummaries.isNotEmpty()) {
                    item { FinancialOverview(settlementSummaries) }
                }

                // Trips Section
                item { TripsHeader(tripCount = trips.size) }

                // Handle different states
                when {
                    errorMessage != null -> {
                        item {
                            ErrorStateCard(errorMessage!!) {
                                scope.launch {
                                    isLoading = true
                                    refreshData()
                                    isLoading = false
                                }
                            }
                        }
                    }
                    trips.isEmpty() -> {
                        item { EmptyTripsCard(navController) }
                    }
                    else -> {
                        val currentUserId = TokenManager.getUserId(context) ?: ""
                        items(trips, key = { it.trip_id }) { trip ->
                            ProfessionalTripCard(
                                trip = trip,
                                currentUserId = currentUserId,
                                onClick = { navController.navigate("tripDetails/${trip.trip_id}") },
                                onDeleteClick = {
                                    tripToDelete = trip
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
                // Bottom spacing for FAB
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog && tripToDelete != null) {
            DeleteConfirmationDialog(
                tripName = tripToDelete?.trip_name ?: "this trip",
                isDeleting = isDeleting,
                onDismiss = {
                    showDeleteDialog = false
                    tripToDelete = null
                },
                onConfirm = {
                    scope.launch {
                        isDeleting = true
                        val token = TokenManager.getToken(context)
                        if (token.isNullOrBlank() || tripToDelete == null) {
                            snackbarHostState.showSnackbar("Authentication error. Please log in again.")
                            isDeleting = false
                            showDeleteDialog = false
                            return@launch
                        }

                        try {
                            val response = RetrofitInstance.api.deleteTrip(
                                token = token,
                                request = DeleteTripRequest(trip_id = tripToDelete!!.trip_id)
                            )
                            if (response.isSuccessful) {
                                // Remove the deleted trip from the current list
                                val updatedTrips = trips.filterNot { it.trip_id == tripToDelete!!.trip_id }
                                trips = updatedTrips

                                // Recalculate settlements without the deleted trip
                                updateSettlements(updatedTrips)

                                snackbarHostState.showSnackbar(
                                    response.body()?.message ?: "Trip deleted successfully"
                                )
                                Log.d("DeleteTrip", "Trip ${tripToDelete!!.trip_id} deleted successfully")
                            } else {
                                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                                Log.e("DeleteTrip", "Error: ${response.code()} - $errorBody")
                                snackbarHostState.showSnackbar("Failed to delete trip. You might not be the creator.")
                            }
                        } catch (e: Exception) {
                            Log.e("DeleteTrip", "Exception: ${e.message}")
                            snackbarHostState.showSnackbar("An error occurred: ${e.message}")
                        } finally {
                            isDeleting = false
                            showDeleteDialog = false
                            tripToDelete = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    tripName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Delete Trip", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to permanently delete the trip \"$tripName\"? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopBar(onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "SplitEx",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        actions = {
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "Sign out",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp)
    )
}

@Composable
fun EnhancedFAB(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .size(56.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Create new trip",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun QuickActionsSection(navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionCard(
            title = "New Trip",
            subtitle = "Create & manage",
            icon = Icons.Outlined.Add,
            onClick = { navController.navigate("createtrip") },
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )

        ActionCard(
            title = "Join Trip",
            subtitle = "Enter invite code",
            icon = Icons.Outlined.Add,
            onClick = { navController.navigate("joinTrip") },
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun FinancialOverview(summaries: List<SettlementSummary>) {
    val totalOwedToMe = summaries.filter { it.isPositive }.sumOf { it.netAmount }
    val totalIOwe = summaries.filter { !it.isPositive }.sumOf { kotlin.math.abs(it.netAmount) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Financial Overview",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Icon(
                    Icons.Default.AccountBox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FinancialMetricCard(
                    title = "You get",
                    amount = totalOwedToMe,
                    icon = Icons.Default.KeyboardArrowUp,
                    isPositive = true,
                    modifier = Modifier.weight(1f)
                )

                FinancialMetricCard(
                    title = "You owe",
                    amount = totalIOwe,
                    icon = Icons.Default.KeyboardArrowDown,
                    isPositive = false,
                    modifier = Modifier.weight(1f)
                )
            }

            if (summaries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Recent Settlements",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(summaries) { summary ->
                        CompactSettlementItem(summary)
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialMetricCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)

    Card(
        modifier = modifier.height(70.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                "₹${String.format("%.0f", amount)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Text(
                title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun CompactSettlementItem(summary: SettlementSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (summary.isPositive)
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else
                        Color(0xFFF44336).copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = summary.personName.take(1).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (summary.isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.personName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = summary.formattedAmount,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (summary.isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

@Composable
fun TripsHeader(tripCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Your Trips",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (tripCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "$tripCount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

fun generateAvatarColor(text: String): Color {
    val hash = text.hashCode()
    val r = (hash and 0xFF0000 shr 16)
    val g = (hash and 0x00FF00 shr 8)
    val b = (hash and 0x0000FF)
    return Color(r, g, b).copy(alpha = 1f)
}

@RequiresApi(Build.VERSION_CODES.O)
fun formatDate1(dateString: String): String {
    return try {
        val instant = Instant.parse(dateString)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        localDateTime.format(formatter)
    } catch (e: Exception) {
        "Unknown Date"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalTripCard(
    trip: Trip,
    currentUserId: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val displayName = trip.trip_name.takeIf { it.isNotBlank() } ?: "Unnamed Trip"
    val avatarColor = generateAvatarColor(displayName)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(avatarColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.take(1).uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person, contentDescription = "Members",
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${trip.members.size} members",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        " • ${formatDate1(trip.created_at)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (trip.creator_id == currentUserId) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Trip",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EmptyTripsCard(navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Text(
                "Start Your Journey",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                "Create your first trip to start managing group expenses effortlessly",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                lineHeight = 20.sp
            )

            Button(
                onClick = { navController.navigate("createtrip") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Your First Trip")
            }
        }
    }
}

@Composable
fun ErrorStateCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )

            Text(
                "Something went wrong",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )

            Text(
                "Loading...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// Enhanced settlement processing
fun processSettlements(settlements: List<Settlement>, context: Context): List<SettlementSummary> {
    val currentUser = TokenManager.getCurrentUserName(context) ?: ""
    val summaryMap = mutableMapOf<String, SettlementSummary>()

    settlements.forEach { settlement ->
        val amount = settlement.amount.toDoubleOrNull() ?: 0.0

        // Update for the person who owes money
        val fromSummary = summaryMap.getOrPut(settlement.from) {
            SettlementSummary(settlement.from, 0.0, 0.0, 0.0)
        }
        summaryMap[settlement.from] = fromSummary.copy(
            amountIOwe = fromSummary.amountIOwe + amount
        )

        // Update for the person who is owed money
        val toSummary = summaryMap.getOrPut(settlement.to) {
            SettlementSummary(settlement.to, 0.0, 0.0, 0.0)
        }
        summaryMap[settlement.to] = toSummary.copy(
            amountOwedToMe = toSummary.amountOwedToMe + amount
        )
    }

    return summaryMap.values
        .filter { it.personName != currentUser } // Filter out current user
        .map { summary ->
            val netAmount = summary.amountOwedToMe - summary.amountIOwe
            summary.copy(
                netAmount = netAmount,
                isPositive = netAmount > 0,
                formattedAmount = "₹${String.format("%.2f", kotlin.math.abs(netAmount))}"
            )
        }.filter { it.netAmount != 0.0 }
        .sortedByDescending { it.netAmount }
}