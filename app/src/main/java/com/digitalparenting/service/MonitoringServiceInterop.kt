package com.digitalparenting.service

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference

/**
 * Transitional interop for the legacy MonitoringService.
 *
 * These adapters keep the current service compiling without widening the
 * stabilization PR into a full service rewrite. They should disappear when
 * MonitoringService is decomposed into identity, heartbeat, sync, command,
 * and enforcement components.
 */
internal typealias BuildConfig = com.digitalparenting.BuildConfig

/**
 * MonitoringService currently references the authenticated child UID as a
 * service-level value when syncing a completed usage session. Fail fast rather
 * than ever writing usage under a guessed or placeholder child identity.
 */
internal val MonitoringService.childUid: String
    get() = checkNotNull(FirebaseAuth.getInstance().currentUser?.uid) {
        "Authenticated child UID is required before usage sync"
    }

/**
 * Firestore's Java update(Map<String, Any>) overload does not accept Kotlin's
 * Map<String, Any?>. Omit unknown nullable telemetry fields instead of forcing
 * a bogus value into the child heartbeat document.
 */
internal fun DocumentReference.update(fields: Map<String, Any?>): Task<Void> {
    val nonNullFields = HashMap<String, Any>()
    fields.forEach { (key, value) ->
        if (value != null) nonNullFields[key] = value
    }
    return update(nonNullFields)
}
