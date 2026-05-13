package com.digitalparenting.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.digitalparenting.R
import com.digitalparenting.ui.HomeStatusActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_MONITORING = "monitoring_status"
        const val CHANNEL_CHILD_ALERTS = "child_alerts"
        const val CHANNEL_SECURITY = "security_alerts"

        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2001
        const val SECURITY_NOTIFICATION_ID = 3001
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING,
            "Monitoring Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows monitoring service status"
        }

        val riskChannel = NotificationChannel(
            CHANNEL_CHILD_ALERTS,
            "Child Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Child-facing alerts such as app blocked and request status"
        }

        val securityChannel = NotificationChannel(
            CHANNEL_SECURITY,
            "Security Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when protections are weakened"
        }

        manager.createNotificationChannel(monitoringChannel)
        manager.createNotificationChannel(riskChannel)
        manager.createNotificationChannel(securityChannel)
    }

    fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(context, HomeStatusActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Digital Parenting Active")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showChildAlert(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_CHILD_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID + title.hashCode(), notification)
    }

    fun showSecurityAlert(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_SECURITY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(SECURITY_NOTIFICATION_ID + title.hashCode(), notification)
    }
}
