package com.digitalparenting.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/**
 * Instrumented test to verify incident logging works correctly.
 * Tests that incidents are properly recorded to the database and can be retrieved.
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
    fun testLogHighRiskIncident() = runBlocking {
        // Arrange
        val incident = ProtectionIncidentEntity(
            type = "high_risk",
            title = "High Risk Behavior",
            message = "Risky usage pattern detected for com.example.app",
            appPackage = "com.example.app",
            timestamp = System.currentTimeMillis()
        )

        // Act
        incidentDao.insert(incident)
        val incidents = incidentDao.getRecentIncidents(10)

        // Assert
        assert(incidents.isNotEmpty())
        assert(incidents[0].type == "high_risk")
        assert(incidents[0].appPackage == "com.example.app")
        assert(incidents[0].title == "High Risk Behavior")
        println("✅ High-risk incident logged successfully")
    }

    @Test
    fun testLogAppBlockedIncident() = runBlocking {
        // Arrange
        val incident = ProtectionIncidentEntity(
            type = "app_blocked",
            title = "App Blocked",
            message = "com.example.social was blocked due to limit or risk level",
            appPackage = "com.example.social",
            timestamp = System.currentTimeMillis()
        )

        // Act
        incidentDao.insert(incident)
        val incidents = incidentDao.getRecentIncidents(10)

        // Assert
        assert(incidents.isNotEmpty())
        assert(incidents[0].type == "app_blocked")
        assert(incidents[0].appPackage == "com.example.social")
        println("✅ App-blocked incident logged successfully")
    }

    @Test
    fun testLogAccessibilityDisabledIncident() = runBlocking {
        // Arrange
        val incident = ProtectionIncidentEntity(
            type = "accessibility_disabled",
            title = "Protection Weakened",
            message = "Accessibility service is disabled. Re-enable it to continue protection.",
            appPackage = null,
            timestamp = System.currentTimeMillis()
        )

        // Act
        incidentDao.insert(incident)
        val incidents = incidentDao.getRecentIncidents(10)

        // Assert
        assert(incidents.isNotEmpty())
        assert(incidents[0].type == "accessibility_disabled")
        assert(incidents[0].appPackage == null)
        println("✅ Accessibility-disabled incident logged successfully")
    }

    @Test
    fun testLogOverlayMissingIncident() = runBlocking {
        // Arrange
        val incident = ProtectionIncidentEntity(
            type = "overlay_missing",
            title = "Overlay Permission Missing",
            message = "Overlay permission is required for app blocking.",
            appPackage = null,
            timestamp = System.currentTimeMillis()
        )

        // Act
        incidentDao.insert(incident)
        val incidents = incidentDao.getRecentIncidents(10)

        // Assert
        assert(incidents.isNotEmpty())
        assert(incidents[0].type == "overlay_missing")
        println("✅ Overlay-missing incident logged successfully")
    }

    @Test
    fun testMultipleIncidentsRetrieved() = runBlocking {
        // Arrange - create multiple incidents
        val incidents = listOf(
            ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk Behavior",
                message = "Risk detected",
                appPackage = "com.example.app1",
                timestamp = System.currentTimeMillis()
            ),
            ProtectionIncidentEntity(
                type = "app_blocked",
                title = "App Blocked",
                message = "App blocked",
                appPackage = "com.example.app2",
                timestamp = System.currentTimeMillis() + 1000
            ),
            ProtectionIncidentEntity(
                type = "accessibility_disabled",
                title = "Protection Weakened",
                message = "Accessibility disabled",
                appPackage = null,
                timestamp = System.currentTimeMillis() + 2000
            )
        )

        // Act
        incidents.forEach { incidentDao.insert(it) }
        val retrieved = incidentDao.getRecentIncidents(10)

        // Assert
        assert(retrieved.size == 3)
        assert(retrieved[0].type == "accessibility_disabled") // Most recent first
        assert(retrieved[1].type == "app_blocked")
        assert(retrieved[2].type == "high_risk")
        println("✅ Multiple incidents retrieved in correct order (most recent first)")
    }

    @Test
    fun testRecentIncidentsLimitedToTen() = runBlocking {
        // Arrange - create 15 incidents
        repeat(15) { index ->
            val incident = ProtectionIncidentEntity(
                type = "high_risk",
                title = "High Risk",
                message = "Risk $index",
                appPackage = "com.example.app$index",
                timestamp = System.currentTimeMillis() + index * 1000
            )
            incidentDao.insert(incident)
        }

        // Act
        val incidents = incidentDao.getRecentIncidents(10)

        // Assert
        assert(incidents.size == 10)
        println("✅ Recent incidents correctly limited to 10 most recent")
    }

    @Test
    fun testIncidentTimestampPreserved() = runBlocking {
        // Arrange
        val testTime = System.currentTimeMillis()
        val incident = ProtectionIncidentEntity(
            type = "high_risk",
            title = "High Risk",
            message = "Risk detected",
            appPackage = "com.example.app",
            timestamp = testTime
        )

        // Act
        incidentDao.insert(incident)
        val retrieved = incidentDao.getRecentIncidents(10)[0]

        // Assert
        assert(retrieved.timestamp == testTime)
        println("✅ Incident timestamp correctly preserved")
    }
}
