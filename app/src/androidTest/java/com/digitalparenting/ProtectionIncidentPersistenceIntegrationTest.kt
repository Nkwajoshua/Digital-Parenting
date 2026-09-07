package com.digitalparenting

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.ProtectionIncidentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration coverage for the Room persistence boundary used by ChildIncidentRecorder.
 *
 * These tests intentionally exercise the database/DAO contract only. They do not claim
 * to cover MonitoringService, cloud notifications, or a Parent-facing UI.
 */
@RunWith(AndroidJUnit4::class)
class ProtectionIncidentPersistenceIntegrationTest {
    private lateinit var database: AppDatabase
    private val baseTime = System.currentTimeMillis()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun highRiskIncidentRoundTripsThroughRoom() = runBlocking {
        val dao = database.protectionIncidentDao()
        dao.insert(
            ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk Behavior",
                message = "Risky usage pattern detected for com.social.app",
                appPackage = "com.social.app",
                timestamp = baseTime
            )
        )

        val incidents = dao.getRecentIncidents(10)

        assertEquals(1, incidents.size)
        assertEquals("high_risk", incidents[0].type)
        assertEquals("com.social.app", incidents[0].appPackage)
    }

    @Test
    fun appBlockedIncidentRoundTripsThroughRoom() = runBlocking {
        val dao = database.protectionIncidentDao()
        dao.insert(
            ProtectionIncidentEntity(
                type = "app_blocked",
                title = "App Blocked",
                message = "com.gaming.app exceeded usage limits",
                appPackage = "com.gaming.app",
                timestamp = baseTime
            )
        )

        val incident = dao.getRecentIncidents(10).first()

        assertEquals("app_blocked", incident.type)
        assertEquals("com.gaming.app", incident.appPackage)
    }

    @Test
    fun permissionIncidentsCanBeStoredWithoutPackage() = runBlocking {
        val dao = database.protectionIncidentDao()
        dao.insert(
            ProtectionIncidentEntity(
                type = "accessibility_disabled",
                title = "Protection Weakened",
                message = "Accessibility service is disabled.",
                appPackage = null,
                timestamp = baseTime
            )
        )

        val incident = dao.getRecentIncidents(10).first()

        assertEquals("accessibility_disabled", incident.type)
        assertNull(incident.appPackage)
    }

    @Test
    fun overlayIncidentCanBeRetrievedByRecentQuery() = runBlocking {
        val dao = database.protectionIncidentDao()
        dao.insert(
            ProtectionIncidentEntity(
                type = "overlay_missing",
                title = "Overlay Permission Missing",
                message = "Overlay permission is required for app blocking.",
                appPackage = null,
                timestamp = baseTime
            )
        )

        val incidents = dao.getRecentIncidents(10)

        assertTrue(incidents.any { it.type == "overlay_missing" })
    }

    @Test
    fun multipleIncidentTypesPreserveReverseChronologicalOrder() = runBlocking {
        val dao = database.protectionIncidentDao()
        dao.insert(
            ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk Behavior",
                message = "Risky pattern detected",
                appPackage = "com.app1",
                timestamp = baseTime
            )
        )
        dao.insert(
            ProtectionIncidentEntity(
                type = "app_blocked",
                title = "App Blocked",
                message = "App exceeded limits",
                appPackage = "com.app2",
                timestamp = baseTime + 1_000
            )
        )
        dao.insert(
            ProtectionIncidentEntity(
                type = "accessibility_disabled",
                title = "Protection Weakened",
                message = "Accessibility disabled",
                appPackage = null,
                timestamp = baseTime + 2_000
            )
        )

        val incidents = dao.getRecentIncidents(10)

        assertEquals(3, incidents.size)
        assertEquals("accessibility_disabled", incidents[0].type)
        assertEquals("app_blocked", incidents[1].type)
        assertEquals("high_risk", incidents[2].type)
    }

    @Test
    fun allPersistedFieldsSurviveRoundTrip() = runBlocking {
        val dao = database.protectionIncidentDao()
        val expected = ProtectionIncidentEntity(
            type = "app_blocked",
            title = "Blocked",
            message = "Usage limit exceeded",
            appPackage = "com.game",
            timestamp = baseTime,
            resolved = false
        )

        dao.insert(expected)
        val actual = dao.getRecentIncidents(10).first()

        assertEquals(expected.type, actual.type)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.message, actual.message)
        assertEquals(expected.appPackage, actual.appPackage)
        assertEquals(expected.timestamp, actual.timestamp)
        assertEquals(expected.resolved, actual.resolved)
    }
}
