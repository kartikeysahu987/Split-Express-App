package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.network.Trip
import com.example.splitexpress.utils.TokenManager
import com.vanpra.composematerialdialogs.datetime.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load trips when screen loads
    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "LaunchedEffect started")

        try {
            val rawToken = TokenManager.getToken(context)
            Log.d("HomeScreen", "Token retrieved: ${if (rawToken.isNullOrBlank()) "null/blank" else "exists"}")

            if (rawToken.isNullOrBlank()) {
                errorMessage = "Authentication token not found"
                isLoading = false
                Log.w("HomeScreen", "No token found, redirecting to auth")
                return@LaunchedEffect
            }

            Log.d("HomeScreen", "Making API call...")
            val response = RetrofitInstance.api.getAllMyTrips(rawToken)
            Log.d("HomeScreen", "API response received - Success: ${response.isSuccessful}, Code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                Log.d("HomeScreen", "Response body: $responseBody")

                // Safe null checking
                val userItems = responseBody?.user_items
                Log.d("HomeScreen", "User items: $userItems, Size: ${userItems?.size ?: 0}")

                // Add detailed logging for each trip
                userItems?.forEachIndexed { index, trip ->
                    Log.d("HomeScreen", "Trip $index: ID=${trip.trip_id}, Name='${trip.trip_name}', Description='${trip.description}'")
                }

                trips = userItems ?: emptyList()
                Log.d("HomeScreen", "Trips set successfully, count: ${trips.size}")

                // Additional logging after setting trips
                trips.forEachIndexed { index, trip ->
                    Log.d("HomeScreen", "Final Trip $index: Name='${trip.trip_name}' (null: ${trip.trip_name == null}, blank: ${trip.trip_name.isNullOrBlank()})")
                }
            } else {
                val errorMsg = "Failed to load trips: ${response.code()} ${response.message()}"
                Log.e("HomeScreen", errorMsg)

                // Try to get error body for more details
                try {
                    val errorBody = response.errorBody()?.string()
                    Log.e("HomeScreen", "Error body: $errorBody")
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Failed to read error body", e)
                }

                errorMessage = errorMsg
            }
        } catch (e: Exception) {
            val errorMsg = "Error loading trips: ${e.localizedMessage}"
            Log.e("HomeScreen", "Exception in LaunchedEffect", e)
            errorMessage = errorMsg
        } finally {
            isLoading = false
            Log.d("HomeScreen", "Loading finished")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Split Express",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        TokenManager.clearTokens(context)
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("createtrip")
            }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Create Trip", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Welcome Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Welcome to Split Express!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Manage your trips and split expenses with friends",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("createtrip") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Trip")
                }

                OutlinedButton(
                    onClick = { navController.navigate("joinTrip") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Join Trip")
                }
            }

            // Trips Section
            Text(
                text = "My Trips",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = {
                                // Retry loading
                                isLoading = true
                                errorMessage = null
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
                trips.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "No trips yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "Create your first trip to get started!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(trips) { trip ->
                            TripCard(trip) {
                                navController.navigate("tripDetails/${trip.trip_id}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripCard(trip: Trip, onTripClick: () -> Unit) {
    // Log the trip data when the card is being composed
    Log.d("TripCard", "Composing card for trip: ID=${trip.trip_id}, Name='${trip.trip_name}'")

    Card(onClick = onTripClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Improved trip name handling with better fallback
                val displayName = when {
                    !trip.trip_name.isNullOrBlank() -> trip.trip_name
                    !trip.trip_id.isNullOrBlank() -> "Trip ${trip.trip_id.takeLast(6)}"
                    else -> "Unnamed Trip"
                }

                Text(
                    text = displayName,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowForward, contentDescription = "View trip")
            }

            // Safe handling of description
            trip.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${trip.members?.size ?: 0} members")
                Text("Code: ${trip.invite_code?.takeLast(6) ?: "N/A"}")
            }

            // Debug information (remove this in production)
            if (BuildConfig.DEBUG) {
                Text(
                    text = "Debug: trip_name='${trip.trip_name}', trip_id='${trip.trip_id}'",
                    fontSize = 10.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}