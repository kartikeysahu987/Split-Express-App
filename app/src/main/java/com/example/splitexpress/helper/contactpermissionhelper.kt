// utils/PermissionHelper.kt
package com.example.splitexpress.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController

@Composable
fun rememberContactPermission(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {}
): ContactPermissionState {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }

    return remember {
        ContactPermissionState(
            context = context,
            requestPermission = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
        )
    }
}

class ContactPermissionState(
    private val context: Context,
    private val requestPermission: () -> Unit
) {
    fun checkAndRequestPermission(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit = {}
    ) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                onPermissionGranted()
            }
            else -> {
                // Request permission
                requestPermission()
            }
        }
    }

    fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

// Extension function for easier usage in non-Compose contexts
fun Context.hasContactPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
}

// Usage Examples:

// Example 1: Basic usage in a Composable
@Composable
fun ExampleUsage() {
    val contactPermission = rememberContactPermission(
        onPermissionGranted = {
            // Handle permission granted
            println("Contact permission granted!")
            // Your logic here (e.g., read contacts)
        },
        onPermissionDenied = {
            // Handle permission denied
            println("Contact permission denied!")
            // Show message to user or handle gracefully
        }
    )

    // Check permission and request if needed
    LaunchedEffect(Unit) {
        contactPermission.checkAndRequestPermission(
            onPermissionGranted = {
                // This will be called if permission is already granted
                // or after user grants permission
            }
        )
    }

    // Or use it on button click
    Button(
        onClick = {
            contactPermission.checkAndRequestPermission(
                onPermissionGranted = {
                    // Read contacts or perform action
                }
            )
        }
    ) {
        Text("Access Contacts")
    }
}

// Example 2: Usage in your existing screens
@Composable
fun JoinTripScreenWithPermission(navController: NavHostController) {
    val contactPermission = rememberContactPermission(
        onPermissionGranted = {
            // Permission granted, proceed with contact access
            // You can read contacts here
        },
        onPermissionDenied = {
            // Show a message or alternative UI
        }
    )

    // Your existing JoinTripScreen content
    // When you need to access contacts:
    Button(
        onClick = {
            contactPermission.checkAndRequestPermission(
                onPermissionGranted = {
                    // Access contacts and populate the trip invitation
                }
            )
        }
    ) {
        Text("Invite from Contacts")
    }
}