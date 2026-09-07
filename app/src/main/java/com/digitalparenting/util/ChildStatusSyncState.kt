package com.digitalparenting.util

import android.content.Context

object ChildStatusSyncState {
    private const val PREFS_NAME = "child_status_sync_state"
    private const val KEY_LAST_SUCCESSFUL_SYNC_AT = "last_successful_sync_at"

    const val RECENT_SYNC_WINDOW_MILLIS = 2 * 60 * 1000L

    enum class Status {
        NOT_AUTHENTICATED,
        WAITING,
        SYNCED,
        STALE
    }

    fun recordSuccessfulSync(context: Context, timestamp: Long = System.currentTimeMillis()) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SUCCESSFUL_SYNC_AT, timestamp)
            .apply()
    }

    fun lastSuccessfulSyncAt(context: Context): Long {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SUCCESSFUL_SYNC_AT, 0L)
    }

    fun status(
        context: Context,
        authenticated: Boolean,
        now: Long = System.currentTimeMillis()
    ): Status {
        if (!authenticated) return Status.NOT_AUTHENTICATED

        val lastSyncAt = lastSuccessfulSyncAt(context)
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
