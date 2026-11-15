package org.mbnavigator.android.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mbnavigator.android.core.model.GeoPoint

/**
 * Abstracción del proveedor de telemetría GPS para permitir desacoplamiento total en TDD.
 */
interface LocationTracker {
    val currentLocation: StateFlow<GeoPoint?>
    val isTrackingActive: StateFlow<Boolean>
    fun startTracking()
    fun stopTracking()
}

/**
 * Implementación simulada de telemetría para pruebas unitarias y emulación sin hardware físico.
 */
class MockLocationTracker(
    private val simulatedPoints: List<GeoPoint> = emptyList()
) : LocationTracker {

    private val _currentLocation = MutableStateFlow<GeoPoint?>(simulatedPoints.firstOrNull())
    override val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    override val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private var currentIndex = 0

    override fun startTracking() {
        _isTrackingActive.value = true
    }

    override fun stopTracking() {
        _isTrackingActive.value = false
    }

    /**
     * Emite manualmente una coordenada para verificar la reacción del sistema en tests.
     */
    fun emitLocation(point: GeoPoint) {
        _currentLocation.value = point
    }

    /**
     * Avanza al siguiente punto del recorrido simulado.
     */
    fun advanceToNextSimulatedPoint(): GeoPoint? {
        if (simulatedPoints.isEmpty()) return null
        currentIndex = (currentIndex + 1) % simulatedPoints.size
        val point = simulatedPoints[currentIndex]
        _currentLocation.value = point
        return point
    }
}
