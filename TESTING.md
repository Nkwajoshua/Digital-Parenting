# Digital Parenting - Incident Logging Test Suite

## Overview

This document describes the comprehensive test suite for the incident logging system in the Digital Parenting app. The tests verify that:

1. **Incident logging works** at all critical alert points in MonitoringService
2. **Database operations** correctly store and retrieve incidents
3. **Protection Center** correctly displays incidents to parents
4. **End-to-end flow** is intact from service detection through UI display

## Test Architecture

### Test Layers

```
┌─────────────────────────────────────────────────────┐
│  Integration Tests (IncidentFlowIntegrationTest)    │
│  Simulate real-world incident flows                 │
└─────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│  ViewModel Tests (ProtectionCenterViewModelTest)    │
│  Verify UI logic correctly loads and displays data  │
└─────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│  Database Tests (IncidentLoggingTest)               │
│  Verify data access layer persistence               │
└─────────────────────────────────────────────────────┘
```

## Test Files

### 1. IncidentLoggingTest.kt
**Location:** `app/src/androidTest/java/com/digitalparenting/data/local/`

**Purpose:** Database layer tests verifying ProtectionIncidentDao functionality

**Tests:**
- `testLogHighRiskIncident()` - High-risk incidents are saved and retrievable
- `testLogAppBlockedIncident()` - App-blocked incidents are saved correctly
- `testLogAccessibilityDisabledIncident()` - Accessibility incidents are logged
- `testLogOverlayMissingIncident()` - Overlay permission incidents are logged
- `testMultipleIncidentsRetrieved()` - Multiple incidents ordered by recency (newest first)
- `testRecentIncidentsLimitedToTen()` - DAO correctly limits to 10 most recent
- `testIncidentTimestampPreserved()` - Timestamps are accurately maintained

**What It Validates:**
✅ Database operations are atomic and correct
✅ Incident types are properly categorized
✅ Timestamp ordering works as expected
✅ Data integrity through save/retrieve cycle

### 2. ProtectionCenterViewModelTest.kt
**Location:** `app/src/androidTest/java/com/digitalparenting/ui/viewmodel/`

**Purpose:** ViewModel layer tests verifying incident loading and status calculation

**Tests:**
- `testViewModelLoadsIncidents()` - ViewModel correctly loads incidents from database
- `testViewModelCalculatesWarningStatus()` - Status calculation integrates permission and incident data
- `testViewModelLogIncident()` - ViewModel can programmatically log incidents
- `testViewModelDisplaysMultipleIncidents()` - Multiple incidents are surfaced to UI
- `testViewModelClearsOldIncidents()` - Incidents older than 7 days are removed
- `testViewModelRespectsIncidentLimit()` - UI never shows more than 10 incidents

**What It Validates:**
✅ UI receives correct data from database
✅ Status indicators compute properly
✅ Cleanup routines work
✅ Memory efficiency (limited incident list)

### 3. IncidentFlowIntegrationTest.kt
**Location:** `app/src/androidTest/java/com/digitalparenting/`

**Purpose:** End-to-end integration tests simulating real-world incident flows

**Tests:**
- `testEndToEndHighRiskIncidentFlow()` - High-risk detection → storage → UI display
- `testEndToEndAppBlockedIncidentFlow()` - App blocking → storage → parent notification
- `testEndToEndAccessibilityWarningFlow()` - Permission loss → persistence → alert
- `testEndToEndOverlayWarningFlow()` - Overlay missing → recovery flow
- `testMultipleIncidentsFlowSequentially()` - Multiple events create correct timeline
- `testIncidentDataIntegrity()` - All fields preserved through full cycle

**What It Validates:**
✅ Data flows correctly from service → database → UI
✅ Timeline ordering is correct for parent viewing
✅ Status transitions work properly
✅ No data loss in the complete pipeline

## Running the Tests

### Full Test Suite

```bash
cd /workspaces/Digital-Parenting
./gradlew connectedAndroidTest --no-daemon
```

**Requirements:**
- Connected Android device or running emulator
- API level 21+ (min SDK level for the app)
- USB debugging enabled (for device testing)

### Run Specific Test Class

```bash
# Database tests only
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.data.local.IncidentLoggingTest

# ViewModel tests only
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.ui.viewmodel.ProtectionCenterViewModelTest

# Integration tests only
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.IncidentFlowIntegrationTest
```

### Run Single Test Method

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.data.local.IncidentLoggingTest#testLogHighRiskIncident
```

## Setting Up a Test Environment

### Option 1: Android Emulator

```bash
# List available emulators
emulator -list-avds

# Start emulator (replace "Pixel_4_API_30" with your AVD name)
emulator -avd Pixel_4_API_30 -no-snapshot-load &

# Wait for boot, then run tests
./gradlew connectedAndroidTest --no-daemon
```

### Option 2: Connected Device

1. Connect USB cable to Android device
2. Enable Developer Options (Settings → About Phone → tap Build Number 7x)
3. Enable USB Debugging (Settings → Developer Options → USB Debugging)
4. Grant USB debugging permission when prompted on device
5. Verify connection: `adb devices`
6. Run tests: `./gradlew connectedAndroidTest --no-daemon`

## Test Coverage Map

### Incident Types Covered

| Incident Type | Created By | Test Class | Test Method |
|---|---|---|---|
| `high_risk` | Risk prediction ≥80 | IncidentLoggingTest | testLogHighRiskIncident |
| `app_blocked` | BlockMode.BLOCKED | IncidentLoggingTest | testLogAppBlockedIncident |
| `accessibility_disabled` | Watchdog verification | IncidentLoggingTest | testLogAccessibilityDisabledIncident |
| `overlay_missing` | Watchdog verification | IncidentLoggingTest | testLogOverlayMissingIncident |

### Service Integration Points Tested

| Service Method | Incident Logged | Integration Test |
|---|---|---|
| handleAppSwitch() - high risk | high_risk | testEndToEndHighRiskIncidentFlow |
| handleAppSwitch() - blocked | app_blocked | testEndToEndAppBlockedIncidentFlow |
| verifyProtectionState() - accessibility | accessibility_disabled | testEndToEndAccessibilityWarningFlow |
| verifyProtectionState() - overlay | overlay_missing | testEndToEndOverlayWarningFlow |

### UI Display Tested

| Component | Verified In |
|---|---|
| Incident timeline (RecyclerView) | ProtectionCenterViewModelTest, IncidentFlowIntegrationTest |
| Status indicators (CRITICAL/WARNING/HEALTHY) | ProtectionCenterViewModelTest |
| Recent incidents limit (10 max) | IncidentLoggingTest, ProtectionCenterViewModelTest |
| Chronological ordering (newest first) | IncidentLoggingTest, IncidentFlowIntegrationTest |

## Expected Test Results

When all tests pass, you should see output like:

```
✅ High-risk incident logged successfully
✅ App-blocked incident logged successfully
✅ Accessibility-disabled incident logged successfully
✅ Overlay-missing incident logged successfully
✅ Multiple incidents retrieved in correct order (most recent first)
✅ Recent incidents correctly limited to 10 most recent
✅ Incident timestamp correctly preserved
✅ ViewModel correctly loaded incidents
✅ ViewModel correctly logs incident
✅ ViewModel displays multiple incidents correctly
✅ ViewModel correctly clears old incidents
✅ ViewModel correctly limits incidents to 10 most recent
✅ END-TO-END FLOW COMPLETE: High-risk incident flows from service → database → UI
✅ END-TO-END FLOW COMPLETE: App block flows from enforcement → database → parent notification
✅ END-TO-END FLOW COMPLETE: Permission loss flows from watchdog → database → parent alert
✅ END-TO-END FLOW COMPLETE: Permission recovery flows from watchdog → database → parent action
✅ END-TO-END FLOW COMPLETE: Multi-incident timeline displays all events in order
✅ Data integrity verified: All incident fields preserved through database round-trip
```

## Troubleshooting

### Tests Won't Run on Device
```bash
# Check device connection
adb devices

# Reinstall app and tests
./gradlew uninstallDebug
./gradlew connectedAndroidTest --no-daemon
```

### Tests Fail with Database Errors
```bash
# Clean build and rebuild
./gradlew clean
./gradlew connectedAndroidTest --no-daemon
```

### Device Not Found
```bash
# Restart adb
adb kill-server
adb start-server
adb devices
```

## Adding New Tests

To add tests for new incident types:

1. **Add DAO test** in `IncidentLoggingTest.kt`:
   ```kotlin
   @Test
   fun testLogNewIncidentType() = runBlocking {
       val incident = ProtectionIncidentEntity(
           type = "new_type",
           title = "New Alert",
           message = "Description",
           appPackage = "com.example",
           timestamp = System.currentTimeMillis()
       )
       incidentDao.insert(incident)
       val incidents = incidentDao.getRecentIncidents(10)
       assert(incidents[0].type == "new_type")
   }
   ```

2. **Add ViewModel test** in `ProtectionCenterViewModelTest.kt`:
   ```kotlin
   @Test
   fun testViewModelLoadsNewIncidentType() = runBlocking {
       // Similar to existing tests
   }
   ```

3. **Add integration test** in `IncidentFlowIntegrationTest.kt`:
   ```kotlin
   @Test
   fun testEndToEndNewIncidentTypeFlow() = runBlocking {
       // Simulate full flow
   }
   ```

## Performance Considerations

**Test Execution Time:**
- Database tests: ~2-3 seconds
- ViewModel tests: ~3-5 seconds
- Integration tests: ~5-8 seconds
- **Total full suite: ~15-20 seconds on typical device**

**Memory Usage:**
- In-memory database: ~5-10 MB per test
- All tests cleaned up in tearDown()

## Continuous Integration

These tests are designed to run in CI/CD pipelines:

```yaml
# Example GitHub Actions
- name: Run Instrumented Tests
  run: ./gradlew connectedAndroidTest --no-daemon
```

## Notes

- Tests use in-memory Room databases to avoid side effects
- `allowMainThreadQueries()` used for testing only (not in production)
- Coroutines and LiveData observers use proper synchronization
- All tests are isolated and can run in any order
- No network or external dependencies required

## Next Steps

After verifying tests pass:
1. ✅ Push incident logging code to main branch
2. ✅ Monitor incident logs in production Protection Center
3. ⏳ Add additional incident types as new threats are detected
4. ⏳ Implement incident resolution workflows
5. ⏳ Add analytics on incident frequency and types
