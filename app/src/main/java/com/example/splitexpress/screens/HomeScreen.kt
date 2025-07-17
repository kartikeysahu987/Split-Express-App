package com.example.splitexpress.screens

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.splitexpress.network.DeleteTripRequest
import com.example.splitexpress.network.GetCasualNameRequest
import com.example.splitexpress.network.GetSettlementsRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.network.Settlement
import com.example.splitexpress.network.Trip
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

//region THEME & COLORS (For a self-contained, runnable example)
// In a real app, this should be in its own file (e.g., ui/theme/Theme.kt)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4285F4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F2FD), // Lighter blue container
    onPrimaryContainer = Color(0xFF0D47A1), // Darker blue for contrast
    secondary = Color(0xFF5F6368),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8F0FE), // Light blue-gray container
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF34A853), // Green for settlements/success states
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8F5E8), // Light green container for settlements
    onTertiaryContainer = Color(0xFF1B5E20),
    error = Color(0xFFEA4335),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEDEA),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFAFAFA), // Slightly gray background
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF5F5F5), // Light gray for cards
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFE0E0E0), // Light border color
    outlineVariant = Color(0xFFF0F0F0),
    scrim = Color(0x80000000),
    surfaceTint = Color(0xFF4285F4),
    inverseSurface = Color(0xFF313131),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF8AB4F8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFFBDC1C6),
    onSecondary = Color(0xFF2D3132),
    secondaryContainer = Color(0xFF3C4043),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFF81C784), // Light green for dark mode
    onTertiary = Color(0xFF1B5E20),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = Color(0xFFC8E6C9),
    error = Color(0xFFFF8A80),
    onError = Color(0xFFB71C1C),
    errorContainer = Color(0xFFD32F2F),
    onErrorContainer = Color(0xFFFFCDD2),
    background = Color(0xFF121212), // True dark background
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1E1E1E), // Dark surface for cards
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2D2D2D), // Darker variant for elevated surfaces
    onSurfaceVariant = Color(0xFFBDC1C6),
    outline = Color(0xFF5F6368), // Darker border
    outlineVariant = Color(0xFF3C4043),
    scrim = Color(0x80000000),
    surfaceTint = Color(0xFF8AB4F8),
    inverseSurface = Color(0xFFE8EAED),
    inverseOnSurface = Color(0xFF1F1F1F),
    inversePrimary = Color(0xFF4285F4),
)
@Composable
fun SplitExpressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Assumes a default Typography is defined in your project
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
//endregion

//region Data and Navigation Models

data class SettlementSummary(
    val personName: String,
    val netAmount: Double,
    val isOwedToMe: Boolean = netAmount > 0,
    val formattedAmount: String = "₹${String.format("%.2f", abs(netAmount))}"
)

/**
 * Sealed class for type-safe bottom navigation, with selected/unselected icons.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Trips : Screen("trips", "Trips", Icons.Outlined.CardTravel, Icons.Filled.CardTravel)
    object Settlements : Screen("settlements", "Settlements", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet)
}

val bottomNavItems = listOf(Screen.Trips, Screen.Settlements)

//endregion

//region Main Screen with Bottom Navigation

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    // This NavController is for the tabs within the HomeScreen
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State is hoisted to this top-level component
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var settlementSummaries by remember { mutableStateOf<List<SettlementSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    val refreshData: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val rawToken = TokenManager.getToken(context)
                if (rawToken.isNullOrBlank()) {
                    errorMessage = "Authentication required. Please log in again."
                    isLoading = false
                    return@launch
                }
                val allTripsResponse = RetrofitInstance.api.getAllMyTrips(rawToken)
                if (allTripsResponse.isSuccessful) {
                    val allTrips = allTripsResponse.body()?.user_items ?: emptyList()
                    val activeTrips = allTrips.filter { it.isDeleted != true }.sortedByDescending { it.created_at }
                    trips = activeTrips
                    settlementSummaries = fetchAllSettlements(activeTrips, rawToken, context)
                } else {
                    errorMessage = "Unable to load your data. Please try again."
                }
            } catch (e: Exception) {
                errorMessage = "Connection error. Please check your internet and try again."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(key1 = Unit) {
        refreshData()
    }

    SplitExpressTheme {
        Scaffold(
            topBar = {
                SplitExpressTopAppBar(onLogout = {
                    TokenManager.clearTokens(context)
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                })
            },
            bottomBar = {
                AppBottomNavigation(navController = tabNavController)
            },
            floatingActionButton = {
                if (currentRoute == Screen.Trips.route) {
                    ExpandingFloatingActionButton(
                        isExpanded = isFabMenuExpanded,
                        onFabClick = { isFabMenuExpanded = !isFabMenuExpanded },
                        onCreateTripClick = {
                            isFabMenuExpanded = false
                            navController.navigate("createtrip")
                        },
                        onJoinTripClick = {
                            isFabMenuExpanded = false
                            navController.navigate("joinTrip")
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Crossfade(
                targetState = when {
                    isLoading -> "LOADING"
                    errorMessage != null -> "ERROR"
                    else -> "CONTENT"
                },
                modifier = Modifier.padding(innerPadding),
                animationSpec = tween(500), label = "StateCrossfade"
            ) { state ->
                when (state) {
                    "LOADING" -> LoadingState()
                    "ERROR" -> ErrorState(message = errorMessage!!, onRetry = refreshData)
                    "CONTENT" -> {
                        NavHost(
                            navController = tabNavController,
                            startDestination = Screen.Trips.route,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                val initialRoute = initialState.destination.route
                                val targetRoute = targetState.destination.route
                                val initialIndex = bottomNavItems.indexOfFirst { it.route == initialRoute }
                                val targetIndex = bottomNavItems.indexOfFirst { it.route == targetRoute }

                                if (initialIndex == -1 || targetIndex == -1) { // Fallback for initial load
                                    fadeIn(animationSpec = tween(300))
                                } else if (targetIndex > initialIndex) { // Navigating "forward"
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400))
                                } else { // Navigating "backward"
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400))
                                }
                            },
                            exitTransition = {
                                val initialRoute = initialState.destination.route
                                val targetRoute = targetState.destination.route
                                val initialIndex = bottomNavItems.indexOfFirst { it.route == initialRoute }
                                val targetIndex = bottomNavItems.indexOfFirst { it.route == targetRoute }

                                if (initialIndex == -1 || targetIndex == -1) { // Fallback for initial load
                                    fadeOut(animationSpec = tween(300))
                                } else if (targetIndex > initialIndex) { // Navigating "forward"
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400))
                                } else { // Navigating "backward"
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(400))
                                }
                            }
                        ) {
                            composable(Screen.Trips.route) {
                                TripsScreen(
                                    navController = navController,
                                    trips = trips,
                                    onDeleteClick = { trip ->
                                        tripToDelete = trip
                                        showDeleteDialog = true
                                    }
                                )
                            }
                            composable(Screen.Settlements.route) {
                                SettlementsScreen(
                                    settlementSummaries = settlementSummaries,
                                    onRefresh = refreshData
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && tripToDelete != null) {
            DeleteTripDialog(
                tripName = tripToDelete?.trip_name ?: "this trip",
                isDeleting = isDeleting,
                onDismiss = { if (!isDeleting) { showDeleteDialog = false; tripToDelete = null } },
                onConfirm = {
                    scope.launch {
                        isDeleting = true
                        val success = deleteTrip(tripToDelete!!, context)
                        if (success) {
                            snackbarHostState.showSnackbar("Trip deleted successfully.")
                            refreshData()
                        } else {
                            snackbarHostState.showSnackbar("Failed to delete trip. You may not be the creator.")
                        }
                        isDeleting = false
                        showDeleteDialog = false
                        tripToDelete = null
                    }
                }
            )
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        bottomNavItems.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { Icon(if (isSelected) screen.selectedIcon else screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
//endregion

//region Trips Screen and its Components

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun TripsScreen(
    navController: NavHostController,
    trips: List<Trip>,
    onDeleteClick: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trips.isEmpty()) {
        EmptyTripsState(
            onCreateTripClick = { navController.navigate("createtrip") }
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp // Space for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TripsHeader(tripCount = trips.size)
        }

        items(
            items = trips,
            key = { trip -> trip.trip_id }
        ) { trip ->
            TripCard(
                trip = trip,
                currentUserId = TokenManager.getUserId(LocalContext.current) ?: "",
                onViewDetailsClick = {
                    navController.navigate("tripDetails/${trip.trip_id}")
                },
                onDeleteClick = { onDeleteClick(trip) },
                modifier = Modifier.animateItemPlacement(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            )
        }
    }
}

@Composable
fun TripsHeader(
    tripCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Your Trips",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$tripCount ${if (tripCount == 1) "trip" else "trips"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCard(
    trip: Trip,
    currentUserId: String,
    onViewDetailsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onViewDetailsClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp,
            hoveredElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trip Avatar with Initial
            TripAvatar(
                tripName = trip.trip_name,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Trip Information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = trip.trip_name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Trip Metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Members Count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Members",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${trip.members.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Separator
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Creation Date
                    Text(
                        text = formatDate(trip.created_at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trip Status Badge
                if (trip.creator_id == currentUserId) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = "Creator",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Overflow Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TripCardDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    isCreator = trip.creator_id == currentUserId,
                    onViewDetails = {
                        menuExpanded = false
                        onViewDetailsClick()
                    },
                    onDelete = {
                        menuExpanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
fun TripAvatar(
    tripName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tripName.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TripCardDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isCreator: Boolean,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("View Details") },
            onClick = onViewDetails,
            leadingIcon = { Icon(Icons.Outlined.Info, "View Details") }
        )

        // REMOVED: The disabled "Edit" item is gone.
        if (isCreator) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Delete Trip", color = MaterialTheme.colorScheme.error) },
                onClick = onDelete,
                leadingIcon = { Icon(Icons.Outlined.Delete, "Delete Trip", tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

@Composable
fun EmptyTripsState(
    onCreateTripClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Explore,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Trips Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create your first trip to start splitting expenses with friends.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onCreateTripClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create a Trip")
        }
    }
}

//endregion

//region Settlements Screen and its Components

@Composable
fun SettlementsScreen(
    settlementSummaries: List<SettlementSummary>,
    onRefresh: () -> Unit
) {
    val totalOwedToMe = settlementSummaries.filter { it.isOwedToMe }.sumOf { it.netAmount }
    val totalIOwe = settlementSummaries.filter { !it.isOwedToMe }.sumOf { it.netAmount }

    if (settlementSummaries.isEmpty()) {
        EmptyState(
            title = "All Settled Up!",
            message = "You have no outstanding balances. Great job!",
            icon = Icons.Outlined.CheckCircle
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Overall Balance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
            }
        }
        item { SettlementOverviewCard(owedToMe = totalOwedToMe, iOwe = abs(totalIOwe)) }
        item {
            Text("Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        }
        items(settlementSummaries, key = { it.personName }) { summary ->
            SettlementItem(summary = summary)
        }
    }
}


@Composable
fun SettlementOverviewCard(owedToMe: Double, iOwe: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceAround) {
            val greenColor = Color(0xFF388E3C)
            val redColor = MaterialTheme.colorScheme.error
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You are Owed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(text = "₹${String.format("%.2f", owedToMe)}", style = MaterialTheme.typography.headlineSmall, color = greenColor, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You Owe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(text = "₹${String.format("%.2f", iOwe)}", style = MaterialTheme.typography.headlineSmall, color = redColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SettlementItem(summary: SettlementSummary) {
    val amountColor = if (summary.isOwedToMe) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
    val icon = if (summary.isOwedToMe) Icons.Default.NorthEast else Icons.Default.SouthWest
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = "Transaction direction", tint = amountColor)
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (summary.isOwedToMe) "${summary.personName} " else " ${summary.personName}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium
            )
            Text(text = summary.formattedAmount, style = MaterialTheme.typography.bodyLarge, color = amountColor, fontWeight = FontWeight.Bold)
        }
    }
}
//endregion

//region Shared UI Components

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitExpressTopAppBar(onLogout: () -> Unit) {
    TopAppBar(
        title = { Text("SplitExpress", fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log Out") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun ExpandingFloatingActionButton(
    isExpanded: Boolean,
    onFabClick: () -> Unit,
    onCreateTripClick: () -> Unit,
    onJoinTripClick: () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 45f else 0f, label = "FAB_Rotate")
    val fabAnimationSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = fabAnimationSpec) + scaleIn(animationSpec = fabAnimationSpec),
            exit = fadeOut(animationSpec = fabAnimationSpec) + scaleOut(animationSpec = fabAnimationSpec)
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Join Trip Row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                        Text("Join Trip", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    SmallFloatingActionButton(
                        onClick = onJoinTripClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Outlined.GroupAdd, contentDescription = "Join a Trip")
                    }
                }
                // Create Trip Row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                        Text("Create Trip", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    SmallFloatingActionButton(
                        onClick = onCreateTripClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Outlined.AddCircle, contentDescription = "Create a new Trip")
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Trip", modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
fun DeleteTripDialog(tripName: String, isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
        title = { Text("Delete Trip?") },
        text = { Text("Are you sure you want to permanently delete \"$tripName\"? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") } }
    )
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Loading your data...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
//endregion

//region Utility and Network Functions

@RequiresApi(Build.VERSION_CODES.O)
private fun formatDate(dateString: String): String {
    return try {
        val instant = Instant.parse(dateString)
        LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (e: Exception) { "Unknown Date" }
}

private suspend fun deleteTrip(trip: Trip, context: Context): Boolean {
    val token = TokenManager.getToken(context) ?: return false
    return try {
        RetrofitInstance.api.deleteTrip(token, DeleteTripRequest(trip_id = trip.trip_id)).isSuccessful
    } catch (e: Exception) { false }
}

private suspend fun fetchAllSettlements(activeTrips: List<Trip>, token: String, context: Context): List<SettlementSummary> {
    if (activeTrips.isEmpty()) return emptyList()
    return coroutineScope {
        val settlementResults = activeTrips.map { trip ->
            async {
                try {
                    val response = RetrofitInstance.api.getSettlements(token, GetSettlementsRequest(trip.trip_id))
                    if (response.isSuccessful) Pair(response.body()?.settlements ?: emptyList(), trip.trip_id) else Pair(emptyList(), trip.trip_id)
                } catch (e: Exception) { Pair(emptyList<Settlement>(), trip.trip_id) }
            }
        }.awaitAll()

        val currentUserCasualNames = activeTrips.map { trip ->
            async {
                try {
                    val request = GetCasualNameRequest(trip_id = trip.trip_id)
                    val response = RetrofitInstance.api.getCasualNameByUID(token, request)
                    Pair(trip.trip_id, response.body()?.casual_name ?: "")
                } catch (e: Exception) { Pair(trip.trip_id, "") }
            }
        }.awaitAll().toMap()

        val summaryMap = mutableMapOf<String, Double>()
        for ((settlements, tripId) in settlementResults) {
            val currentUserCasualName = currentUserCasualNames[tripId] ?: ""
            for (s in settlements) {
                val amount = s.amount.toDoubleOrNull() ?: 0.0
                when (currentUserCasualName) {
                    s.from -> summaryMap[s.to] = (summaryMap[s.to] ?: 0.0) + amount
                    s.to -> summaryMap[s.from] = (summaryMap[s.from] ?: 0.0) - amount
                }
            }
        }
        summaryMap.filter { abs(it.value) >= 0.01 }.map { (name, netAmount) -> // Filter out negligible amounts
            SettlementSummary(personName = name, netAmount = netAmount)
        }.sortedByDescending { it.netAmount }
    }
}