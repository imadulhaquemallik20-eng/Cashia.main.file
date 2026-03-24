package com.hia.cashia

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "cashia_notifications"
        private const val CHANNEL_NAME = "CasHIA Notifications"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")

        // Save token to Firebase
        saveTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: ${message.data}")

        // Check if message contains notification payload
        message.notification?.let {
            Log.d(TAG, "Notification: ${it.title} - ${it.body}")
            sendNotification(it.title ?: "CasHIA", it.body ?: "New notification")
        }

        // Handle custom data payload
        message.data.let { data ->
            val title = data["title"] ?: "CasHIA"
            val body = data["body"] ?: "You have a new notification"
            val type = data["type"] ?: "general"

            sendNotification(title, body, type)
        }
    }

    private fun saveTokenToServer(token: String) {
        val userId = UserManager().getCurrentUserId()
        if (userId != null) {
            // Save token to Firestore
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "Token saved to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save token: ${e.message}")
                }
        }
    }

    private fun sendNotification(title: String, message: String, type: String = "general") {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "CasHIA game notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent for when notification is clicked
        val intent = when (type) {
            "daily_login" -> Intent(this, MainActivity::class.java)
            "cooldown" -> Intent(this, WatchAdsActivity::class.java)
            "leaderboard" -> Intent(this, LeaderboardActivity::class.java)
            "achievement" -> Intent(this, AchievementsActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification) // Create this icon
            .setColor(getColor(R.color.premium_primary))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}