package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SalonRepository(private val context: Context) {
    val supabaseClient = SupabaseClient(context)

    private val _favoriteIds = MutableStateFlow<Set<String>>(loadFavorites())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _bookings = MutableStateFlow<List<BookingItem>>(emptyList())
    val bookings: StateFlow<List<BookingItem>> = _bookings.asStateFlow()

    private fun loadFavorites(): Set<String> {
        val prefs = context.getSharedPreferences("salon_favs", Context.MODE_PRIVATE)
        return prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
    }

    suspend fun toggleFavorite(salonId: String, customerId: String? = null) {
        val current = _favoriteIds.value.toMutableSet()
        val willBeFavorite = !current.contains(salonId)
        if (willBeFavorite) {
            current.add(salonId)
        } else {
            current.remove(salonId)
        }
        context.getSharedPreferences("salon_favs", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("fav_ids", current)
            .apply()
        _favoriteIds.value = current

        // Remote sync with Supabase favorite_salons table
        if (!customerId.isNullOrBlank()) {
            if (willBeFavorite) {
                supabaseClient.addFavoriteSalon(customerId, salonId)
            } else {
                supabaseClient.removeFavoriteSalon(customerId, salonId)
            }
        }
    }

    fun toggleFavorite(salonId: String) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(salonId)) {
            current.remove(salonId)
        } else {
            current.add(salonId)
        }
        context.getSharedPreferences("salon_favs", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("fav_ids", current)
            .apply()
        _favoriteIds.value = current
    }

    suspend fun syncFavorites(customerId: String) {
        val res = supabaseClient.getFavoriteSalons(customerId)
        if (res.isSuccess) {
            val jsonArray = res.getOrNull()
            if (jsonArray != null) {
                val serverSet = mutableSetOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val sId = obj.optString("salon_id", "")
                    if (sId.isNotEmpty()) serverSet.add(sId)
                }
                _favoriteIds.value = serverSet
                context.getSharedPreferences("salon_favs", Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("fav_ids", serverSet)
                    .apply()
            }
        }
    }

    /** Like [getSalons] but tells a network/server failure apart from "no salons". */
    suspend fun loadSalons(): Result<List<Salon>> {
        val result = supabaseClient.getSalons()
        val arr = result.getOrNull()
            ?: return Result.failure(Exception("Couldn't load salons. Check your internet connection and try again."))
        return Result.success((0 until arr.length()).map { parseSalon(arr.getJSONObject(it)) })
    }

    /** Salons near the customer's location (or in their city), nearest first. */
    suspend fun loadNearbySalons(location: UserLocation, amenityIds: List<String> = emptyList()): Result<List<Salon>> {
        val arr = supabaseClient.getNearbySalons(location.latitude, location.longitude, location.city, amenityIds).getOrNull()
            ?: return Result.failure(Exception("Couldn't load salons. Check your internet connection and try again."))
        return Result.success((0 until arr.length()).map { parseSalon(arr.getJSONObject(it)) })
    }

    private var amenityCache: List<Amenity>? = null

    /** The facility list (cached for the app session). */
    suspend fun loadAmenities(): List<Amenity> {
        amenityCache?.let { return it }
        val arr = supabaseClient.getAmenities().getOrNull() ?: return emptyList()
        val list = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Amenity(
                id = o.optString("id"), name = o.optString("name"), icon = o.optString("icon"),
                groupName = o.optString("group_name"),
                exclusiveGroup = if (o.isNull("exclusive_group")) null else o.optString("exclusive_group"),
                highlight = o.optBoolean("highlight")
            )
        }
        amenityCache = list
        return list
    }

    /** Cities that have live salons, with how many: used for manual city selection. */
    suspend fun loadCities(): Result<List<Pair<String, Int>>> {
        val arr = supabaseClient.listCities().getOrNull()
            ?: return Result.failure(Exception("Couldn't load cities. Check your internet connection and try again."))
        return Result.success((0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            o.optString("city") to o.optInt("salon_count")
        }.filter { it.first.isNotBlank() })
    }

    suspend fun getSalons(): List<Salon> {
        val result = supabaseClient.getSalons()
        if (result.isSuccess) {
            val jsonArray = result.getOrNull()
            if (jsonArray != null && jsonArray.length() > 0) {
                val list = mutableListOf<Salon>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(parseSalon(obj))
                }
                return list
            }
        }
        return emptyList()
    }

    suspend fun getSalonById(salonId: String): Salon? {
        val res = supabaseClient.getSalonById(salonId)
        if (res.isSuccess) {
            val obj = res.getOrNull()
            if (obj != null) {
                return parseSalon(obj)
            }
        }
        val all = getSalons()
        return all.find { it.id == salonId }
    }

    suspend fun getServiceCategories(salonId: String): List<ServiceCategory> {
        val res = supabaseClient.getServiceCategories(salonId)
        if (res.isSuccess) {
            val arr = res.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<ServiceCategory>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val img = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url") else null
                    list.add(
                        ServiceCategory(
                            id = obj.optString("id"),
                            salonId = obj.optString("salon_id", salonId),
                            name = obj.optString("name", "Services"),
                            sortOrder = obj.optInt("sort_order", i),
                            imageUrl = img
                        )
                    )
                }
                return list
            }
        }
        return emptyList()
    }

    suspend fun getServices(salonId: String): List<ServiceItem> {
        val res = supabaseClient.getServices(salonId)
        if (res.isSuccess) {
            val arr = res.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<ServiceItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val catObj = obj.optJSONObject("service_categories")
                    val img = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url") else null
                    list.add(
                        ServiceItem(
                            id = obj.optString("id"),
                            salonId = obj.optString("salon_id", salonId),
                            categoryId = obj.optString("category_id", ""),
                            categoryName = catObj?.optString("name") ?: "",
                            name = obj.optString("name", "Service"),
                            duration = obj.optInt("duration_minutes", obj.optInt("duration", 30)),
                            price = obj.optDouble("price", 0.0),
                            description = obj.optString("description", ""),
                            imageUrl = img,
                            isActive = obj.optBoolean("is_active", true),
                            isExpress = obj.optBoolean("is_express", false)
                        )
                    )
                }
                return list
            }
        }
        return emptyList()
    }

    suspend fun getCombos(salonId: String): List<ComboItem> {
        val res = supabaseClient.getCombos(salonId)
        if (res.isSuccess) {
            val arr = res.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<ComboItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val comboId = obj.optString("id")
                    val serviceNamesList = mutableListOf<String>()
                    var totalDuration = 0

                    val comboServicesArr = obj.optJSONArray("combo_services")
                    if (comboServicesArr != null) {
                        for (j in 0 until comboServicesArr.length()) {
                            val csObj = comboServicesArr.getJSONObject(j)
                            val srvObj = csObj.optJSONObject("services")
                            if (srvObj != null) {
                                val sName = srvObj.optString("name")
                                if (sName.isNotEmpty()) serviceNamesList.add(sName)
                                totalDuration += srvObj.optInt("duration_minutes", 0)
                            }
                        }
                    }

                    if (serviceNamesList.isEmpty()) {
                        val serviceNamesArray = obj.optJSONArray("service_names")
                        if (serviceNamesArray != null) {
                            for (j in 0 until serviceNamesArray.length()) {
                                serviceNamesList.add(serviceNamesArray.getString(j))
                            }
                        }
                    }

                    list.add(
                        ComboItem(
                            id = comboId,
                            salonId = obj.optString("salon_id", salonId),
                            name = obj.optString("name", "Package"),
                            price = obj.optDouble("price", 0.0),
                            originalPrice = obj.optDouble("original_price", obj.optDouble("price", 0.0)),
                            description = obj.optString("description", ""),
                            serviceNames = serviceNamesList,
                            durationMinutes = totalDuration
                        )
                    )
                }
                return list
            }
        }
        return emptyList()
    }

    suspend fun getSalonHours(salonId: String): List<SalonHours> {
        val res = supabaseClient.getSalonHours(salonId)
        if (res.isSuccess) {
            val arr = res.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<SalonHours>()
                val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val dayOfWeek = obj.optInt("day_of_week", i)
                    val dayName = if (dayOfWeek in dayNames.indices) dayNames[dayOfWeek] else "Day $dayOfWeek"
                    list.add(
                        SalonHours(
                            id = obj.optString("id"),
                            salonId = obj.optString("salon_id", salonId),
                            dayOfWeek = dayOfWeek,
                            dayName = dayName,
                            openTime = formatSlotDisplay(obj.optString("open_time", "10:00:00")),
                            closeTime = formatSlotDisplay(obj.optString("close_time", "20:00:00")),
                            isClosed = obj.optBoolean("is_closed", false)
                        )
                    )
                }
                return list.sortedBy { it.dayOfWeek }
            }
        }
        return emptyList()
    }

    suspend fun getQueueStatus(salonId: String): QueueStatus {
        val rpcRes = supabaseClient.getQueueRpc(salonId)
        if (rpcRes.isSuccess) {
            val arr = rpcRes.getOrNull()
            if (arr != null && arr.length() > 0) {
                var freeStylists = 0
                var minWaitForBusy = Int.MAX_VALUE
                var totalWaiting = 0

                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    val isBusy = row.optBoolean("is_busy", false)
                    val waitMin = row.optInt("wait_minutes", 0)
                    val waitingCount = row.optInt("waiting_count", 0)
                    totalWaiting += waitingCount
                    if (!isBusy) {
                        freeStylists++
                    } else {
                        if (waitMin in 1..<minWaitForBusy) {
                            minWaitForBusy = waitMin
                        }
                    }
                }

                val allBusy = (freeStylists == 0)
                val displayWait = if (minWaitForBusy != Int.MAX_VALUE) minWaitForBusy else 15
                val msgEn = if (freeStylists > 0) {
                    "$freeStylists stylist${if (freeStylists > 1) "s" else ""} free now"
                } else {
                    "Busy — next free in ~$displayWait min"
                }
                val msgHi = if (freeStylists > 0) {
                    "$freeStylists स्टाइलिस्ट अभी उपलब्ध हैं"
                } else {
                    "व्यस्त — अगला उपलब्ध ~$displayWait मिनट में"
                }

                return QueueStatus(
                    salonId = salonId,
                    stylistsFree = freeStylists,
                    waitMinutes = if (allBusy) displayWait else 0,
                    isBusy = allBusy,
                    queueLength = totalWaiting,
                    messageEn = msgEn,
                    messageHi = msgHi
                )
            }
        }

        return QueueStatus(
            salonId = salonId,
            stylistsFree = 0,
            waitMinutes = 0,
            isBusy = false,
            queueLength = 0,
            messageEn = "Live queue available",
            messageHi = "लाइव कतार उपलब्ध है"
        )
    }

    suspend fun getSalonStaff(salonId: String): List<StaffMember> {
        val res = supabaseClient.getStaffBySalon(salonId)
        if (res.isSuccess) {
            val arr = res.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<StaffMember>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        StaffMember(
                            id = obj.optString("id"),
                            salonId = salonId,
                            name = obj.optString("name", "Stylist"),
                            photoUrl = obj.optString("photo_url", "").takeUnless { obj.isNull("photo_url") }.orEmpty(),
                            ratingAvg = obj.optDouble("rating_avg", 0.0),
                            ratingCount = obj.optInt("rating_count", 0),
                            title = obj.optString("title", "Stylist")
                        )
                    )
                }
                return list
            }
        }
        return emptyList()
    }

    suspend fun getStaffForService(serviceId: String, salonId: String): List<StaffMember> {
        val rpcRes = supabaseClient.getStaffServicesRpc(serviceId)
        if (rpcRes.isSuccess) {
            val arr = rpcRes.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<StaffMember>()
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    val staffObj = row.optJSONObject("staff")
                    if (staffObj != null) {
                        list.add(
                            StaffMember(
                                id = staffObj.optString("id", row.optString("staff_id")),
                                salonId = salonId,
                                name = staffObj.optString("name", "Stylist"),
                                photoUrl = staffObj.optString("photo_url", "").takeUnless { staffObj.isNull("photo_url") }.orEmpty(),
                                ratingAvg = staffObj.optDouble("rating_avg", 5.0),
                                ratingCount = staffObj.optInt("rating_count", 0),
                                title = staffObj.optString("title", "Stylist")
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return list
            }
        }

        // If no direct service mapping, fetch all active staff for salon
        val salonStaffRes = supabaseClient.getStaffBySalon(salonId)
        if (salonStaffRes.isSuccess) {
            val arr = salonStaffRes.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<StaffMember>()
                for (i in 0 until arr.length()) {
                    val staffObj = arr.getJSONObject(i)
                    list.add(
                        StaffMember(
                            id = staffObj.optString("id"),
                            salonId = salonId,
                            name = staffObj.optString("name", "Stylist"),
                            photoUrl = staffObj.optString("photo_url", "").takeUnless { staffObj.isNull("photo_url") }.orEmpty(),
                            ratingAvg = staffObj.optDouble("rating_avg", 5.0),
                            ratingCount = staffObj.optInt("rating_count", 0),
                            title = staffObj.optString("title", "Stylist")
                        )
                    )
                }
                return list
            }
        }

        return emptyList()
    }

    suspend fun getWeekAvailability(serviceId: String, staffId: String?, windowDays: Int = 14): List<DayAvailability> {
        val cleanStaffId = staffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        Log.d("SlotDebug", "Calling getWeekAvailability with serviceId=$serviceId staffId=$cleanStaffId")
        val rpcRes = supabaseClient.getWeekAvailabilityRpc(serviceId, cleanStaffId)
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = istZone }
        val sdfDayName = SimpleDateFormat("EEE", Locale.US).apply { timeZone = istZone }
        val sdfDayNum = SimpleDateFormat("d", Locale.US).apply { timeZone = istZone }

        val todayCal = Calendar.getInstance(istZone)
        val todayStr = sdfDate.format(todayCal.time)

        if (rpcRes.isSuccess) {
            val arr = rpcRes.getOrNull()
            Log.d("SlotDebug", "getWeekAvailability result count=${arr?.length()}")
            if (arr != null && arr.length() > 0) {
                val days = mutableListOf<DayAvailability>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    // The backend returns "day": "2026-09-24", "slot_count": 0
                    val dateKey = item.optString("day", item.optString("date", item.optString("slot_date", "")))
                    val count = item.optInt("slot_count", item.optInt("slots_count", 0))
                    if (dateKey.isNotEmpty()) {
                        val isToday = (dateKey == todayStr)
                        val dayName = if (isToday) {
                            "Today"
                        } else {
                            try {
                                val d = sdfDate.parse(dateKey)
                                if (d != null) sdfDayName.format(d) else "Day ${i + 1}"
                            } catch (e: Exception) {
                                "Day ${i + 1}"
                            }
                        }
                        val dayNum = try {
                            val d = sdfDate.parse(dateKey)
                            if (d != null) sdfDayNum.format(d) else dateKey.substringAfterLast("-")
                        } catch (e: Exception) {
                            dateKey.substringAfterLast("-")
                        }

                        days.add(
                            DayAvailability(
                                dateString = dateKey,
                                dayName = dayName,
                                dayNumber = dayNum,
                                slotCount = count,
                                isOpen = item.optBoolean("is_open", true)
                            )
                        )
                    }
                }
                if (days.isNotEmpty()) {
                    return days
                }
            }
        } else {
            Log.e("SlotDebug", "getWeekAvailability failed", rpcRes.exceptionOrNull())
        }

        // Fallback if RPC failed or returned empty: construct dates in IST
        val fallbackDays = mutableListOf<DayAvailability>()
        val cal = Calendar.getInstance(istZone)
        for (i in 0 until windowDays.coerceIn(7, 21)) {
            val dateStr = sdfDate.format(cal.time)
            val dayName = if (i == 0) "Today" else sdfDayName.format(cal.time)
            val dayNum = sdfDayNum.format(cal.time)
            fallbackDays.add(
                DayAvailability(
                    dateString = dateStr,
                    dayName = dayName,
                    dayNumber = dayNum,
                    slotCount = 0
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return fallbackDays
    }

    suspend fun getAvailableSlots(serviceId: String, staffId: String?, dateString: String): List<TimeSlot> {
        val cleanStaffId = staffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val cleanDate = dateString.trim()
        Log.d("SlotDebug", "Calling with serviceId=$serviceId date=$cleanDate staffId=$cleanStaffId")
        val rpcRes = if (cleanStaffId == null) {
            supabaseClient.getAvailableSlotsAnyRpc(serviceId, cleanDate)
        } else {
            supabaseClient.getAvailableSlotsRpc(cleanStaffId, serviceId, cleanDate)
        }
        Log.d("SlotDebug", "Raw result: isSuccess=${rpcRes.isSuccess}, count=${rpcRes.getOrNull()?.length()}")

        if (rpcRes.isSuccess) {
            val arr = rpcRes.getOrNull()
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<TimeSlot>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawStart = obj.optString("slot_start", obj.optString("start_time", ""))
                    val displayTime = formatSlotDisplay(rawStart)
                    val period = calculatePeriod(displayTime)
                    val stylistsAvailable = obj.optInt("stylists_available", 1)
                    list.add(
                        TimeSlot(
                            slotStart = rawStart,
                            displayTime = displayTime,
                            period = period,
                            stylistsAvailable = stylistsAvailable
                        )
                    )
                }
                return list
            }
        } else {
            Log.e("SlotDebug", "getAvailableSlots failed", rpcRes.exceptionOrNull())
        }

        return emptyList()
    }

    suspend fun createBooking(
        salonId: String,
        salonName: String,
        salonArea: String,
        service: ServiceItem,
        staffId: String?,
        stylistName: String,
        dateFormatted: String,
        timeSlot: TimeSlot,
        notes: String,
        paymentOption: String = "advance"
    ): Result<BookingItem> {
        val cleanStaffId = staffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val startIso = timeSlot.slotStart
        val rpcRes = supabaseClient.createBookingWithPaymentRpc(cleanStaffId, service.id, startIso, paymentOption, notes)

        if (rpcRes.isFailure) {
            val ex = rpcRes.exceptionOrNull()
            val msg = ex?.message ?: ""
            if (msg.contains("booked", ignoreCase = true) || msg.contains("conflict", ignoreCase = true) || msg.contains("409")) {
                return Result.failure(Exception("Slot just got booked, please pick another"))
            }
            return Result.failure(ex ?: Exception("Failed to book slot"))
        }

        val resJson = rpcRes.getOrNull()
        val bookingId = resJson?.optString("id", resJson.optString("booking_id", "bk_${System.currentTimeMillis()}"))
            ?: "bk_${System.currentTimeMillis()}"

        val newBooking = BookingItem(
            id = bookingId,
            salonId = salonId,
            salonName = salonName,
            salonArea = salonArea,
            serviceId = service.id,
            serviceName = service.name,
            staffId = staffId,
            stylistName = if (staffId == null) "Fastest Available Stylist" else stylistName,
            price = service.price,
            date = dateFormatted,
            timeSlot = timeSlot.displayTime,
            startTimeIso = startIso,
            status = "pending_payment",
            queuePosition = 1,
            waitMinutes = 0,
            notes = notes,
            paymentOption = paymentOption,
            paymentStatus = "pending"
        )

        addBooking(newBooking)
        return Result.success(newBooking)
    }

    /** Creates (or reuses) the Razorpay order for a booking that is holding its slot for payment. */
    suspend fun createPaymentOrder(bookingId: String): Result<PaymentOrder> {
        val res = supabaseClient.invokeFunction("create-payment-order", JSONObject().put("booking_id", bookingId))
        val o = res.getOrElse { return Result.failure(it) }
        val prefill = o.optJSONObject("prefill")
        return Result.success(
            PaymentOrder(
                bookingId = o.optString("booking_id", bookingId),
                orderId = o.optString("order_id"),
                amountPaise = o.optInt("amount_paise"),
                currency = o.optString("currency", "INR"),
                keyId = o.optString("key_id"),
                salonName = o.optString("salon_name"),
                holdExpiresAt = o.optString("hold_expires_at"),
                prefillName = prefill?.optString("name").orEmpty(),
                prefillContact = prefill?.optString("contact").orEmpty(),
                prefillEmail = prefill?.optString("email").orEmpty()
            )
        )
    }

    /**
     * Sends Razorpay Checkout's result to the server, which checks the signature and the payment
     * with Razorpay before confirming the booking. Returns the booking status ("confirmed" normally).
     */
    suspend fun verifyPayment(orderId: String, paymentId: String, signature: String): Result<String> {
        val res = supabaseClient.invokeFunction(
            "verify-payment",
            JSONObject().put("razorpay_order_id", orderId).put("razorpay_payment_id", paymentId).put("razorpay_signature", signature)
        )
        return res.map { it.optString("booking_status", "pending_payment") }
    }

    /** Refund preview shown before the customer confirms a cancellation. */
    suspend fun getCancellationTerms(bookingId: String): Result<CancellationTerms> =
        supabaseClient.getCancellationTermsRpc(bookingId).map {
            CancellationTerms(it.optDouble("refund_amount", 0.0), it.optDouble("kept_amount", 0.0), it.optString("message"))
        }

    /** Asks the server to send a refund right away (the 5-minute job would do it anyway). */
    suspend fun requestRefundNow(bookingId: String) {
        supabaseClient.invokeFunction("process-refunds", JSONObject().put("booking_id", bookingId))
    }

    suspend fun getMyBookingStatus(bookingId: String): BookingLiveStatus {
        val rpcRes = supabaseClient.getMyBookingStatusRpc(bookingId)
        if (rpcRes.isSuccess) {
            val json = rpcRes.getOrNull()
            if (json != null) {
                val status = json.optString("status", "confirmed")
                val waitMins = json.optInt("wait_minutes", 0)
                val delayMins = json.optInt("delay_minutes", 0)
                val peopleAhead = json.optInt("people_ahead", 0)
                val scheduled = json.optString("scheduled_start", "")
                val estimated = json.optString("expected_start", scheduled)
                val msg = if (delayMins > 0) {
                    "Delayed by ~$delayMins min — estimated start at $estimated"
                } else if (scheduled.isNotEmpty()) {
                    "On time — arrive by $scheduled"
                } else {
                    "Booking active"
                }
                return BookingLiveStatus(
                    bookingId = bookingId,
                    status = status,
                    waitMinutes = waitMins,
                    delayMinutes = delayMins,
                    peopleAhead = peopleAhead,
                    scheduledStart = scheduled,
                    estimatedStart = estimated,
                    message = msg
                )
            }
        }

        val booking = _bookings.value.find { it.id == bookingId }
        val scheduled = booking?.timeSlot ?: ""
        return BookingLiveStatus(
            bookingId = bookingId,
            status = booking?.status ?: "confirmed",
            waitMinutes = booking?.waitMinutes ?: 0,
            delayMinutes = booking?.delayMinutes ?: 0,
            peopleAhead = booking?.peopleAhead ?: 0,
            scheduledStart = scheduled,
            estimatedStart = scheduled,
            message = if (scheduled.isNotEmpty()) "On time — arrive by $scheduled" else "Confirmed"
        )
    }

    suspend fun getSingleBooking(bookingId: String): BookingItem? {
        val found = _bookings.value.find { it.id == bookingId }
        if (found != null) return found
        val res = supabaseClient.getSingleBooking(bookingId)
        if (res.isSuccess) {
            val obj = res.getOrNull()
            if (obj != null) {
                val parsed = parseBooking(obj)
                addBooking(parsed)
                return parsed
            }
        }
        return null
    }

    suspend fun syncCustomerBookings(customerId: String) {
        val res = supabaseClient.getCustomerBookings(customerId)
        if (res.isSuccess) {
            val arr = res.getOrNull()
            if (arr != null) {
                val list = mutableListOf<BookingItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(parseBooking(obj))
                }
                _bookings.value = list
            }
        }
    }

    suspend fun rescheduleBooking(
        bookingId: String,
        newDateFormatted: String,
        newSlot: TimeSlot
    ): Result<Unit> {
        val rpcRes = supabaseClient.rescheduleBookingRpc(bookingId, newSlot.slotStart)
        if (rpcRes.isFailure) {
            val err = rpcRes.exceptionOrNull()?.message ?: ""
            if (err.contains("booked", ignoreCase = true) || err.contains("409")) {
                return Result.failure(Exception("That slot was just booked, please choose another"))
            }
            return Result.failure(Exception(err.ifBlank { "Could not reschedule booking" }))
        }

        // Update local booking
        val list = _bookings.value.map { item ->
            if (item.id == bookingId) {
                item.copy(
                    date = newDateFormatted,
                    timeSlot = newSlot.displayTime,
                    startTimeIso = newSlot.slotStart,
                    status = "confirmed"
                )
            } else item
        }
        _bookings.value = list
        return Result.success(Unit)
    }

    suspend fun cancelBooking(bookingId: String): Result<Unit> {
        val rpcRes = supabaseClient.cancelMyBookingRpc(bookingId)
        if (rpcRes.isFailure) {
            val msg = rpcRes.exceptionOrNull()?.message ?: ""
            return Result.failure(Exception(msg.ifBlank { "Cannot cancel this booking" }))
        }

        val list = _bookings.value.map { item ->
            if (item.id == bookingId) {
                item.copy(status = "cancelled")
            } else item
        }
        _bookings.value = list
        requestRefundNow(bookingId) // no-op unless the cancel made a refund due
        return Result.success(Unit)
    }

    suspend fun submitReview(bookingId: String, rating: Int, comment: String): Result<Unit> {
        val rpcRes = supabaseClient.submitReviewRpc(bookingId, rating, comment)
        if (rpcRes.isFailure) {
            val msg = rpcRes.exceptionOrNull()?.message ?: ""
            return Result.failure(Exception(msg.ifBlank { "Failed to submit review" }))
        }

        val list = _bookings.value.map { item ->
            if (item.id == bookingId) {
                item.copy(
                    isReviewed = true,
                    userRating = rating,
                    reviewComment = comment
                )
            } else item
        }
        _bookings.value = list
        return Result.success(Unit)
    }

    fun addReview(bookingId: String, rating: Int, comment: String) {
        val list = _bookings.value.map { item ->
            if (item.id == bookingId) {
                item.copy(
                    isReviewed = true,
                    userRating = rating,
                    reviewComment = comment
                )
            } else item
        }
        _bookings.value = list
    }

    suspend fun getSalonReviews(salonId: String): List<ReviewItem> {
        val res = supabaseClient.getSalonReviews(salonId)
        if (res.isSuccess) {
            val jsonArray = res.getOrNull()
            if (jsonArray != null && jsonArray.length() > 0) {
                val list = mutableListOf<ReviewItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ReviewItem(
                            id = obj.optString("id", "rev_$i"),
                            bookingId = obj.optString("booking_id", ""),
                            salonId = obj.optString("salon_id", salonId),
                            customerName = obj.optString("customer_name", "Customer"),
                            rating = obj.optInt("rating", 5),
                            comment = obj.optString("comment", ""),
                            date = obj.optString("created_at", "").take(10),
                            ownerReply = if (obj.has("owner_reply") && !obj.isNull("owner_reply")) obj.optString("owner_reply") else null,
                            ownerReplyDate = if (obj.has("owner_reply_date") && !obj.isNull("owner_reply_date")) obj.optString("owner_reply_date").take(10) else null
                        )
                    )
                }
                return list
            }
        }
        return emptyList()
    }

    suspend fun getFavoriteSalons(customerId: String? = null): List<Salon> {
        if (!customerId.isNullOrBlank()) {
            syncFavorites(customerId)
        }
        val favIds = _favoriteIds.value
        val allSalons = getSalons()
        return allSalons.filter { favIds.contains(it.id) }
    }

    suspend fun getRebookOptions(): List<RebookOption> {
        val res = supabaseClient.getRebookOptionsRpc()
        if (res.isSuccess) {
            val jsonArray = res.getOrNull()
            if (jsonArray != null && jsonArray.length() > 0) {
                val list = mutableListOf<RebookOption>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        RebookOption(
                            salonId = obj.optString("salon_id", ""),
                            salonName = obj.optString("salon_name", "Salon"),
                            salonArea = obj.optString("salon_area", ""),
                            salonPhotoUrl = obj.optString("salon_photo_url", ""),
                            stylistId = if (obj.has("stylist_id") && !obj.isNull("stylist_id")) obj.optString("stylist_id") else null,
                            stylistName = obj.optString("stylist_name", "Stylist"),
                            serviceId = obj.optString("service_id", ""),
                            serviceName = obj.optString("service_name", "Service"),
                            price = obj.optDouble("price", 0.0),
                            lastVisitDate = obj.optString("last_visit_date", "Recently")
                        )
                    )
                }
                return list
            }
        }

        // Derive rebook options from completed live bookings in user history
        val completed = _bookings.value.filter { it.status.lowercase() == "completed" }
        val bySalon = completed.groupBy { it.salonId }
        val options = mutableListOf<RebookOption>()
        for ((_, bList) in bySalon) {
            val last = bList.firstOrNull() ?: continue
            options.add(
                RebookOption(
                    salonId = last.salonId,
                    salonName = last.salonName,
                    salonArea = last.salonArea,
                    salonPhotoUrl = last.salonPhotoUrl,
                    stylistId = last.staffId,
                    stylistName = last.stylistName,
                    serviceId = last.serviceId,
                    serviceName = last.serviceName,
                    price = last.price,
                    lastVisitDate = "${last.date} at ${last.timeSlot}"
                )
            )
        }

        return options
    }

    suspend fun getMyCreditBalance(): List<SalonCredit> {
        val res = supabaseClient.getMyCreditBalanceRpc()
        if (res.isSuccess) {
            val jsonArray = res.getOrNull()
            if (jsonArray != null && jsonArray.length() > 0) {
                val list = mutableListOf<SalonCredit>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val bal = obj.optDouble("balance", 0.0)
                    if (bal > 0.0) {
                        list.add(
                            SalonCredit(
                                salonId = obj.optString("salon_id", ""),
                                salonName = obj.optString("salon_name", "Salon"),
                                balance = bal,
                                reason = obj.optString("reason", "Late start credit")
                            )
                        )
                    }
                }
                return list
            }
        }
        return emptyList()
    }

    fun addBooking(booking: BookingItem) {
        val list = _bookings.value.toMutableList()
        list.add(0, booking)
        _bookings.value = list
    }

    internal fun formatSlotDisplay(raw: String): String {
        return try {
            if (raw.contains("T")) {
                // ISO timestamp like "2026-09-25T04:30:00+00:00" or "2026-09-25T04:30:00Z"
                val istZone = TimeZone.getTimeZone("Asia/Kolkata")
                val clean = if (raw.contains(".")) {
                    val preDot = raw.substringBefore(".")
                    val postDot = raw.substringAfter(".")
                    val tz = if (postDot.contains("+")) "+" + postDot.substringAfter("+")
                             else if (postDot.contains("-")) "-" + postDot.substringAfter("-")
                             else if (postDot.endsWith("Z")) "Z"
                             else "+00:00"
                    "$preDot$tz"
                } else raw

                val isoClean = if (clean.endsWith("Z")) clean.replace("Z", "+00:00") else clean
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date = inputFormat.parse(isoClean)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("hh:mm a", Locale.US).apply {
                        timeZone = istZone
                    }
                    outputFormat.format(date)
                } else {
                    raw
                }
            } else if (raw.contains("AM", ignoreCase = true) || raw.contains("PM", ignoreCase = true)) {
                raw
            } else if (raw.contains(":")) {
                val parts = raw.split(":")
                val hour = parts[0].toInt()
                val min = parts[1]
                val ampm = if (hour >= 12) "PM" else "AM"
                val h12 = if (hour % 12 == 0) 12 else hour % 12
                String.format(Locale.US, "%02d:%s %s", h12, min, ampm)
            } else {
                raw
            }
        } catch (e: Exception) {
            // Fallback for parsing ISO timestamp in case of unexpected format: UTC to IST (+5h 30m)
            try {
                if (raw.contains("T")) {
                    val timePart = raw.substringAfter("T").substringBefore("+").substringBefore("Z")
                    val parts = timePart.split(":")
                    val utcHour = parts[0].toInt()
                    val utcMin = parts[1].toInt()
                    val totalMins = (utcHour * 60 + utcMin + 330) % 1440
                    val istHour = totalMins / 60
                    val istMin = totalMins % 60
                    val ampm = if (istHour >= 12) "PM" else "AM"
                    val h12 = if (istHour % 12 == 0) 12 else istHour % 12
                    String.format(Locale.US, "%02d:%02d %s", h12, istMin, ampm)
                } else {
                    raw
                }
            } catch (_: Exception) {
                raw
            }
        }
    }

    internal fun calculatePeriod(displayTime: String): String {
        return try {
            val clean = displayTime.trim()
            val isPm = clean.contains("PM", ignoreCase = true)
            val timePart = clean.substringBefore(" ")
            val parts = timePart.split(":")
            var hour = parts[0].toInt()
            if (isPm && hour < 12) hour += 12
            if (!isPm && hour == 12) hour = 0
            when {
                hour < 12 -> "Morning"
                hour < 17 -> "Afternoon"
                else -> "Evening"
            }
        } catch (e: Exception) {
            when {
                displayTime.contains("AM", ignoreCase = true) -> "Morning"
                displayTime.startsWith("12:") || displayTime.startsWith("01:") || displayTime.startsWith("02:") || displayTime.startsWith("03:") || displayTime.startsWith("04:") -> "Afternoon"
                else -> "Evening"
            }
        }
    }

    private fun parseSalon(json: JSONObject): Salon {
        val photosArray = json.optJSONArray("photos")
        val photosList = mutableListOf<String>()
        if (photosArray != null) {
            for (i in 0 until photosArray.length()) {
                photosList.add(photosArray.getString(i))
            }
        }
        val lat = if (json.has("latitude") && !json.isNull("latitude")) json.optDouble("latitude") else null
        val lng = if (json.has("longitude") && !json.isNull("longitude")) json.optDouble("longitude") else null
        return Salon(
            id = json.optString("id"),
            name = json.optString("name", "Salon"),
            salonType = json.optString("salon_type", "Unisex"),
            area = json.optString("area", ""),
            city = json.optString("city", ""),
            address = json.optString("address", ""),
            phone = json.optString("phone", ""),
            description = json.optString("description", ""),
            photos = photosList,
            coverPhotoIndex = json.optInt("cover_photo_index", 0),
            ratingAvg = json.optDouble("rating_avg", 0.0),
            ratingCount = json.optInt("rating_count", 0),
            isVerified = json.optBoolean("is_verified", true),
            isActive = json.optBoolean("is_active", true),
            latitude = lat,
            longitude = lng,
            bookingWindowDays = json.optInt("booking_window_days", 14),
            distanceKm = if (json.has("distance_km") && !json.isNull("distance_km")) json.optDouble("distance_km") else null,
            amenityIds = json.optJSONArray("amenity_ids")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        )
    }

    private fun parseBooking(json: JSONObject): BookingItem {
        val salonObj = json.optJSONObject("salons")
        val staffObj = json.optJSONObject("staff")
        val serviceObj = json.optJSONObject("services")

        val startTime = json.optString("start_time", "")
        val displayTime = formatSlotDisplay(startTime)
        val dateDisplay = if (startTime.length >= 10) startTime.substring(0, 10) else "Upcoming"

        val photosArr = salonObj?.optJSONArray("photos")
        val salonPhoto = if (photosArr != null && photosArr.length() > 0) photosArr.getString(0) else ""

        val sId = json.optString("salon_id", salonObj?.optString("id") ?: "")
        val sName = salonObj?.optString("name") ?: json.optString("salon_name", "Salon")
        val sArea = salonObj?.optString("area") ?: json.optString("salon_area", "")
        val srvId = json.optString("service_id", serviceObj?.optString("id") ?: "")
        val srvName = serviceObj?.optString("name") ?: json.optString("service_name", "Service")
        val stName = staffObj?.optString("name") ?: json.optString("stylist_name", "Stylist")
        val defaultPrice = serviceObj?.optDouble("price", 0.0) ?: 0.0
        val price = json.optDouble("price", defaultPrice)

        return BookingItem(
            id = json.optString("id"),
            salonId = sId,
            salonName = sName,
            salonArea = sArea,
            salonPhotoUrl = salonPhoto,
            serviceId = srvId,
            serviceName = srvName,
            staffId = if (json.has("staff_id") && !json.isNull("staff_id")) json.optString("staff_id") else null,
            stylistName = stName,
            price = price,
            date = dateDisplay,
            timeSlot = displayTime,
            startTimeIso = startTime,
            status = json.optString("status", "confirmed"),
            queuePosition = 1,
            waitMinutes = 0,
            delayMinutes = 0,
            peopleAhead = 0,
            notes = json.optString("notes", ""),
            paymentOption = json.optString("payment_option", "pay_at_salon"),
            paymentStatus = json.optString("payment_status", "not_required"),
            amountDue = json.optDouble("amount_due", 0.0),
            amountPaid = json.optDouble("amount_paid", 0.0)
        )
    }
}
