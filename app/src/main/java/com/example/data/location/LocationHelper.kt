package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Device location + city name using the platform LocationManager / Geocoder (no Play Services needed). */
object LocationHelper {

    val PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /** A recent last-known fix, or a fresh one (waits up to 15 s). Null when unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 10 * 60 * 1000 }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent

        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                providers.forEach { p ->
                    runCatching { lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper()) }
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        } ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** City name for a point, e.g. "Bengaluru". Null when the geocoder is unavailable/offline. */
    suspend fun cityFor(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.ENGLISH)
        val address = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }
                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return (address?.locality ?: address?.subAdminArea ?: address?.adminArea)?.trim()?.takeIf { it.isNotBlank() }
    }

    private val ALIASES = listOf(
        setOf("bengaluru", "bangalore"),
        setOf("mumbai", "bombay"),
        setOf("kolkata", "calcutta"),
        setOf("chennai", "madras"),
        setOf("gurugram", "gurgaon"),
        setOf("mysuru", "mysore"),
        setOf("puducherry", "pondicherry"),
        setOf("thiruvananthapuram", "trivandrum"),
        setOf("vadodara", "baroda"),
        setOf("new delhi", "delhi")
    )

    /** Maps the geocoder's name onto the spelling salons use (Bengaluru -> Bangalore), if any match. */
    fun matchCity(geocoded: String, knownCities: List<String>): String? {
        val g = geocoded.trim().lowercase()
        knownCities.firstOrNull { it.trim().lowercase() == g }?.let { return it }
        val group = ALIASES.firstOrNull { g in it } ?: return null
        return knownCities.firstOrNull { it.trim().lowercase() in group }
    }
}
