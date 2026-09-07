package com.digitalparenting

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitalparenting.data.BlockMode
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.util.ProtectionStateManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration coverage for persisted Child block-state recovery.
 *
 * CI compile-protects this class through assembleDebugAndroidTest. Execute it on
 * an Android device/emulator with connectedAndroidTest for runtime proof.
 */
@RunWith(AndroidJUnit4::class)
class ProtectionStateRecoveryIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        BlockStateManager.clearBlocked()
        ProtectionStateManager.clearPersistedBlockState(context)
    }

    @After
    fun tearDown() {
        BlockStateManager.clearBlocked()
        ProtectionStateManager.clearPersistedBlockState(context)
    }

    @Test
    fun persistedStateHydratesIntoMemory() {
        ProtectionStateManager.persistBlockState(
            context = context,
            blockedPackages = setOf("com.example.blocked"),
            blockModes = mapOf("com.example.blocked" to BlockMode.BLOCKED)
        )
        BlockStateManager.clearBlocked()

        val snapshot = ProtectionStateManager.hydrateBlockState(context)

        assertEquals(setOf("com.example.blocked"), snapshot.blockedPackages)
        assertTrue(BlockStateManager.isBlocked("com.example.blocked"))
        assertEquals(BlockMode.BLOCKED, BlockStateManager.getBlockMode("com.example.blocked"))
    }

    @Test
    fun emptyPersistedStateClearsStaleMemory() {
        BlockStateManager.replaceState(
            packages = setOf("com.example.stale"),
            modes = mapOf("com.example.stale" to BlockMode.BLOCKED)
        )

        ProtectionStateManager.hydrateBlockState(context)

        assertFalse(BlockStateManager.isBlocked("com.example.stale"))
        assertEquals(BlockMode.NONE, BlockStateManager.getBlockMode("com.example.stale"))
    }

    @Test
    fun currentMemoryStatePersistsAndRoundTrips() {
        BlockStateManager.replaceState(
            packages = setOf("com.example.parentblocked"),
            modes = emptyMap()
        )

        ProtectionStateManager.persistCurrentBlockState(context)
        BlockStateManager.clearBlocked()
        ProtectionStateManager.hydrateBlockState(context)

        assertTrue(BlockStateManager.isBlocked("com.example.parentblocked"))
    }
}
