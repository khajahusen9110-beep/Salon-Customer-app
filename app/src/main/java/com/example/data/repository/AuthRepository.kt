package com.example.data.repository

import android.content.Context
import com.example.data.model.UserLocation
import com.example.data.model.UserProfile
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(private val context: Context) {
    val supabaseClient = SupabaseClient(context)
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUser = MutableStateFlow<UserProfile?>(loadSavedProfile())
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _currentLanguage = MutableStateFlow(prefs.getString("app_language", "en") ?: "en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _location = MutableStateFlow(loadSavedLocation())
    /** Null until the customer has chosen a city (by GPS or by hand). */
    val location: StateFlow<UserLocation?> = _location.asStateFlow()

    private fun loadSavedProfile(): UserProfile? {
        // A cached profile without a live Supabase session is not a logged-in user.
        if (!supabaseClient.hasSession) return null
        val id = prefs.getString("user_id", null) ?: return null
        val name = prefs.getString("user_name", "") ?: ""
        val phone = prefs.getString("user_phone", "") ?: ""
        val email = prefs.getString("user_email", "") ?: ""
        val lang = prefs.getString("app_language", "en") ?: "en"
        return UserProfile(id = id, email = email, fullName = name, phone = phone, role = "customer", language = lang)
    }

    private fun loadSavedLocation(): UserLocation? {
        val city = prefs.getString("loc_city", null) ?: return null
        val lat = prefs.getString("loc_lat", null)?.toDoubleOrNull()
        val lng = prefs.getString("loc_lng", null)?.toDoubleOrNull()
        return UserLocation(city, lat, lng)
    }

    fun setLocation(location: UserLocation) {
        prefs.edit()
            .putString("loc_city", location.city)
            .putString("loc_lat", location.latitude?.toString())
            .putString("loc_lng", location.longitude?.toString())
            .apply()
        _location.value = location
    }

    /** Step 1 of login: text an OTP to +91 [mobile10]. */
    suspend fun sendOtp(mobile10: String): Result<Unit> = supabaseClient.sendPhoneOtp("+91$mobile10")

    /**
     * Step 2: check the OTP. A brand-new number gets its account created by Supabase here; the
     * profile row (with the phone) is created by the database trigger. Returns the profile, whose
     * name is blank for a new customer.
     */
    suspend fun verifyOtp(mobile10: String, code: String): Result<UserProfile> {
        val res = supabaseClient.verifyPhoneOtp("+91$mobile10", code)
        val session = res.getOrElse { return Result.failure(it) }
        val user = session.optJSONObject("user")
        val id = user?.optString("id").orEmpty().ifBlank { supabaseClient.currentUserId.orEmpty() }
        if (id.isBlank()) return Result.failure(Exception("Login failed. Please try again."))

        var name = ""
        var lang = _currentLanguage.value
        supabaseClient.getProfile(id).getOrNull()?.let { p ->
            name = p.optString("full_name", "").takeIf { it != "null" }.orEmpty()
            p.optString("language", "").takeIf { it.isNotBlank() && it != "null" }?.let {
                lang = it
                _currentLanguage.value = it
            }
        }
        val profile = UserProfile(
            id = id,
            email = user?.optString("email").orEmpty().takeIf { it != "null" }.orEmpty(),
            fullName = name.trim(),
            phone = mobile10,
            role = "customer",
            language = lang
        )
        saveProfile(profile)
        _currentUser.value = profile
        return Result.success(profile)
    }

    /** Saves the customer's name on the server (finishes account creation for a new number). */
    suspend fun saveName(fullName: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("Please log in again."))
        val name = fullName.trim()
        val res = supabaseClient.updateProfile(current.id, name, current.phone, current.language)
        if (res.isSuccess) {
            val updated = current.copy(fullName = name)
            saveProfile(updated)
            _currentUser.value = updated
        }
        return res.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(Exception("Couldn't save your name. Check your internet and try again.")) }
        )
    }

    fun updateProfile(fullName: String, language: String, phone: String = "") {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            fullName = fullName.ifBlank { current.fullName },
            language = language,
            phone = phone.ifBlank { current.phone }
        )
        saveProfile(updated)
        _currentUser.value = updated
        setLanguage(language)
        coroutineScope.launch {
            supabaseClient.updateProfile(updated.id, updated.fullName, updated.phone, updated.language)
        }
    }

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        prefs.edit().putString("app_language", lang).apply()
        val user = _currentUser.value
        if (user != null) {
            _currentUser.value = user.copy(language = lang)
            coroutineScope.launch {
                supabaseClient.updateProfileLanguage(user.id, lang)
            }
        }
    }

    /** Deletes the account on the server, then clears everything stored on this device. */
    suspend fun deleteAccount(): Result<Unit> {
        val res = supabaseClient.deleteMyAccount()
        if (res.isSuccess) {
            prefs.edit().clear().apply()
            context.getSharedPreferences("salon_favs", Context.MODE_PRIVATE).edit().clear().apply()
            _currentUser.value = null
            _location.value = null
        }
        return res
    }

    suspend fun signOut() {
        supabaseClient.signOutRemote()
        prefs.edit().clear().apply()
        _location.value?.let { setLocation(it) } // the chosen city is a device setting, keep it
        _currentUser.value = null
    }

    private fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString("user_id", profile.id)
            .putString("user_email", profile.email)
            .putString("user_name", profile.fullName)
            .putString("user_phone", profile.phone)
            .putString("app_language", profile.language)
            .apply()
    }
}
