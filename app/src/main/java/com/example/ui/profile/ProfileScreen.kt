package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.RebookOption
import com.example.data.model.SalonCredit
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.common.AppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authRepo: AuthRepository,
    salonRepo: SalonRepository,
    onSignOut: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToBookings: () -> Unit = {},
    onRebook: (salonId: String, serviceId: String?, staffId: String?) -> Unit = { _, _, _ -> }
) {
    val lang by authRepo.currentLanguage.collectAsState()
    val currentUser by authRepo.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    var rebookOptions by remember { mutableStateOf<List<RebookOption>>(emptyList()) }
    var creditBalances by remember { mutableStateOf<List<SalonCredit>>(emptyList()) }
    var isLoadingRebook by remember { mutableStateOf(true) }

    var showBackendDialog by remember { mutableStateOf(false) }
    var anonKeyInput by remember { mutableStateOf(authRepo.supabaseClient.anonKey) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingRebook = true
        rebookOptions = salonRepo.getRebookOptions()
        creditBalances = salonRepo.getMyCreditBalance()
        isLoadingRebook = false
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
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = AppStrings.get("tab_profile", lang),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Header Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("user_header_card")
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Slate900),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (currentUser?.fullName?.take(1)?.ifBlank { "U" } ?: "U").uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUser?.fullName?.ifBlank { "Customer" } ?: "Customer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = GoldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = (currentUser?.role ?: "customer").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        if (!currentUser?.email.isNullOrBlank()) {
                            Text(
                                text = currentUser?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate500
                            )
                        }
                        if (!currentUser?.phone.isNullOrBlank()) {
                            Text(
                                text = currentUser?.phone ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            // PART 4: Book Again (One-tap Rebook) Section
            if (rebookOptions.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Replay, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Book Again",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Speed Rebooking",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate500
                        )
                    }
                    Text(
                        text = "One-tap rebook with your trusted stylist & service",
                        fontSize = 12.sp,
                        color = Slate500,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    rebookOptions.forEach { opt ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .testTag("rebook_card_${opt.salonId}"),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (opt.salonPhotoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = opt.salonPhotoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.salonName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "${opt.serviceName} with ${opt.stylistName}",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                    Text(
                                        text = "₹${opt.price.toInt()} • Last: ${opt.lastVisitDate}",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onRebook(opt.salonId, opt.serviceId, opt.stylistId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("one_tap_rebook_btn_${opt.salonId}")
                                ) {
                                    Text("Book Again", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                                }
                            }
                        }
                    }
                }
            }

            // Quick Hub: Favorites & Bookings Shortcuts
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("profile_hub_shortcuts_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Hub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedCard(
                            onClick = onNavigateToFavorites,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_favorites_shortcut"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = Slate50)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Saved Salons", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("View your favorites", fontSize = 11.sp, color = Slate500)
                            }
                        }

                        OutlinedCard(
                            onClick = onNavigateToBookings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_bookings_shortcut"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = Slate50)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("My Bookings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Active & past visits", fontSize = 11.sp, color = Slate500)
                            }
                        }
                    }
                }
            }

            // PART 4 & 5: My Credits Section (Only shown when balance != 0)
            if (creditBalances.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().testTag("my_credits_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "My Credits",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Late-Start Guarantee",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Credits are auto-credited when a salon begins your service past the scheduled time.",
                            fontSize = 12.sp,
                            color = Slate500
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        creditBalances.forEach { credit ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate100,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("credit_item_${credit.salonId}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = credit.salonName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = credit.reason,
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                    }
                                    Text(
                                        text = "₹${credit.balance.toInt()} Credit",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }

                        // Explainer notice regarding credit redemption
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Slate400, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Credit redemption will apply directly during checkout at billing.",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }

            // Language Preference Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("language_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.get("language", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedCard(
                            onClick = { authRepo.setLanguage("en") },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("lang_en_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (lang == "en") Slate900 else Color.White
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "English",
                                    fontWeight = FontWeight.Bold,
                                    color = if (lang == "en") Color.White else Slate900
                                )
                            }
                        }

                        OutlinedCard(
                            onClick = { authRepo.setLanguage("hi") },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("lang_hi_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (lang == "hi") Slate900 else Color.White
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "हिंदी (Hindi)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (lang == "hi") Color.White else Slate900
                                )
                            }
                        }
                    }
                }
            }

            // Supabase Backend Integration Details Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("backend_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Supabase Backend",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            color = EmeraldContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ap-south-1",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Project: zmdjtcjbwimiiphjnvcd\nRegion: ap-south-1 (Mumbai)",
                        fontSize = 12.sp,
                        color = Slate600
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showBackendDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("backend_config_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure Anon Key / Test Connection")
                    }
                }
            }

            // Sign Out Button
            Button(
                onClick = {
                    scope.launch {
                        authRepo.signOut()
                        onSignOut()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("signout_button")
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.get("logout", lang),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Supabase Backend Config Dialog
        if (showBackendDialog) {
            AlertDialog(
                onDismissRequest = { showBackendDialog = false },
                title = { Text("Supabase API Config", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter the anon public key for Supabase project zmdjtcjbwimiiphjnvcd to query live PostgreSQL tables directly:",
                            fontSize = 13.sp,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = anonKeyInput,
                            onValueChange = { anonKeyInput = it },
                            placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6...") },
                            label = { Text("Anon Key (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (connectionStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = connectionStatus ?: "",
                                fontSize = 12.sp,
                                color = if (connectionStatus?.contains("Success") == true) Color(0xFF059669) else Slate600
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            authRepo.supabaseClient.anonKey = anonKeyInput.trim()
                            isTestingConnection = true
                            scope.launch {
                                val res = authRepo.supabaseClient.getSalons()
                                isTestingConnection = false
                                connectionStatus = if (res.isSuccess) {
                                    "Connected! Received ${res.getOrNull()?.length()} salons."
                                } else {
                                    "Status: ${res.exceptionOrNull()?.message ?: "Check key"}"
                                }
                            }
                        }
                    ) {
                        Text(if (isTestingConnection) "Testing..." else "Save & Test")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBackendDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

