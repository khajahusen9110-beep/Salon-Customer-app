package com.example.ui.bookings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.book.FlowTimeSlotRow
import com.example.ui.common.AppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MyBookingsScreen(
    salonRepo: SalonRepository,
    authRepo: AuthRepository,
    onBookingClick: (String) -> Unit,
    onExploreSalons: () -> Unit,
    onBookAgain: (String, String?, String?) -> Unit = { _, _, _ -> }
) {
    val currentUser by authRepo.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val lang by authRepo.currentLanguage.collectAsState()
    val bookings by salonRepo.bookings.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Upcoming, 1 = Past
    var showReviewDialogForBooking by remember { mutableStateOf<BookingItem?>(null) }
    var rebookOptions by remember { mutableStateOf<List<com.example.data.model.RebookOption>>(emptyList()) }

    LaunchedEffect(currentUser?.id) {
        val custId = currentUser?.id
        if (!custId.isNullOrBlank()) {
            salonRepo.syncCustomerBookings(custId)
        }
        rebookOptions = salonRepo.getRebookOptions()
    }

    // Split bookings into Upcoming and Past
    val upcomingBookings = remember(bookings) {
        bookings.filter { it.status.lowercase() in listOf("pending_payment", "confirmed", "arrived", "in_service", "in_queue") }
    }
    val pastBookings = remember(bookings) {
        bookings.filter { it.status.lowercase() in listOf("completed", "cancelled", "no_show") }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        text = AppStrings.get("my_bookings", lang),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track live queues, manage appointments & rebook",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sub-sections tabs: Upcoming and Past
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate100, RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        Surface(
                            onClick = { selectedTab = 0 },
                            color = if (selectedTab == 0) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = if (selectedTab == 0) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("tab_upcoming_bookings")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Upcoming",
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTab == 0) Slate900 else Slate500,
                                        fontSize = 13.sp
                                    )
                                    if (upcomingBookings.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (selectedTab == 0) GoldContainer else Slate200,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "${upcomingBookings.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedTab == 0) GoldPrimary else Slate600,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            onClick = { selectedTab = 1 },
                            color = if (selectedTab == 1) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = if (selectedTab == 1) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("tab_past_bookings")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Past",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTab == 1) Slate900 else Slate500,
                                        fontSize = 13.sp
                                    )
                                    if (pastBookings.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (selectedTab == 1) Slate200 else Slate200,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "${pastBookings.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate600,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val currentList = if (selectedTab == 0) upcomingBookings else pastBookings

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.EventAvailable else Icons.Default.History,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (selectedTab == 0) "No upcoming appointments" else "No past visits yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedTab == 0) "Skip the wait and reserve your next salon slot in seconds." else "Completed appointments will appear here.",
                        fontSize = 13.sp,
                        color = Slate500,
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                    )
                    Button(
                        onClick = onExploreSalons,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        modifier = Modifier.testTag("empty_explore_salons_btn")
                    ) {
                        Text(AppStrings.get("tab_discover", lang), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // If in Past tab and we have Rebook options, display the quick Rebook strip
                if (selectedTab == 1 && rebookOptions.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate100),
                            modifier = Modifier.fillMaxWidth().testTag("fast_rebook_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Replay, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("One-Tap Rebook", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                    }
                                    Text("Fastest Available", fontSize = 11.sp, color = Slate500)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                rebookOptions.take(2).forEach { opt ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .clickable { onBookAgain(opt.salonId, opt.serviceId, opt.stylistId) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(opt.salonName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                                                Text("${opt.serviceName} • ${opt.stylistName} • ₹${opt.price.toInt()}", fontSize = 11.sp, color = Slate600)
                                            }
                                            Text("Book", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(currentList, key = { it.id }) { booking ->
                    BookingListCard(
                        booking = booking,
                        isPast = selectedTab == 1,
                        onClick = { onBookingClick(booking.id) },
                        onRateVisit = { showReviewDialogForBooking = booking },
                        onBookAgain = { onBookAgain(booking.salonId, booking.serviceId, booking.staffId) }
                    )
                }
            }
        }
    }

    // Rate Visit Dialog
    if (showReviewDialogForBooking != null) {
        val targetBooking = showReviewDialogForBooking!!
        ReviewDialog(
            booking = targetBooking,
            onDismiss = { showReviewDialogForBooking = null },
            onSubmit = { rating, comment ->
                scope.launch {
                    salonRepo.submitReview(targetBooking.id, rating, comment)
                }
                showReviewDialogForBooking = null
            }
        )
    }
}

@Composable
fun BookingListCard(
    booking: BookingItem,
    isPast: Boolean,
    onClick: () -> Unit,
    onRateVisit: () -> Unit,
    onBookAgain: () -> Unit
) {
    val statusLower = booking.status.lowercase()
    val isLive = statusLower in listOf("in_queue", "in_service", "arrived") || (statusLower == "confirmed" && booking.isToday)
    val isCompleted = statusLower == "completed"
    val isCancelled = statusLower == "cancelled"

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Salon photo thumbnail + details + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (booking.salonPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = booking.salonPhotoUrl,
                        contentDescription = booking.salonName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.salonName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = booking.salonArea,
                        fontSize = 12.sp,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Stylist: ${booking.stylistName}",
                        fontSize = 12.sp,
                        color = GoldPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = when (statusLower) {
                        "pending_payment" -> GoldContainer
                        "confirmed" -> EmeraldContainer
                        "in_queue", "arrived" -> EmeraldContainer
                        "in_service" -> GoldContainer
                        "completed" -> Slate100
                        "cancelled" -> Color(0xFFFEE2E2)
                        else -> Slate100
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (statusLower) {
                            "pending_payment" -> "PAYMENT PENDING"
                            "confirmed" -> if (booking.isToday) "TODAY" else "CONFIRMED"
                            "in_queue" -> "IN QUEUE (#${booking.queuePosition})"
                            "arrived" -> "ARRIVED"
                            "in_service" -> "IN CHAIR"
                            "completed" -> "COMPLETED"
                            "cancelled" -> "CANCELLED"
                            else -> booking.status.uppercase()
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (statusLower) {
                            "pending_payment" -> GoldPrimary
                            "confirmed", "in_queue", "arrived" -> Color(0xFF065F46)
                            "in_service" -> GoldPrimary
                            "cancelled" -> Color(0xFFB91C1C)
                            else -> Slate600
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            // Middle Row: Service name, Price, Date & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = booking.serviceName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Slate800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate500, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${booking.date} at ${booking.timeSlot}",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }

                Text(
                    text = "₹${booking.price.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Slate900
                )
            }

            // Bottom Section: Live tracker teaser or Past action prompts
            if (isLive && !isPast) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = EmeraldContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldLive)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (statusLower == "in_service") "In service right now" else if (booking.delayMinutes > 0) "Delayed ~${booking.delayMinutes}m" else "On time — turn in ~${booking.waitMinutes}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46)
                            )
                        }

                        Text(
                            text = "Track Live →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLive
                        )
                    }
                }
            } else if (isPast && isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!booking.isReviewed) {
                        OutlinedButton(
                            onClick = onRateVisit,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp).testTag("rate_visit_btn_${booking.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rate your visit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        }
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(booking.userRating) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reviewed (${booking.userRating}★)", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                            }
                            if (booking.reviewComment.isNotBlank()) {
                                Text(
                                    text = "\"${booking.reviewComment}\"",
                                    fontSize = 11.sp,
                                    color = Slate600,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onBookAgain,
                        modifier = Modifier.testTag("book_again_btn_${booking.id}")
                    ) {
                        Text("Book Again", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingTrackerScreen(
    bookingId: String,
    salonRepo: SalonRepository,
    authRepo: AuthRepository,
    onBack: () -> Unit,
    onBookAgain: (String, String?, String?) -> Unit = { _, _, _ -> },
    onNeedHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by authRepo.currentLanguage.collectAsState()
    val bookings by salonRepo.bookings.collectAsState()
    var currentBooking by remember { mutableStateOf(bookings.find { it.id == bookingId }) }

    LaunchedEffect(bookings, bookingId) {
        val found = bookings.find { it.id == bookingId }
        if (found != null) {
            currentBooking = found
        } else if (currentBooking == null) {
            currentBooking = salonRepo.getSingleBooking(bookingId)
        }
    }
    val booking = currentBooking

    // Live status polling state
    var liveStatus by remember { mutableStateOf<BookingLiveStatus?>(null) }
    var isPolling by remember { mutableStateOf(false) }

    // Reschedule & Cancel sheet/dialog state
    var showRescheduleSheet by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var cancelErrorMessage by remember { mutableStateOf<String?>(null) }
    var isCancelling by remember { mutableStateOf(false) }
    var rescheduleErrorMessage by remember { mutableStateOf<String?>(null) }
    var isRescheduling by remember { mutableStateOf(false) }

    // Reschedule picker state
    var reschedDayList by remember { mutableStateOf<List<DayAvailability>>(emptyList()) }
    var reschedSelectedDay by remember { mutableStateOf<DayAvailability?>(null) }
    var reschedSlots by remember { mutableStateOf<List<TimeSlot>>(emptyList()) }
    var reschedSelectedSlot by remember { mutableStateOf<TimeSlot?>(null) }

    // Periodic polling for live booking status (every 30s)
    LaunchedEffect(bookingId) {
        while (isActive) {
            val status = salonRepo.getMyBookingStatus(bookingId)
            liveStatus = status
            delay(30000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(AppStrings.get("live_tracker", lang), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Booking #${booking?.id ?: bookingId}", fontSize = 12.sp, color = Slate500)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tracker_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNeedHelp, modifier = Modifier.testTag("tracker_help_button")) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Help")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (booking == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Booking not found")
            }
            return@Scaffold
        }

        val statusLower = (liveStatus?.status ?: booking.status).lowercase()
        val isConfirmed = statusLower == "confirmed"
        val isArrived = statusLower == "arrived"
        val isInService = statusLower == "in_service"
        val isCompleted = statusLower == "completed"
        val isCancelled = statusLower == "cancelled"
        val isTodayActive = (isConfirmed || isArrived || isInService) && booking.isToday

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Success Toast / Confirmation Header
            Surface(
                color = when {
                    isCancelled -> Color(0xFFFEF2F2)
                    isCompleted -> Slate100
                    else -> EmeraldContainer
                },
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        when {
                            isCancelled -> Color(0xFFF87171)
                            isCompleted -> Slate300
                            else -> EmeraldLive.copy(alpha = 0.5f)
                        }
                    )
                ),
                modifier = Modifier.fillMaxWidth().testTag("booked_success_toast")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCancelled -> Color(0xFFEF4444)
                                    isCompleted -> Slate700
                                    else -> EmeraldLive
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isCancelled -> Icons.Default.Close
                                isCompleted -> Icons.Default.Check
                                else -> Icons.Default.Check
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                isCancelled -> "Booking Cancelled"
                                isCompleted -> "Visit Completed"
                                isInService -> "Currently In Service"
                                else -> "Booked! See you at ${booking.timeSlot}"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCancelled -> Color(0xFF991B1B)
                                isCompleted -> Slate900
                                else -> Color(0xFF065F46)
                            }
                        )
                        Text(
                            text = when {
                                isCancelled -> "This appointment has been cancelled."
                                isCompleted -> "Thank you for visiting! Let us know how it went."
                                isInService -> "Stylist ${booking.stylistName} is working on your service."
                                else -> "Zero-wait slot reserved. Arrive on time to skip the wait."
                            },
                            fontSize = 12.sp,
                            color = when {
                                isCancelled -> Color(0xFFB91C1C)
                                isCompleted -> Slate600
                                else -> Color(0xFF047857)
                            }
                        )
                    }
                }
            }

            // CORE ZERO-WAIT UX: Prominent Live Tracker Card (Centerpiece)
            if (isTodayActive) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier.fillMaxWidth().testTag("live_tracker_centerpiece")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top live beacon pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldLive)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE QUEUE ACTIVE",
                                color = EmeraldLive,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isInService) {
                            Text(
                                text = "You are in the chair",
                                color = Slate300,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "In Service",
                                color = GoldAccent,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            val waitMins = liveStatus?.waitMinutes ?: booking.waitMinutes
                            val delayMins = liveStatus?.delayMinutes ?: booking.delayMinutes
                            val scheduled = liveStatus?.scheduledStart ?: booking.timeSlot
                            val people = liveStatus?.peopleAhead ?: booking.peopleAhead

                            if (delayMins > 0) {
                                Text(
                                    text = "Estimated Start",
                                    color = Slate300,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = liveStatus?.estimatedStart ?: scheduled,
                                    color = GoldAccent,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = Color(0xFF451A03),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "Delayed by ~$delayMins min due to ongoing service",
                                        color = GoldAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Your turn in",
                                    color = Slate300,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "~$waitMins min",
                                    color = GoldAccent,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "On time — arrive by $scheduled",
                                    color = Slate300,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (people > 0) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    color = Slate800,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "$people ${if (people == 1) "person" else "people"} ahead of you in line",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Salon & Appointment Details Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("appointment_details_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Appointment Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    DetailRow("Salon", booking.salonName)
                    DetailRow("Location", booking.salonArea)
                    DetailRow("Stylist", booking.stylistName, highlight = true)
                    DetailRow("Service", booking.serviceName)
                    DetailRow("Date & Time", "${booking.date} at ${booking.timeSlot}")
                    DetailRow("Total Amount", "₹${booking.price.toInt()}")
                    paymentSummary(booking)?.let { DetailRow("Payment", it, highlight = true) }

                    if (booking.notes.isNotEmpty()) {
                        DetailRow("Notes for Stylist", booking.notes)
                    }
                }
            }

            // Action Buttons based on status
            if (isConfirmed && !isInService && !isCancelled) {
                // Future/Upcoming: Reschedule & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            rescheduleErrorMessage = null
                            reschedSelectedSlot = null
                            // Load date window for reschedule
                            scope.launch {
                                reschedDayList = salonRepo.getWeekAvailability(booking.bookedServiceIds, booking.staffId)
                                reschedSelectedDay = reschedDayList.firstOrNull { it.slotCount > 0 } ?: reschedDayList.firstOrNull()
                                val day = reschedSelectedDay
                                if (day != null) {
                                    reschedSlots = salonRepo.getAvailableSlots(booking.bookedServiceIds, booking.staffId, day.dateString)
                                }
                                showRescheduleSheet = true
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reschedule_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reschedule")
                    }

                    Button(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("cancel_booking_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Slot", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isCompleted) {
                // Completed: Rate Visit / View Review & Book Again
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (booking.isReviewed) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().testTag("reviewed_card"),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        repeat(booking.userRating) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Your Review (${booking.userRating}★)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                    }
                                    Surface(
                                        color = EmeraldContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Verified Visit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                if (booking.reviewComment.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("\"${booking.reviewComment}\"", fontSize = 12.sp, color = Slate700)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!booking.isReviewed) {
                            OutlinedButton(
                                onClick = { showReviewDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("rate_visit_completed_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rate Visit")
                            }
                        }

                        Button(
                            onClick = { onBookAgain(booking.salonId, booking.serviceId, booking.staffId) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("book_again_completed_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Book Again", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick Salon Contact & Directions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919820012345"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Salon")
                }

                Button(
                    onClick = {
                        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(booking.salonName + " " + booking.salonArea)}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Directions")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Cancel Confirmation Dialog (shows the refund the customer will get, from the server's rules)
    var cancelTerms by remember { mutableStateOf<CancellationTerms?>(null) }
    LaunchedEffect(showCancelDialog) {
        cancelTerms = null
        if (showCancelDialog) cancelTerms = salonRepo.getCancellationTerms(bookingId).getOrNull()
    }
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Appointment?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to cancel your booking at ${booking?.salonName}?")
                    val terms = cancelTerms
                    if (terms != null && (terms.refundAmount > 0 || terms.keptAmount > 0)) {
                        Text(
                            text = terms.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (terms.keptAmount > 0) Color(0xFFB91C1C) else Color(0xFF065F46),
                            modifier = Modifier.padding(top = 8.dp).testTag("cancel_refund_terms")
                        )
                    }
                    Text(
                        text = "Cancelling frees up the stylist for other customers. You can book again anytime.",
                        fontSize = 12.sp,
                        color = Slate500,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (cancelErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(cancelErrorMessage!!, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCancelling = true
                        cancelErrorMessage = null
                        scope.launch {
                            val res = salonRepo.cancelBooking(bookingId)
                            isCancelling = false
                            if (res.isSuccess) {
                                showCancelDialog = false
                            } else {
                                cancelErrorMessage = res.exceptionOrNull()?.message ?: "Unable to cancel"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_cancel_btn")
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Yes, Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Appointment")
                }
            }
        )
    }

    // Reschedule Bottom Sheet
    if (showRescheduleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRescheduleSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Reschedule Appointment",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select a new date and available time slot for ${booking?.serviceName}",
                    fontSize = 13.sp,
                    color = Slate500
                )

                if (rescheduleErrorMessage != null) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = rescheduleErrorMessage!!,
                            color = Color(0xFFB91C1C),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Date Strip
                Text("Select New Date", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reschedDayList.forEach { day ->
                        val isSelected = reschedSelectedDay?.dateString == day.dateString
                        val isFull = day.slotCount <= 0
                        Surface(
                            onClick = {
                                reschedSelectedDay = day
                                scope.launch {
                                    reschedSlots = salonRepo.getAvailableSlots(booking?.bookedServiceIds.orEmpty(), booking?.staffId, day.dateString)
                                    reschedSelectedSlot = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Slate900 else if (isFull) Slate100 else Color.White,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) Slate900 else Slate200)
                            ),
                            modifier = Modifier.width(68.dp).height(80.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(day.dayName.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) GoldAccent else Slate500)
                                Text(day.dayNumber, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Slate900)
                                Text(if (isFull) "Full" else "${day.slotCount} slots", fontSize = 9.sp, color = if (isSelected) Slate300 else Slate500)
                            }
                        }
                    }
                }

                // Slot Grid
                Text("Select New Time Slot", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (reschedSlots.isEmpty()) {
                    Text("No slots open for selected date", fontSize = 12.sp, color = Slate500)
                } else {
                    FlowTimeSlotRow(
                        slots = reschedSlots,
                        selectedSlot = reschedSelectedSlot,
                        onSelect = { slot ->
                            reschedSelectedSlot = slot
                            rescheduleErrorMessage = null
                        }
                    )
                }

                // Confirm Reschedule Button
                Button(
                    onClick = {
                        val slot = reschedSelectedSlot ?: return@Button
                        val day = reschedSelectedDay ?: return@Button
                        isRescheduling = true
                        rescheduleErrorMessage = null
                        scope.launch {
                            val res = salonRepo.rescheduleBooking(
                                bookingId = bookingId,
                                newDateFormatted = "${day.dayName}, ${day.dayNumber}",
                                newSlot = slot
                            )
                            isRescheduling = false
                            if (res.isSuccess) {
                                showRescheduleSheet = false
                            } else {
                                rescheduleErrorMessage = res.exceptionOrNull()?.message ?: "Slot collision, pick another"
                            }
                        }
                    },
                    enabled = reschedSelectedSlot != null && !isRescheduling,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_reschedule_btn")
                ) {
                    if (isRescheduling) {
                        CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm Reschedule", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showReviewDialog && booking != null) {
        ReviewDialog(
            booking = booking,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment ->
                scope.launch {
                    salonRepo.submitReview(booking.id, rating, comment)
                }
                showReviewDialog = false
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Slate500, fontSize = 13.sp)
        Text(
            text = value,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) GoldPrimary else Slate900,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ReviewDialog(
    booking: BookingItem,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate your visit", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${booking.salonName} • ${booking.serviceName}",
                    fontSize = 13.sp,
                    color = Slate600
                )

                // 5 Star Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i stars",
                                tint = if (i <= rating) GoldPrimary else Slate400,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Share feedback on speed, wait time, haircut...", fontSize = 12.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("review_comment_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                modifier = Modifier.testTag("submit_review_btn")
            ) {
                Text("Submit Review", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** One-line payment state for a booking, e.g. "₹80 paid (20% advance)" or "Refund of ₹80 started". */
private fun paymentSummary(b: BookingItem): String? {
    val paid = "₹${b.amountPaid.toInt()}"
    val kind = if (b.paymentOption == "full") "full payment" else "20% advance"
    return when (b.paymentStatus) {
        "pending" -> "Waiting for payment of ₹${b.amountDue.toInt()}"
        "paid" -> if (b.paymentOption == "full") "$paid paid online (nothing to pay at salon)"
                  else "$paid paid ($kind) • ₹${(b.price - b.amountPaid).toInt()} at salon"
        "refund_pending" -> "Refund of $paid started (5-7 working days)"
        "refunded" -> "$paid refunded"
        "forfeited" -> "$paid not refunded (late cancel / no-show)"
        "failed" -> "Payment not completed"
        else -> null
    }
}
