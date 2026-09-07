package com.digitalparenting.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.util.ProtectionStateManager

class ChildAccessibilityService : AccessibilityService() {

    private var lastActionTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ProtectionStateManager.hydrateBlockState(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (isBlocked(packageName)) {
            val now = System.currentTimeMillis()
            if (now - lastActionTime > 1000) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                lastActionTime = now
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    private fun isBlocked(pkg: String): Boolean {
        return BlockStateManager.isBlocked(pkg)
    }
}
