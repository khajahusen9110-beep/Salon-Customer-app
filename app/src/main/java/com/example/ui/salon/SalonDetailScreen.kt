package com.example.ui.salon

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.*
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.common.AppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SalonDetailScreen(
    salonId: String,
    salonRepo: SalonRepository,
    authRepo: AuthRepository,
    onBack: () -> Unit,
    onBookService: (String, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by authRepo.currentLanguage.collectAsState()
    val currentUser by authRepo.currentUser.collectAsState()
    val favoriteIds by salonRepo.favoriteIds.collectAsState()
    val isFavorite = favoriteIds.contains(salonId)

    // Data States
    var salon by remember { mutableStateOf<Salon?>(null) }
    var salonError by remember { mutableStateOf<String?>(null) }

    var hours by remember { mutableStateOf<List<SalonHours>>(emptyList()) }
    var hoursError by remember { mutableStateOf<String?>(null) }

    var categories by remember { mutableStateOf<List<ServiceCategory>>(emptyList()) }
    var categoriesError by remember { mutableStateOf<String?>(null) }

    var services by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var servicesError by remember { mutableStateOf<String?>(null) }

    var combos by remember { mutableStateOf<List<ComboItem>>(emptyList()) }
    var combosError by remember { mutableStateOf<String?>(null) }

    var staffList by remember { mutableStateOf<List<StaffMember>>(emptyList()) }
    var staffError by remember { mutableStateOf<String?>(null) }

    var queueStatus by remember { mutableStateOf<QueueStatus?>(null) }
    var queueError by remember { mutableStateOf<String?>(null) }

    var reviews by remember { mutableStateOf<List<ReviewItem>>(emptyList()) }
    var reviewsError by remember { mutableStateOf<String?>(null) }

    var isInitialLoading by remember { mutableStateOf(true) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var allAmenities by remember { mutableStateOf<List<Amenity>>(emptyList()) }
    LaunchedEffect(Unit) { allAmenities = salonRepo.loadAmenities() }

    // Fetch individual sections with isolated failure resilience
    suspend fun loadSalonData() {
        try {
            salonError = null
            salon = salonRepo.getSalonById(salonId)
            if (salon == null) salonError = "Salon details unavailable"
        } catch (e: Exception) {
            salonError = e.message ?: "Failed to load salon"
        }
    }

    suspend fun loadHoursData() {
        try {
            hoursError = null
            hours = salonRepo.getSalonHours(salonId)
        } catch (e: Exception) {
            hoursError = "Failed to load timings"
        }
    }

    suspend fun loadCategoriesData() {
        try {
            categoriesError = null
            categories = salonRepo.getServiceCategories(salonId)
        } catch (e: Exception) {
            categoriesError = "Failed to load categories"
        }
    }

    suspend fun loadServicesData() {
        try {
            servicesError = null
            services = salonRepo.getServices(salonId)
        } catch (e: Exception) {
            servicesError = "Failed to load services"
        }
    }

    suspend fun loadCombosData() {
        try {
            combosError = null
            combos = salonRepo.getCombos(salonId)
        } catch (e: Exception) {
            combosError = "Failed to load combos"
        }
    }

    suspend fun loadStaffData() {
        try {
            staffError = null
            staffList = salonRepo.getSalonStaff(salonId)
        } catch (e: Exception) {
            staffError = "Failed to load staff"
        }
    }

    suspend fun loadQueueData() {
        try {
            queueError = null
            queueStatus = salonRepo.getQueueStatus(salonId)
        } catch (e: Exception) {
            queueError = "Failed to load live queue"
        }
    }

    suspend fun loadReviewsData() {
        try {
            reviewsError = null
            reviews = salonRepo.getSalonReviews(salonId)
        } catch (e: Exception) {
            reviewsError = "Failed to load reviews"
        }
    }

    LaunchedEffect(salonId) {
        isInitialLoading = true
        currentUser?.id?.let { custId ->
            salonRepo.syncFavorites(custId)
        }

        coroutineScope {
            val jSalon = async { loadSalonData() }
            val jHours = async { loadHoursData() }
            val jCategories = async { loadCategoriesData() }
            val jServices = async { loadServicesData() }
            val jCombos = async { loadCombosData() }
            val jStaff = async { loadStaffData() }
            val jQueue = async { loadQueueData() }
            val jReviews = async { loadReviewsData() }

            jSalon.await()
            jHours.await()
            jCategories.await()
            jServices.await()
            jCombos.await()
            jStaff.await()
            jQueue.await()
            jReviews.await()
        }

        isInitialLoading = false
    }

    if (isInitialLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GoldPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading salon details...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
            }
        }
        return
    }

    val currentSalon = salon
    if (currentSalon == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = salonError ?: "Salon not found", style = MaterialTheme.typography.titleMedium, color = Slate900)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { scope.launch { loadSalonData() } }, colors = ButtonDefaults.buttonColors(containerColor = Slate900)) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    // Filtered services
    val filteredServices = if (selectedCategoryId == null) {
        services
    } else {
        services.filter { it.categoryId == selectedCategoryId }
    }

    // Photos handling
    val photosList = currentSalon.photos.filter { it.isNotBlank() }.ifEmpty {
        if (currentSalon.coverPhotoUrl.isNotBlank()) listOf(currentSalon.coverPhotoUrl) else emptyList()
    }
    val initialPage = currentSalon.coverPhotoIndex.coerceIn(0, (photosList.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = initialPage) { photosList.size }

    // Today's day of week in IST (0 = Sun, 1 = Mon, ..., 6 = Sat)
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    val todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSalon.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentSalon.phone}"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("call_salon_bottom_button")
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Slate900, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Salon", fontWeight = FontWeight.SemiBold, color = Slate900)
                        }
                    }

                    Button(
                        onClick = {
                            val firstSrv = services.firstOrNull()?.id
                            onBookService(salonId, firstSrv, null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("book_now_bottom_button")
                    ) {
                        Text(AppStrings.get("book_now", lang), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Photo Gallery Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                ) {
                    if (photosList.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            SubcomposeAsyncImage(
                                model = photosList[page],
                                contentDescription = "Salon Photo ${page + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(modifier = Modifier.fillMaxSize().background(Slate200), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                                    }
                                },
                                error = {
                                    Box(modifier = Modifier.fillMaxSize().background(Slate200), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Slate400, modifier = Modifier.size(48.dp))
                                    }
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Slate200),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Slate400, modifier = Modifier.size(64.dp))
                        }
                    }

                    // Navigation Overlay Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        salonRepo.toggleFavorite(salonId, currentUser?.id)
                                    }
                                },
                                modifier = Modifier.testTag("detail_fav_button")
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color.Red else Color.White
                                )
                            }
                        }
                    }

                    // Photo Counter Indicator
                    if (photosList.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${photosList.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 1. Salon Header Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    // Type and Verified Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = Slate100,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = currentSalon.salonType.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        if (currentSalon.isVerified) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(AppStrings.get("verified", lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GoldPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentSalon.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Location Line: area, city
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Slate500, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${currentSalon.area}, ${currentSalon.city}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate600
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rating stars + rating_avg + rating_count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GoldContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f", currentSalon.ratingAvg),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GoldPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${currentSalon.ratingCount} reviews)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                    }

                    // Call Salon Button
                    if (currentSalon.phone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentSalon.phone}"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("header_call_salon_button")
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Slate900, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Call Salon (${currentSalon.phone})", fontWeight = FontWeight.SemiBold, color = Slate900, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 7. Live Queue Teaser Strip
            item {
                if (queueError != null) {
                    InlineRetryCard(
                        message = queueError ?: "Queue info error",
                        onRetry = { scope.launch { loadQueueData() } }
                    )
                } else if (queueStatus != null) {
                    val q = queueStatus!!
                    Surface(
                        color = if (q.isBusy) Color(0xFFFFFBEB) else EmeraldContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .border(
                                1.dp,
                                if (q.isBusy) GoldPrimary.copy(alpha = 0.3f) else EmeraldLive.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("live_queue_strip")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (q.isBusy) GoldPrimary else EmeraldLive)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "LIVE QUEUE STATUS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = if (q.isBusy) GoldPrimary else Color(0xFF065F46)
                                    )
                                    Text(
                                        text = if (lang == "hi") q.messageHi else q.messageEn,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                }
                            }

                            if (q.waitMinutes > 0) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "~${q.waitMinutes}m wait",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Facilities (AC, Free WiFi, Parking...)
            val salonAmenities = allAmenities.filter { it.id in (salon?.amenityIds ?: emptyList()) }
            if (salonAmenities.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp).testTag("salon_facilities")) {
                        Text(
                            text = "Facilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            salonAmenities.forEach { a ->
                                Surface(shape = RoundedCornerShape(20.dp), color = Slate100) {
                                    Text(
                                        "${a.icon} ${a.name}",
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Staff / Stylists Section
            if (staffError != null) {
                item {
                    InlineRetryCard(
                        message = staffError ?: "Staff info error",
                        onRetry = { scope.launch { loadStaffData() } }
                    )
                }
            } else if (staffList.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Meet Our Stylists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(staffList) { staff ->
                                StylistCard(staff = staff)
                            }
                        }
                    }
                }
            }

            // 3. Categories Chips/Cards
            if (categoriesError != null) {
                item {
                    InlineRetryCard(
                        message = categoriesError ?: "Categories error",
                        onRetry = { scope.launch { loadCategoriesData() } }
                    )
                }
            } else if (categories.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Service Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // "All" chip
                            item {
                                CategoryChip(
                                    name = "All Services",
                                    imageUrl = null,
                                    isSelected = selectedCategoryId == null,
                                    onClick = { selectedCategoryId = null }
                                )
                            }

                            items(categories) { category ->
                                CategoryChip(
                                    name = category.name,
                                    imageUrl = category.imageUrl,
                                    isSelected = selectedCategoryId == category.id,
                                    onClick = { selectedCategoryId = category.id }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Combos / Packages (if any)
            if (combosError != null) {
                item {
                    InlineRetryCard(
                        message = combosError ?: "Combos error",
                        onRetry = { scope.launch { loadCombosData() } }
                    )
                }
            } else if (combos.isNotEmpty()) {
                item {
                    Text(
                        text = "Combos & Packages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                }

                items(combos) { combo ->
                    ComboPackageCard(
                        combo = combo
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // 4. Services List
            if (servicesError != null) {
                item {
                    InlineRetryCard(
                        message = servicesError ?: "Services error",
                        onRetry = { scope.launch { loadServicesData() } }
                    )
                }
            } else if (filteredServices.isNotEmpty()) {
                item {
                    Text(
                        text = if (selectedCategoryId != null) {
                            val catName = categories.find { it.id == selectedCategoryId }?.name ?: "Services"
                            "$catName (${filteredServices.size})"
                        } else {
                            "All Services (${filteredServices.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                }

                items(filteredServices) { service ->
                    ServiceCard(
                        service = service,
                        lang = lang,
                        onBook = { onBookService(salonId, service.id, null) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 2. Working Hours / Timings
            if (hoursError != null) {
                item {
                    InlineRetryCard(
                        message = hoursError ?: "Hours error",
                        onRetry = { scope.launch { loadHoursData() } }
                    )
                }
            } else if (hours.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("working_hours_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Slate700, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Working Hours",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            hours.forEach { hour ->
                                val isToday = (hour.dayOfWeek == todayDayOfWeek)
                                Surface(
                                    color = if (isToday) GoldContainer.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = hour.dayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isToday) Slate900 else Slate700
                                            )
                                            if (isToday) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = GoldPrimary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Today",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (hour.isClosed) {
                                            Text(
                                                text = "Closed",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                text = "${hour.openTime} – ${hour.closeTime}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = Slate900
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Slate100)
                            }
                        }
                    }
                }
            }

            // 9. Location / Map
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("location_info_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Slate700, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Location & Address",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentSalon.address.ifEmpty { "${currentSalon.area}, ${currentSalon.city}" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate800,
                            lineHeight = 20.sp
                        )

                        Text(
                            text = "${currentSalon.area}, ${currentSalon.city}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // If latitude and longitude exist, show "Open in Google Maps"
                        val lat = currentSalon.latitude
                        val lng = currentSalon.longitude
                        if (lat != null && lng != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_maps_button")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = Slate900, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Google Maps", fontWeight = FontWeight.SemiBold, color = Slate900, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 8. Reviews Section (if any reviews exist)
            if (reviewsError != null) {
                item {
                    InlineRetryCard(
                        message = reviewsError ?: "Reviews error",
                        onRetry = { scope.launch { loadReviewsData() } }
                    )
                }
            } else if (reviews.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Customer Reviews (${reviews.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Rating Summary Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().testTag("salon_reviews_summary_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", currentSalon.ratingAvg),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    repeat(5) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Text(
                                    text = "Based on ${currentSalon.ratingCount} reviews",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        reviews.forEach { review ->
                            ReviewCard(review = review)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CategoryChip(
    name: String,
    imageUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Slate900 else MaterialTheme.colorScheme.surface,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.testTag("category_chip_$name")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape),
                    error = {
                        Icon(
                            Icons.Default.Spa,
                            contentDescription = null,
                            tint = if (isSelected) GoldAccent else Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Default.Spa,
                    contentDescription = null,
                    tint = if (isSelected) GoldAccent else Slate500,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Slate800
            )
        }
    }
}

@Composable
fun StylistCard(staff: StaffMember) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier
            .width(130.dp)
            .testTag("staff_card_${staff.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (staff.photoUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = staff.photoUrl,
                    contentDescription = staff.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    error = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Slate100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = staff.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (staff.ratingAvg > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", staff.ratingAvg),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldPrimary
                    )
                }
            } else {
                Text(
                    text = "Stylist",
                    fontSize = 11.sp,
                    color = Slate500,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ServiceCard(
    service: ServiceItem,
    lang: String,
    onBook: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("service_card_${service.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail image with neutral icon placeholder
            if (!service.imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = service.imageUrl,
                    contentDescription = service.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    error = {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = Slate400, modifier = Modifier.size(24.dp))
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = Slate400, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (service.description.isNotBlank()) {
                    Text(
                        text = service.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Slate500, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = service.formattedDuration,
                        fontSize = 12.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "₹${service.price.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onBook,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.testTag("book_service_${service.id}")
            ) {
                Text("Book", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun ComboPackageCard(
    combo: ComboItem
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("combo_card_${combo.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = combo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (combo.durationMinutes > 0) {
                    Surface(
                        color = Slate100,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = ServiceItem.formatDuration(combo.durationMinutes),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (combo.serviceNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    combo.serviceNames.forEach { serviceName ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = serviceName, fontSize = 12.sp, color = Slate700)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${combo.price.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    if (combo.originalPrice > combo.price) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "₹${combo.originalPrice.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = Slate400
                        )
                    }
                }

                // Online booking of packages is not supported by the backend yet (a booking holds a single
                // service); booking here used to reserve an unrelated service at the wrong price.
                Text(
                    text = "Ask at the salon",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600,
                    modifier = Modifier.testTag("combo_info_${combo.id}")
                )
            }
        }
    }
}

@Composable
fun ReviewCard(review: ReviewItem) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review_card_${review.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Slate800),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = review.customerName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GoldAccent
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = review.customerName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = review.date, fontSize = 11.sp, color = Slate400)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(review.rating) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                    }
                }
            }

            if (review.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = review.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate700,
                    lineHeight = 20.sp
                )
            }

            // Owner Reply
            if (!review.ownerReply.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = Slate700, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Reply from salon", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                            }
                            if (!review.ownerReplyDate.isNullOrBlank()) {
                                Text(text = review.ownerReplyDate, fontSize = 11.sp, color = Slate400)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = review.ownerReply, fontSize = 12.sp, color = Slate600, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InlineRetryCard(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate100,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = Slate600)
            TextButton(onClick = onRetry) {
                Text("Retry", fontWeight = FontWeight.Bold, color = Slate900)
            }
        }
    }
}
