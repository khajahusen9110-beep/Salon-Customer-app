package com.example.data.repository

import android.content.Context
import com.example.data.model.NotificationItem
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class NotificationRepository(
    private val context: Context,
    private val supabaseClient: SupabaseClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    val unreadCount: StateFlow<Int> = _notifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val _urgentAlert = MutableSharedFlow<NotificationItem>(extraBufferCapacity = 1)
    val urgentAlert: SharedFlow<NotificationItem> = _urgentAlert.asSharedFlow()

    private val seenUrgentIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var pollingJob: Job? = null

    fun startPolling(userId: String) {
        pollingJob?.cancel()
        if (userId.isBlank()) return
        pollingJob = scope.launch {
            while (isActive) {
                fetchNotifications(userId)
                delay(12000) // Poll notifications every 12 seconds
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun fetchNotifications(userId: String) {
        if (userId.isBlank()) {
            _notifications.value = emptyList()
            return
        }
        val res = supabaseClient.getNotifications(userId)
        if (res.isSuccess) {
            val jsonArray = res.getOrNull()
            if (jsonArray != null) {
                val list = mutableListOf<NotificationItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(parseNotification(obj, userId))
                }
                updateNotifications(list)
                return
            }
        }
    }

    private fun updateNotifications(newList: List<NotificationItem>) {
        val existing = _notifications.value
        val existingReadMap = existing.associate { it.id to it.isRead }

        // Preserve locally marked read status
        val merged = newList.map { item ->
            val localIsRead = existingReadMap[item.id]
            if (localIsRead == true) item.copy(isRead = true) else item
        }

        // Check for new incoming urgent notifications (delay_alert or booking_cancelled)
        merged.forEach { item ->
            if (!item.isRead && (item.type == "delay_alert" || item.type == "booking_cancelled")) {
                if (!seenUrgentIds.contains(item.id)) {
                    seenUrgentIds.add(item.id)
                    _urgentAlert.tryEmit(item)
                }
            }
        }

        _notifications.value = merged
    }

    suspend fun markAsRead(notificationId: String) {
        val list = _notifications.value.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        _notifications.value = list
        supabaseClient.markNotificationRead(notificationId)
    }

    suspend fun markAllAsRead(userId: String) {
        val list = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = list
        supabaseClient.markAllNotificationsRead(userId)
    }

    private fun parseNotification(obj: JSONObject, fallbackUserId: String): NotificationItem {
        val id = obj.optString("id", UUID.randomUUID().toString())
        val userId = obj.optString("user_id", fallbackUserId)
        val title = obj.optString("title", "Notification")
        val body = obj.optString("body", "")
        val type = obj.optString("type", "reminder")
        val bookingId = if (obj.has("booking_id") && !obj.isNull("booking_id")) obj.optString("booking_id") else null
        val isRead = obj.optBoolean("is_read", false)
        val createdAt = obj.optString("created_at", "")

        return NotificationItem(
            id = id,
            userId = userId,
            title = title,
            body = body,
            type = type,
            bookingId = bookingId,
            isRead = isRead,
            createdAtIso = createdAt,
            relativeTime = formatRelativeTime(createdAt)
        )
    }

    private fun formatRelativeTime(iso: String): String {
        if (iso.isBlank()) return "Recently"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = sdf.parse(iso.substringBefore("."))
            if (date != null) {
                val diff = System.currentTimeMillis() - date.time
                val minutes = diff / (60 * 1000)
                when {
                    minutes < 2 -> "Just now"
                    minutes < 60 -> "${minutes}m ago"
                    minutes < 1440 -> "${minutes / 60}h ago"
                    else -> "${minutes / 1440}d ago"
                }
            } else "Recently"
        } catch (e: Exception) {
            "Recently"
        }
    }
}
