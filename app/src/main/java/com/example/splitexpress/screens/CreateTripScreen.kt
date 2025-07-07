package com.example.splitexpress.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitexpress.network.CreateTripRequest
import com.example.splitexpress.network.CreateTripResponse
import com.example.splitexpress.network.RetrofitInstance
import com.example.splitexpress.utils.TokenManager
import kotlinx.coroutines.launch
import retrofit2.Response
// Additional imports for contact functionality
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.focus.FocusManager
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.splitexpress.network.*
import com.example.splitexpress.helper.getContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

// Data class for member with account status
data class MemberWithStatus(
    val name: String,
    val contactno: String? = null,
    val hasAccount: Boolean = false,
    val uid: String? = null
)

// Enhanced ViewModel with improved contact functionality
class CreateTripViewModel : ViewModel() {
    var permissionChecked by mutableStateOf(false)
    var tripName by mutableStateOf("")
    var description by mutableStateOf("")
    var currentMemberInput by mutableStateOf("")
    var membersList by mutableStateOf<List<MemberWithStatus>>(emptyList())
    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var validationErrors by mutableStateOf<Map<String, String>>(emptyMap())

    // Contact-related state
    var deviceContacts by mutableStateOf<List<Contact>>(emptyList())
    var filteredContacts by mutableStateOf<List<Contact>>(emptyList())
    var contactSearchQuery by mutableStateOf("")
    var isLoadingContacts by mutableStateOf(false)
    var showContactsList by mutableStateOf(false)
    var hasContactPermission by mutableStateOf(false)
    var permissionDenied by mutableStateOf(false)
    var createdTripInviteCode by mutableStateOf<String?>(null)
    var contactsLoaded by mutableStateOf(false)

    // State for instant reload functionality
    var screenRefreshTrigger by mutableStateOf(0)
    var showPermissionGrantedMessage by mutableStateOf(false)

    fun addMember(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isNotBlank() && !membersList.any { it.name == trimmedName }) {
            membersList = membersList + MemberWithStatus(name = trimmedName)
            currentMemberInput = ""
        }
    }

    fun addContactAsMember(contact: Contact, token: String) {
        // Check if contact already exists in members list
        if (membersList.any { it.name == contact.name }) {
            message = "${contact.name} is already added"
            return
        }

        // Add contact to members list
        val memberWithStatus = MemberWithStatus(
            name = contact.name ?: "Unknown",
            contactno = contact.contactno,
            hasAccount = false
        )
        membersList = membersList + memberWithStatus

        // Check if contact has account
        checkContactAccount(contact, token)
    }

    private fun checkContactAccount(contact: Contact, token: String) {
        viewModelScope.launch {
            try {
                val normalizedContact = Contact(
                    name = contact.name,
                    contactno = normalizePhoneNumber(contact.contactno)
                )

                val result = getContactInfo(token, listOf(normalizedContact))

                result.onSuccess { response ->
                    val contactInfo = response.data.contactsinfo.firstOrNull()
                    if (contactInfo?.uid != null && contactInfo.uid.isNotBlank()) {
                        // Update member with account status
                        membersList = membersList.map { member ->
                            if (member.name == contact.name) {
                                member.copy(hasAccount = true, uid = contactInfo.uid)
                            } else {
                                member
                            }
                        }
                    }
                }.onFailure { error ->
                    // Contact doesn't have account, keep as regular member
                }
            } catch (e: Exception) {
                // Handle error silently or show minimal message
            }
        }
    }

    fun removeMember(member: MemberWithStatus) {
        membersList = membersList.filter { it.name != member.name }
    }

    fun updateContactSearch(query: String) {
        contactSearchQuery = query
        filteredContacts = if (query.isBlank()) {
            deviceContacts
        } else {
            deviceContacts.filter { contact ->
                contact.name?.contains(query, ignoreCase = true) == true ||
                        contact.contactno?.contains(query) == true
            }
        }
    }

    fun loadDeviceContacts(context: android.content.Context) {
        viewModelScope.launch {
            try {
                isLoadingContacts = true
                val contacts = withContext(Dispatchers.IO) {
                    getContactsFromDevice(context)
                }
                deviceContacts = contacts
                filteredContacts = contacts
                contactsLoaded = true
            } catch (e: Exception) {
                message = "Failed to load contacts: ${e.message}"
                contactsLoaded = false
            } finally {
                isLoadingContacts = false
            }
        }
    }

    // Enhanced permission result handler with instant reload
    fun onPermissionResult(isGranted: Boolean, context: android.content.Context) {
        hasContactPermission = isGranted
        permissionDenied = !isGranted

        if (isGranted) {
            // Show success message briefly
            showPermissionGrantedMessage = true

            // Trigger screen refresh and load contacts immediately
            screenRefreshTrigger++
            loadDeviceContacts(context)

            // Hide the success message after 2 seconds
            viewModelScope.launch {
                delay(2000)
                showPermissionGrantedMessage = false
            }
        } else {
            contactsLoaded = false
        }
    }

    fun resetPermissionState() {
        permissionDenied = false
        showPermissionGrantedMessage = false
    }

    // Function to force screen refresh
    fun refreshScreen() {
        screenRefreshTrigger++
    }

    fun automaticLinkMembers(token: String, inviteCode: String) {
        val membersToLink = membersList.filter { it.hasAccount && it.uid != null }

        if (membersToLink.isEmpty()) return

        viewModelScope.launch {
            try {
                isLoading = true
                var successCount = 0
                var errorCount = 0

                for (member in membersToLink) {
                    try {
                        val request = AutomaticLinkMemberRequest(
                            invite_code = inviteCode,
                            name = member.name,
                            uid = member.uid ?: ""
                        )

                        val response = RetrofitInstance.api.automaticLinkMember(token, request)

                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                    }
                }

                message = if (errorCount == 0) {
                    "Successfully linked $successCount members with accounts"
                } else {
                    "Linked $successCount members, $errorCount failed"
                }
            } catch (e: Exception) {
                message = "Failed to link members: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun validateForm(): Boolean {
        val errors = mutableMapOf<String, String>()

        if (tripName.isBlank()) {
            errors["tripName"] = "Trip name is required"
        } else if (tripName.length < 3) {
            errors["tripName"] = "Trip name must be at least 3 characters"
        }

        if (membersList.size > 20) {
            errors["members"] = "Maximum 20 members allowed"
        }

        validationErrors = errors
        return errors.isEmpty()
    }

    fun createTrip(
        token: String?,
        request: CreateTripRequest,
        onSuccess: (String) -> Unit = {}
    ) {
        if (!validateForm()) return

        if (token.isNullOrBlank()) {
            message = "Authentication required"
            return
        }

        isLoading = true
        message = null

        viewModelScope.launch {
            try {
                val response: Response<CreateTripResponse> = RetrofitInstance.api.createTrip(
                    token = token,
                    request = request
                )
                if (response.isSuccessful) {
                    val tripResponse = response.body()
                    val inviteCode = tripResponse?.invite_code

                    if (inviteCode != null) {
                        createdTripInviteCode = inviteCode

                        // Auto-link members with accounts
                        if (membersList.any { it.hasAccount }) {
                            automaticLinkMembers(token, inviteCode)
                        }

                        onSuccess(inviteCode)
                    } else {
                        message = "Trip created but no invite code received"
                    }
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

    fun clearMessage() {
        message = null
    }
}

// Function to normalize phone number
fun normalizePhoneNumber(phoneNumber: String?): String {
    if (phoneNumber.isNullOrBlank()) return ""

    // Remove all non-digit characters
    val digitsOnly = phoneNumber.replace(Regex("[^\\d]"), "")

    // If the number has more than 10 digits, take the last 10
    // This handles cases like +91XXXXXXXXXX or 91XXXXXXXXXX
    return if (digitsOnly.length > 10) {
        digitsOnly.takeLast(10)
    } else {
        digitsOnly
    }
}

// Function to get contacts from device
private suspend fun getContactsFromDevice(context: android.content.Context): List<Contact> {
    return withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex)

                if (name != null && number != null) {
                    // Normalize the phone number before adding to contacts
                    val normalizedNumber = normalizePhoneNumber(number)
                    contacts.add(Contact(name = name, contactno = normalizedNumber))
                }
            }
        }

        contacts.distinctBy { it.name }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    navController: NavController,
    viewModel: CreateTripViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onTripCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val token = TokenManager.getToken(context)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> viewModel.onPermissionResult(isGranted, context) }

    val hasContactPermission by remember {
        derivedStateOf {
            val result = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.hasContactPermission = result
            result
        }
    }

    // Auto-load contacts when permission granted
    LaunchedEffect(hasContactPermission, viewModel.screenRefreshTrigger) {
        if (hasContactPermission && !viewModel.contactsLoaded) {
            delay(100)
            viewModel.loadDeviceContacts(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Trip",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permission Success Banner
                item {
                    AnimatedVisibility(
                        visible = viewModel.showPermissionGrantedMessage,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        SuccessBanner(
                            text = "Permission granted! Loading contacts...",
                            icon = Icons.Default.CheckCircle
                        )
                    }
                }

                // Permission Request Card
                item {
                    if (!hasContactPermission && !viewModel.isLoadingContacts && !viewModel.showPermissionGrantedMessage) {
                        PermissionRequestCard(
                            onGrantPermission = {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        )
                    }
                }

                // Loading Contacts Banner
                item {
                    if (viewModel.isLoadingContacts) {
                        LoadingBanner(text = "Loading contacts...")
                    }
                }

                // Trip Form Section
                item {
                    TripFormSection(
                        tripName = viewModel.tripName,
                        description = viewModel.description,
                        onTripNameChange = { viewModel.tripName = it },
                        onDescriptionChange = { viewModel.description = it },
                        validationErrors = viewModel.validationErrors,
                        focusManager = focusManager
                    )
                }

//                // Members Section
//                item {
//                    MembersSection(
//                        currentInput = viewModel.currentMemberInput,
//                        onInputChange = { viewModel.currentMemberInput = it },
//                        onAddMember = { viewModel.addMember(viewModel.currentMemberInput) },
//                        focusManager = focusManager
//                    )
//                }

                // Contacts Section
                item {
                    if (hasContactPermission && (viewModel.contactsLoaded || viewModel.isLoadingContacts)) {
                        ContactsSection(
                            searchQuery = viewModel.contactSearchQuery,
                            onSearchChange = { viewModel.updateContactSearch(it) },
                            contacts = viewModel.filteredContacts,
                            addedMembers = viewModel.membersList,
                            onContactAdd = { contact ->
                                viewModel.addContactAsMember(contact, token ?: "")
                            },
                            isLoading = viewModel.isLoadingContacts,
                            contactsLoaded = viewModel.contactsLoaded
                        )
                    }
                }

                // Added Members List
                item {
                    if (viewModel.membersList.isNotEmpty()) {
                        AddedMembersSection(
                            members = viewModel.membersList,
                            onRemoveMember = { viewModel.removeMember(it) }
                        )
                    }
                }

                // Create Button
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    CreateTripButton(
                        isLoading = viewModel.isLoading,
                        isEnabled = viewModel.tripName.isNotBlank(),
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()

                            val request = CreateTripRequest(
                                trip_name = viewModel.tripName,
                                description = viewModel.description.ifBlank { null },
                                members = viewModel.membersList.map { it.name }
                            )
                            viewModel.createTrip(token, request) { inviteCode ->
                                onTripCreated(inviteCode)
                            }
                        }
                    )
                }

                // Status Message
                item {
                    viewModel.message?.let { message ->
                        StatusMessage(
                            message = message,
                            onDismiss = { viewModel.clearMessage() }
                        )
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SuccessBanner(
    text: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(
    onGrantPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Contacts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Contact Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Allow contact access to quickly add members from your contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SecurityUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant Permission",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LoadingBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TripFormSection(
    tripName: String,
    description: String,
    onTripNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    validationErrors: Map<String, String>,
    focusManager: FocusManager
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Trip Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tripName,
                onValueChange = onTripNameChange,
                label = { Text("Trip Name") },
                placeholder = { Text("Enter trip name") },
                isError = validationErrors.containsKey("tripName"),
                supportingText = {
                    validationErrors["tripName"]?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FlightTakeoff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (Optional)") },
                placeholder = { Text("Describe your trip") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                maxLines = 3,
                minLines = 1
            )
        }
    }
}

//@Composable
//private fun MembersSection(
//    currentInput: String,
//    onInputChange: (String) -> Unit,
//    onAddMember: () -> Unit,
//    focusManager: FocusManager
//) {
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surface
//        ),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//    ) {
//        Column(
//            modifier = Modifier.padding(20.dp)
//        ) {
//            Text(
//                text = "Add Members Manually",
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.SemiBold,
//                color = MaterialTheme.colorScheme.onSurface
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(12.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                OutlinedTextField(
//                    value = currentInput,
//                    onValueChange = onInputChange,
//                    label = { Text("Member Name") },
//                    placeholder = { Text("Enter member name") },
//                    leadingIcon = {
//                        Icon(
//                            imageVector = Icons.Default.PersonAdd,
//                            contentDescription = null,
//                            tint = MaterialTheme.colorScheme.primary
//                        )
//                    },
//                    modifier = Modifier.weight(1f),
//                    keyboardOptions = KeyboardOptions(
//                        capitalization = KeyboardCapitalization.Words,
//                        imeAction = ImeAction.Done
//                    ),
//                    keyboardActions = KeyboardActions(
//                        onDone = {
//                            if (currentInput.trim().isNotBlank()) {
//                                onAddMember()
//                            }
//                        }
//                    ),
//                    singleLine = true
//                )
//
//                FilledTonalButton(
//                    onClick = onAddMember,
//                    enabled = currentInput.trim().isNotBlank(),
//                    modifier = Modifier.height(56.dp)
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Add,
//                        contentDescription = null,
//                        modifier = Modifier.size(18.dp)
//                    )
//                    Spacer(modifier = Modifier.width(4.dp))
//                    Text("Add")
//                }
//            }
//        }
//    }
//}

@Composable
private fun ContactsSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    contacts: List<Contact>,
    addedMembers: List<MemberWithStatus>,
    onContactAdd: (Contact) -> Unit,
    isLoading: Boolean,
    contactsLoaded: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Add from Contacts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                label = { Text("Search Contacts") },
                placeholder = { Text("Search by name or number") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = contactsLoaded,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading contacts...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    contacts.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContactPhone,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isBlank()) "No contacts available" else "No contacts found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(contacts, key = { "${it.name}_${it.contactno}" }) { contact ->
                                ContactListItem(
                                    contact = contact,
                                    isAdded = addedMembers.any { it.name == contact.name },
                                    onAddContact = { onContactAdd(contact) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactListItem(
    contact: Contact,
    isAdded: Boolean,
    onAddContact: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded) { onAddContact() },
        colors = CardDefaults.cardColors(
            containerColor = if (isAdded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isAdded) 1.dp else 3.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAdded) Icons.Default.CheckCircle else Icons.Default.Person,
                contentDescription = null,
                tint = if (isAdded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = contact.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isAdded) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!contact.contactno.isNullOrBlank()) {
                    Text(
                        text = contact.contactno,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAdded) {
                Text(
                    text = "Added",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add contact",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddedMembersSection(
    members: List<MemberWithStatus>,
    onRemoveMember: (MemberWithStatus) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Added Members",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = members.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(members, key = { it.name }) { member ->
                    MemberChip(
                        member = member,
                        onRemove = { onRemoveMember(member) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberChip(
    member: MemberWithStatus,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (member.hasAccount) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (member.hasAccount) Icons.Default.AccountCircle else Icons.Default.Person,
                contentDescription = null,
                tint = if (member.hasAccount) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (member.hasAccount) {
                    Text(
                        text = "Auto-link",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateTripButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = isEnabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Creating Trip...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Create Trip",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.contains("Error", ignoreCase = true)) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (message.contains("Error", ignoreCase = true)) {
                    Icons.Default.Error
                } else {
                    Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = if (message.contains("Error", ignoreCase = true)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.contains("Error", ignoreCase = true)) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = if (message.contains("Error", ignoreCase = true)) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: MemberWithStatus,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (member.hasAccount) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (member.hasAccount) Icons.Default.AccountCircle else Icons.Default.Person,
                contentDescription = null,
                tint = if (member.hasAccount) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (member.hasAccount) {
                    Text(
                        text = "Has account - will be auto-linked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (member.contactno != null) {
                    Text(
                        text = "No account found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRemove
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsListDialog(
    contacts: List<Contact>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onContactSelect: (Contact) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search contacts") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onSelect = { onContactSelect(contact) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ContactItem(
    contact: Contact,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = contact.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = contact.contactno ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.Add,
                contentDescription = "Add contact",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}