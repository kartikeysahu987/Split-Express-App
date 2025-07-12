package com.example.splitexpress.utils

import android.content.Context

object TokenManager {
    private const val PREFS_NAME = "SplitExpressPrefs"
    private const val TOKEN_KEY = "token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"
    private const val IS_LOGGED_IN_KEY = "is_logged_in"

    fun saveToken(context: Context, token: String, refreshToken: String? = null) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(TOKEN_KEY, token)
            refreshToken?.let { putString(REFRESH_TOKEN_KEY, it) }
            putBoolean(IS_LOGGED_IN_KEY, true) // Add this line
            apply()
        }
    }

    fun saveUserData(context: Context, user: com.example.splitexpress.network.User) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("first_name", user.first_name)
            putString("last_name", user.last_name)
            putString("email", user.email)
            putString("phone", user.phone)
            putString("user_type", user.user_type)
            putString("user_id", user.user_id)
            apply()
        }
    }

    fun getToken(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(TOKEN_KEY, null)
    }

    fun getRefreshToken(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(REFRESH_TOKEN_KEY, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(IS_LOGGED_IN_KEY, false) && hasValidToken(context)
    }

    fun clearTokens(context: Context) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear() // This will clear all data including user data
            apply()
        }
    }

    fun hasValidToken(context: Context): Boolean {
        return getToken(context) != null
    }

    fun getAuthHeader(context: Context): String? {
        val token = getToken(context)
        return if (token != null) "$token" else null
    }
    fun getCurrentUserName(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val firstName = sharedPref.getString("first_name", null)
        val lastName = sharedPref.getString("last_name", null)

        return if (firstName != null && lastName != null) {
            "${firstName}_${lastName}"
        } else null
    }
    fun getUserId(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString("user_id", null)
    }
}

