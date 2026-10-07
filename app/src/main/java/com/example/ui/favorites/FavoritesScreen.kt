package com.example.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Salon
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.common.AppStrings
import com.example.ui.discover.SalonCard
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate900
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    salonRepo: SalonRepository,
    authRepo: AuthRepository,
    onSalonSelected: (String) -> Unit,
    onExploreSalons: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val lang by authRepo.currentLanguage.collectAsState()
    val currentUser by authRepo.currentUser.collectAsState()
    val favoriteIds by salonRepo.favoriteIds.collectAsState()

    var allSalons by remember { mutableStateOf<List<Salon>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentUser?.id) {
        isLoading = true
        currentUser?.id?.let { custId ->
            salonRepo.syncFavorites(custId)
        }
        allSalons = salonRepo.getSalons()
        isLoading = false
    }

    val favSalons = remember(allSalons, favoriteIds) {
        allSalons.filter { favoriteIds.contains(it.id) }
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
                        text = AppStrings.get("tab_favorites", lang),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your preferred salons for instant grooming",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
            }
        }
    ) { padding ->
        if (favSalons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved salons yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap the heart on any salon to save it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onExploreSalons,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                    ) {
                        Text("Discover Salons", color = Color.White)
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
                items(favSalons, key = { it.id }) { salon ->
                    SalonCard(
                        salon = salon,
                        isFavorite = true,
                        onFavoriteClick = {
                            scope.launch {
                                salonRepo.toggleFavorite(salon.id, currentUser?.id)
                            }
                        },
                        onClick = { onSalonSelected(salon.id) }
                    )
                }
            }
        }
    }
}
