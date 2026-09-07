package com.digitalparenting.service

import android.util.Log
import com.digitalparenting.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Child-directed FCM messages.
 *
 * Push delivery is supplemental: this service never applies time requests,
 * commands, or block-state mutations from the FCM payload. Firestore remains
 * the authoritative state channel and the existing runtime listeners perform
 * those transitions.
 */
class ChildFirebaseMessagingService : FirebaseMessagingService() {

    private val tokenRegistrar by lazy { ChildFcmTokenRegistrar() }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("CHILD_FCM", "Firebase Messaging token refreshed")
        tokenRegistrar.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"]
        if (type != "time_request_resolved") {
            Log.d(
                "CHILD_FCM",
                "Ignoring unsupported Child FCM message type=${type ?: "unknown"} id=${message.messageId ?: "unknown"}"
            )
            return
        }

        val status = message.data["status"]
        val title = message.notification?.title ?: "Time Request Update"
        val body = message.notification?.body ?: when (status) {
            "approved" -> "Your request was approved"
            "denied" -> "Your request was denied"
            else -> "Your time request has been updated"
        }

        NotificationHelper(this).apply {
            createChannels()
            showChildAlert(title, body)
        }

        Log.d(
            "CHILD_FCM",
            "Displayed time-request update notification status=${status ?: "unknown"} requestId=${message.data["requestId"] ?: "unknown"}"
        )
    }
}
