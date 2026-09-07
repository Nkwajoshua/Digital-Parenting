package com.digitalparenting.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for local protection-incident persistence and retrieval.
 */
@RunWith(AndroidJUnit4::class)
class IncidentLoggingTest {
    private lateinit var database: AppDatabase
    private lateinit var incidentDao: ProtectionIncidentDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        incidentDao = database.protectionIncidentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun highRiskIncidentIsPersisted() = runBlocking {
        incidentDao.insert(
            ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk Behavior",
                message = "Risky usage pattern detected for com.example.app",
                appPackage = "com.example.app",
                timestamp = System.currentTimeMillis()
            )
        )

        val incidents = incidentDao.getRecentIncidents(10)

        assertTrue(incidents.isNotEmpty())
        assertEquals("high_risk", incidents[0].type)
        assertEquals("com.example.app", incidents[0].appPackage)
        assertEquals("High Risk Behavior", incidents[0].title)
    }

    @Test
    fun appBlockedIncidentIsPersisted() = runBlocking {
        incidentDao.insert(
            ProtectionIncidentEntity(
                type = "app_blocked",
                title = "App Blocked",
                message = "com.example.social was blocked due to limit or risk level",
                appPackage = "com.example.social",
                timestamp = System.currentTimeMillis()
            )
        )

        val incidents = incidentDao.getRecentIncidents(10)

        assertTrue(incidents.isNotEmpty())
        assertEquals("app_blocked", incidents[0].type)
        assertEquals("com.example.social", incidents[0].appPackage)
    }

    @Test
    fun accessibilityDisabledIncidentAllowsNullPackage() = runBlocking {
        incidentDao.insert(
            ProtectionIncidentEntity(
                type = "accessibility_disabled",
                title = "Protection Weakened",
                message = "Accessibility service is disabled. Re-enable it to continue protection.",
                appPackage = null,
                timestamp = System.currentTimeMillis()
            )
        )

        val incidents = incidentDao.getRecentIncidents(10)

        assertTrue(incidents.isNotEmpty())
        assertEquals("accessibility_disabled", incidents[0].type)
        assertNull(incidents[0].appPackage)
    }

    @Test
    fun overlayMissingIncidentIsPersisted() = runBlocking {
        incidentDao.insert(
            ProtectionIncidentEntity(
                type = "overlay_missing",
                title = "Overlay Permission Missing",
                message = "Overlay permission is required for app blocking.",
                appPackage = null,
                timestamp = System.currentTimeMillis()
            )
        )

        val incidents = incidentDao.getRecentIncidents(10)

        assertTrue(incidents.isNotEmpty())
        assertEquals("overlay_missing", incidents[0].type)
    }

    @Test
    fun incidentsAreReturnedMostRecentFirst() = runBlocking {
        val baseTime = System.currentTimeMillis()
        val incidents = listOf(
            ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk Behavior",
                message = "Risk detected",
                appPackage = "com.example.app1",
                timestamp = baseTime
            ),
            ProtectionIncidentEntity(
                type = "app_blocked",
                title = "App Blocked",
                message = "App blocked",
                appPackage = "com.example.app2",
                timestamp = baseTime + 1_000
            ),
            ProtectionIncidentEntity(
                type = "accessibility_disabled",
                title = "Protection Weakened",
                message = "Accessibility disabled",
                appPackage = null,
                timestamp = baseTime + 2_000
            )
        )

        incidents.forEach { incidentDao.insert(it) }
        val retrieved = incidentDao.getRecentIncidents(10)

        assertEquals(3, retrieved.size)
        assertEquals("accessibility_disabled", retrieved[0].type)
        assertEquals("app_blocked", retrieved[1].type)
        assertEquals("high_risk", retrieved[2].type)
    }

    @Test
    fun recentIncidentQueryHonorsLimit() = runBlocking {
        val baseTime = System.currentTimeMillis()
        repeat(15) { index ->
            incidentDao.insert(
                ProtectionIncidentEntity(
                    type = "high_risk",
                    title = "High Risk",
                    message = "Risk $index",
                    appPackage = "com.example.app$index",
                    timestamp = baseTime + index * 1_000L
                )
            )
        }

        val incidents = incidentDao.getRecentIncidents(10)

        assertEquals(10, incidents.size)
    }

    @Test
    fun incidentTimestampIsPreserved() = runBlocking {
        val testTime = System.currentTimeMillis()
        incidentDao.insert(
            ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk",
                message = "Risk detected",
                appPackage = "com.example.app",
                timestamp = testTime
            )
        )

        val retrieved = incidentDao.getRecentIncidents(10).first()

        assertEquals(testTime, retrieved.timestamp)
    }
}
