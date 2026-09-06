package com.digitalparenting.service

import com.google.firebase.auth.FirebaseAuth

/**
 * Transitional interop for the legacy MonitoringService.
 *
 * These adapters keep the current service compiling without widening the
 * stabilization PR into a full service rewrite. They should disappear when
 * MonitoringService is decomposed into identity, heartbeat, sync, command,
 * and enforcement components.
 */
internal object BuildConfig {
    // Mirrors app/build.gradle for this stabilization branch. The service
    // refactor will replace this with an injected app-version provider.
    const val VERSION_NAME = "1.0"
}

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
 * The legacy service builds Firestore update payloads with nullable telemetry
 * values (notably battery percentage), while the Java Firestore update(Map)
 * overload expects non-null Any values from Kotlin. This same-package overload
 * is a temporary compatibility bridge: nullable telemetry is omitted instead
 * of writing a fabricated value.
 */
internal fun mapOf(vararg pairs: Pair<String, Any?>): MutableMap<String, Any> {
    val result = linkedMapOf<String, Any>()
    pairs.forEach { (key, value) ->
        if (value != null) result[key] = value
    }
    return result
}
