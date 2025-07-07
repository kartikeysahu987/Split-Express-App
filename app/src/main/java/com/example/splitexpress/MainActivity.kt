package com.example.splitexpress

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.splitexpress.ui.screens.CreateTripScreen
import com.example.splitexpress.ui.screens.CreateTripViewModel
import com.example.splitexpress.ui.theme.SplitExpressTheme
import com.example.splitexpress.utils.TokenManager
import com.example.splitexpress.screens.HomeScreen
import com.example.splitexpress.screens.JoinTripScreen
import com.example.splitexpress.screens.LoginScreen
import com.example.splitexpress.screens.SignupScreen
import com.example.splitexpress.screens.TripDetailScreen
import com.example.splitexpress.screens.PayScreen
import com.example.splitexpress.screens.OTPLoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitExpressTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // Use LocalContext to access prefs
    val context = LocalContext.current
    val isLoggedIn = TokenManager.isLoggedIn(context)
    val startDestination = if (isLoggedIn) "home" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Authentication Routes
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("signup") {
            SignupScreen(navController = navController)
        }
        composable("otplogin") {
            OTPLoginScreen(navController = navController)
        }

        // Main App Routes
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("createtrip") {
            val viewModel = viewModel<CreateTripViewModel>()
            CreateTripScreen(
                viewModel = viewModel,
                onTripCreated = {
                    navController.popBackStack() // or navController.navigate("home")
                },

                navController = navController
            )
        }

        // Add the new Join Trip route
        composable("joinTrip") {
            JoinTripScreen(navController = navController)
        }
        composable("tripDetails/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TripDetailScreen(navController, tripId)
        }

        // Add the new Pay Screen route
        composable("payScreen/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            PayScreen(navController = navController, tripId = tripId)
        }

        // Add other routes as needed
    }
}