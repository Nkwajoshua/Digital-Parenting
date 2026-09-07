package com.digitalparenting.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.digitalparenting.data.BlockMode
import com.digitalparenting.data.BlockStateManager

data class BlockStateSnapshot(
    val blockedPackages: Set<String>,
    val blockModes: Map<String, BlockMode>
)

object ProtectionStateManager {
    private const val PREFS_NAME = "protection_state"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_BLOCK_MODES = "block_modes"

    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun persistBlockState(context: Context, blockedPackages: Set<String>, blockModes: Map<String, BlockMode>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockedValue = blockedPackages.joinToString(",")
        val modeValue = blockModes.entries.joinToString(";") { "${it.key}:${it.value.name}" }
        prefs.edit()
            .putString(KEY_BLOCKED_PACKAGES, blockedValue)
            .putString(KEY_BLOCK_MODES, modeValue)
            .apply()
    }

    fun persistCurrentBlockState(context: Context) {
        val (blockedPackages, blockModes) = BlockStateManager.snapshotState()
        if (blockedPackages.isEmpty()) {
            clearPersistedBlockState(context)
        } else {
            persistBlockState(context, blockedPackages, blockModes)
        }
    }

    fun restoreBlockState(context: Context): BlockStateSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockedValue = prefs.getString(KEY_BLOCKED_PACKAGES, "") ?: ""
        val modeValue = prefs.getString(KEY_BLOCK_MODES, "") ?: ""

        val blockedPackages = blockedValue
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()

        val blockModes = modeValue
            .split(";")
            .mapNotNull {
                val parts = it.split(":")
                if (parts.size != 2) return@mapNotNull null
                val pkg = parts[0]
                val mode = try {
                    BlockMode.valueOf(parts[1])
                } catch (ex: Exception) {
                    null
                }
                mode?.let { pkg to it }
            }
            .toMap()

        return BlockStateSnapshot(blockedPackages, blockModes)
    }

    fun hydrateBlockState(context: Context): BlockStateSnapshot {
        val snapshot = restoreBlockState(context)
        BlockStateManager.replaceState(snapshot.blockedPackages, snapshot.blockModes)
        return snapshot
    }

    fun clearPersistedBlockState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun buildOverlaySettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
