// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Best effort by design: a fix failure must never hold up the panic itself.
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            val cts = CancellationTokenSource()
            val location = suspendCancellableCoroutine<Location?> { cont ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
                cont.invokeOnCancellation { cts.cancel() }
            }
            location?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.w(TAG, "Location fix unavailable; panic goes out without coordinates", e)
            null
        }
    }

    private companion object {
        const val TAG = "LocationHelper"
    }
}
