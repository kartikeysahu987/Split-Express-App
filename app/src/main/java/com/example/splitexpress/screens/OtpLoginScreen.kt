package com.example.splitexpress.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    var countdown by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Animation states
    val slideTransition = updateTransition(targetState = isOtpSent, label = "slideTransition")
    val titleAlpha by animateFloatAsState(
        targetValue = if (isOtpSent) 0.9f else 1f,
        animationSpec = tween(300)
    )

    // Countdown timer
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header Section
        HeaderSection(
            isOtpSent = isOtpSent,
            email = email,
            titleAlpha = titleAlpha
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Form Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Email Input
                EmailInputField(
                    email = email,
                    onEmailChange = { email = it },
                    enabled = !isOtpSent,
                    isError = errorMessage.contains("email", ignoreCase = true)
                )

                // OTP Input with Animation
                slideTransition.AnimatedVisibility(
                    visible = { it },
                    enter = slideInVertically(
                        initialOffsetY = { -40 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(400)),
                    exit = slideOutVertically(
                        targetOffsetY = { -40 },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    OTPInputField(
                        otp = otp,
                        onOtpChange = { if (it.length <= 6) otp = it },
                        isError = errorMessage.contains("OTP", ignoreCase = true),
                        onDone = {
                            keyboardController?.hide()
                            if (otp.length == 6) {
                                // Trigger OTP verification
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Messages Section
                MessagesSection(
                    errorMessage = errorMessage,
                    successMessage = successMessage
                )

                // Action Button
                ActionButton(
                    isOtpSent = isOtpSent,
                    isLoading = isLoading,
                    onClick = {
                        if (!isOtpSent) {
                            handleSendOTP(
                                email = email,
                                coroutineScope = coroutineScope,
                                onLoading = { isLoading = it },
                                onError = { errorMessage = it },
                                onSuccess = { message ->
                                    successMessage = message
                                    isOtpSent = true
                                    errorMessage = ""
                                    countdown = 60
                                }
                            )
                        } else {
                            handleVerifyOTP(
                                email = email,
                                otp = otp,
                                context = context,
                                navController = navController,
                                coroutineScope = coroutineScope,
                                onLoading = { isLoading = it },
                                onError = { errorMessage = it }
                            )
                        }
                    }
                )

                // Resend OTP Section
                if (isOtpSent) {
                    ResendOTPSection(
                        countdown = countdown,
                        onResend = {
                            if (countdown == 0) {
                                handleSendOTP(
                                    email = email,
                                    coroutineScope = coroutineScope,
                                    onLoading = { isLoading = it },
                                    onError = { errorMessage = it },
                                    onSuccess = { message ->
                                        successMessage = message
                                        countdown = 60
                                    }
                                )
                            }
                        },
                        onChangeEmail = {
                            isOtpSent = false
                            otp = ""
                            errorMessage = ""
                            successMessage = ""
                            countdown = 0
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Navigation Options
        NavigationOptions(navController = navController)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun HeaderSection(
    isOtpSent: Boolean,
    email: String,
    titleAlpha: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo/Icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = "Security",
                modifier = Modifier
                    .size(48.dp)
                    .alpha(titleAlpha),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Secure Login",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = isOtpSent,
            transitionSpec = {
                slideInVertically { height -> height } + fadeIn() with
                        slideOutVertically { height -> -height } + fadeOut()
            }
        ) { otpSent ->
            if (otpSent) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Enter the 6-digit code sent to",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = email,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Enter your email address to receive a verification code",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmailInputField(
    email: String,
    onEmailChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address") },
        leadingIcon = {
            Icon(
                Icons.Default.Email,
                contentDescription = "Email",
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (email.isNotEmpty() && enabled) {
                IconButton(onClick = { onEmailChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun OTPInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    isError: Boolean,
    onDone: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = onOtpChange,
            label = { Text("Verification Code") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "OTP",
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (otp.length == 6) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Valid",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // OTP Visual Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (index < otp.length)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
private fun MessagesSection(
    errorMessage: String,
    successMessage: String
) {
    AnimatedVisibility(
        visible = errorMessage.isNotEmpty() || successMessage.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (errorMessage.isNotEmpty())
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (errorMessage.isNotEmpty()) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (errorMessage.isNotEmpty())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = errorMessage.ifEmpty { successMessage },
                    color = if (errorMessage.isNotEmpty())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    isOtpSent: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (isOtpSent) Icons.Default.VerifiedUser else Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOtpSent) "Verify Code" else "Send Code",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ResendOTPSection(
    countdown: Int,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (countdown > 0) {
            Text(
                text = "Resend code in ${countdown}s",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        } else {
            TextButton(onClick = onResend) {
                Text("Resend Code", fontWeight = FontWeight.Medium)
            }
        }

        TextButton(onClick = onChangeEmail) {
            Text("Change Email Address")
        }
    }
}

@Composable
private fun NavigationOptions(navController: NavController) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
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
            onClick = { navController.navigate("signup") }
        ) {
            Text("Sign Up")
        }
    }
}

// Helper functions
private fun handleSendOTP(
    email: String,
    coroutineScope: CoroutineScope,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    if (email.isBlank()) {
        onError("Please enter your email address")
        return
    }

    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        onError("Please enter a valid email address")
        return
    }

    coroutineScope.launch {
        onLoading(true)
        onError("")

        try {
            val response = RetrofitInstance.api.getOTP(
                com.example.splitexpress.network.OTPRequest(email = email)
            )

            if (response.isSuccessful && response.body() != null) {
                onSuccess(response.body()!!.message)
            } else {
                onError("Failed to send verification code. Please try again.")
            }
        } catch (e: Exception) {
            onError("Network error: ${e.message}")
        } finally {
            onLoading(false)
        }
    }
}

private fun handleVerifyOTP(
    email: String,
    otp: String,
    context: android.content.Context,
    navController: NavController,
    coroutineScope: CoroutineScope,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    if (otp.length != 6) {
        onError("Please enter the complete 6-digit code")
        return
    }

    coroutineScope.launch {
        onLoading(true)
        onError("")

        try {
            val response = RetrofitInstance.api.verifyOTP(
                com.example.splitexpress.network.OTPVerification(
                    email = email,
                    otp = otp
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!

                TokenManager.saveToken(
                    context,
                    loginResponse.token,
                    loginResponse.refresh_token
                )

                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            } else {
                onError("Invalid verification code. Please try again.")
            }
        } catch (e: Exception) {
            onError("Network error: ${e.message}")
        } finally {
            onLoading(false)
        }
    }
}