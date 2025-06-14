package com.example.splitexpress.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitexpress.network.CreateTripRequest
import com.example.splitexpress.network.CreateTripResponse
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch
import retrofit2.Response

class CreateTripViewModel : ViewModel() {
    var tripName by mutableStateOf("")
    var description by mutableStateOf("")
    var membersText by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun createTrip(
        token: String?,
        request: CreateTripRequest,
        onSuccess: () -> Unit = {}
    ) {
        if (token.isNullOrBlank()) {
            message = "User not authenticated"
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                val response: Response<CreateTripResponse> = RetrofitInstance.api.createTrip(
                    token = token,
                    request = request
                )
                if (response.isSuccessful) {
                    message = response.body()?.message ?: "Trip created successfully"
                    onSuccess()
                } else {
                    message = "Error ${response.code()}: ${response.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                message = "Network error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    viewModel: CreateTripViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onTripCreated: () -> Unit
) {
    val context = LocalContext.current
    val token = TokenManager.getToken(context)

    // Direct access to ViewModel state
    val tripName = viewModel.tripName
    val description = viewModel.description
    val membersText = viewModel.membersText
    val isLoading = viewModel.isLoading
    val message = viewModel.message

    Scaffold(
        topBar = { TopAppBar(title = { Text("Create Trip") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = tripName,
                onValueChange = { viewModel.tripName = it },
                label = { Text("Trip Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = membersText,
                onValueChange = { viewModel.membersText = it },
                label = { Text("Members (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            // Updated button using form inputs
            Button(
                onClick = {
                    // Parse members from comma-separated text
                    val membersList = membersText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    // Create request with form data
                    val request = CreateTripRequest(
                        trip_name = tripName,
                        description = description.ifBlank { null } ?: description,
                        members = membersList
                    )

                    viewModel.createTrip(
                        token = token,
                        request = request
                    ) {
                        onTripCreated()
                    }
                },
                enabled = !isLoading && tripName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Trip")
                }
            }

            message?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}