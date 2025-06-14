package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.splitexpress.network.SignupRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(navController: NavController) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var userType by remember { mutableStateOf("USER") }
    var responseMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Updated user types to match backend expectations
    val userTypes = listOf("USER", "ADMIN")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User Type Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = userType,
                onValueChange = {},
                readOnly = true,
                label = { Text("User Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                enabled = !isLoading
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                userTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            userType = type
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            isError = confirmPassword.isNotEmpty() && password != confirmPassword
        )

        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
            Text(
                text = "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    firstName.isBlank() -> responseMessage = "Please enter your first name"
                    lastName.isBlank() -> responseMessage = "Please enter your last name"
                    email.isBlank() -> responseMessage = "Please enter your email"
                    phone.isBlank() -> responseMessage = "Please enter your phone number"
                    password.isBlank() -> responseMessage = "Please enter a password"
                    password != confirmPassword -> responseMessage = "Passwords do not match"
                    password.length < 6 -> responseMessage = "Password must be at least 6 characters"
                    else -> {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val response = RetrofitInstance.api.signup(
                                    SignupRequest(
                                        first_name = firstName.trim(),
                                        last_name = lastName.trim(),
                                        email = email.trim(),
                                        password = password,
                                        phone = phone.trim(),
                                        user_type = userType
                                    )
                                )

                                if (response.isSuccessful) {
                                    val signupResponse = response.body()
                                    if (signupResponse != null) {
                                        responseMessage = "Account created successfully!"

                                        // Auto-login after successful signup
                                        kotlinx.coroutines.delay(1000)
                                        val loginResponse = RetrofitInstance.api.login(
                                            com.example.splitexpress.network.LoginRequest(email.trim(), password)
                                        )

                                        // In your SignupScreen.kt, replace the auto-login success block with this:

                                        if (loginResponse.isSuccessful) {
                                            val loginData = loginResponse.body()
                                            if (loginData != null) {
                                                // Save tokens using TokenManager
                                                TokenManager.saveToken(
                                                    context,
                                                    loginData.token,
                                                    loginData.refresh_token
                                                )

                                                // Save user data using TokenManager
                                                TokenManager.saveUserData(context, loginData.user)
                                            }
                                        }

                                        // Navigate to home after successful signup
                                        kotlinx.coroutines.delay(500)
                                        navController.navigate("home") {
                                            popUpTo("signup") { inclusive = true }
                                        }
                                    } else {
                                        responseMessage = "Account created but no data received"
                                    }
                                } else {
                                    // Handle error responses based on backend
                                    val errorBody = response.errorBody()?.string()
                                    responseMessage = when (response.code()) {
                                        400 -> {
                                            if (errorBody?.contains("Validation error") == true) {
                                                "Please check all required fields"
                                            } else {
                                                "Invalid input data"
                                            }
                                        }
                                        409 -> {
                                            if (errorBody?.contains("email") == true) {
                                                "This email is already registered"
                                            } else if (errorBody?.contains("phone") == true) {
                                                "This phone number is already registered"
                                            } else {
                                                "Email or phone already exists"
                                            }
                                        }
                                        500 -> "Server error. Please try again later"
                                        else -> "Signup failed: ${response.code()}"
                                    }
                                }
                            } catch (e: Exception) {
                                responseMessage = "Network error: ${e.localizedMessage}"
                                Log.e("SignupScreen", "Signup error", e)
                            } finally {
                                isLoading = false
                            }
                        }
                    }
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
                Text("Create Account")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                navController.navigate("login") {
                    popUpTo("signup") { inclusive = true }
                }
            },
            enabled = !isLoading
        ) {
            Text("Already have an account? Login")
        }

        if (responseMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (responseMessage.contains("successfully")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Text(
                    text = responseMessage,
                    modifier = Modifier.padding(16.dp),
                    color = if (responseMessage.contains("successfully")) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}