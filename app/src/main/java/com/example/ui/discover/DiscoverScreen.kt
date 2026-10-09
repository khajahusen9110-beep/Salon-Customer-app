package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Amenity
import com.example.data.model.Salon
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.common.AppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    salonRepo: SalonRepository,
    authRepo: AuthRepository,
    unreadNotificationsCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onChangeLocation: () -> Unit = {},
    onSalonSelected: (String) -> Unit
) {
    val lang by authRepo.currentLanguage.collectAsState()
    val location by authRepo.location.collectAsState()
    val favoriteIds by salonRepo.favoriteIds.collectAsState()
    val scope = rememberCoroutineScope()

    var salons by remember { mutableStateOf<List<Salon>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("All") } // "All", "Men", "Women", "Unisex"
    // With a GPS point the list comes back nearest-first; otherwise rating is the useful default.
    var sortBy by remember(location) { mutableStateOf(if (location?.latitude != null) "Nearest" else "Rating") }

    // Facility filter (AC, Free WiFi, Parking...): the server returns only salons that have ALL of them.
    var amenities by remember { mutableStateOf<List<Amenity>>(emptyList()) }
    var amenityFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAmenitySheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { amenities = salonRepo.loadAmenities() }

    LaunchedEffect(reloadKey, location, amenityFilter) {
        val loc = location ?: return@LaunchedEffect
        isLoading = true
        val res = salonRepo.loadNearbySalons(loc, amenityFilter.toList())
        res.onSuccess { salons = it; loadError = null }
        res.onFailure { loadError = it.message }
        isLoading = false
    }

    val filteredSalons = remember(salons, searchQuery, selectedTypeFilter, sortBy) {
        var list = salons.filter { it.isVerified && it.isActive }

        if (selectedTypeFilter != "All") {
            // Men / Women also include unisex places; "Unisex" shows only unisex ones.
            val wanted = selectedTypeFilter.lowercase()
            list = list.filter { it.salonType == wanted || (wanted != "unisex" && it.salonType == "unisex") }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                it.area.lowercase().contains(q) ||
                it.city.lowercase().contains(q)
            }
        }

        if (sortBy == "Rating") {
            list = list.sortedByDescending { it.ratingAvg }
        } else {
            list = list.sortedWith(compareBy(nullsLast()) { it.distanceKm })
        }

        list
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Row: Location & Brand
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "LOCATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChangeLocation() }
                                .padding(vertical = 2.dp)
                                .testTag("city_selector_dropdown"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (location?.latitude != null) Icons.Default.MyLocation else Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = location?.city ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Change location",
                                tint = Slate500,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Verified badge indicator
                        Surface(
                            color = GoldContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ZERO WAIT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Bell icon with unread count badge
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = onOpenNotifications,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Slate100)
                                    .testTag("notifications_bell_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Slate900,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (unreadNotificationsCount > 0) {
                                Surface(
                                    color = Color(0xFFDC2626),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .testTag("notifications_unread_badge")
                                ) {
                                    Text(
                                        text = if (unreadNotificationsCount > 9) "9+" else unreadNotificationsCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(AppStrings.get("search_placeholder", lang), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Slate500)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate500)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Slate200,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = Slate50
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("salon_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter & Sort Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterTypes = listOf(
                        "All" to AppStrings.get("filter_all", lang),
                        "Men" to AppStrings.get("filter_men", lang),
                        "Women" to AppStrings.get("filter_women", lang),
                        "Unisex" to AppStrings.get("filter_unisex", lang)
                    )

                    filterTypes.forEach { (typeKey, label) ->
                        FilterChip(
                            selected = selectedTypeFilter == typeKey,
                            onClick = { selectedTypeFilter = typeKey },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Slate900,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedTypeFilter == typeKey,
                                borderColor = Slate200
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    VerticalDivider(modifier = Modifier.height(20.dp), color = Slate300)
                    Spacer(modifier = Modifier.width(4.dp))

                    // Facilities filter
                    FilterChip(
                        selected = amenityFilter.isNotEmpty(),
                        onClick = { showAmenitySheet = true },
                        label = {
                            Text(if (amenityFilter.isEmpty()) "Facilities" else "Facilities (${amenityFilter.size})", fontSize = 12.sp)
                        },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Slate900,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = amenityFilter.isNotEmpty(), borderColor = Slate200),
                        modifier = Modifier.testTag("filter_facilities")
                    )
                    // Quick chips for the highlighted facilities (e.g. AC, Free WiFi)
                    amenities.filter { it.highlight }.forEach { a ->
                        val on = a.id in amenityFilter
                        FilterChip(
                            selected = on,
                            onClick = { amenityFilter = toggleAmenity(amenityFilter, a, amenities) },
                            label = { Text("${a.icon} ${a.name}", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = Color.White),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = Slate200)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    VerticalDivider(modifier = Modifier.height(20.dp), color = Slate300)
                    Spacer(modifier = Modifier.width(4.dp))

                    // Sort By Rating / Nearest
                    AssistChip(
                        onClick = {
                            sortBy = if (sortBy == "Rating") "Nearest" else "Rating"
                        },
                        label = {
                            Text(
                                if (sortBy == "Rating") AppStrings.get("sort_rating", lang) else AppStrings.get("sort_nearest", lang),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (sortBy == "Rating") Icons.Default.Star else Icons.Default.NearMe,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Slate100),
                        border = null
                    )
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldPrimary)
            }
        } else if (loadError != null && salons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = loadError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { reloadKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.testTag("discover_retry")
                    ) { Text("Retry") }
                }
            }
        } else if (filteredSalons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No salons found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (salons.isEmpty() && amenityFilter.isNotEmpty()) "No salons nearby have all the facilities you picked. Remove a filter to see more."
                        else if (salons.isEmpty()) "No salons near ${location?.city ?: "you"} yet. Try another city."
                        else "Try adjusting your search or type filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (salons.isEmpty() && amenityFilter.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { amenityFilter = emptySet() }, modifier = Modifier.testTag("discover_clear_facilities")) {
                            Text("Clear facility filters", color = GoldPrimary)
                        }
                    } else if (salons.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onChangeLocation, modifier = Modifier.testTag("discover_change_city")) {
                            Text("Change location", color = GoldPrimary)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Confident Hero Strip
                    Surface(
                        color = Slate900,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.get("hero_tagline", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Book instantly • Live queue tracker • Real-time slot",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate300,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                color = EmeraldLive.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldLive)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldLive
                                    )
                                }
                            }
                        }
                    }
                }

                items(filteredSalons, key = { it.id }) { salon ->
                    val isFav = favoriteIds.contains(salon.id)
                    SalonCard(
                        salon = salon,
                        highlightAmenities = amenities.filter { it.highlight && it.id in salon.amenityIds },
                        isFavorite = isFav,
                        onFavoriteClick = { salonRepo.toggleFavorite(salon.id) },
                        onClick = { onSalonSelected(salon.id) }
                    )
                }
            }
        }
    }

    if (showAmenitySheet) {
        AmenityFilterSheet(
            amenities = amenities,
            selected = amenityFilter,
            onApply = { amenityFilter = it; showAmenitySheet = false },
            onDismiss = { showAmenitySheet = false }
        )
    }
}

@Composable
fun SalonCard(
    salon: Salon,
    isFavorite: Boolean,
    highlightAmenities: List<Amenity> = emptyList(),
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("salon_card_${salon.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Photo Header with Cover Photo and Floating Badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = salon.coverPhotoUrl,
                    contentDescription = salon.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Salon Type Badge (Men / Women / Unisex)
                Surface(
                    color = Slate900.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = salon.typeLabel.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Favorite Toggle Button
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(12.dp)
                        .size(36.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .clickable { onFavoriteClick() }
                        .testTag("fav_button_${salon.id}")
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else Slate700,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Live stylist availability is shown on the salon page (real queue data).
            }

            // Card Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = salon.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (salon.isVerified) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = listOf(salon.area, salon.city).filter { it.isNotBlank() }.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    salon.distanceKm?.let { km ->
                        Text(
                            text = " • " + if (km < 1) "${(km * 1000).toInt()} m" else String.format(java.util.Locale.US, "%.1f km", km),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                    }
                }

                if (highlightAmenities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = highlightAmenities.joinToString("  ·  ") { "${it.icon} ${it.name}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Star Rating & Review Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GoldContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", salon.ratingAvg),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = GoldPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${salon.ratingCount} reviews)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                    }

                    Text(
                        text = "View Services →",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldPrimary
                    )
                }
            }
        }
    }
}

/** Adds/removes a facility; for one-of groups (AC / Non-AC / Partly AC) keeps only the new choice. */
private fun toggleAmenity(current: Set<String>, a: Amenity, all: List<Amenity>): Set<String> {
    if (a.id in current) return current - a.id
    val sameGroup = a.exclusiveGroup?.let { g -> all.filter { it.exclusiveGroup == g }.map { it.id }.toSet() } ?: emptySet()
    return (current - sameGroup) + a.id
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AmenityFilterSheet(
    amenities: List<Amenity>,
    selected: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Filter by facilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Show salons that have all of these.", style = MaterialTheme.typography.bodySmall, color = Slate500)
            Spacer(modifier = Modifier.height(12.dp))
            amenities.groupBy { it.groupName }.forEach { (group, items) ->
                Text(group, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { a ->
                        val on = a.id in picked
                        FilterChip(
                            selected = on,
                            onClick = { picked = toggleAmenity(picked, a, amenities) },
                            label = { Text("${a.icon} ${a.name}", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = Color.White)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { picked = emptySet() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(
                    onClick = { onApply(picked) },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                    modifier = Modifier.weight(1f).testTag("apply_facility_filter")
                ) { Text("Show salons", color = Color.White) }
            }
        }
    }
}
