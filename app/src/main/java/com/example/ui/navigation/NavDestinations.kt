package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Discover : Screen("discover")
    object Bookings : Screen("bookings")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")

    object Login : Screen("login")
    object Signup : Screen("signup")
    object Onboarding : Screen("onboarding")

    object SalonDetail : Screen("salon/{salonId}") {
        fun createRoute(salonId: String) = "salon/$salonId"
    }

    object BookFlow : Screen("book/{salonId}?serviceId={serviceId}&comboId={comboId}&staffId={staffId}") {
        fun createRoute(salonId: String, serviceId: String? = null, comboId: String? = null, staffId: String? = null): String {
            val s = serviceId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val c = comboId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val st = staffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val params = mutableListOf<String>()
            if (s != null) params.add("serviceId=$s")
            if (c != null) params.add("comboId=$c")
            if (st != null) params.add("staffId=$st")
            return if (params.isNotEmpty()) "book/$salonId?${params.joinToString("&")}" else "book/$salonId"
        }
    }

    object BookingTracker : Screen("booking/{bookingId}") {
        fun createRoute(bookingId: String) = "booking/$bookingId"
    }
}
