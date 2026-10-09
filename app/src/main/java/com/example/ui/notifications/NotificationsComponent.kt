package com.example.ui.notifications

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationItem
import com.example.data.repository.NotificationRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsBottomSheet(
    notificationRepo: NotificationRepository,
    userId: String,
    onDismiss: () -> Unit,
    onNavigateToBooking: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val notifications by notificationRepo.notifications.collectAsState()
    val unreadCount by notificationRepo.unreadCount.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("notifications_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFDC2626),
                            shape = CircleShape
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    notificationRepo.markAllAsRead(userId)
                                }
                            },
                            modifier = Modifier.testTag("mark_all_read_btn")
                        ) {
                            Text("Mark all read", fontSize = 12.sp, color = GoldPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                    }
                }
            }

            HorizontalDivider(color = Slate200)

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No notifications yet",
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Text(
                            text = "We'll notify you on turn updates & live queue alerts",
                            fontSize = 12.sp,
                            color = Slate500,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            onClick = {
                                scope.launch {
                                    notificationRepo.markAsRead(item.id)
                                }
                                if (!item.bookingId.isNullOrBlank()) {
                                    onDismiss()
                                    onNavigateToBooking(item.bookingId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit
) {
    val isUrgent = item.type == "delay_alert" || item.type == "booking_cancelled"
    val containerColor = when (item.type) {
        "delay_alert" -> if (!item.isRead) Color(0xFFFFFBEB) else Color.White
        "booking_cancelled" -> if (!item.isRead) Color(0xFFFEF2F2) else Color.White
        "late_credit" -> if (!item.isRead) Color(0xFFF0FDF4) else Color.White
        else -> if (!item.isRead) Color(0xFFF8FAFC) else Color.White
    }

    val border = if (item.type == "delay_alert" && !item.isRead) {
        BorderStroke(1.5.dp, GoldPrimary)
    } else if (item.type == "booking_cancelled" && !item.isRead) {
        BorderStroke(1.5.dp, Color(0xFFDC2626))
    } else {
        BorderStroke(1.dp, if (!item.isRead) Slate300 else Slate200)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("notification_card_${item.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!item.isRead) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon Badge
            NotificationIconBadge(type = item.type)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isUrgent) {
                            Surface(
                                color = if (item.type == "delay_alert") GoldContainer else Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = if (item.type == "delay_alert") "URGENT" else "CANCELLED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (item.type == "delay_alert") Color(0xFF92400E) else Color(0xFFDC2626),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = item.title,
                            fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.relativeTime,
                            fontSize = 11.sp,
                            color = Slate500
                        )
                        if (!item.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isUrgent) Color(0xFFDC2626) else GoldPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.body,
                    fontSize = 12.sp,
                    color = Slate700,
                    lineHeight = 16.sp
                )

                if (!item.bookingId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "View Booking Details →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationIconBadge(type: String) {
    val (icon: ImageVector, bg: Color, tint: Color) = when (type) {
        "delay_alert" -> Triple(Icons.Default.Warning, Color(0xFFFEF3C7), Color(0xFFD97706))
        "booking_cancelled" -> Triple(Icons.Default.Cancel, Color(0xFFFEE2E2), Color(0xFFDC2626))
        "booking_rescheduled" -> Triple(Icons.Default.Event, Color(0xFFEEF2FF), Color(0xFF4F46E5))
        "late_credit" -> Triple(Icons.Default.AccountBalanceWallet, Color(0xFFD1FAE5), Color(0xFF059669))
        "announcement" -> Triple(Icons.Default.Campaign, Color(0xFFFCE7F3), Color(0xFFBE185D))
        "support_reply" -> Triple(Icons.Default.SupportAgent, Color(0xFFEDE9FE), Color(0xFF7C3AED))
        else -> Triple(Icons.Default.AccessTime, Color(0xFFE0F2FE), Color(0xFF0284C7)) // reminder
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Immediate Floating Urgent Alert Banner
 * Slides down instantly when delay_alert or booking_cancelled arrives while user is in the app.
 */
@Composable
fun UrgentNotificationBanner(
    item: NotificationItem?,
    onDismiss: () -> Unit,
    onNavigateToBooking: (String) -> Unit
) {
    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        if (item != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("urgent_alert_banner"),
                shape = RoundedCornerShape(16.dp),
                color = if (item.type == "booking_cancelled") Color(0xFF991B1B) else Slate900,
                shadowElevation = 8.dp,
                border = BorderStroke(1.5.dp, if (item.type == "booking_cancelled") Color(0xFFF87171) else GoldPrimary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (item.type == "booking_cancelled") Color(0xFFDC2626) else GoldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.type == "booking_cancelled") Icons.Default.Cancel else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = item.body,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!item.bookingId.isNullOrBlank()) {
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToBooking(item.bookingId)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.type == "booking_cancelled") Color.White else GoldPrimary,
                                contentColor = if (item.type == "booking_cancelled") Color(0xFF991B1B) else Slate900
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp).testTag("urgent_banner_action_btn")
                        ) {
                            Text("Track", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
