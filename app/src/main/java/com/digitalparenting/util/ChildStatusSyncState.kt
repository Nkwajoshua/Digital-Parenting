package com.digitalparenting.util

import android.content.Context

/**
 * Stores local evidence that the active Child identity successfully wrote a
 * heartbeat/status update to Firestore.
 *
 * The evidence is keyed by Child UID so a newly authenticated anonymous Child
 * cannot inherit a previous identity's healthy sync state.
 */
object ChildStatusSyncState {
    private const val PREFS_NAME = "child_status_sync_state"
    private const val KEY_LAST_SUCCESSFUL_SYNC_AT = "last_successful_sync_at"
    private const val KEY_CHILD_UID = "child_uid"

    const val RECENT_SYNC_WINDOW_MILLIS = 2 * 60 * 1000L

    enum class Status {
        NOT_AUTHENTICATED,
        WAITING,
        SYNCED,
        STALE
    }

    fun recordSuccessfulSync(
        context: Context,
        childUid: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (childUid.isBlank()) return

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHILD_UID, childUid)
            .putLong(KEY_LAST_SUCCESSFUL_SYNC_AT, timestamp)
            .apply()
    }

    fun lastSuccessfulSyncAt(context: Context, childUid: String?): Long {
        if (childUid.isNullOrBlank()) return 0L

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recordedUid = prefs.getString(KEY_CHILD_UID, null)
        if (recordedUid != childUid) return 0L

        return prefs.getLong(KEY_LAST_SUCCESSFUL_SYNC_AT, 0L)
    }

    fun status(
        context: Context,
        childUid: String?,
        now: Long = System.currentTimeMillis()
    ): Status {
        if (childUid.isNullOrBlank()) return Status.NOT_AUTHENTICATED

        val lastSyncAt = lastSuccessfulSyncAt(context, childUid)
        if (lastSyncAt <= 0L) return Status.WAITING

        return if (now - lastSyncAt <= RECENT_SYNC_WINDOW_MILLIS) {
            Status.SYNCED
        } else {
            Status.STALE
        }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
