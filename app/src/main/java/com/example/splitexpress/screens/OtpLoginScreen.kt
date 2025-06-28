package com.example.splitexpress.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPLoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "OTP Login",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Subtitle
        Text(
            text = if (!isOtpSent) "Enter your email to receive OTP" else "Enter the OTP sent to your email",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Email Input (always shown)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = "Email")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = !isOtpSent, // Disable when OTP is sent
            shape = RoundedCornerShape(12.dp)
        )

        // OTP Input (shown only after OTP is sent)
        if (isOtpSent) {
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                label = { Text("6-Digit OTP") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "OTP")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Error Message
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Success Message
        if (successMessage.isNotEmpty()) {
            Text(
                text = successMessage,
                color = Color(0xFF4CAF50),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Main Action Button
        Button(
            onClick = {
                if (!isOtpSent) {
                    // Send OTP
                    if (email.isBlank()) {
                        errorMessage = "Please enter your email"
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = ""

                        try {
                            val response = RetrofitInstance.api.getOTP(
                                com.example.splitexpress.network.OTPRequest(email = email)
                            )

                            if (response.isSuccessful && response.body() != null) {
                                successMessage = response.body()!!.message
                                isOtpSent = true
                                errorMessage = ""
                            } else {
                                errorMessage = "Failed to send OTP. Please try again."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Network error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    // Verify OTP
                    if (otp.length != 6) {
                        errorMessage = "Please enter a valid 6-digit OTP"
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = ""

                        try {
                            val response = RetrofitInstance.api.verifyOTP(
                                com.example.splitexpress.network.OTPVerification(
                                    email = email,
                                    otp = otp
                                )
                            )

                            if (response.isSuccessful && response.body() != null) {
                                val loginResponse = response.body()!!

                                // Save tokens
                                TokenManager.saveToken(
                                    context,
                                    loginResponse.token,
                                    loginResponse.refresh_token
                                )

                                // Navigate to home
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } else {
                                errorMessage = "Invalid OTP. Please try again."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Network error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (!isOtpSent) "Send OTP" else "Verify OTP",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary Actions
        if (isOtpSent) {
            // Resend OTP Button
            TextButton(
                onClick = {
                    isOtpSent = false
                    otp = ""
                    errorMessage = ""
                    successMessage = ""
                }
            ) {
                Text("Change Email / Resend OTP")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Back to Login Options
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                onClick = {
                    navController.navigate("login") {
                        popUpTo("otplogin") { inclusive = true }
                    }
                }
            ) {
                Text("Password Login")
            }

            Text(
                text = "•",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = {
                    navController.navigate("signup")
                }
            ) {
                Text("Sign Up")
            }
        }
    }
}