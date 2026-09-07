package com.digitalparenting

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitalparenting.util.ChildStatusSyncState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChildStatusSyncStateIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        ChildStatusSyncState.clear(context)
    }

    @After
    fun tearDown() {
        ChildStatusSyncState.clear(context)
    }

    @Test
    fun missingAuthenticationIsReportedSeparately() {
        assertEquals(
            ChildStatusSyncState.Status.NOT_AUTHENTICATED,
            ChildStatusSyncState.status(context, null)
        )
    }

    @Test
    fun authenticatedChildWithoutSuccessfulWriteIsWaiting() {
        assertEquals(
            ChildStatusSyncState.Status.WAITING,
            ChildStatusSyncState.status(context, "child-a")
        )
    }

    @Test
    fun recentSuccessfulWriteIsSyncedForSameChild() {
        val now = 10_000L
        ChildStatusSyncState.recordSuccessfulSync(context, "child-a", now)

        assertEquals(
            ChildStatusSyncState.Status.SYNCED,
            ChildStatusSyncState.status(context, "child-a", now + 1_000L)
        )
        assertEquals(now, ChildStatusSyncState.lastSuccessfulSyncAt(context, "child-a"))
    }

    @Test
    fun successfulWriteDoesNotLeakAcrossChildIdentities() {
        ChildStatusSyncState.recordSuccessfulSync(context, "child-a", 10_000L)

        assertEquals(
            ChildStatusSyncState.Status.WAITING,
            ChildStatusSyncState.status(context, "child-b", 11_000L)
        )
        assertEquals(0L, ChildStatusSyncState.lastSuccessfulSyncAt(context, "child-b"))
    }

    @Test
    fun oldSuccessfulWriteBecomesStale() {
        val now = 1_000_000L
        val oldTimestamp = now - ChildStatusSyncState.RECENT_SYNC_WINDOW_MILLIS - 1L
        ChildStatusSyncState.recordSuccessfulSync(context, "child-a", oldTimestamp)

        assertEquals(
            ChildStatusSyncState.Status.STALE,
            ChildStatusSyncState.status(context, "child-a", now)
        )
        assertTrue(ChildStatusSyncState.lastSuccessfulSyncAt(context, "child-a") > 0L)
    }
}
