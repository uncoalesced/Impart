// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// One location fix for a panic, from Android's own LocationManager (T21): no Google Play
// services, no network request from this app. Best effort by design: a missing or slow fix
// never holds up the panic, it only means the panic goes out without coordinates.
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager: LocationManager? = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        val manager = locationManager ?: return null
        if (!granted()) return null
        return try {
            val providers = LocationPolicy.providerOrder(manager.getProviders(true))
            if (providers.isEmpty()) {
                Log.w(TAG, "No location provider enabled; panic goes out without coordinates")
                return null
            }
            val fresh = withTimeoutOrNull(LocationPolicy.FIX_TIMEOUT_MS) { firstFix(manager, providers) }
            (fresh ?: recentLastKnown(manager, providers))?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission withdrawn; panic goes out without coordinates")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Location fix unavailable; panic goes out without coordinates", e)
            null
        }
    }

    // Asks every provider at once; the first non-null fix wins and the rest are cancelled.
    @SuppressLint("MissingPermission")
    private suspend fun firstFix(manager: LocationManager, providers: List<String>): Location? {
        val result = CompletableDeferred<Location?>()
        val pending = AtomicInteger(providers.size)
        val signals = providers.map { provider ->
            CancellationSignal().also { signal ->
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { location ->
                    if (location != null) result.complete(location)
                    else if (pending.decrementAndGet() == 0) result.complete(null)
                }
            }
        }
        return try {
            result.await()
        } finally {
            signals.forEach { it.cancel() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun recentLastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.takeIf { LocationPolicy.lastKnownUsable(it.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos()) }

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "LocationHelper"
    }
}
