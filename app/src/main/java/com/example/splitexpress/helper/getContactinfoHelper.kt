package com.example.splitexpress.helper

import com.example.splitexpress.network.Contact
import com.example.splitexpress.network.ContactInfoResponse
import com.example.splitexpress.network.GetContactRequest
import com.example.splitexpress.network.RetrofitInstance

suspend fun getContactInfo(
    token: String,
    contacts: List<Contact>
): Result<ContactInfoResponse> {
    return try {
        val request = GetContactRequest(contacts = contacts)
        val response = RetrofitInstance.api.getContactInfo(token, request)

        if (response.isSuccessful) {
            response.body()?.let { contactInfo ->
                Result.success(contactInfo)
            } ?: Result.failure(Exception("Empty response body"))
        } else {
            Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}