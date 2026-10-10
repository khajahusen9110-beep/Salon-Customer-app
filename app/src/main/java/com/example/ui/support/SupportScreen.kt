package com.example.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppInfo
import com.example.data.model.SupportTicket
import com.example.data.repository.SalonRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private val CATEGORIES = listOf(
    "booking" to "Booking", "payment" to "Payment", "refund" to "Refund",
    "salon" to "Salon / service", "app" to "App problem", "account" to "My account", "other" to "Other"
)

/**
 * Help & Support: contact the platform team and raise a request (complaint / question).
 * Replies arrive as a notification and are shown here. [bookingId] links the request to a booking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    salonRepo: SalonRepository,
    bookingId: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<AppInfo?>(null) }
    var tickets by remember { mutableStateOf<List<SupportTicket>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var category by remember { mutableStateOf(if (bookingId != null) "booking" else "other") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }
    var sendFailed by remember { mutableStateOf(false) }

    suspend fun reload() {
        loadError = null
        salonRepo.getMySupportTickets()
            .onSuccess { tickets = it }
            .onFailure { loadError = it.message ?: "Could not load your requests" }
    }
    LaunchedEffect(Unit) {
        info = salonRepo.getAppInfo()
        reload()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("support_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
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
            info?.let { ContactCard(it) }

            // New request
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("support_form")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Raise a request", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (bookingId != null) {
                        Text("About your booking", fontSize = 12.sp, color = Slate500)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CATEGORIES.forEach { (key, label) ->
                            FilterChip(
                                selected = category == key,
                                onClick = { category = key },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.testTag("support_cat_$key")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it.take(120) },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("support_subject"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(2000) },
                        label = { Text("Describe the problem") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("support_message"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    sendResult?.let {
                        Text(it, fontSize = 12.sp, color = if (sendFailed) MaterialTheme.colorScheme.error else EmeraldLive,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            sending = true
                            sendResult = null
                            scope.launch {
                                salonRepo.createSupportTicket(category, subject, message, bookingId)
                                    .onSuccess {
                                        sendFailed = false
                                        sendResult = "Sent. Our team will reply here and in your notifications."
                                        subject = ""
                                        message = ""
                                        reload()
                                    }
                                    .onFailure {
                                        sendFailed = true
                                        sendResult = it.message ?: "Could not send. Please try again."
                                    }
                                sending = false
                            }
                        },
                        enabled = !sending && subject.trim().length >= 3 && message.trim().length >= 5,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("support_send"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                    ) {
                        if (sending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Send", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // My requests
            Text("My requests", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            when {
                loadError != null -> Text(loadError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                tickets == null -> CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(24.dp))
                tickets!!.isEmpty() -> Text("You have not raised any requests.", color = Slate500, fontSize = 13.sp)
                else -> tickets!!.forEach { TicketCard(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Support contact in a dialog (used on the login screen, where the full Help screen is not available). */
@Composable
fun SupportContactDialog(info: AppInfo?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Need help?", fontWeight = FontWeight.Bold) },
        text = {
            when {
                info == null -> CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(24.dp))
                !info.hasContact -> Text("Please try again in a few minutes. If the OTP still does not arrive, check that the number is correct.")
                else -> ContactCard(info)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ContactCard(info: AppInfo) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("support_contact")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Contact us", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            info.supportHours?.let { Text(it, fontSize = 12.sp, color = Slate500) }
            if (!info.hasContact) {
                Text("Raise a request below and our team will get back to you.", fontSize = 13.sp, color = Slate600)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                info.supportPhone?.let { phone ->
                    OutlinedButton(onClick = { context.open("tel:${phone.filter { it.isDigit() || it == '+' }}") }) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Call", fontSize = 12.sp)
                    }
                }
                info.supportWhatsapp?.let { wa ->
                    OutlinedButton(onClick = { context.open("https://wa.me/${wa.filter { it.isDigit() }}") }) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("WhatsApp", fontSize = 12.sp)
                    }
                }
                info.supportEmail?.let { mail ->
                    OutlinedButton(onClick = { context.open("mailto:$mail") }) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Email", fontSize = 12.sp)
                    }
                }
            }
            if (info.termsUrl != null || info.privacyUrl != null) {
                Row {
                    info.termsUrl?.let { url -> TextButton(onClick = { context.open(url) }) { Text("Terms", fontSize = 12.sp) } }
                    info.privacyUrl?.let { url -> TextButton(onClick = { context.open(url) }) { Text("Privacy policy", fontSize = 12.sp) } }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(t: SupportTicket) {
    val (label, color) = when (t.status) {
        "resolved" -> "Resolved" to EmeraldLive
        "closed" -> "Closed" to Slate500
        "in_progress" -> "In progress" to GoldPrimary
        else -> "Open" to Color(0xFF2563EB)
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("ticket_${t.ticketNo}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("#${t.ticketNo} · ${t.subject}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(t.message, fontSize = 12.sp, color = Slate600, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            t.adminReply?.let {
                Surface(color = Slate100, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Support team", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        Text(it, fontSize = 13.sp, color = Slate900)
                    }
                }
            }
        }
    }
}

private fun Context.open(uri: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // No app to handle it (e.g. no email app installed): nothing to do.
    }
}
