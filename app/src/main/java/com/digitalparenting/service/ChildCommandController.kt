package com.digitalparenting.service

import android.content.Context
import android.util.Log
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.data.local.AppLimit
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.util.ProtectionStateManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns the Child-side command queue lifecycle and command acknowledgements.
 *
 * Parent authorization and command creation remain server-authoritative. This
 * component consumes only commands addressed to the authenticated anonymous
 * Child identity and applies the corresponding local effect.
 */
internal class ChildCommandController(
    context: Context,
    private val limitDao: AppLimitDao,
    private val onBlockRequested: (appName: String, appPackage: String, reason: String) -> Unit,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val appContext = context.applicationContext
    private var listener: ListenerRegistration? = null

    fun start() {
        val user = auth.currentUser
        if (user == null) {
            Log.d("RemoteCommand", "No Firebase user. Skipping command listener.")
            stop()
            return
        }
        if (!user.isAnonymous) {
            Log.e("RemoteCommand", "Active identity is not a Child identity. Command listener not started.")
            stop()
            return
        }

        stop()
        listener = firestore
            .collection("children")
            .document(user.uid)
            .collection("commands")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("RemoteCommand", "Command listener failed", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val type = doc.getString("type") ?: continue
                    val appPackage = doc.getString("appPackage") ?: ""
                    val appName = doc.getString("appName") ?: appPackage
                    val reason = doc.getString("reason") ?: "Blocked by parent"
                    val maxMinutes = (doc.getLong("maxMinutes") ?: 0L).toInt()
                    val enabled = doc.getBoolean("enabled") ?: true

                    handleCommand(
                        childUid = user.uid,
                        commandId = doc.id,
                        type = type,
                        appPackage = appPackage,
                        appName = appName,
                        reason = reason,
                        maxMinutes = maxMinutes,
                        enabled = enabled
                    )
                }
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
    }

    private fun handleCommand(
        childUid: String,
        commandId: String,
        type: String,
        appPackage: String,
        appName: String,
        reason: String,
        maxMinutes: Int,
        enabled: Boolean
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (type) {
                    "block_app" -> {
                        BlockStateManager.setBlocked(setOf(appPackage))
                        ProtectionStateManager.persistCurrentBlockState(appContext)
                        onBlockRequested(appName, appPackage, reason)
                    }

                    "unblock_app" -> {
                        BlockStateManager.removeBlocked(appPackage)
                        ProtectionStateManager.persistCurrentBlockState(appContext)
                    }

                    "set_limit" -> {
                        val existing = limitDao.getLimit(appPackage)
                        val updatedLimit = if (existing != null) {
                            existing.copy(
                                appName = if (existing.appName.isBlank()) appName else existing.appName,
                                maxMinutes = maxMinutes,
                                enabled = enabled
                            )
                        } else {
                            AppLimit(
                                packageName = appPackage,
                                appName = appName,
                                maxMinutes = maxMinutes,
                                enabled = enabled,
                                lastReset = System.currentTimeMillis()
                            )
                        }
                        limitDao.setLimit(updatedLimit)
                    }

                    else -> {
                        markFailed(childUid, commandId, "Unknown command type: $type")
                        return@launch
                    }
                }

                markHandled(childUid, commandId)
            } catch (error: Exception) {
                Log.e("RemoteCommand", "Failed to handle command $commandId", error)
                markFailed(
                    childUid = childUid,
                    commandId = commandId,
                    errorMessage = error.message ?: "Unknown error"
                )
            }
        }
    }

    private fun markHandled(childUid: String, commandId: String) {
        firestore.collection("children")
            .document(childUid)
            .collection("commands")
            .document(commandId)
            .update(
                mapOf(
                    "status" to "handled",
                    "handledAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->
                Log.e("RemoteCommand", "Failed to acknowledge handled command $commandId", error)
            }
    }

    private fun markFailed(childUid: String, commandId: String, errorMessage: String) {
        firestore.collection("children")
            .document(childUid)
            .collection("commands")
            .document(commandId)
            .update(
                mapOf(
                    "status" to "failed",
                    "handledAt" to FieldValue.serverTimestamp(),
                    "errorMessage" to errorMessage
                )
            )
            .addOnFailureListener { error ->
                Log.e("RemoteCommand", "Failed to acknowledge failed command $commandId", error)
            }
    }
}
