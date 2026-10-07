package com.example.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.model.NotificationItem
import com.example.data.repository.AuthRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.SalonRepository
import com.example.ui.auth.*
import com.example.ui.book.BookingFlowScreen
import com.example.ui.bookings.BookingTrackerScreen
import com.example.ui.bookings.MyBookingsScreen
import com.example.ui.common.AppStrings
import com.example.ui.discover.DiscoverScreen
import com.example.ui.favorites.FavoritesScreen
import com.example.ui.navigation.Screen
import com.example.ui.notifications.NotificationsBottomSheet
import com.example.ui.notifications.UrgentNotificationBanner
import com.example.ui.profile.ProfileScreen
import com.example.ui.salon.SalonDetailScreen
import com.example.ui.theme.*

@Composable
fun MainApp(
    salonRepo: SalonRepository,
    authRepo: AuthRepository
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val currentUser by authRepo.currentUser.collectAsState()
    val lang by authRepo.currentLanguage.collectAsState()

    val notificationRepo = remember { NotificationRepository(context, authRepo.supabaseClient) }
    val unreadNotificationsCount by notificationRepo.unreadCount.collectAsState()
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var activeUrgentAlert by remember { mutableStateOf<NotificationItem?>(null) }

    // Start background notification polling and Realtime sync
    LaunchedEffect(currentUser?.id) {
        val uid = currentUser?.id
        if (!uid.isNullOrBlank()) {
            notificationRepo.startPolling(uid)
            salonRepo.syncCustomerBookings(uid)
            salonRepo.syncFavorites(uid)
        } else {
            notificationRepo.stopPolling()
        }
    }

    // Collect urgent alerts for instant Toast/Banner display (delay_alert, booking_cancelled)
    LaunchedEffect(Unit) {
        notificationRepo.urgentAlert.collect { alert ->
            activeUrgentAlert = alert
        }
    }

    // Determine if bottom navigation should be visible
    val showBottomBar = currentRoute in listOf(
        Screen.Discover.route,
        Screen.Bookings.route,
        Screen.Favorites.route,
        Screen.Profile.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    val tabs = listOf(
                        Triple(Screen.Discover.route, AppStrings.get("tab_discover", lang), Icons.Filled.Search to Icons.Outlined.Search),
                        Triple(Screen.Bookings.route, AppStrings.get("tab_bookings", lang), Icons.Filled.DateRange to Icons.Outlined.DateRange),
                        Triple(Screen.Favorites.route, AppStrings.get("tab_favorites", lang), Icons.Filled.Favorite to Icons.Outlined.FavoriteBorder),
                        Triple(Screen.Profile.route, AppStrings.get("tab_profile", lang), Icons.Filled.Person to Icons.Outlined.Person)
                    )

                    tabs.forEach { (route, label, icons) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) icons.first else icons.second,
                                    contentDescription = label,
                                    tint = if (selected) Slate900 else Slate500
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Slate900 else Slate500
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = GoldContainer
                            ),
                            modifier = Modifier.testTag("nav_tab_$route")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Discover.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // Discover Screen (Home)
                composable(Screen.Discover.route) {
                    DiscoverScreen(
                        salonRepo = salonRepo,
                        authRepo = authRepo,
                        unreadNotificationsCount = unreadNotificationsCount,
                        onOpenNotifications = { showNotificationsSheet = true },
                        onSalonSelected = { salonId ->
                            navController.navigate(Screen.SalonDetail.createRoute(salonId))
                        }
                    )
                }

                // Bookings Screen
                composable(Screen.Bookings.route) {
                    MyBookingsScreen(
                        salonRepo = salonRepo,
                        authRepo = authRepo,
                        onBookingClick = { bookingId ->
                            navController.navigate(Screen.BookingTracker.createRoute(bookingId))
                        },
                        onExploreSalons = {
                            navController.navigate(Screen.Discover.route)
                        },
                        onBookAgain = { salonId, serviceId, staffId ->
                            navController.navigate(Screen.BookFlow.createRoute(salonId, serviceId, null, staffId))
                        }
                    )
                }

                // Favorites Screen
                composable(Screen.Favorites.route) {
                    FavoritesScreen(
                        salonRepo = salonRepo,
                        authRepo = authRepo,
                        onSalonSelected = { salonId ->
                            navController.navigate(Screen.SalonDetail.createRoute(salonId))
                        },
                        onExploreSalons = {
                            navController.navigate(Screen.Discover.route)
                        }
                    )
                }

                // Profile Screen
                composable(Screen.Profile.route) {
                    if (currentUser == null) {
                        LoginScreen(
                            authRepo = authRepo,
                            onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                            onLoginSuccess = { /* remains on profile */ }
                        )
                    } else {
                        ProfileScreen(
                            authRepo = authRepo,
                            salonRepo = salonRepo,
                            onSignOut = {
                                navController.navigate(Screen.Discover.route) {
                                    popUpTo(0)
                                }
                            },
                            onNavigateToFavorites = {
                                navController.navigate(Screen.Favorites.route)
                            },
                            onNavigateToBookings = {
                                navController.navigate(Screen.Bookings.route)
                            },
                            onRebook = { salonId, serviceId, staffId ->
                                navController.navigate(Screen.BookFlow.createRoute(salonId, serviceId, null, staffId))
                            }
                        )
                    }
                }

                // Auth: Login Screen
                composable(Screen.Login.route) {
                    LoginScreen(
                        authRepo = authRepo,
                        onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                        onLoginSuccess = { navController.popBackStack() }
                    )
                }

                // Auth: Signup Screen
                composable(Screen.Signup.route) {
                    SignupScreen(
                        authRepo = authRepo,
                        onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                        onSignupSuccess = { navController.navigate(Screen.Onboarding.route) }
                    )
                }

                // Onboarding Profile Screen
                composable(Screen.Onboarding.route) {
                    OnboardingProfileScreen(
                        authRepo = authRepo,
                        onComplete = {
                            navController.navigate(Screen.Discover.route) {
                                popUpTo(0)
                            }
                        }
                    )
                }

                // Salon Detail Screen
                composable(
                    route = Screen.SalonDetail.route,
                    arguments = listOf(navArgument("salonId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val salonId = backStackEntry.arguments?.getString("salonId") ?: ""
                    SalonDetailScreen(
                        salonId = salonId,
                        salonRepo = salonRepo,
                        authRepo = authRepo,
                        onBack = { navController.popBackStack() },
                        onBookService = { sId, srvId, comboId ->
                            navController.navigate(Screen.BookFlow.createRoute(sId, srvId, comboId))
                        }
                    )
                }

                // Booking Flow Screen
                composable(
                    route = Screen.BookFlow.route,
                    arguments = listOf(
                        navArgument("salonId") { type = NavType.StringType },
                        navArgument("serviceId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("comboId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("staffId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val salonId = backStackEntry.arguments?.getString("salonId") ?: ""
                    val serviceId = backStackEntry.arguments?.getString("serviceId")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                    val comboId = backStackEntry.arguments?.getString("comboId")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                    val staffId = backStackEntry.arguments?.getString("staffId")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                    BookingFlowScreen(
                        salonId = salonId,
                        serviceId = serviceId,
                        comboId = comboId,
                        staffId = staffId,
                        salonRepo = salonRepo,
                        authRepo = authRepo,
                        onBack = { navController.popBackStack() },
                        onBookingConfirmed = { bookingId ->
                            navController.navigate(Screen.BookingTracker.createRoute(bookingId)) {
                                popUpTo(Screen.Discover.route)
                            }
                        }
                    )
                }

                // Booking Detail / Live Queue Tracker Screen
                composable(
                    route = Screen.BookingTracker.route,
                    arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
                    BookingTrackerScreen(
                        bookingId = bookingId,
                        salonRepo = salonRepo,
                        authRepo = authRepo,
                        onBack = { navController.popBackStack() },
                        onBookAgain = { salonId, serviceId, staffId ->
                            navController.navigate(Screen.BookFlow.createRoute(salonId, serviceId, null, staffId))
                        }
                    )
                }
            }

            // Realtime Urgent Alert Banner (delay_alert, booking_cancelled)
            UrgentNotificationBanner(
                item = activeUrgentAlert,
                onDismiss = { activeUrgentAlert = null },
                onNavigateToBooking = { bId ->
                    activeUrgentAlert = null
                    navController.navigate(Screen.BookingTracker.createRoute(bId))
                }
            )

            // Notifications Bottom Sheet
            if (showNotificationsSheet) {
                NotificationsBottomSheet(
                    notificationRepo = notificationRepo,
                    userId = currentUser?.id ?: "cust_1",
                    onDismiss = { showNotificationsSheet = false },
                    onNavigateToBooking = { bId ->
                        showNotificationsSheet = false
                        navController.navigate(Screen.BookingTracker.createRoute(bId))
                    }
                )
            }
        }
    }
}
