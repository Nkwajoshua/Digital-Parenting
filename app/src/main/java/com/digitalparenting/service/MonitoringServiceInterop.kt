package com.digitalparenting.service

import com.google.firebase.auth.FirebaseAuth

/**
 * Transitional identity bridge for the legacy MonitoringService.
 *
 * Usage sync still references the authenticated Child UID as a service-level
 * value. This bridge should disappear when session sync is extracted into its
 * own component.
 */
internal val MonitoringService.childUid: String
    get() = checkNotNull(FirebaseAuth.getInstance().currentUser?.uid) {
        "Authenticated child UID is required before usage sync"
    }
