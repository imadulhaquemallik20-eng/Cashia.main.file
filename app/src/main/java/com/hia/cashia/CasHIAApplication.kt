package com.hia.cashia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

class CasHIAApplication : Application() {

    companion object {
        lateinit var instance: CasHIAApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d("CasHIAApp", "🚀 App starting...")

        // Create notification channel for Android O and above
        createNotificationChannel()

        // Initialize Firebase Cloud Messaging
        initializeFCM()

        // Initialize IronSource Ads
        initializeIronSource()
    }

    private fun createNotificationChannel() {
        // Create notification channel for Android 8.0 (Oreo) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "cashia_notifications",
                "CasHIA Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for daily rewards, game updates, and achievements"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d("CasHIAApp", "✅ Notification channel created")
        }
    }

    private fun initializeFCM() {
        // Get Firebase Cloud Messaging token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "✅ FCM Token: $token")
                Log.d("FCM", "📱 Device is ready to receive push notifications")

                // Save token to local storage for later use
                saveTokenLocally(token)
            } else {
                Log.e("FCM", "❌ Failed to get FCM token: ${task.exception?.message}")
            }
        }

        // Subscribe to topics for broadcast notifications
        subscribeToTopics()
    }

    private fun subscribeToTopics() {
        // Subscribe to general announcements topic
        FirebaseMessaging.getInstance().subscribeToTopic("cashia_announcements")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "✅ Subscribed to announcements topic")
                } else {
                    Log.e("FCM", "❌ Failed to subscribe to announcements")
                }
            }

        // Subscribe to daily rewards topic
        FirebaseMessaging.getInstance().subscribeToTopic("cashia_daily_rewards")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "✅ Subscribed to daily rewards topic")
                }
            }
    }

    private fun saveTokenLocally(token: String) {
        // Save token to SharedPreferences for later use
        val sharedPref = getSharedPreferences("cashia_prefs", MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()
    }

    private fun initializeIronSource() {
        try {
            IronSourceManager.initialize(this)
            Log.d("CasHIAApp", "✅ IronSource initialized successfully")
        } catch (e: Exception) {
            Log.e("CasHIAApp", "❌ IronSource initialization failed: ${e.message}")
        }
    }

    fun getFCMToken(): String? {
        val sharedPref = getSharedPreferences("cashia_prefs", MODE_PRIVATE)
        return sharedPref.getString("fcm_token", null)
    }
}