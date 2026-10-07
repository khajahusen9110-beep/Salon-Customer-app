package com.example.data.repository

import android.content.Context
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

    private fun loadSavedProfile(): UserProfile? {
        val email = prefs.getString("user_email", null) ?: return null
        val id = prefs.getString("user_id", null) ?: return null
        val name = prefs.getString("user_name", "") ?: ""
        val phone = prefs.getString("user_phone", "") ?: ""
        val lang = prefs.getString("app_language", "en") ?: "en"
        return UserProfile(id = id, email = email, fullName = name, phone = phone, role = "customer", language = lang)
    }

    suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        val res = supabaseClient.signIn(email, pass)
        if (res.isSuccess) {
            val userObj = res.getOrNull()?.optJSONObject("user")
            val id = userObj?.optString("id") ?: supabaseClient.currentUserId ?: ""
            var name = userObj?.optJSONObject("user_metadata")?.optString("full_name") ?: ""
            var phone = ""
            var lang = _currentLanguage.value

            // Try to fetch detailed profile from profiles table
            if (id.isNotEmpty()) {
                val profileRes = supabaseClient.getProfile(id)
                if (profileRes.isSuccess) {
                    val pJson = profileRes.getOrNull()
                    if (pJson != null) {
                        val pName = pJson.optString("full_name", "")
                        if (pName.isNotBlank()) name = pName
                        phone = pJson.optString("phone", "")
                        val pLang = pJson.optString("language", "")
                        if (pLang.isNotBlank()) {
                            lang = pLang
                            _currentLanguage.value = lang
                        }
                    }
                }
            }

            if (name.isBlank()) {
                name = email.substringBefore("@")
            }

            val profile = UserProfile(
                id = id,
                email = email,
                fullName = name,
                phone = phone,
                role = "customer",
                language = lang
            )
            saveProfile(profile)
            _currentUser.value = profile
            return Result.success(profile)
        } else {
            return Result.failure(res.exceptionOrNull() ?: Exception("Sign in failed"))
        }
    }

    suspend fun signUp(email: String, pass: String, fullName: String): Result<UserProfile> {
        val res = supabaseClient.signUp(email, pass, fullName)
        if (res.isSuccess) {
            val userObj = res.getOrNull()?.optJSONObject("user")
            val id = userObj?.optString("id") ?: supabaseClient.currentUserId ?: ""
            val profile = UserProfile(
                id = id,
                email = email,
                fullName = fullName,
                phone = "",
                role = "customer",
                language = _currentLanguage.value
            )
            saveProfile(profile)
            _currentUser.value = profile
            return Result.success(profile)
        } else {
            return Result.failure(res.exceptionOrNull() ?: Exception("Sign up failed"))
        }
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

    suspend fun signOut() {
        supabaseClient.signOutRemote()
        prefs.edit().clear().apply()
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
