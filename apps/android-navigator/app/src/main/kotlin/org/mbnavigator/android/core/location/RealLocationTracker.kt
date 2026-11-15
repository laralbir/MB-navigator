package org.mbnavigator.android.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mbnavigator.android.core.model.GeoPoint
import java.time.Instant

/**
 * Proveedor de telemetría GPS real en hardware Android utilizando el chip GPS del dispositivo.
 */
class RealLocationTracker(
    private val context: Context
) : LocationTracker {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    override val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    override val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private val _hasGpsFix = MutableStateFlow(false)
    val hasGpsFix: StateFlow<Boolean> = _hasGpsFix.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _hasGpsFix.value = true
            _currentLocation.value = GeoPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                elevationMeters = if (location.hasAltitude()) location.altitude else null,
                timestamp = Instant.ofEpochMilli(location.time),
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                bearingDegrees = if (location.hasBearing()) location.bearing else 0f,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _hasGpsFix.value = false
            }
        }
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun startTracking() {
        if (!hasPermission() || locationManager == null) return

        _isTrackingActive.value = true

        // 1. Obtener última posición conocida de forma inmediata
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val bestLast = lastGps ?: lastNet

        if (bestLast != null) {
            _currentLocation.value = GeoPoint(
                latitude = bestLast.latitude,
                longitude = bestLast.longitude,
                elevationMeters = if (bestLast.hasAltitude()) bestLast.altitude else null,
                timestamp = Instant.ofEpochMilli(bestLast.time),
                speedMps = if (bestLast.hasSpeed()) bestLast.speed else 0f,
                bearingDegrees = if (bestLast.hasBearing()) bestLast.bearing else 0f,
                accuracyMeters = if (bestLast.hasAccuracy()) bestLast.accuracy else null
            )
        }

        // 2. Solicitar actualizaciones continuas (cada 1 segundo o 1 metro)
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    locationListener
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    5.0f,
                    locationListener
                )
            }
        } catch (_: SecurityException) {}
    }

    override fun stopTracking() {
        _isTrackingActive.value = false
        _hasGpsFix.value = false
        locationManager?.removeUpdates(locationListener)
    }
}
