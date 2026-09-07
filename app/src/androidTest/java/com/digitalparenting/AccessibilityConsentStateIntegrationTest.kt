package com.digitalparenting

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitalparenting.util.AccessibilityConsentState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration coverage for the local Accessibility disclosure consent gate.
 *
 * CI compile-protects this class through assembleDebugAndroidTest. Execute it on
 * an Android device/emulator with connectedAndroidTest for runtime proof.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityConsentStateIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        clearPermissionSetupPreferences()
    }

    @After
    fun tearDown() {
        clearPermissionSetupPreferences()
    }

    @Test
    fun freshStateDoesNotHaveAccessibilityConsent() {
        assertFalse(AccessibilityConsentState.hasCurrentConsent(context))
    }

    @Test
    fun recordingCurrentConsentUnlocksConsentGate() {
        AccessibilityConsentState.recordCurrentConsent(context)

        assertTrue(AccessibilityConsentState.hasCurrentConsent(context))
    }

    private fun clearPermissionSetupPreferences() {
        context
            .getSharedPreferences("child_permission_setup", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
