package com.hodowo.data

import android.content.Context
import com.google.ar.core.ArCoreApk

/**
 * Asks ARCore whether this phone supports AR (#239). It only reads the state:
 * installing or updating Google Play Services for AR is done by the AR screen
 * with `ArCoreApk.requestInstall` (#240), when the user chooses AR.
 */
class ArCoreAvailabilityChecker(context: Context) : ArAvailabilityChecker {

    private val appContext = context.applicationContext

    override fun check(): ArCheck =
        when (ArCoreApk.getInstance().checkAvailability(appContext)) {
            ArCoreApk.Availability.UNKNOWN_CHECKING -> ArCheck.CHECKING
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArCheck.UNSUPPORTED
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArCheck.NOT_INSTALLED
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArCheck.APK_TOO_OLD
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCheck.INSTALLED
            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ArCheck.UNKNOWN
        }
}

/** The one [ArAvailabilityRepository] of the app, so the answer is checked once and shared. */
object ArSupportProvider {

    @Volatile
    private var repository: ArAvailabilityRepository? = null

    fun get(context: Context): ArAvailabilityRepository =
        repository ?: synchronized(this) {
            repository ?: ArAvailabilityRepository(ArCoreAvailabilityChecker(context)).also { repository = it }
        }
}
