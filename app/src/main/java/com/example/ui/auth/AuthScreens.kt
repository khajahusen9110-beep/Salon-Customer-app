package com.example.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.location.LocationHelper
import com.example.data.model.UserLocation
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun t(lang: String, en: String, hi: String) = if (lang == "hi") hi else en

/** Mobile number -> OTP. A new number gets an account automatically after the OTP is verified. */
@Composable
fun PhoneLoginScreen(authRepo: AuthRepository) {
    val lang by authRepo.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(otpSent, resendIn) {
        if (otpSent && resendIn > 0) {
            delay(1000)
            resendIn -= 1
        }
    }

    fun sendOtp() {
        if (!Regex("^[6-9]\\d{9}$").matches(mobile)) {
            error = t(lang, "Enter a valid 10-digit mobile number", "सही 10 अंकों का मोबाइल नंबर डालें")
            return
        }
        isLoading = true
        error = null
        scope.launch {
            val res = authRepo.sendOtp(mobile)
            isLoading = false
            res.onSuccess { otpSent = true; otp = ""; resendIn = 30 }
            res.onFailure { error = it.message }
        }
    }

    fun verify() {
        if (otp.length != 6) {
            error = t(lang, "Enter the 6-digit OTP", "6 अंकों का OTP डालें")
            return
        }
        isLoading = true
        error = null
        scope.launch {
            val res = authRepo.verifyOtp(mobile, otp)
            isLoading = false
            res.onFailure { error = it.message }
            // On success MainApp moves on by itself (currentUser is set).
        }
    }

    AuthScaffold(
        icon = Icons.Default.ContentCut,
        title = "QFree Salon",
        subtitle = if (!otpSent) t(lang, "Log in with your mobile number", "अपने मोबाइल नंबर से लॉग इन करें")
        else t(lang, "Enter the OTP sent to +91 $mobile", "+91 $mobile पर भेजा गया OTP डालें"),
        error = error
    ) {
        if (!otpSent) {
            OutlinedTextField(
                value = mobile,
                onValueChange = { v -> mobile = v.filter { it.isDigit() }.take(10); error = null },
                label = { Text(t(lang, "Mobile number", "मोबाइल नंबर")) },
                prefix = { Text("+91 ", fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Slate500) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("login_phone_input"),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(t(lang, "Send OTP", "OTP भेजें"), isLoading, Modifier.testTag("send_otp_button")) { sendOtp() }
            Text(
                text = t(lang, "We'll send a 6-digit code by SMS.", "हम SMS से 6 अंकों का कोड भेजेंगे।"),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            OutlinedTextField(
                value = otp,
                onValueChange = { v ->
                    otp = v.filter { it.isDigit() }.take(6); error = null
                },
                label = { Text("OTP") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Slate500) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("login_otp_input"),
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(letterSpacing = 6.sp, fontSize = 20.sp)
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(t(lang, "Verify & continue", "वेरिफाई करें"), isLoading, Modifier.testTag("verify_otp_button")) { verify() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { otpSent = false; otp = ""; error = null }, enabled = !isLoading) {
                    Text(t(lang, "Change number", "नंबर बदलें"), color = Slate700)
                }
                TextButton(onClick = { sendOtp() }, enabled = !isLoading && resendIn == 0) {
                    Text(
                        if (resendIn > 0) t(lang, "Resend in ${resendIn}s", "${resendIn}s में दोबारा भेजें")
                        else t(lang, "Resend OTP", "OTP दोबारा भेजें"),
                        color = if (resendIn > 0) Slate400 else GoldPrimary
                    )
                }
            }
        }
    }
}

/** Shown once after the first OTP login: the customer's name completes the account. */
@Composable
fun NameScreen(authRepo: AuthRepository) {
    val lang by authRepo.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold(
        icon = Icons.Default.Person,
        title = t(lang, "What's your name?", "आपका नाम क्या है?"),
        subtitle = t(lang, "The salon will see this name on your booking.", "सैलून आपकी बुकिंग पर यही नाम देखेगा।"),
        error = error
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60); error = null },
            label = { Text(t(lang, "Full name", "पूरा नाम")) },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Slate500) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("name_input"),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(t(lang, "Create account", "अकाउंट बनाएं"), isLoading, Modifier.testTag("save_name_button")) {
            if (name.trim().length < 2) {
                error = t(lang, "Please enter your name", "कृपया अपना नाम डालें")
                return@PrimaryButton
            }
            isLoading = true
            scope.launch {
                val res = authRepo.saveName(name)
                isLoading = false
                res.onFailure { error = it.message }
            }
        }
    }
}

/**
 * Picks the customer's city: asks for location permission first and detects the city; if the
 * customer says no (or GPS is off) they choose a city from the list of cities that have salons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    authRepo: AuthRepository,
    salonRepo: SalonRepository,
    onBack: (() -> Unit)? = null,
    onDone: () -> Unit = {}
) {
    val lang by authRepo.currentLanguage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detecting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }
    var cities by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    var citiesError by remember { mutableStateOf<String?>(null) }
    var citiesReload by remember { mutableIntStateOf(0) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(citiesReload) {
        salonRepo.loadCities()
            .onSuccess { cities = it; citiesError = null }
            .onFailure { citiesError = it.message }
    }

    fun detect() {
        detecting = true
        message = null
        scope.launch {
            if (!LocationHelper.isLocationEnabled(context)) {
                detecting = false
                message = t(lang, "Your phone's location is off. Turn it on and try again, or choose your city.",
                    "फ़ोन की लोकेशन बंद है। चालू करके दोबारा कोशिश करें या शहर चुनें।")
                showManual = true
                return@launch
            }
            val loc = LocationHelper.currentLocation(context)
            if (loc == null) {
                detecting = false
                message = t(lang, "Couldn't find your location. Please choose your city.",
                    "आपकी लोकेशन नहीं मिली। कृपया शहर चुनें।")
                showManual = true
                return@launch
            }
            val known = cities ?: salonRepo.loadCities().getOrNull().orEmpty().also { cities = it }
            val geocoded = LocationHelper.cityFor(context, loc.latitude, loc.longitude)
            val city = geocoded?.let { LocationHelper.matchCity(it, known.map { c -> c.first }) } ?: geocoded
                ?: t(lang, "Near you", "आपके पास")
            authRepo.setLocation(UserLocation(city, loc.latitude, loc.longitude))
            detecting = false
            onDone()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            detect()
        } else {
            showManual = true
            message = t(lang, "No problem — choose your city below.", "कोई बात नहीं — नीचे से अपना शहर चुनें।")
        }
    }

    // Ask for permission straight away the first time this screen opens.
    LaunchedEffect(Unit) {
        if (!askedOnce) {
            askedOnce = true
            if (LocationHelper.hasPermission(context)) detect()
            else permissionLauncher.launch(LocationHelper.PERMISSIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(t(lang, "Change location", "लोकेशन बदलें"), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (onBack == null) 48.dp else 16.dp))
            BrandBadge(Icons.Default.LocationOn)
            Spacer(Modifier.height(16.dp))
            Text(
                text = t(lang, "Find salons near you", "अपने पास के सैलून खोजें"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = t(lang, "Allow location to see the nearest salons first.", "सबसे पास के सैलून पहले देखने के लिए लोकेशन की अनुमति दें।"),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            PrimaryButton(
                text = t(lang, "Use my current location", "मेरी मौजूदा लोकेशन इस्तेमाल करें"),
                isLoading = detecting,
                modifier = Modifier.testTag("use_location_button")
            ) {
                if (LocationHelper.hasPermission(context)) detect() else permissionLauncher.launch(LocationHelper.PERMISSIONS)
            }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Slate600, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp))
            }

            if (!showManual) {
                TextButton(onClick = { showManual = true }, modifier = Modifier.padding(top = 8.dp).testTag("choose_city_manually")) {
                    Text(t(lang, "Choose city manually", "शहर खुद चुनें"), color = GoldPrimary, fontWeight = FontWeight.Medium)
                }
            } else {
                Spacer(Modifier.height(20.dp))
                Text(
                    t(lang, "Cities with salons", "सैलून वाले शहर"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                when {
                    citiesError != null && cities == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(citiesError ?: "", color = Slate600, textAlign = TextAlign.Center)
                        TextButton(onClick = { citiesReload++ }) { Text(t(lang, "Retry", "दोबारा कोशिश करें"), color = GoldPrimary) }
                    }
                    cities == null -> CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.padding(16.dp))
                    cities!!.isEmpty() -> Text(
                        t(lang, "No salons are live yet. Please check back soon.", "अभी कोई सैलून लाइव नहीं है। जल्द ही दोबारा देखें।"),
                        color = Slate600, textAlign = TextAlign.Center
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(cities!!, key = { it.first }) { (city, count) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        authRepo.setLocation(UserLocation(city))
                                        onDone()
                                    }
                                    .testTag("city_option_$city")
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationCity, contentDescription = null, tint = Slate500)
                                    Spacer(Modifier.width(12.dp))
                                    Text(city, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(
                                        t(lang, if (count == 1) "1 salon" else "$count salons", "$count सैलून"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AuthScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    error: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(32.dp))
            BrandBadge(icon)
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )
            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BrandBadge(icon: ImageVector) {
    Box(
        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Slate900),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, isLoading: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Slate900)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
