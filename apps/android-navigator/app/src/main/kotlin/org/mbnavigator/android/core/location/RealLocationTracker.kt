package org.mbnavigator.android.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mbnavigator.android.core.model.GeoPoint
import java.time.Instant

enum class GnssConstellation(val label: String, val flag: String) {
    GPS("GPS (EE.UU.)", "🇺🇸"),
    GLONASS("GLONASS (RU)", "🇷🇺"),
    GALILEO("Galileo (UE)", "🇪🇺"),
    BEIDOU("BeiDou (CN)", "🇨🇳"),
    QZSS("QZSS (JP)", "🇯🇵"),
    SBAS("SBAS (Corrección)", "🛰️"),
    UNKNOWN("Otro GNSS", "📡")
}

data class SatelliteInfo(
    val svid: Int,
    val constellation: GnssConstellation,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean
)

data class GnssStatusSummary(
    val satellites: List<SatelliteInfo> = emptyList(),
    val totalVisible: Int = 0,
    val usedInFixCount: Int = 0,
    val averageCn0DbHz: Float = 0f,
    val gpsProviderEnabled: Boolean = false
)

/**
 * Proveedor de telemetría GPS y diagnóstico de satélites GNSS en hardware Android.
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

    private val _gnssSummary = MutableStateFlow(
        GnssStatusSummary(
            gpsProviderEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        )
    )
    val gnssSummary: StateFlow<GnssStatusSummary> = _gnssSummary.asStateFlow()

    @Volatile
    private var lastAccurateFixMs: Long = 0L

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val count = status.satelliteCount
            val list = ArrayList<SatelliteInfo>(count)
            var usedCount = 0
            var snrSum = 0f

            for (i in 0 until count) {
                val constType = when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_GPS -> GnssConstellation.GPS
                    GnssStatus.CONSTELLATION_GLONASS -> GnssConstellation.GLONASS
                    GnssStatus.CONSTELLATION_GALILEO -> GnssConstellation.GALILEO
                    GnssStatus.CONSTELLATION_BEIDOU -> GnssConstellation.BEIDOU
                    GnssStatus.CONSTELLATION_QZSS -> GnssConstellation.QZSS
                    GnssStatus.CONSTELLATION_SBAS -> GnssConstellation.SBAS
                    else -> GnssConstellation.UNKNOWN
                }
                val used = status.usedInFix(i)
                if (used) usedCount++
                val cn0 = status.getCn0DbHz(i)
                snrSum += cn0

                list.add(
                    SatelliteInfo(
                        svid = status.getSvid(i),
                        constellation = constType,
                        cn0DbHz = cn0,
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        usedInFix = used,
                        hasEphemeris = status.hasEphemerisData(i)
                    )
                )
            }

            list.sortWith(compareByDescending<SatelliteInfo> { it.usedInFix }.thenByDescending { it.cn0DbHz })
            _gnssSummary.value = GnssStatusSummary(
                satellites = list,
                totalVisible = count,
                usedInFixCount = usedCount,
                averageCn0DbHz = if (count > 0) snrSum / count else 0f,
                gpsProviderEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            )
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val provider = location.provider ?: LocationManager.GPS_PROVIDER
            val isPreciseSource = provider != LocationManager.NETWORK_PROVIDER &&
                (!location.hasAccuracy() || location.accuracy <= 35f)
            if (isPreciseSource) {
                _hasGpsFix.value = true
            }

            val candidate = GeoPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                elevationMeters = if (location.hasAltitude()) location.altitude else null,
                timestamp = Instant.ofEpochMilli(location.time),
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                bearingDegrees = if (location.hasBearing()) location.bearing else 0f,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
            )

            val stabilized = LocationStabilizer.stabilize(
                previousAnchored = _currentLocation.value,
                lastAccurateFixMs = lastAccurateFixMs,
                candidate = candidate,
                provider = provider,
                nowMs = System.currentTimeMillis()
            )
            lastAccurateFixMs = stabilized.lastAccurateFixMs
            _currentLocation.value = stabilized.location
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
        val lm = locationManager
        if (!hasPermission() || lm == null) return

        _isTrackingActive.value = true

        // 1. Obtener última posición precisa conocida (priorizando Fused/GPS sobre Red)
        val lastFused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER) }.getOrNull()
        } else {
            null
        }
        val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val bestLast = lastGps ?: lastFused ?: lastNet

        if (bestLast != null) {
            val stabilized = LocationStabilizer.stabilize(
                previousAnchored = _currentLocation.value,
                lastAccurateFixMs = lastAccurateFixMs,
                candidate = GeoPoint(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    elevationMeters = if (bestLast.hasAltitude()) bestLast.altitude else null,
                    timestamp = Instant.ofEpochMilli(bestLast.time),
                    speedMps = if (bestLast.hasSpeed()) bestLast.speed else 0f,
                    bearingDegrees = if (bestLast.hasBearing()) bestLast.bearing else 0f,
                    accuracyMeters = if (bestLast.hasAccuracy()) bestLast.accuracy else null
                ),
                provider = bestLast.provider ?: LocationManager.GPS_PROVIDER,
                nowMs = System.currentTimeMillis()
            )
            lastAccurateFixMs = stabilized.lastAccurateFixMs
            _currentLocation.value = stabilized.location
        }

        // 2. Solicitar ubicación de alta precisión (FUSED HIGH_ACCURACY en Android 12+ y GPS hardware)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER)
            ) {
                val request = LocationRequest.Builder(1000L)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(500L)
                    .setMinUpdateDistanceMeters(0f)
                    .build()
                lm.requestLocationUpdates(
                    LocationManager.FUSED_PROVIDER,
                    request,
                    context.mainExecutor,
                    locationListener
                )
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    locationListener
                )
            }
            lm.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {}
    }

    override fun stopTracking() {
        _isTrackingActive.value = false
        _hasGpsFix.value = false
        try {
            locationManager?.unregisterGnssStatusCallback(gnssCallback)
        } catch (_: Exception) {}
        locationManager?.removeUpdates(locationListener)
    }

    companion object {
        @Volatile
        private var instance: RealLocationTracker? = null

        fun getInstance(context: Context): RealLocationTracker {
            return instance ?: synchronized(this) {
                instance ?: RealLocationTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
