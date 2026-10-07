package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    onSalonSelected: (String) -> Unit
) {
    val lang by authRepo.currentLanguage.collectAsState()
    val favoriteIds by salonRepo.favoriteIds.collectAsState()
    val scope = rememberCoroutineScope()

    var salons by remember { mutableStateOf<List<Salon>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("All Cities") }
    var cityMenuExpanded by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("All") } // "All", "Men", "Women", "Unisex"
    var sortBy by remember { mutableStateOf("Rating") } // "Rating", "Nearest"

    val cities = remember(salons) {
        val uniqueCities = salons.map { it.city.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("All Cities") + uniqueCities
    }

    LaunchedEffect(Unit) {
        isLoading = true
        salons = salonRepo.getSalons()
        isLoading = false
    }

    val filteredSalons = remember(salons, searchQuery, selectedCity, selectedTypeFilter, sortBy) {
        var list = salons.filter { it.isVerified && it.isActive }

        if (selectedCity != "All Cities") {
            list = list.filter { it.city.equals(selectedCity, ignoreCase = true) }
        }

        if (selectedTypeFilter != "All") {
            list = list.filter { it.salonType.equals(selectedTypeFilter, ignoreCase = true) }
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
            list = list.sortedBy { it.area }
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

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { cityMenuExpanded = true }
                                    .padding(vertical = 2.dp)
                                    .testTag("city_selector_dropdown"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedCity,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Dropdown",
                                    tint = Slate500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = cityMenuExpanded,
                                onDismissRequest = { cityMenuExpanded = false }
                            ) {
                                cities.forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(city, fontWeight = if (city == selectedCity) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedCity = city
                                            cityMenuExpanded = false
                                        }
                                    )
                                }
                            }
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
                        text = "Try adjusting your search query, city or type filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                        isFavorite = isFav,
                        onFavoriteClick = { salonRepo.toggleFavorite(salon.id) },
                        onClick = { onSalonSelected(salon.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SalonCard(
    salon: Salon,
    isFavorite: Boolean,
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
                        text = salon.salonType.uppercase(),
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

                // Live Availability Teaser Pill
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 12.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldLive)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2 stylists free now",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
                        text = "${salon.area}, ${salon.city}",
                        style = MaterialTheme.typography.bodyMedium,
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
