package com.example.ui.book

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
import com.example.data.payment.PaymentResult
import com.example.data.payment.RazorpayPayments
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.common.AppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingFlowScreen(
    salonId: String,
    serviceId: String?,
    comboId: String?,
    staffId: String? = null,
    salonRepo: SalonRepository,
    authRepo: AuthRepository,
    onBack: () -> Unit,
    onBookingConfirmed: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by authRepo.currentLanguage.collectAsState()

    var salon by remember { mutableStateOf<Salon?>(null) }
    var allServices by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var selectedService by remember { mutableStateOf<ServiceItem?>(null) }
    var isComboSelected by remember { mutableStateOf(false) }
    var selectedComboItem by remember { mutableStateOf<ComboItem?>(null) }

    // Step B: Stylist state
    val cleanInitialStaffId = staffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    var staffList by remember { mutableStateOf<List<StaffMember>>(emptyList()) }
    var selectedStaffId by remember { mutableStateOf<String?>(cleanInitialStaffId) } // pre-select if provided

    // Step C: Date strip state
    var dayAvailabilityList by remember { mutableStateOf<List<DayAvailability>>(emptyList()) }
    var selectedDay by remember { mutableStateOf<DayAvailability?>(null) }
    var isLoadingDates by remember { mutableStateOf(false) }

    // Step D: Slots state
    var availableSlots by remember { mutableStateOf<List<TimeSlot>>(emptyList()) }
    var selectedSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var isLoadingSlots by remember { mutableStateOf(false) }

    // Notes & booking action state
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var inlineErrorMessage by remember { mutableStateOf<String?>(null) }
    var showServicePickerSheet by remember { mutableStateOf(false) }
    var isLoadingInitial by remember { mutableStateOf(true) }

    // Step F: online payment (20% advance or full). The slot is held while the customer pays.
    var paymentOption by remember { mutableStateOf("advance") }
    var payingBookingId by remember { mutableStateOf<String?>(null) }
    var paymentStage by remember { mutableStateOf<String?>(null) } // status text while paying

    // Load Initial Salon and Service Data
    LaunchedEffect(salonId) {
        isLoadingInitial = true
        val s = salonRepo.getSalonById(salonId)
        salon = s
        val services = salonRepo.getServices(salonId)
        val combos = salonRepo.getCombos(salonId)
        allServices = services

        if (!comboId.isNullOrEmpty()) {
            val combo = combos.find { it.id == comboId }
            selectedComboItem = combo
            isComboSelected = true
            // Also select first service as fallback
            selectedService = services.firstOrNull()
        } else if (!serviceId.isNullOrEmpty()) {
            selectedService = services.find { it.id == serviceId } ?: services.firstOrNull()
            isComboSelected = false
        } else {
            selectedService = services.firstOrNull()
            isComboSelected = false
        }
        isLoadingInitial = false
    }

    // Refresh Stylists when Selected Service Changes
    LaunchedEffect(selectedService?.id) {
        val srv = selectedService ?: return@LaunchedEffect
        staffList = salonRepo.getStaffForService(srv.id, salonId)
    }

    // Refresh Dates Availability when Stylist changes or Service changes
    LaunchedEffect(selectedService?.id, selectedStaffId) {
        val srv = selectedService ?: return@LaunchedEffect
        val window = salon?.bookingWindowDays ?: 14
        isLoadingDates = true
        val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val days = salonRepo.getWeekAvailability(srv.id, cleanStaff, window)
        dayAvailabilityList = days
        isLoadingDates = false

        // Default-select earliest date with slot_count > 0, or first day
        val firstAvailableDay = days.firstOrNull { it.slotCount > 0 } ?: days.firstOrNull()
        if (selectedDay == null || days.none { it.dateString == selectedDay?.dateString }) {
            selectedDay = firstAvailableDay
        }
    }

    // Refresh Slots when Selected Date or Stylist changes
    LaunchedEffect(selectedDay?.dateString, selectedStaffId, selectedService?.id) {
        val srv = selectedService ?: return@LaunchedEffect
        val day = selectedDay ?: return@LaunchedEffect
        isLoadingSlots = true
        inlineErrorMessage = null
        val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val slots = salonRepo.getAvailableSlots(srv.id, cleanStaff, day.dateString)
        availableSlots = slots
        isLoadingSlots = false

        // Reset or validate selected slot
        if (selectedSlot != null && slots.none { it.slotStart == selectedSlot?.slotStart }) {
            selectedSlot = null
        }
    }

    fun releaseHeldSlot(bookingId: String) {
        scope.launch { salonRepo.cancelBooking(bookingId) }
    }

    fun refreshSlotsAfterConflict() {
        val srv = selectedService ?: return
        val day = selectedDay ?: return
        scope.launch {
            isLoadingSlots = true
            selectedSlot = null
            val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            availableSlots = salonRepo.getAvailableSlots(srv.id, cleanStaff, day.dateString)
            isLoadingSlots = false
        }
    }

    // Razorpay reports back through MainActivity -> RazorpayPayments.results.
    LaunchedEffect(Unit) {
        RazorpayPayments.results.collect { result ->
            val bookingId = payingBookingId ?: return@collect
            when (result) {
                is PaymentResult.Success -> {
                    paymentStage = "Confirming your payment…"
                    val verified = salonRepo.verifyPayment(result.orderId, result.paymentId, result.signature)
                    paymentStage = null
                    isSubmitting = false
                    payingBookingId = null
                    // Even if this check fails (network), Razorpay's webhook confirms the booking or refunds it.
                    verified.onFailure { android.util.Log.w("Payment", "verify failed: ${it.message}") }
                    onBookingConfirmed(bookingId)
                }
                is PaymentResult.Failed -> {
                    releaseHeldSlot(bookingId)
                    payingBookingId = null
                    paymentStage = null
                    isSubmitting = false
                    inlineErrorMessage = result.message + " The slot was released — you can pick a time again."
                    refreshSlotsAfterConflict()
                }
            }
        }
    }

    if (isLoadingInitial || salon == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = GoldPrimary)
        }
        return
    }

    val currentSalon = salon!!
    val currentService = selectedService

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Book Appointment", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(currentSalon.name, fontSize = 12.sp, color = Slate500)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("book_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Step E: Sticky Bottom Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = currentService?.name ?: "Select Service",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "₹${currentService?.price?.toInt() ?: 0}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Slate900
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${currentService?.formattedDuration ?: "30 min"}",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                        if (paymentStage != null) {
                            Text(
                                text = paymentStage ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldPrimary
                            )
                        } else if (selectedDay != null && selectedSlot != null) {
                            Text(
                                text = "${selectedDay?.dayName} at ${selectedSlot?.displayTime}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldPrimary
                            )
                        } else {
                            Text(
                                text = "Select a time slot",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentService == null || selectedSlot == null || selectedDay == null) return@Button
                            isSubmitting = true
                            inlineErrorMessage = null

                            val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                            val chosenStylist = staffList.find { it.id == cleanStaff }
                            val stylistName = chosenStylist?.name ?: "Any Stylist (Fastest Available)"

                            val activity = context.findActivity()
                            scope.launch {
                                paymentStage = "Holding your slot…"
                                val result = salonRepo.createBooking(
                                    salonId = currentSalon.id,
                                    salonName = currentSalon.name,
                                    salonArea = "${currentSalon.area}, ${currentSalon.city}",
                                    service = currentService,
                                    staffId = cleanStaff,
                                    stylistName = stylistName,
                                    dateFormatted = "${selectedDay?.dayName}, ${selectedDay?.dayNumber}",
                                    timeSlot = selectedSlot!!,
                                    notes = notes.trim(),
                                    paymentOption = paymentOption
                                )
                                val booking = result.getOrElse {
                                    paymentStage = null
                                    isSubmitting = false
                                    inlineErrorMessage = it.message ?: "Slot just got booked, please pick another"
                                    refreshSlotsAfterConflict()
                                    return@launch
                                }

                                paymentStage = "Opening payment…"
                                val order = salonRepo.createPaymentOrder(booking.id).getOrElse {
                                    releaseHeldSlot(booking.id)
                                    paymentStage = null
                                    isSubmitting = false
                                    inlineErrorMessage = it.message ?: "Could not start the payment. Please try again."
                                    return@launch
                                }
                                if (activity == null) {
                                    releaseHeldSlot(booking.id)
                                    paymentStage = null
                                    isSubmitting = false
                                    inlineErrorMessage = "Could not open the payment screen. Please try again."
                                    return@launch
                                }
                                payingBookingId = booking.id
                                paymentStage = "Waiting for payment…"
                                val what = if (paymentOption == "full") "Full payment" else "20% advance"
                                RazorpayPayments.open(activity, order, "$what • ${currentService.name}", holdSecondsLeft = 14 * 60)
                            }
                        },
                        enabled = !isSubmitting && selectedSlot != null && !isComboSelected,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Slate900,
                            disabledContainerColor = Slate200
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("confirm_booking_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Pay ₹${payNowAmount(currentService?.price ?: 0.0, paymentOption).toInt()} & Book",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedSlot != null && !isComboSelected) Color.White else Slate500
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Combo Limitation Notice
            if (isComboSelected && selectedComboItem != null) {
                Surface(
                    color = GoldContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GoldPrimary.copy(alpha = 0.5f))),
                    modifier = Modifier.fillMaxWidth().testTag("combo_limitation_banner")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Combo Package: ${selectedComboItem?.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Slate900
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Multi-service combo packages require phone confirmation with front desk staff. You can call directly or choose an individual service to book instantly.",
                            fontSize = 12.sp,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentSalon.phone}"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call Salon to Book", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    isComboSelected = false
                                    showServicePickerSheet = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Select Individual Service", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Inline Error Banner (Slot clash / auto-refreshed)
            AnimatedVisibility(visible = inlineErrorMessage != null) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF87171))),
                    modifier = Modifier.fillMaxWidth().testTag("slot_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = inlineErrorMessage ?: "Slot just got booked, please pick another",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Availability grid refreshed below with remaining open slots.",
                                fontSize = 11.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                }
            }

            // Step A — Service Confirmation Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("step_a_service_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "STEP 1: SERVICE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 1.sp
                        )

                        TextButton(
                            onClick = { showServicePickerSheet = true },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.testTag("change_service_link")
                        ) {
                            Text(
                                text = "Change service",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentService != null) {
                        Text(
                            text = currentService.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentService.description.isNotEmpty()) {
                            Text(
                                text = currentService.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Slate100, shape = RoundedCornerShape(6.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Slate600, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(currentService.formattedDuration, fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "₹${currentService.price.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }
                    } else {
                        Button(
                            onClick = { showServicePickerSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                        ) {
                            Text("Select a service to start", color = Color.White)
                        }
                    }
                }
            }

            // Step B — Stylist Selection Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("step_b_stylist_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STEP 2: CHOOSE STYLIST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 1: "Any Stylist — fastest available" (Pre-selected by default)
                    val isAnySelected = selectedStaffId == null
                    Surface(
                        onClick = { selectedStaffId = null },
                        color = if (isAnySelected) GoldContainer.copy(alpha = 0.5f) else Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isAnySelected) GoldPrimary else Slate200)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("stylist_option_any")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Slate900),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Any Stylist",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Slate900
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = EmeraldContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "FASTEST SLOT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF065F46),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Maximum slot availability • Skip waiting",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }

                            RadioButton(
                                selected = isAnySelected,
                                onClick = { selectedStaffId = null },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Or choose a specific master stylist:",
                        fontSize = 12.sp,
                        color = Slate500,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Individual Stylists List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        staffList.forEach { staff ->
                            val isChosen = selectedStaffId == staff.id
                            Surface(
                                onClick = { selectedStaffId = staff.id },
                                color = if (isChosen) GoldContainer.copy(alpha = 0.5f) else Color.White,
                                shape = RoundedCornerShape(12.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(if (isChosen) GoldPrimary else Slate200)
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("stylist_option_${staff.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = staff.photoUrl,
                                        contentDescription = staff.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = staff.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = staff.title,
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "${staff.ratingAvg} (${staff.ratingCount})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Slate700
                                            )
                                            if (selectedDay != null) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "• ${staff.slotsCountToday} slots",
                                                    fontSize = 11.sp,
                                                    color = EmeraldLive,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    RadioButton(
                                        selected = isChosen,
                                        onClick = { selectedStaffId = staff.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step C — Date Strip Card (Horizontal scrollable window)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("step_c_date_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "STEP 3: SELECT DATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 1.sp
                        )

                        if (isLoadingDates) {
                            CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dayAvailabilityList.forEach { day ->
                            val isSelected = selectedDay?.dateString == day.dateString
                            val isFull = day.slotCount <= 0
                            val isLow = day.slotCount in 1..3

                            Surface(
                                onClick = {
                                    selectedDay = day
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isSelected -> Slate900
                                    isFull -> Slate100
                                    else -> Color.White
                                },
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        when {
                                            isSelected -> Slate900
                                            isFull -> Slate200
                                            else -> Slate200
                                        }
                                    )
                                ),
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(86.dp)
                                    .testTag("date_chip_${day.dateString}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = day.dayName.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) GoldAccent else if (isFull) Slate400 else Slate500
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = day.dayNumber,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else if (isFull) Slate400 else Slate900
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (isFull) {
                                        Text(
                                            text = "Full",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate400
                                        )
                                    } else if (isLow) {
                                        Text(
                                            text = "${day.slotCount} left",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) GoldAccent else GoldPrimary
                                        )
                                    } else {
                                        Text(
                                            text = "${day.slotCount} slots",
                                            fontSize = 9.sp,
                                            color = if (isSelected) Slate300 else Slate500
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step D — Time Slot Grid Card (Grouped by Morning / Afternoon / Evening)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("step_d_slots_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "STEP 4: SELECT TIME SLOT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 1.sp
                        )

                        if (isLoadingSlots) {
                            CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (availableSlots.isEmpty() && !isLoadingSlots) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No slots available this day", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Please choose another day on the date strip above", fontSize = 12.sp, color = Slate500)
                            }
                        }
                    } else {
                        val groupedSlots = listOf("Morning", "Afternoon", "Evening")
                        groupedSlots.forEach { periodName ->
                            val periodSlots = availableSlots.filter { it.period == periodName }
                            if (periodSlots.isNotEmpty()) {
                                Text(
                                    text = periodName.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate600,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )

                                FlowTimeSlotRow(
                                    slots = periodSlots,
                                    selectedSlot = selectedSlot,
                                    onSelect = { slot ->
                                        selectedSlot = slot
                                        inlineErrorMessage = null
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            // Step F — How to pay (online, held for 15 minutes until paid)
            if (currentService != null && !isComboSelected) {
                val price = currentService.price
                val advance = payNowAmount(price, "advance")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().testTag("step_payment_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PAYMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        PaymentOptionRow(
                            selected = paymentOption == "advance",
                            title = "Pay 20% advance now — ₹${advance.toInt()}",
                            subtitle = "Pay the remaining ₹${(price - advance).toInt()} at the salon",
                            tag = "pay_option_advance"
                        ) { paymentOption = "advance" }
                        PaymentOptionRow(
                            selected = paymentOption == "full",
                            title = "Pay full amount now — ₹${price.toInt()}",
                            subtitle = "Nothing to pay at the salon",
                            tag = "pay_option_full"
                        ) { paymentOption = "full" }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Free cancellation up to 2 hours before your appointment (full refund). " +
                                "Cancelling later or not showing up: the amount paid is not refunded.",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }
            }

            // Step 5: Optional Notes Field
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("step_notes_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NOTES FOR STYLIST (OPTIONAL)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("e.g. Skin fade with low taper, clean beard outline...", fontSize = 13.sp) },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stylist_notes_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Modal Service Picker Sheet
    if (showServicePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showServicePickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select a Service",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose an a la carte service to book online with instant confirmation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                val grouped = allServices.groupBy { it.categoryName.ifEmpty { "Styling & Grooming" } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    grouped.forEach { (catName, services) ->
                        item {
                            Surface(
                                color = Slate100,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = catName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Slate700,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        items(services) { srv ->
                            val isSelected = selectedService?.id == srv.id
                            Surface(
                                onClick = {
                                    selectedService = srv
                                    isComboSelected = false
                                    showServicePickerSheet = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldContainer else Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("picker_service_${srv.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(srv.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(srv.formattedDuration, fontSize = 12.sp, color = Slate500)
                                    }
                                    Text(
                                        text = "₹${srv.price.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun FlowTimeSlotRow(
    slots: List<TimeSlot>,
    selectedSlot: TimeSlot?,
    onSelect: (TimeSlot) -> Unit
) {
    // 3 column grid for time slot chips
    val chunked = slots.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunked.forEach { rowSlots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSlots.forEach { slot ->
                    val isSelected = selectedSlot?.slotStart == slot.slotStart
                    Surface(
                        onClick = { onSelect(slot) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Slate900 else Color.White,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) Slate900 else Slate200)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("slot_chip_${slot.displayTime.replace(" ", "_")}")
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = slot.displayTime,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Slate900
                            )
                        }
                    }
                }
                // Fill space if less than 3
                if (rowSlots.size < 3) {
                    repeat(3 - rowSlots.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Amount charged online now; the server computes the same (20% advance rounded up, or the full price). */
private fun payNowAmount(price: Double, option: String): Double =
    if (option == "full") price else kotlin.math.max(1.0, kotlin.math.ceil(price * 20 / 100.0))

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun PaymentOptionRow(selected: Boolean, title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = Slate500)
        }
    }
}
