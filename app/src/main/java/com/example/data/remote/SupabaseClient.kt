package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseClient(context: Context) {
    private val prefs = context.getSharedPreferences("supabase_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_PROJECT_ID = "zmdjtcjbwimiiphjnvcd"
        const val DEFAULT_BASE_URL = "https://zmdjtcjbwimiiphjnvcd.supabase.co"
        const val KNOWN_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InptZGp0Y2pid2ltaWlwaGpudmNkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk4MjAzMzMsImV4cCI6MjEwNTM5NjMzM30.qeuC8iQgam09-c0UmWXnbSw1jN1E7SiUak_4xvNgRB4"
        private const val PREF_ANON_KEY = "supabase_anon_key"
        private const val PREF_ACCESS_TOKEN = "supabase_access_token"
        private const val PREF_USER_ID = "supabase_user_id"
        private const val PREF_USER_EMAIL = "supabase_user_email"
        private const val PREF_REFRESH_TOKEN = "supabase_refresh_token"
        private const val PREF_EXPIRES_AT = "supabase_expires_at"

        // Several SupabaseClient instances share the same SharedPreferences; serialize refreshes so a
        // refresh token is never spent twice.
        private val refreshLock = Any()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    var anonKey: String
        get() {
            val savedKey = prefs.getString(PREF_ANON_KEY, "") ?: ""
            if (savedKey.isNotBlank()) return savedKey
            val envKey = System.getenv("SUPABASE_ANON_KEY")?.trim() ?: ""
            if (envKey.isNotBlank()) return envKey
            val bcKey = try {
                val field = BuildConfig::class.java.getField("SUPABASE_ANON_KEY")
                (field.get(null) as? String)?.trim() ?: ""
            } catch (e: Exception) {
                ""
            }
            if (bcKey.isNotBlank() && !bcKey.equals("YOUR_SUPABASE_ANON_KEY", ignoreCase = true)) {
                return bcKey
            }
            return KNOWN_ANON_KEY
        }
        set(value) = prefs.edit().putString(PREF_ANON_KEY, value).apply()

    var accessToken: String?
        get() = prefs.getString(PREF_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(PREF_ACCESS_TOKEN, value).apply()

    var currentUserId: String?
        get() = prefs.getString(PREF_USER_ID, null)
        set(value) = prefs.edit().putString(PREF_USER_ID, value).apply()

    var currentUserEmail: String?
        get() = prefs.getString(PREF_USER_EMAIL, null)
        set(value) = prefs.edit().putString(PREF_USER_EMAIL, value).apply()

    // Sessions saved by older builds have no refresh token and cannot be renewed; treat them as signed out.
    val hasSession: Boolean
        get() = !accessToken.isNullOrBlank() && !prefs.getString(PREF_REFRESH_TOKEN, null).isNullOrBlank()

    private fun saveSession(json: JSONObject) {
        val token = json.optString("access_token")
        if (token.isEmpty()) return
        val expiresAt = json.optLong("expires_at", 0L).takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600L))
        prefs.edit()
            .putString(PREF_ACCESS_TOKEN, token)
            .putString(PREF_REFRESH_TOKEN, json.optString("refresh_token"))
            .putLong(PREF_EXPIRES_AT, expiresAt)
            .apply()
    }

    /**
     * Returns an access token that is valid for at least another minute, refreshing it with the
     * stored refresh token when needed. Returns null (and clears the session) when the session has
     * been revoked or expired, so callers fall back to anonymous access and the UI asks for login.
     */
    private fun validAccessToken(): String? {
        synchronized(refreshLock) {
            return refreshIfNeeded()
        }
    }

    private fun refreshIfNeeded(): String? {
        val token = accessToken ?: return null
        val expiresAt = prefs.getLong(PREF_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis() / 1000
        if (expiresAt == 0L || expiresAt - 60 > now) return token
        val refresh = prefs.getString(PREF_REFRESH_TOKEN, null)
        if (refresh.isNullOrBlank()) return token
        return try {
            val body = JSONObject().put("refresh_token", refresh).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$DEFAULT_BASE_URL/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey)
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                when {
                    response.isSuccessful -> {
                        saveSession(JSONObject(text))
                        accessToken
                    }
                    response.code in 400..401 -> {
                        clearSession()
                        null
                    }
                    else -> token
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseClient", "token refresh failed: ${e.message}")
            token
        }
    }

    fun clearSession() {
        prefs.edit()
            .remove(PREF_REFRESH_TOKEN)
            .remove(PREF_EXPIRES_AT)
            .remove(PREF_ACCESS_TOKEN)
            .remove(PREF_USER_ID)
            .remove(PREF_USER_EMAIL)
            .apply()
    }

    private fun buildRequest(
        url: String,
        method: String = "GET",
        jsonBody: String? = null,
        singleObject: Boolean = false
    ): Request {
        val builder = Request.Builder().url(url)
        val key = anonKey
        val token = if (url.contains("/auth/v1/token") || url.contains("/auth/v1/signup") ||
            url.contains("/auth/v1/otp") || url.contains("/auth/v1/verify")) null else validAccessToken()
        builder.addHeader("apikey", key)
        builder.addHeader("Authorization", "Bearer ${token ?: key}")
        builder.addHeader("Content-Type", "application/json")
        // PostgREST returns set-returning RPCs as arrays; ask for a single JSON object where we expect one.
        builder.addHeader("Accept", if (singleObject) "application/vnd.pgrst.object+json" else "application/json")

        if (jsonBody != null) {
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.method(method, body)
        } else if (method == "POST") {
            val body = "".toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.method("POST", body)
        } else if (method == "DELETE") {
            builder.delete()
        }
        return builder.build()
    }

    /** Sends a login OTP by SMS. [phone] is in E.164 form, e.g. +919876543210. */
    suspend fun sendPhoneOtp(phone: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("phone", phone).put("create_user", true)
            val request = buildRequest("$DEFAULT_BASE_URL/auth/v1/otp", "POST", payload.toString())
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(otpErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet connection. Please try again."))
        }
    }

    /** Verifies the SMS code and stores the session. Returns the GoTrue session JSON. */
    suspend fun verifyPhoneOtp(phone: String, code: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("type", "sms").put("phone", phone).put("token", code)
            val request = buildRequest("$DEFAULT_BASE_URL/auth/v1/verify", "POST", payload.toString())
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    saveSession(json)
                    currentUserId = json.optJSONObject("user")?.optString("id")
                    currentUserEmail = ""
                    Result.success(json)
                } else {
                    Result.failure(Exception(otpErrorMessage(response.code, body)))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet connection. Please try again."))
        }
    }

    private fun otpErrorMessage(code: Int, body: String): String {
        val lower = body.lowercase()
        return when {
            lower.contains("expired") || (lower.contains("invalid") && lower.contains("token")) ->
                "Wrong or expired OTP. Please check the code or request a new one."
            lower.contains("only request this after") || lower.contains("rate limit") || code == 429 ->
                "Please wait a minute before asking for another OTP."
            lower.contains("phone") && (lower.contains("disabled") || lower.contains("provider")) ->
                "Mobile login is not available right now. Please try again later."
            lower.contains("sms") || lower.contains("hook") ->
                "Could not send the OTP SMS. Please try again."
            else -> extractErrorMessage(code, body)
        }
    }

    suspend fun getProfile(userId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/profiles?id=eq.$userId&select=id,full_name,phone,role,language"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    Result.success(arr.getJSONObject(0))
                } else {
                    Result.failure(Exception("Profile not found"))
                }
            } else {
                Result.failure(Exception("Fetch profile failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(userId: String, fullName: String, phone: String, language: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/profiles?id=eq.$userId"
            val payload = JSONObject().apply {
                put("full_name", fullName)
                put("phone", phone)
                put("language", language)
            }
            val request = buildRequest(url, "PATCH", payload.toString())
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Update profile failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfileLanguage(userId: String, language: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/profiles?id=eq.$userId"
            val payload = JSONObject().apply { put("language", language) }
            val request = buildRequest(url, "PATCH", payload.toString())
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Update profile language failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOutRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/auth/v1/logout"
            val request = buildRequest(url, "POST", "{}")
            httpClient.newCall(request).execute()
            clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            clearSession()
            Result.success(Unit)
        }
    }

    /** Permanently deletes the signed-in account (Play Store requirement). */
    suspend fun deleteMyAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("$DEFAULT_BASE_URL/rest/v1/rpc/delete_my_account", "POST", "{}")
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                clearSession()
                Result.success(Unit)
            } else {
                Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet connection. Please try again."))
        }
    }

    // Salons REST table
    suspend fun getSalons(): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/salons?select=id,name,description,salon_type,area,city,address,phone,photos,cover_photo_index,rating_avg,rating_count,is_verified,is_active,latitude,longitude,booking_window_days&is_verified=eq.true&is_active=eq.true"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch salons failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Salons within [radiusKm] of the point (plus same-city salons without a map pin), nearest first. */
    suspend fun getNearbySalons(
        lat: Double?, lng: Double?, city: String?, amenityIds: List<String> = emptyList(), radiusKm: Double = 25.0
    ): Result<JSONArray> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("p_lat", lat ?: JSONObject.NULL)
                    .put("p_lng", lng ?: JSONObject.NULL)
                    .put("p_radius_km", radiusKm)
                    .put("p_city", city?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    .put("p_amenity_ids", if (amenityIds.isEmpty()) JSONObject.NULL else JSONArray(amenityIds))
                // search_salons = get_nearby_salons + facility filter + each salon's facility ids
                val request = buildRequest("$DEFAULT_BASE_URL/rest/v1/rpc/search_salons", "POST", payload.toString())
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "[]"
                    if (response.isSuccessful) Result.success(JSONArray(body))
                    else Result.failure(Exception(extractErrorMessage(response.code, body)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Active facilities (AC, Free WiFi...), in display order. */
    suspend fun getAmenities(): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/amenities?is_active=eq.true&select=id,name,icon,group_name,exclusive_group,highlight,sort_order&order=sort_order,name"
            httpClient.newCall(buildRequest(url)).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                if (response.isSuccessful) Result.success(JSONArray(body))
                else Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cities that have at least one live salon: [{city, salon_count}]. */
    suspend fun listCities(): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("$DEFAULT_BASE_URL/rest/v1/rpc/list_cities", "POST", "{}")
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                if (response.isSuccessful) Result.success(JSONArray(body))
                else Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSalonById(salonId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/salons?id=eq.$salonId&select=id,name,description,salon_type,area,city,address,phone,photos,cover_photo_index,rating_avg,rating_count,is_verified,is_active,latitude,longitude,booking_window_days,amenity_ids"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    Result.success(arr.getJSONObject(0))
                } else {
                    Result.failure(Exception("Salon not found"))
                }
            } else {
                Result.failure(Exception("Fetch salon failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Service Categories REST table
    suspend fun getServiceCategories(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/service_categories?salon_id=eq.$salonId&select=id,salon_id,name,sort_order,image_url&order=sort_order.asc"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch service categories failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Services REST table
    suspend fun getServices(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            // Only services at least one active stylist does (bookable:...!inner drops the rest).
            val url = "$DEFAULT_BASE_URL/rest/v1/services?salon_id=eq.$salonId&is_active=eq.true" +
                "&select=id,salon_id,category_id,name,duration_minutes,price,description,image_url,is_active,is_express," +
                "service_categories(id,name),bookable:staff_services!inner(staff!inner(id))" +
                "&bookable.staff.is_active=eq.true&order=price.asc"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch services failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Combos REST table
    suspend fun getCombos(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/combos?salon_id=eq.$salonId&is_active=eq.true&select=id,salon_id,name,price,is_active,combo_services(service_id,services(id,name,duration_minutes,price,is_active))"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch combos failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Combo Details RPC
    suspend fun getComboDetailsRpc(comboId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/get_combo_details"
            val payload = JSONObject().apply {
                put("p_combo_id", comboId)
            }
            val request = buildRequest(url, "POST", payload.toString())
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                if (body.trim().startsWith("[")) {
                    val arr = JSONArray(body)
                    Result.success(if (arr.length() > 0) arr.getJSONObject(0) else JSONObject())
                } else {
                    Result.success(JSONObject(body))
                }
            } else {
                Result.failure(Exception("Fetch combo details failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Salon Hours REST table
    suspend fun getSalonHours(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/salon_hours?salon_id=eq.$salonId&select=id,salon_id,day_of_week,open_time,close_time,is_closed&order=day_of_week.asc"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch salon hours failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Staff by Salon
    suspend fun getStaffBySalon(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/staff?salon_id=eq.$salonId&is_active=eq.true&select=id,salon_id,name,photo_url,rating_avg,rating_count,is_active"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch staff failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Live Queue RPC
    suspend fun getQueueRpc(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/get_salon_queue"
            val payload = JSONObject().apply {
                put("p_salon_id", salonId)
            }
            val request = buildRequest(url, "POST", payload.toString())
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                val array = if (body.trim().startsWith("[")) {
                    JSONArray(body)
                } else if (body.trim().startsWith("{")) {
                    JSONArray().put(JSONObject(body))
                } else {
                    JSONArray()
                }
                Result.success(array)
            } else {
                Result.failure(Exception("Fetch queue RPC failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Staff for Service RPC/Relation
    suspend fun getStaffServicesRpc(serviceId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/staff_services?select=staff_id,staff:staff(id,name,photo_url,rating_avg,rating_count,salon_id)&service_id=eq.$serviceId"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch staff_services failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POSTs to an RPC that returns rows. */
    private suspend fun rpcRows(name: String, payload: JSONObject): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("$DEFAULT_BASE_URL/rest/v1/rpc/$name", "POST", payload.toString())
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                if (response.isSuccessful) Result.success(JSONArray(body))
                else Result.failure(Exception("$name failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Log.e("SlotDebug", "$name failed", e)
            Result.failure(e)
        }
    }

    private fun idArray(ids: List<String>) = JSONArray().apply { ids.forEach { put(it) } }

    /** Stylists who can do every one of these services. */
    suspend fun getStaffForServicesRpc(serviceIds: List<String>): Result<JSONArray> =
        rpcRows("get_staff_for_services", JSONObject().put("p_service_ids", idArray(serviceIds)))

    /** Day strip for a set of services done back-to-back (null stylist = any stylist). */
    suspend fun getWeekAvailabilityMultiRpc(serviceIds: List<String>, staffId: String?): Result<JSONArray> =
        rpcRows("get_week_availability_multi", JSONObject()
            .put("p_service_ids", idArray(serviceIds))
            .put("p_staff_id", staffId ?: JSONObject.NULL))

    /** Free start times for a set of services (null stylist = any stylist who can do them all). */
    suspend fun getAvailableSlotsMultiRpc(serviceIds: List<String>, staffId: String?, date: String): Result<JSONArray> =
        if (staffId == null) {
            rpcRows("get_available_slots_any_multi", JSONObject()
                .put("p_service_ids", idArray(serviceIds)).put("p_date", date))
        } else {
            rpcRows("get_available_slots_multi", JSONObject()
                .put("p_staff_id", staffId).put("p_service_ids", idArray(serviceIds)).put("p_date", date))
        }

    /**
     * Books one or more services (or a combo package) with one stylist, paid online.
     * The server works out the total time and price; the slot is held for 15 minutes until paid.
     */
    suspend fun createMultiBookingRpc(
        staffId: String?, serviceIds: List<String>, comboId: String?, start: String, paymentOption: String, notes: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("p_staff_id", staffId ?: JSONObject.NULL)
                .put("p_service_ids", if (comboId != null) JSONObject.NULL else idArray(serviceIds))
                .put("p_combo_id", comboId ?: JSONObject.NULL)
                .put("p_start", start)
                .put("p_payment_option", paymentOption)
                .put("p_notes", notes)
            val request = buildRequest("$DEFAULT_BASE_URL/rest/v1/rpc/create_multi_booking_with_payment", "POST", payload.toString())
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                if (response.isSuccessful) Result.success(parseIdResponse(body))
                else Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet connection. Please try again."))
        }
    }

    /** Calls one of our Edge Functions as the signed-in user. Errors come back as {"error": "..."}. */
    suspend fun invokeFunction(name: String, payload: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("$DEFAULT_BASE_URL/functions/v1/$name", "POST", payload.toString())
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                if (response.isSuccessful) {
                    Result.success(json)
                } else {
                    val msg = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
                        ?: if (response.code == 401) "Please log in again." else "Server is busy. Please try again."
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet connection. Please try again."))
        }
    }

    /** What the customer gets back if they cancel now: {refund_amount, kept_amount, message}. */
    suspend fun getCancellationTermsRpc(bookingId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("p_booking_id", bookingId)
            val request = buildRequest("$DEFAULT_BASE_URL/rest/v1/rpc/get_cancellation_terms", "POST", payload.toString(), singleObject = true)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                if (response.isSuccessful) Result.success(JSONObject(body))
                else Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("No internet connection. Please try again."))
        }
    }

    // Live Booking Status RPC
    suspend fun getMyBookingStatusRpc(bookingId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/get_my_booking_status"
            val payload = JSONObject().apply {
                put("p_booking_id", bookingId)
            }
            val request = buildRequest(url, "POST", payload.toString(), singleObject = true)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                Result.success(JSONObject(body))
            } else {
                Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Reschedule Booking RPC
    suspend fun rescheduleBookingRpc(bookingId: String, newStart: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/reschedule_booking"
            val payload = JSONObject().apply {
                put("p_booking_id", bookingId)
                put("p_new_start", newStart)
            }
            val request = buildRequest(url, "POST", payload.toString())
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                Result.success(if (body.startsWith("{")) JSONObject(body) else JSONObject())
            } else {
                Result.failure(Exception(extractErrorMessage(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Cancel My Booking RPC
    suspend fun cancelMyBookingRpc(bookingId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/cancel_my_booking"
            val payload = JSONObject().apply {
                put("p_booking_id", bookingId)
            }
            val request = buildRequest(url, "POST", payload.toString())
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                Result.success(if (body.startsWith("{")) JSONObject(body) else JSONObject())
            } else {
                val err = extractErrorMessage(response.code, body)
                Result.failure(Exception(if (err.isBlank()) "Unable to cancel this booking" else err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Customer Bookings REST table
    suspend fun getCustomerBookings(customerId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/bookings?select=id,salon_id,service_id,staff_id,status,start_time,end_time,price,notes,payment_option,payment_status,amount_due,amount_paid,hold_expires_at,service_summary,booking_services(service_id,position),salons(id,name,area,city,photos,phone),staff(id,name,photo_url),services(id,name,duration_minutes,price)&customer_id=eq.$customerId&order=start_time.desc"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch bookings failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Single Booking details
    suspend fun getSingleBooking(bookingId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/bookings?id=eq.$bookingId&select=id,salon_id,service_id,staff_id,status,start_time,end_time,price,notes,payment_option,payment_status,amount_due,amount_paid,hold_expires_at,service_summary,booking_services(service_id,position),salons(id,name,area,city,photos,phone),staff(id,name,photo_url),services(id,name,duration_minutes,price)"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    Result.success(arr.getJSONObject(0))
                } else {
                    Result.failure(Exception("Booking not found"))
                }
            } else {
                Result.failure(Exception("Fetch booking failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Submit Review RPC
    suspend fun submitReviewRpc(bookingId: String, rating: Int, comment: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/submit_review"
            val payload = JSONObject().apply {
                put("p_booking_id", bookingId)
                put("p_rating", rating)
                put("p_comment", comment)
            }
            val request = buildRequest(url, "POST", payload.toString())
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (response.isSuccessful) {
                Result.success(if (body.startsWith("{")) JSONObject(body) else JSONObject())
            } else {
                val err = extractErrorMessage(response.code, body)
                Result.failure(Exception(if (err.contains("already", ignoreCase = true)) "Already reviewed" else err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Salon Reviews REST table
    suspend fun getSalonReviews(salonId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/reviews?salon_id=eq.$salonId&select=id,booking_id,salon_id,customer_name,rating,comment,created_at,owner_reply&order=created_at.desc"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch reviews failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Favorites REST table
    suspend fun getFavoriteSalons(customerId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/favorite_salons?customer_id=eq.$customerId&select=salon_id,salons(*)"
            val request = buildRequest(url)
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch favorites failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addFavoriteSalon(customerId: String, salonId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/favorite_salons"
            val payload = JSONObject().apply {
                put("customer_id", customerId)
                put("salon_id", salonId)
            }
            val request = buildRequest(url, "POST", payload.toString())
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add favorite: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFavoriteSalon(customerId: String, salonId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/favorite_salons?customer_id=eq.$customerId&salon_id=eq.$salonId"
            val request = buildRequest(url, "DELETE")
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove favorite: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Rebook Options RPC
    suspend fun getRebookOptionsRpc(): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/get_rebook_options"
            val request = buildRequest(url, "POST", "{}")
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch rebook options failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Credit Balance RPC
    suspend fun getMyCreditBalanceRpc(): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/rpc/get_my_credit_balance"
            val request = buildRequest(url, "POST", "{}")
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch credits failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Notifications REST table
    suspend fun getNotifications(userId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/notifications?user_id=eq.$userId&select=id,user_id,title,body,type,booking_id,is_read,created_at&order=created_at.desc&limit=50"
            val request = buildRequest(url, "GET")
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("Fetch notifications failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/notifications?id=eq.$notificationId"
            val payload = JSONObject().apply { put("is_read", true) }
            val request = buildRequest(url, "PATCH", payload.toString())
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Mark read failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DEFAULT_BASE_URL/rest/v1/notifications?user_id=eq.$userId&is_read=eq.false"
            val payload = JSONObject().apply { put("is_read", true) }
            val request = buildRequest(url, "PATCH", payload.toString())
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Mark all read failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** RPCs that return a bare uuid come back as a JSON string ("..."); expose it as {"id": ...}. */
    private fun parseIdResponse(body: String): JSONObject {
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("\"") -> JSONObject().put("id", trimmed.trim('"'))
            else -> JSONObject()
        }
    }

    /**
     * Turns PostgREST / GoTrue errors into messages that are safe to show to customers. Business-rule
     * messages raised by our RPCs (e.g. "Slot not available") are already user-facing and pass through.
     */
    private fun extractErrorMessage(code: Int, body: String): String {
        val raw = try {
            val json = JSONObject(body)
            listOf("message", "msg", "error_description", "error").map { json.optString(it) }.firstOrNull { it.isNotBlank() } ?: ""
        } catch (e: Exception) {
            ""
        }
        val lower = raw.lowercase()
        return when {
            lower.contains("invalid login credentials") -> "Incorrect email or password."
            lower.contains("email not confirmed") -> "Please confirm your email address, then sign in."
            lower.contains("user already registered") -> "An account with this email already exists. Please sign in."
            lower.contains("password should be") -> "Password must be at least 6 characters."
            lower.contains("jwt") || code == 401 -> "Your session has expired. Please sign in again."
            lower.contains("row-level security") || lower.contains("permission denied") || code == 403 ->
                "You are not allowed to do this."
            lower.contains("exclusion") || lower.contains("duplicate key") || code == 409 ->
                "Slot just got booked, please pick another"
            lower.contains("rate limit") || code == 429 -> "Too many attempts. Please wait a moment and try again."
            code >= 500 -> "Server is busy. Please try again."
            raw.isNotBlank() && !lower.contains("violates") && !lower.contains("syntax") && !lower.contains("column") -> raw
            else -> "Something went wrong. Please try again."
        }
    }
}
