package com.example.data.model

data class UserProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String = "",
    val role: String = "customer",
    val language: String = "en"
)

/** A salon facility (AC, Free WiFi...). Same exclusiveGroup = alternatives (AC / Non-AC). */
data class Amenity(
    val id: String,
    val name: String,
    val icon: String,
    val groupName: String,
    val exclusiveGroup: String? = null,
    val highlight: Boolean = false
)

/** Where the customer is looking for salons: a city, plus the GPS point when location was allowed. */
data class UserLocation(
    val city: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class Salon(
    val id: String,
    val name: String,
    val salonType: String, // "men", "women" (beauty parlour), "unisex"
    val area: String,
    val city: String,
    val address: String = "",
    val phone: String = "",
    val description: String = "",
    val photos: List<String> = emptyList(),
    val coverPhotoIndex: Int = 0,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val isVerified: Boolean = true,
    val isActive: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bookingWindowDays: Int = 14,
    val distanceKm: Double? = null,
    val amenityIds: List<String> = emptyList()
) {
    /** What customers see: a women-only place is a beauty parlour. */
    val typeLabel: String
        get() = when (salonType) {
            "men" -> "Men's Salon"
            "women" -> "Beauty Parlour"
            else -> "Unisex"
        }

    val coverPhotoUrl: String
        get() = if (photos.isNotEmpty() && coverPhotoIndex in photos.indices) {
            photos[coverPhotoIndex]
        } else if (photos.isNotEmpty()) {
            photos.first()
        } else {
            ""
        }
}

data class SalonHours(
    val id: String = "",
    val salonId: String = "",
    val dayOfWeek: Int = 0, // 0 = Sun, 1 = Mon, ..., 6 = Sat
    val dayName: String = "",
    val openTime: String = "10:00 AM",
    val closeTime: String = "08:00 PM",
    val isClosed: Boolean = false
)

data class ServiceCategory(
    val id: String,
    val salonId: String,
    val name: String,
    val sortOrder: Int = 0,
    val imageUrl: String? = null
)

data class ServiceItem(
    val id: String,
    val salonId: String,
    val categoryId: String,
    val categoryName: String = "",
    val name: String,
    val duration: Int, // duration in minutes
    val price: Double,
    val description: String = "",
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val isExpress: Boolean = false,
    /** null = regular, "bridal" or "groom" = wedding service (wedding booking rules apply). */
    val weddingType: String? = null
) {
    val weddingLabel: String?
        get() = when (weddingType) { "bridal" -> "Bridal"; "groom" -> "Groom"; else -> null }

    val formattedDuration: String
        get() = formatDuration(duration)

    companion object {
        fun formatDuration(mins: Int): String {
            return when {
                mins < 60 -> "$mins min"
                mins % 60 == 0 -> "${mins / 60} hr"
                else -> "${mins / 60} hr ${mins % 60} min"
            }
        }
    }
}

data class ComboItem(
    val id: String,
    val salonId: String,
    val name: String,
    val price: Double,
    val originalPrice: Double = 0.0,
    val description: String = "",
    val serviceNames: List<String> = emptyList(),
    val durationMinutes: Int = 0,
    val serviceIds: List<String> = emptyList(),
    val isWedding: Boolean = false
) {
    val savings: Double
        get() = (originalPrice - price).coerceAtLeast(0.0)
}

data class StaffMember(
    val id: String,
    val salonId: String,
    val name: String,
    val photoUrl: String = "",
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val title: String = "Stylist"
)

data class DayAvailability(
    val dateString: String, // e.g. "2026-09-24"
    val dayName: String,    // e.g. "Wed"
    val dayNumber: String,  // e.g. "24"
    val slotCount: Int = 0,
    val isOpen: Boolean = true // false = salon closed / stylist off that day (not "full")
)

data class TimeSlot(
    val slotStart: String,     // e.g. "2026-09-24T10:30:00"
    val displayTime: String,   // e.g. "10:30 AM"
    val period: String,        // "Morning", "Afternoon", "Evening"
    val stylistsAvailable: Int = 1
)

data class QueueStatus(
    val salonId: String,
    val stylistsFree: Int = 0,
    val waitMinutes: Int = 0,
    val isBusy: Boolean = false,
    val queueLength: Int = 0,
    val messageEn: String = "Queue status available",
    val messageHi: String = "कतार स्थिति उपलब्ध"
)

data class BookingItem(
    val id: String,
    val salonId: String,
    val salonName: String,
    val salonArea: String,
    val salonPhotoUrl: String = "",
    val serviceId: String = "",
    val serviceName: String,
    /** Every service in this booking, in order (a parlour visit can have several). */
    val serviceIds: List<String> = emptyList(),
    val staffId: String? = null,
    val stylistName: String = "Stylist",
    val price: Double,
    val date: String,
    val timeSlot: String,
    val startTimeIso: String = "",
    val status: String, // "confirmed", "arrived", "in_service", "completed", "cancelled", "no_show"
    val queuePosition: Int = 1,
    val waitMinutes: Int = 0,
    val delayMinutes: Int = 0,
    val peopleAhead: Int = 0,
    val isToday: Boolean = true,
    val notes: String = "",
    val isReviewed: Boolean = false,
    val userRating: Int = 0,
    val reviewComment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val paymentOption: String = "pay_at_salon", // "pay_at_salon", "advance", "full"
    val paymentStatus: String = "not_required", // "not_required", "pending", "paid", "refund_pending", "refunded", "forfeited", "failed"
    val amountDue: Double = 0.0,
    val amountPaid: Double = 0.0,
    val isWedding: Boolean = false
) {
    /** Services of this booking in order; older single-service bookings fall back to [serviceId]. */
    val bookedServiceIds: List<String>
        get() = serviceIds.ifEmpty { listOf(serviceId).filter { it.isNotBlank() } }
}

/** Razorpay order returned by the create-payment-order Edge Function. */
data class PaymentOrder(
    val bookingId: String,
    val orderId: String,
    val amountPaise: Int,
    val currency: String,
    val keyId: String,
    val salonName: String,
    val holdExpiresAt: String,
    val prefillName: String,
    val prefillContact: String,
    val prefillEmail: String
)

/** What a cancellation would refund right now (from get_cancellation_terms). */
data class CancellationTerms(val refundAmount: Double, val keptAmount: Double, val message: String)

data class BookingLiveStatus(
    val bookingId: String,
    val status: String,
    val waitMinutes: Int = 0,
    val delayMinutes: Int = 0,
    val peopleAhead: Int = 0,
    val scheduledStart: String = "",
    val estimatedStart: String = "",
    val message: String = ""
)

data class ReviewItem(
    val id: String,
    val bookingId: String = "",
    val salonId: String,
    val customerName: String,
    val rating: Int,
    val comment: String,
    val date: String,
    val ownerReply: String? = null,
    val ownerReplyDate: String? = null
)

data class RebookOption(
    val salonId: String,
    val salonName: String,
    val salonArea: String,
    val salonPhotoUrl: String = "",
    val stylistId: String? = null,
    val stylistName: String,
    val serviceId: String,
    val serviceName: String,
    val price: Double,
    val lastVisitDate: String
)

data class SalonCredit(
    val salonId: String,
    val salonName: String,
    val balance: Double,
    val reason: String = "Late start credit"
)

data class NotificationItem(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val type: String, // "booking_cancelled", "booking_rescheduled", "reminder", "delay_alert", "late_credit"
    val bookingId: String? = null,
    val isRead: Boolean = false,
    val createdAtIso: String = "",
    val relativeTime: String = "Just now"
)

/** Platform rules for wedding (Bridal / Groom) bookings. */
data class WeddingRules(val advancePercent: Int = 40, val freeCancelDays: Int = 15)

/** Support contact and legal links set by the platform admin. */
data class AppInfo(
    val supportPhone: String? = null,
    val supportEmail: String? = null,
    val supportWhatsapp: String? = null,
    val supportHours: String? = null,
    val termsUrl: String? = null,
    val privacyUrl: String? = null
) {
    val hasContact: Boolean get() = supportPhone != null || supportEmail != null || supportWhatsapp != null
}

/** A help / complaint request and the support team's reply. */
data class SupportTicket(
    val id: String,
    val ticketNo: Long,
    val category: String,
    val subject: String,
    val message: String,
    val status: String, // open, in_progress, resolved, closed
    val adminReply: String?,
    val createdAt: String,
    val bookingId: String?
)
