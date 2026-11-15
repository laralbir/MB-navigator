package org.mbnavigator.android.auto

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Servicio CarAppService registrado para proyectar MB Navigator en Android Auto
 * (y unidades de infoentretenimiento / adaptadores CarPlay-Android Auto).
 */
class MBNavigatorCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (isDebuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return MBNavigatorCarSession()
    }
}
