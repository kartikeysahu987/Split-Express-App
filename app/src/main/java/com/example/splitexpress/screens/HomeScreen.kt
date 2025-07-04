package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
//import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.network.Trip
import com.example.splitexpress.network.GetSettlementsRequest
import com.example.splitexpress.network.Settlement
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

// Enhanced data class with display formatting
data class SettlementSummary(
    val personName: String,
    val amountOwedToMe: Double,
    val amountIOwe: Double,
    val netAmount: Double,
    val isPositive: Boolean = netAmount > 0,
    val formattedAmount: String = "₹${String.format("%.2f", kotlin.math.abs(netAmount))}"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var settlementSummaries by remember { mutableStateOf<List<SettlementSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load data
    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "Loading data...")

        try {
            val rawToken = TokenManager.getToken(context)

            if (rawToken.isNullOrBlank()) {
                errorMessage = "Authentication required. Please log in again."
                isLoading = false
                return@LaunchedEffect
            }

            val response = RetrofitInstance.api.getAllMyTrips(rawToken)

            if (response.isSuccessful) {
                val sortedTrips = response.body()?.user_items?.sortedByDescending {
                    it.created_at
                } ?: emptyList()


                trips = sortedTrips

                // Load settlements concurrently
                val settlementTasks = trips.map { trip ->
                    async {
                        try {
                            val settlementResponse = RetrofitInstance.api.getSettlements(
                                token = rawToken,
                                request = GetSettlementsRequest(trip.trip_id ?: "")
                            )

                            if (settlementResponse.isSuccessful) {
                                settlementResponse.body()?.settlements ?: emptyList()
                            } else {
                                emptyList<Settlement>()
                            }
                        } catch (e: Exception) {
                            emptyList<Settlement>()
                        }
                    }
                }

                val allSettlements = settlementTasks.awaitAll().flatten()
                settlementSummaries = processSettlements(allSettlements)

            } else {
                errorMessage = "Unable to load your trips. Please try again."
            }
        } catch (e: Exception) {
            errorMessage = "Connection error. Please check your internet and try again."
        } finally {
            delay(300)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CompactTopBar(
                onLogout = {
                    TokenManager.clearTokens(context)
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        },
        floatingActionButton = {
            EnhancedFAB(
                onClick = { navController.navigate("createtrip") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        if (isLoading) {
            LoadingScreen()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick Actions Section
                item {
                    QuickActionsSection(navController)
                }

                // Settlement Overview (if available)
                if (settlementSummaries.isNotEmpty()) {
                    item {
                        FinancialOverview(settlementSummaries)
                    }
                }

                // Trips Section
                item {
                    TripsHeader(tripCount = trips.size)
                }

                // Handle different states
                when {
                    errorMessage != null -> {
                        item {
                            ErrorStateCard(errorMessage!!) {
                                isLoading = true
                                errorMessage = null
                            }
                        }
                    }
                    trips.isEmpty() -> {
                        item {
                            EmptyTripsCard(navController)
                        }
                    }
                    else -> {
                        items(trips) { trip ->
                            ProfessionalTripCard(
                                trip = trip,
                                onClick = { navController.navigate("tripDetails/${trip.trip_id}") }
                            )
                        }
                    }
                }

                // Bottom spacing for FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
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

@Composable
fun ProfessionalTripCard(
    trip: Trip,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trip Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Trip Info
            Column(modifier = Modifier.weight(1f)) {
                val displayName = when {
                    !trip.trip_name.isNullOrBlank() -> trip.trip_name
                    !trip.trip_id.isNullOrBlank() -> "Trip ${trip.trip_id.takeLast(6)}"
                    else -> "Unnamed Trip"
                }

                Text(
                    text = displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${trip.members?.size ?: 0} members",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (!trip.invite_code.isNullOrBlank()) {
                        Text(
                            " • ${trip.invite_code.takeLast(6)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Arrow Icon
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
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

//@Composable
//fun LoadingScreen() {
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//        Column(
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            CircularProgressIndicator(
//                modifier = Modifier.size(40.dp),
//                color = MaterialTheme.colorScheme.primary,
//                strokeWidth = 3.dp
//            )
//
//            Text(
//                "Loading...",
//                fontSize = 14.sp,
//                color = MaterialTheme.colorScheme.outline,
//                modifier = Modifier.padding(top = 16.dp)
//            )
//        }
//    }
//}

// Enhanced settlement processing
fun processSettlements(settlements: List<Settlement>): List<SettlementSummary> {
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

    return summaryMap.values.map { summary ->
        val netAmount = summary.amountOwedToMe - summary.amountIOwe
        summary.copy(
            netAmount = netAmount,
            isPositive = netAmount > 0,
            formattedAmount = "₹${String.format("%.2f", kotlin.math.abs(netAmount))}"
        )
    }.filter { it.netAmount != 0.0 }
        .sortedByDescending { it.netAmount }
}