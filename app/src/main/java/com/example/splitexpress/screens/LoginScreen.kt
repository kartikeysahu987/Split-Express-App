package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.splitexpress.network.LoginRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var responseMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val response = RetrofitInstance.api.login(
                                LoginRequest(email.trim(), password)
                            )


                            if (response.isSuccessful) {
                                val loginResponse = response.body()
                                if (loginResponse != null) {
                                    // Save tokens using TokenManager
                                    TokenManager.saveToken(
                                        context,
                                        loginResponse.token,
                                        loginResponse.refresh_token
                                    )

                                    // Save user data using TokenManager
                                    TokenManager.saveUserData(context, loginResponse.user)

                                    responseMessage = "Welcome ${loginResponse.user.first_name ?: "User"}!"

                                    // Navigate to home after brief delay
                                    kotlinx.coroutines.delay(1000)
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                } else {
                                    responseMessage = "Login successful but no data received"
                                }
                            }else {
                                // Handle error responses based on backend
                                val errorBody = response.errorBody()?.string()
                                responseMessage = when (response.code()) {
                                    401 -> {
                                        if (errorBody?.contains("Invalid email or password") == true) {
                                            "Invalid email or password"
                                        } else {
                                            "Invalid credentials"
                                        }
                                    }
                                    400 -> "Invalid request. Please check your input"
                                    500 -> "Server error. Please try again later"
                                    else -> "Login failed: ${response.code()}"
                                }
                            }
                        } catch (e: Exception) {
                            responseMessage = "Network error: ${e.localizedMessage}"
                            Log.e("LoginScreen", "Login error", e)
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    responseMessage = "Please fill in all fields"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                navController.navigate("otplogin")
            },
            enabled = !isLoading
        ) {
            Text("Forget Password ? Get Otp login")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                navController.navigate("signup")
            },
            enabled = !isLoading
        ) {
            Text("Don't have an account? Sign up")
        }

        if (responseMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (responseMessage.contains("Welcome")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Text(
                    text = responseMessage,
                    modifier = Modifier.padding(16.dp),
                    color = if (responseMessage.contains("Welcome")) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}