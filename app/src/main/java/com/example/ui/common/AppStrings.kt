package com.example.ui.common

object AppStrings {
    fun get(key: String, lang: String): String {
        val isHi = lang == "hi"
        return when (key) {
            "tab_discover" -> if (isHi) "खोजें" else "Discover"
            "tab_bookings" -> if (isHi) "बुकिंग्स" else "Bookings"
            "tab_favorites" -> if (isHi) "पसंदीदा" else "Favorites"
            "tab_profile" -> if (isHi) "प्रोफ़ाइल" else "Profile"

            "hero_tagline" -> if (isHi) "इंतज़ार छोड़ें। तुरंत ग्रूमिंग।" else "Skip the wait. Zero friction grooming."
            "search_placeholder" -> if (isHi) "सैलून का नाम या इलाका खोजें..." else "Search salon name or area..."
            "filter_all" -> if (isHi) "सभी" else "All"
            "filter_men" -> if (isHi) "पुरुष" else "Men"
            "filter_women" -> if (isHi) "महिलाएँ" else "Women"
            "filter_unisex" -> if (isHi) "यूनिसेक्स" else "Unisex"
            "sort_rating" -> if (isHi) "शीर्ष रेटेड" else "Highest Rated"
            "sort_nearest" -> if (isHi) "निकटतम" else "Nearest"

            "call_salon" -> if (isHi) "कॉल करें" else "Call Salon"
            "book_now" -> if (isHi) "बुक करें" else "Book"
            "tab_services" -> if (isHi) "सेवाएं" else "Services"
            "tab_packages" -> if (isHi) "पैकेजेस / कॉम्बो" else "Packages"
            "tab_about" -> if (isHi) "जानकारी एवं समय" else "About & Hours"
            "tab_reviews" -> if (isHi) "समीक्षाएँ" else "Reviews"

            "queue_free_now" -> if (isHi) "स्टाइलिस्ट अभी खाली हैं" else "stylists free now"
            "queue_next_in" -> if (isHi) "अगला उपलब्ध" else "Next available in"
            "queue_teaser_label" -> if (isHi) "लाइव उपलब्धता" else "Live Availability"

            "working_hours" -> if (isHi) "कार्य समय" else "Working Hours"
            "address" -> if (isHi) "पता" else "Address"
            "verified" -> if (isHi) "सत्यापित सैलून" else "Verified Salon"

            "login_title" -> if (isHi) "स्वागत है" else "Welcome back"
            "login_subtitle" -> if (isHi) "प्रीमियम सैलून बुकिंग के लिए लॉगिन करें" else "Sign in to book & track your queue live"
            "signup_title" -> if (isHi) "खाता बनाएं" else "Create Account"
            "signup_subtitle" -> if (isHi) "समय बचाने और शून्य इंतज़ार का अनुभव लें" else "Zero-wait appointments for busy professionals"
            "email" -> if (isHi) "ईमेल पता" else "Email address"
            "password" -> if (isHi) "पासवर्ड" else "Password"
            "full_name" -> if (isHi) "पूरा नाम" else "Full name"
            "phone_number" -> if (isHi) "फ़ोन नंबर" else "Phone number"
            "submit_login" -> if (isHi) "लॉगिन करें" else "Sign In"
            "submit_signup" -> if (isHi) "शुरू करें" else "Sign Up"
            "dont_have_account" -> if (isHi) "खाता नहीं है? साइन अप करें" else "Don't have an account? Sign up"
            "already_have_account" -> if (isHi) "पहले से खाता है? लॉगिन करें" else "Already have an account? Sign in"
            "skip" -> if (isHi) "छोड़ें" else "Skip"
            "save_profile" -> if (isHi) "सहेजें और आगे बढ़ें" else "Save & Continue"

            "my_bookings" -> if (isHi) "मेरी बुकिंग्स" else "My Bookings"
            "live_tracker" -> if (isHi) "लाइव कतार ट्रैकर" else "Live Queue Tracker"
            "in_queue" -> if (isHi) "कतार में हैं" else "In Queue"
            "in_service" -> if (isHi) "सेवा जारी है" else "In Service"
            "confirmed" -> if (isHi) "पुष्टि हो गई" else "Confirmed"
            "completed" -> if (isHi) "पूर्ण" else "Completed"
            "your_turn_in" -> if (isHi) "आपकी बारी" else "Your turn in"
            "stylist" -> if (isHi) "स्टाइलिस्ट" else "Stylist"
            "queue_position" -> if (isHi) "कतार स्थिति" else "Queue Position"

            "language" -> if (isHi) "भाषा (Language)" else "Language"
            "logout" -> if (isHi) "लॉग आउट" else "Sign Out"
            "supabase_backend" -> if (isHi) "सुपाबेस बैकएंड स्थिति" else "Supabase Backend Status"
            "connected_to_supabase" -> if (isHi) "सुपाबेस कनेक्टेड (ap-south-1)" else "Connected to Supabase (ap-south-1)"

            else -> key
        }
    }
}
