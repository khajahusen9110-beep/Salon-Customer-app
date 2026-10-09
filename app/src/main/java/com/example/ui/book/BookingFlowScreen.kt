package com.example.ui.book

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
    var allCombos by remember { mutableStateOf<List<ComboItem>>(emptyList()) }
    // What is being booked: one or more services done back-to-back, or a package (combo).
    var selectedServices by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var selectedComboItem by remember { mutableStateOf<ComboItem?>(null) }
    val combo = selectedComboItem
    val bookingServiceIds = combo?.serviceIds ?: selectedServices.map { it.id }
    val bookingKey = combo?.id ?: bookingServiceIds.joinToString(",")
    val bookingTitle = combo?.name ?: selectedServices.joinToString(" + ") { it.name }
    val bookingPrice = combo?.price ?: selectedServices.sumOf { it.price }
    val bookingMinutes = combo?.durationMinutes ?: selectedServices.sumOf { it.duration }

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
        allCombos = combos

        val pickedCombo = comboId?.let { id -> combos.find { it.id == id } }
        if (pickedCombo != null) {
            selectedComboItem = pickedCombo
        } else {
            val first = serviceId?.let { id -> services.find { it.id == id } } ?: services.firstOrNull()
            selectedServices = listOfNotNull(first)
        }
        isLoadingInitial = false
    }

    // Stylists who can do everything that was picked (one stylist does the whole visit)
    LaunchedEffect(bookingKey) {
        if (bookingServiceIds.isEmpty()) {
            staffList = emptyList()
            return@LaunchedEffect
        }
        val list = salonRepo.getStaffForServices(bookingServiceIds, salonId)
        staffList = list
        if (selectedStaffId != null && list.none { it.id == selectedStaffId }) selectedStaffId = null
    }

    // Refresh Dates Availability when Stylist changes or Service changes
    LaunchedEffect(bookingKey, selectedStaffId) {
        if (bookingServiceIds.isEmpty()) return@LaunchedEffect
        val window = salon?.bookingWindowDays ?: 14
        isLoadingDates = true
        val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val days = salonRepo.getWeekAvailability(bookingServiceIds, cleanStaff, window)
        dayAvailabilityList = days
        isLoadingDates = false

        // Default-select earliest date with slot_count > 0, or first day
        val firstAvailableDay = days.firstOrNull { it.slotCount > 0 } ?: days.firstOrNull()
        if (selectedDay == null || days.none { it.dateString == selectedDay?.dateString }) {
            selectedDay = firstAvailableDay
        }
    }

    // Real free-slot count per stylist for the selected day (shown on each stylist card).
    var staffSlotCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(selectedDay?.dateString, bookingKey, staffList) {
        val day = selectedDay ?: return@LaunchedEffect
        staffSlotCounts = emptyMap()
        if (bookingServiceIds.isEmpty()) return@LaunchedEffect
        staffSlotCounts = staffList.associate { st -> st.id to salonRepo.getAvailableSlots(bookingServiceIds, st.id, day.dateString).size }
    }

    // Refresh Slots when Selected Date or Stylist changes
    LaunchedEffect(selectedDay?.dateString, selectedStaffId, bookingKey) {
        val day = selectedDay ?: return@LaunchedEffect
        if (bookingServiceIds.isEmpty()) {
            availableSlots = emptyList()
            selectedSlot = null
            return@LaunchedEffect
        }
        isLoadingSlots = true
        inlineErrorMessage = null
        val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val slots = salonRepo.getAvailableSlots(bookingServiceIds, cleanStaff, day.dateString)
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
        val ids = bookingServiceIds.takeIf { it.isNotEmpty() } ?: return
        val day = selectedDay ?: return
        scope.launch {
            isLoadingSlots = true
            selectedSlot = null
            val cleanStaff = selectedStaffId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            availableSlots = salonRepo.getAvailableSlots(ids, cleanStaff, day.dateString)
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
    val hasSelection = bookingServiceIds.isNotEmpty()

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
                            text = bookingTitle.ifBlank { "Select Service" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "₹${bookingPrice.toInt()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Slate900
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (bookingMinutes > 0) "• ${ServiceItem.formatDuration(bookingMinutes)}" else "",
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
                            if (!hasSelection || selectedSlot == null || selectedDay == null) return@Button
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
                                    serviceIds = bookingServiceIds,
                                    comboId = combo?.id,
                                    title = bookingTitle,
                                    totalPrice = bookingPrice,
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
                                RazorpayPayments.open(activity, order, "$what • $bookingTitle", holdSecondsLeft = 14 * 60)
                            }
                        },
                        enabled = !isSubmitting && selectedSlot != null && hasSelection,
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
                                text = "Pay ₹${payNowAmount(bookingPrice, paymentOption).toInt()} & Book",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedSlot != null && hasSelection) Color.White else Slate500
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
                                text = if (hasSelection) "Change / add" else "Choose",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (combo != null) {
                        // A package: its services at the package price
                        Text(
                            text = "PACKAGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(combo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        combo.serviceNames.forEach { name ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(name, fontSize = 13.sp, color = Slate700)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        BookingTotalRow(bookingMinutes, bookingPrice, combo.originalPrice.takeIf { it > combo.price })
                    } else if (selectedServices.isNotEmpty()) {
                        selectedServices.forEach { srv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("selected_service_${srv.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(srv.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(srv.formattedDuration, fontSize = 12.sp, color = Slate500)
                                }
                                Text("₹${srv.price.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Slate900)
                                if (selectedServices.size > 1) {
                                    IconButton(
                                        onClick = { selectedServices = selectedServices.filterNot { it.id == srv.id } },
                                        modifier = Modifier.size(36.dp).testTag("remove_service_${srv.id}")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Slate400, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (selectedServices.size < MAX_SERVICES_PER_BOOKING) {
                            TextButton(
                                onClick = { showServicePickerSheet = true },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.testTag("add_another_service")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add another service", fontSize = 13.sp, color = GoldPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (selectedServices.size > 1) {
                            Text(
                                "One stylist will do all of these back-to-back.",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            BookingTotalRow(bookingMinutes, bookingPrice, null)
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

                    if (hasSelection && staffList.isEmpty()) {
                        Text(
                            text = if (bookingServiceIds.size > 1)
                                "No single stylist here does all of these services. Remove one, or book them separately."
                            else "No stylist is available for this service right now.",
                            fontSize = 12.sp,
                            color = Color(0xFFB91C1C),
                            modifier = Modifier.padding(vertical = 4.dp).testTag("no_stylist_for_services")
                        )
                    }

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
                                            val count = staffSlotCounts[staff.id]
                                            if (selectedDay != null && count != null) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (count == 0) "• no slots ${if (selectedDay?.dayName == "Today") "today" else "this day"}"
                                                           else "• $count slots ${if (selectedDay?.dayName == "Today") "today" else "this day"}",
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
                            val isClosed = !day.isOpen
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

                                    if (isClosed) {
                                        Text(
                                            text = "Closed",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Slate300 else Slate400
                                        )
                                    } else if (isFull) {
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
            if (hasSelection) {
                val price = bookingPrice
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

    // Service picker: tick one or more services (done back-to-back by one stylist), or pick a package.
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
                    text = "Choose services",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tick everything you want in this visit (up to $MAX_SERVICES_PER_BOOKING), or pick a package.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                val grouped = allServices.groupBy { it.categoryName.ifEmpty { "Services" } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    if (allCombos.isNotEmpty()) {
                        item { PickerGroupHeader("Packages") }
                        items(allCombos) { c ->
                            val isSelected = combo?.id == c.id
                            Surface(
                                onClick = {
                                    selectedComboItem = c
                                    selectedServices = emptyList()
                                    showServicePickerSheet = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldContainer else Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("picker_combo_${c.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(
                                            c.serviceNames.joinToString(" + ") +
                                                if (c.durationMinutes > 0) " • ${ServiceItem.formatDuration(c.durationMinutes)}" else "",
                                            fontSize = 12.sp,
                                            color = Slate500
                                        )
                                    }
                                    Text("₹${c.price.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                                }
                            }
                        }
                    }

                    grouped.forEach { (catName, services) ->
                        item { PickerGroupHeader(catName) }
                        items(services) { srv ->
                            val isSelected = combo == null && selectedServices.any { it.id == srv.id }
                            val canAdd = isSelected || combo != null || selectedServices.size < MAX_SERVICES_PER_BOOKING
                            Surface(
                                onClick = {
                                    if (combo != null) {
                                        // Switching from a package to individual services
                                        selectedComboItem = null
                                        selectedServices = listOf(srv)
                                    } else if (isSelected) {
                                        selectedServices = selectedServices.filterNot { it.id == srv.id }
                                    } else if (canAdd) {
                                        selectedServices = selectedServices + srv
                                    }
                                },
                                enabled = canAdd,
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
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = GoldPrimary)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(srv.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                            color = if (canAdd) Slate900 else Slate400)
                                        Text(srv.formattedDuration, fontSize = 12.sp, color = Slate500)
                                    }
                                    Text(
                                        text = "₹${srv.price.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate900,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showServicePickerSheet = false },
                    enabled = hasSelection,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("picker_done"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Text(
                        if (hasSelection) "Done • ${ServiceItem.formatDuration(bookingMinutes)} • ₹${bookingPrice.toInt()}"
                        else "Pick at least one service",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** Most services one booking can hold (the server enforces the same limit). */
private const val MAX_SERVICES_PER_BOOKING = 6

@Composable
private fun PickerGroupHeader(title: String) {
    Surface(
        color = Slate100,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Slate700,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Total time and price of the whole visit (with the crossed-out price for a package). */
@Composable
private fun BookingTotalRow(minutes: Int, price: Double, originalPrice: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Slate100, shape = RoundedCornerShape(6.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Slate600, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(ServiceItem.formatDuration(minutes), fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("Total ₹${price.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate900)
        if (originalPrice != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "₹${originalPrice.toInt()}",
                fontSize = 13.sp,
                color = Slate400,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
            )
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
