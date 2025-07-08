package com.example.splitexpress.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.LoginRequest
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Custom theme colors to match the elegant design
private val DarkBackground = Color(0xFF0D0D25)
private val PrimaryBlue = Color(0xFF4D6FFF)
private val TextWhite = Color.White
private val TextFieldBackground = Color.White
private val TextFieldIconBlue = Color(0xFF4D6FFF)
private val TextFieldText = Color(0xFF1D1D1D)
private val TextGray = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var responseMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val passwordFocusRequester = remember { FocusRequester() }

    // Elegant dark background
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // SplitExpress Logo and Name
            SplitExpressLogo()

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Log in to your\nAccount",
                color = TextWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sign Up link
            SignUpText(navController = navController, enabled = !isLoading)

            Spacer(modifier = Modifier.height(48.dp))

            // Email field with elegant styling
            TextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TextFieldBackground,
                    unfocusedContainerColor = TextFieldBackground,
                    disabledContainerColor = TextFieldBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PrimaryBlue,
                    focusedTextColor = TextFieldText,
                    unfocusedTextColor = TextFieldText,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = "Email Icon",
                        tint = TextFieldIconBlue
                    )
                },
                placeholder = {
                    Text(
                        text = "Enter your email",
                        color = TextGray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                ),
                enabled = !isLoading,
                isError = emailError != null
            )

            // Email error message
            AnimatedVisibility(
                visible = emailError != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Text(
                    text = emailError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Password field with elegant styling
            TextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (email.isNotBlank() && password.isNotBlank()) {
                            performLogin(
                                email = email,
                                password = password,
                                context = context,
                                navController = navController,
                                coroutineScope = coroutineScope,
                                onLoadingChange = { isLoading = it },
                                onResponseMessage = { responseMessage = it },
                                onEmailError = { emailError = it },
                                onPasswordError = { passwordError = it }
                            )
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TextFieldBackground,
                    unfocusedContainerColor = TextFieldBackground,
                    disabledContainerColor = TextFieldBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PrimaryBlue,
                    focusedTextColor = TextFieldText,
                    unfocusedTextColor = TextFieldText,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Password Icon",
                        tint = TextFieldIconBlue
                    )
                },
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = image,
                            contentDescription = "Toggle password visibility",
                            tint = TextGray
                        )
                    }
                },
                placeholder = {
                    Text(
                        text = "Enter your password",
                        color = TextGray
                    )
                },
                singleLine = true,
                enabled = !isLoading,
                isError = passwordError != null
            )

            // Password error message
            AnimatedVisibility(
                visible = passwordError != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Text(
                    text = passwordError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login with OTP link
            Text(
                text = "Login with OTP",
                color = TextWhite,
                modifier = Modifier.clickable(enabled = !isLoading) {
                    navController.navigate("otplogin")
                },
                style = TextStyle(textDecoration = TextDecoration.Underline),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Elegant Log In Button (positioned higher)
            AnimatedLoginButton(
                isLoading = isLoading,
                enabled = email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    performLogin(
                        email = email,
                        password = password,
                        context = context,
                        navController = navController,
                        coroutineScope = coroutineScope,
                        onLoadingChange = { isLoading = it },
                        onResponseMessage = { responseMessage = it },
                        onEmailError = { emailError = it },
                        onPasswordError = { passwordError = it }
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Response Message
            AnimatedVisibility(
                visible = responseMessage.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                ResponseMessageCard(responseMessage)
            }

            // Bottom spacing for better scrolling
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SplitExpressLogo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Custom App Logo (fallback approach)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            // Outer circle with gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PrimaryBlue,
                                Color(0xFF6586FF),
                                PrimaryBlue.copy(alpha = 0.8f)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Inner content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SE",
                    color = TextWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Name
        Text(
            text = "SplitExpress",
            color = TextWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppLogo() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(64.dp)
    ) {
        val shieldGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF6586FF), PrimaryBlue)
        )
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Logo Shield",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(shieldGradient, blendMode = BlendMode.SrcAtop)
                    }
                }
        )
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Logo Star",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun SignUpText(navController: NavController, enabled: Boolean) {
    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(color = TextGray)) {
            append("Don't have an account? ")
        }
        withStyle(
            style = SpanStyle(
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            pushStringAnnotation(tag = "SignUp", annotation = "SignUp")
            append("Sign Up")
            pop()
        }
    }

    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            if (enabled) {
                annotatedString.getStringAnnotations(tag = "SignUp", start = offset, end = offset)
                    .firstOrNull()?.let {
                        navController.navigate("signup")
                    }
            }
        }
    )
}

@Composable
private fun AnimatedLoginButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryBlue,
            disabledContainerColor = Color(0xFF2A2A4A)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = TextWhite,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "Log In",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ResponseMessageCard(message: String) {
    val isSuccess = message.contains("Welcome")
    val cardColor = if (isSuccess) {
        Color(0xFF1B4332)
    } else {
        Color(0xFF5D1A1A)
    }
    val textColor = if (isSuccess) {
        Color(0xFF90EE90)
    } else {
        Color(0xFFFFB3B3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun performLogin(
    email: String,
    password: String,
    context: android.content.Context,
    navController: NavController,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onLoadingChange: (Boolean) -> Unit,
    onResponseMessage: (String) -> Unit,
    onEmailError: (String?) -> Unit,
    onPasswordError: (String?) -> Unit
) {
    // Input validation
    if (email.isBlank()) {
        onEmailError("Email is required")
        return
    }
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        onEmailError("Please enter a valid email address")
        return
    }
    if (password.isBlank()) {
        onPasswordError("Password is required")
        return
    }
    if (password.length < 6) {
        onPasswordError("Password must be at least 6 characters")
        return
    }

    onLoadingChange(true)
    coroutineScope.launch {
        try {
            withTimeout(180_000) { // 3 minutes timeout
                val response = RetrofitInstance.api.login(
                    LoginRequest(email.trim(), password)
                )

                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        TokenManager.saveToken(
                            context,
                            loginResponse.token,
                            loginResponse.refresh_token
                        )

                        TokenManager.saveUserData(context, loginResponse.user)

                        onResponseMessage("Welcome ${loginResponse.user.first_name ?: "User"}!")

                        delay(1500)
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    } else {
                        onResponseMessage("Login successful but no data received")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    onResponseMessage(when (response.code()) {
                        401 -> {
                            if (errorBody?.contains("Invalid email or password") == true) {
                                "Invalid email or password"
                            } else {
                                "Invalid credentials"
                            }
                        }
                        400 -> "Invalid request. Please check your input"
                        500 -> "Server error. Please try again later"
                        408 -> "Request timeout. Please try again"
                        504 -> "Gateway timeout. Please try again"
                        else -> "Login failed: ${response.code()}"
                    })
                }
            }
        } catch (e: TimeoutCancellationException) {
            onResponseMessage("Request timed out. Please check your connection and try again.")
            Log.e("LoginScreen", "Login timeout", e)
        } catch (e: Exception) {
            onResponseMessage("Network error: ${e.localizedMessage}")
            Log.e("LoginScreen", "Login error", e)
        } finally {
            onLoadingChange(false)
        }
    }
}