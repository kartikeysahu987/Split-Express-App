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
            .background(MaterialTheme.colorScheme.surface) // Simplified background
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        // Header Section
        HeaderSection(
            isOtpSent = isOtpSent,
            email = email,
            titleAlpha = titleAlpha
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- UI REFACTORED: REMOVED WRAPPING CARD ---
        // Form Section Contents
        Column(
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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            ActionButton(
                isOtpSent = isOtpSent,
                isLoading = isLoading,
                onClick = {
                    keyboardController?.hide()
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

            // Messages Section (Moved below the button)
            MessagesSection(
                errorMessage = errorMessage,
                successMessage = successMessage
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

        // Push navigation options to the bottom
        Spacer(modifier = Modifier.weight(1f))

        // Navigation Options
        NavigationOptions(navController = navController)
    }
}


// --- REFACTORED/UNCHANGED HELPER COMPOSABLES ---

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
        Icon(
            Icons.Filled.Security,
            contentDescription = "Security",
            modifier = Modifier
                .size(64.dp)
                .alpha(titleAlpha),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Secure Login",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = isOtpSent,
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                    slideOutVertically { height -> -height } + fadeOut()
                )
            }, label = "headerTextAnimation"
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
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Enter your email to receive a verification code",
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
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
private fun OTPInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    isError: Boolean,
    onDone: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = onOtpChange,
            label = { Text("Verification Code") },
            leadingIcon = { Icon(Icons.Default.Password, contentDescription = "OTP") },
            placeholder = { Text("6-Digit Code") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // OTP Visual Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            repeat(6) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isFocused = index == otp.length
                val hasChar = index < otp.length

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (hasChar) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// --- REFACTORED MessagesSection ---
@Composable
private fun MessagesSection(
    errorMessage: String,
    successMessage: String
) {
    val message = errorMessage.ifEmpty { successMessage }
    val isError = errorMessage.isNotEmpty()

    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(200, 200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
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
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, 90)).togetherWith(fadeOut(animationSpec = tween(90)))
            }, label = "buttonStateAnimation"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = if (isOtpSent) "Verify & Login" else "Send Code",
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
        modifier = Modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val canResend = countdown == 0
        TextButton(onClick = onResend, enabled = canResend) {
            Text(
                text = if (canResend) "Resend Code" else "Resend code in ${countdown}s",
                fontWeight = FontWeight.Medium
            )
        }
        TextButton(onClick = onChangeEmail) {
            Text("Change Email")
        }
    }
}

@Composable
private fun NavigationOptions(navController: NavController) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Or login with", style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = {
                navController.navigate("login") {
                    popUpTo("otplogin") { inclusive = true }
                }
            }
        ) {
            Text("Password", fontWeight = FontWeight.Bold)
        }
    }
}

// Helper functions (UNCHANGED)
private fun handleSendOTP(
    email: String,
    coroutineScope: CoroutineScope,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        onError("Please enter a valid email address")
        return
    }

    coroutineScope.launch {
        onLoading(true)
        onError("")
        onSuccess("")

        try {
            val response = RetrofitInstance.api.getOTP(
                com.example.splitexpress.network.OTPRequest(email = email)
            )

            if (response.isSuccessful && response.body() != null) {
                onSuccess(response.body()!!.message)
            } else {
                onError("Failed to send code. User may not exist.")
            }
        } catch (e: Exception) {
            onError("Network error. Please check your connection.")
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
                TokenManager.saveUserData(context, loginResponse.user)

                navController.navigate("home") {
                    popUpTo("otplogin") { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                onError("Invalid verification code. Please try again.")
            }
        } catch (e: Exception) {
            onError("Network error. Please check your connection.")
        } finally {
            onLoading(false)
        }
    }
}