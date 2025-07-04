package com.example.splitexpress.screens

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
//import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var responseMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Validation states
    var firstNameError by remember { mutableStateOf("") }
    var lastNameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Validation functions
    fun validateFirstName(): Boolean {
        return when {
            firstName.isBlank() -> {
                firstNameError = "First name is required"
                false
            }
            firstName.length < 2 -> {
                firstNameError = "First name must be at least 2 characters"
                false
            }
            !firstName.matches(Regex("^[a-zA-Z\\s]+$")) -> {
                firstNameError = "First name can only contain letters"
                false
            }
            else -> {
                firstNameError = ""
                true
            }
        }
    }

    fun validateLastName(): Boolean {
        return when {
            lastName.isBlank() -> {
                lastNameError = "Last name is required"
                false
            }
            lastName.length < 2 -> {
                lastNameError = "Last name must be at least 2 characters"
                false
            }
            !lastName.matches(Regex("^[a-zA-Z\\s]+$")) -> {
                lastNameError = "Last name can only contain letters"
                false
            }
            else -> {
                lastNameError = ""
                true
            }
        }
    }

    fun validateEmail(): Boolean {
        return when {
            email.isBlank() -> {
                emailError = "Email is required"
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailError = "Please enter a valid email address"
                false
            }
            else -> {
                emailError = ""
                true
            }
        }
    }

    fun validatePhone(): Boolean {
        return when {
            phone.isBlank() -> {
                phoneError = "Phone number is required"
                false
            }
            !phone.matches(Regex("^[0-9]{10}$")) -> {
                phoneError = "Phone number must be exactly 10 digits"
                false
            }
            else -> {
                phoneError = ""
                true
            }
        }
    }

    fun validatePassword(): Boolean {
        return when {
            password.isBlank() -> {
                passwordError = "Password is required"
                false
            }
            password.length < 8 -> {
                passwordError = "Password must be at least 8 characters"
                false
            }
            !password.matches(Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")) -> {
                passwordError = "Password must contain at least one uppercase letter, one lowercase letter, and one number"
                false
            }
            else -> {
                passwordError = ""
                true
            }
        }
    }

    fun validateConfirmPassword(): Boolean {
        return when {
            confirmPassword.isBlank() -> {
                confirmPasswordError = "Please confirm your password"
                false
            }
            password != confirmPassword -> {
                confirmPasswordError = "Passwords do not match"
                false
            }
            else -> {
                confirmPasswordError = ""
                true
            }
        }
    }

    fun isFormValid(): Boolean {
        return validateFirstName() && validateLastName() && validateEmail() &&
                validatePhone() && validatePassword() && validateConfirmPassword()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Header
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Join SplitExpress and start managing your expenses",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Form Fields
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // First Name
                OutlinedTextField(
                    value = firstName,
                    onValueChange = {
                        firstName = it.trimStart()
                        if (firstNameError.isNotEmpty()) validateFirstName()
                    },
                    label = { Text("First Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = firstNameError.isNotEmpty(),
                    supportingText = if (firstNameError.isNotEmpty()) {
                        { Text(firstNameError) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Last Name
                OutlinedTextField(
                    value = lastName,
                    onValueChange = {
                        lastName = it.trimStart()
                        if (lastNameError.isNotEmpty()) validateLastName()
                    },
                    label = { Text("Last Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = lastNameError.isNotEmpty(),
                    supportingText = if (lastNameError.isNotEmpty()) {
                        { Text(lastNameError) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.trim()
                        if (emailError.isNotEmpty()) validateEmail()
                    },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = emailError.isNotEmpty(),
                    supportingText = if (emailError.isNotEmpty()) {
                        { Text(emailError) }
                    } else null,
                    shape = RoundedCornerShape(12.dp)
                )

                // Phone Number
                OutlinedTextField(
                    value = phone,
                    onValueChange = { newValue ->
                        // Only allow digits and limit to 10 characters
                        if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                            phone = newValue
                            if (phoneError.isNotEmpty()) validatePhone()
                        }
                    },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = phoneError.isNotEmpty(),
                    supportingText = if (phoneError.isNotEmpty()) {
                        { Text(phoneError) }
                    } else if (phone.isNotEmpty() && phone.length < 10) {
                        { Text("${phone.length}/10 digits") }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("10-digit phone number") }
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (passwordError.isNotEmpty()) validatePassword()
                        if (confirmPassword.isNotEmpty()) validateConfirmPassword()
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Close else Icons.Default.RemoveRedEye,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = passwordError.isNotEmpty(),
                    supportingText = if (passwordError.isNotEmpty()) {
                        { Text(passwordError) }
                    } else {
                        { Text("At least 8 characters with uppercase, lowercase, and number") }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                // Confirm Password
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        if (confirmPasswordError.isNotEmpty()) validateConfirmPassword()
                    },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Close else Icons.Default.RemoveRedEye,
                                contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = confirmPasswordError.isNotEmpty(),
                    supportingText = if (confirmPasswordError.isNotEmpty()) {
                        { Text(confirmPasswordError) }
                    } else null,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Create Account Button
        Button(
            onClick = {
                if (isFormValid()) {
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
                                    user_type = "USER" // Default user type
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

                                    if (loginResponse.isSuccessful) {
                                        val loginData = loginResponse.body()
                                        if (loginData != null) {
                                            TokenManager.saveToken(
                                                context,
                                                loginData.token,
                                                loginData.refresh_token
                                            )
                                            TokenManager.saveUserData(context, loginData.user)
                                        }
                                    }

                                    kotlinx.coroutines.delay(500)
                                    navController.navigate("home") {
                                        popUpTo("signup") { inclusive = true }
                                    }
                                } else {
                                    responseMessage = "Account created but no data received"
                                }
                            } else {
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
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating Account...", fontSize = 16.sp)
                }
            } else {
                Text(
                    text = "Create Account",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Login Link
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Already have an account?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = {
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                },
                enabled = !isLoading
            ) {
                Text(
                    text = "Login",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Response Message
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
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = responseMessage,
                        color = if (responseMessage.contains("successfully")) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}