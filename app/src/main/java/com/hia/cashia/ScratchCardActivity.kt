package com.hia.cashia

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
class ScratchCardActivity : AppCompatActivity() {

    private lateinit var viewModel: ScratchCardViewModel
    private lateinit var userManager: UserManager
    private var currentUserId: String? = null

    // UI Elements
    private lateinit var coinBalanceText: TextView
    private lateinit var cardsRemainingText: TextView
    private lateinit var coinsEarnedTodayText: TextView
    private lateinit var cooldownTimerText: TextView
    private lateinit var scratchCardButton: Button
    private lateinit var backButton: Button
    private lateinit var cardImage: Button

    // Game state
    private var isOnCooldown = false
    private var cooldownNotificationSent = false
    private var cooldownTimer: CountDownTimer? = null

    // Constants
    companion object {
        const val MAX_SCRATCH_CARDS_PER_DAY = 15
        const val MAX_COINS_PER_DAY_FROM_SCRATCH = 50
        const val MIN_COINS_PER_CARD = 1
        const val MAX_COINS_PER_CARD = 5
        const val CARDS_BEFORE_COOLDOWN = 5
        const val COOLDOWN_MINUTES = 10L
        const val COOLDOWN_MILLIS = COOLDOWN_MINUTES * 60 * 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scratch_card)

        userManager = UserManager()
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            finish()
            return
        }

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ScratchCardViewModel::class.java]

        initViews()
        setupClickListeners()
        observeViewModel()
        viewModel.loadUserData()
    }

    override fun onResume() {
        super.onResume()
        IronSourceManager.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        IronSourceManager.onPause(this)
    }
    private fun initViews() {
        coinBalanceText = findViewById(R.id.coinBalanceText)
        cardsRemainingText = findViewById(R.id.cardsRemainingText)
        coinsEarnedTodayText = findViewById(R.id.coinsEarnedTodayText)
        cooldownTimerText = findViewById(R.id.cooldownTimerText)
        scratchCardButton = findViewById(R.id.scratchCardButton)
        backButton = findViewById(R.id.backButton)
        cardImage = findViewById(R.id.cardImage)

        // Initially hide cooldown timer
        cooldownTimerText.visibility = TextView.GONE
    }

    private fun setupClickListeners() {
        scratchCardButton.setOnClickListener {
            playScratchCard()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            if (user != null) {
                updateUI(user)
            }
        }

        viewModel.gameState.observe(this) { state ->
            if (state != null) {
                updateGameState(state)
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            scratchCardButton.isEnabled = !isLoading && !isOnCooldown
            scratchCardButton.text = if (isLoading) "Playing..." else "Scratch Card"
        }
    }

    private fun updateUI(user: User) {
        coinBalanceText.text = "💰 ${user.coinBalance} coins"

        val cardsPlayed = user.scratchCardsPlayedToday
        val cardsRemaining = MAX_SCRATCH_CARDS_PER_DAY - cardsPlayed
        cardsRemainingText.text = "🎴 Cards Today: $cardsPlayed/$MAX_SCRATCH_CARDS_PER_DAY"

        val coinsEarned = user.scratchCoinsEarnedToday
        coinsEarnedTodayText.text = "💰 Earned Today: $coinsEarned/$MAX_COINS_PER_DAY_FROM_SCRATCH"

        // Check if user has reached limits
        if (cardsRemaining <= 0) {
            scratchCardButton.isEnabled = false
            scratchCardButton.text = "Daily Limit Reached"
        } else if (coinsEarned >= MAX_COINS_PER_DAY_FROM_SCRATCH) {
            scratchCardButton.isEnabled = false
            scratchCardButton.text = "Earning Limit Reached"
        }
    }

    private fun updateGameState(state: ScratchCardGameState) {
        // Update cooldown status
        isOnCooldown = state.isOnCooldown

        if (state.isOnCooldown) {
            startCooldownTimer(state.cooldownEndTime)
        } else {
            stopCooldownTimer()
        }

        // Show scratch result animation if there was a play
        if (state.lastPlayResult != null) {
            showScratchResult(state.lastPlayResult)
            viewModel.clearGameState()
        }
    }

    private fun startCooldownTimer(endTime: Long) {
        val currentTime = System.currentTimeMillis()
        val remainingTime = endTime - currentTime

        if (remainingTime <= 0) {
            // Cooldown already over
            viewModel.checkCooldownStatus()
            return
        }

        cooldownTimerText.visibility = TextView.VISIBLE
        scratchCardButton.isEnabled = false
        scratchCardButton.text = "On Cooldown"
        cooldownNotificationSent = false

        cooldownTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60

                // Fix the formatting - use String.format properly
                val timeString = String.format("%d:%02d", minutes, seconds)
                cooldownTimerText.text = "⏱️ Cooldown: $timeString"
            }

            override fun onFinish() {
                stopCooldownTimer()
                viewModel.checkCooldownStatus()

                // Send notification when cooldown ends
                if (!cooldownNotificationSent) {
                    cooldownNotificationSent = true
                    sendCooldownNotification()
                }
            }
        }.start()
    }

    // Add this function to send cooldown notification
    private fun sendCooldownNotification() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = currentUserId ?: return@launch
                val fcmToken = getUserFCMToken(userId)

                if (fcmToken != null && fcmToken.isNotEmpty()) {
                    // Create notification data
                    val notification = mapOf(
                        "token" to fcmToken,
                        "title" to "🎴 Scratch Cards Ready!",
                        "body" to "Your cooldown is over! Come scratch for more coins!",
                        "type" to "cooldown",
                        "game" to "scratch",
                        "timestamp" to System.currentTimeMillis()
                    )

                    // Save to Firestore for cloud function to send
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("notifications").add(notification)

                    // Also show local notification
                    showLocalNotification(
                        "🎴 Scratch Cards Ready!",
                        "Your cooldown is over! Come scratch for more coins!"
                    )
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    // Add this helper function
    private suspend fun getUserFCMToken(userId: String): String? {
        return try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val snapshot = db.collection("users").document(userId).get().await()
            snapshot.getString("fcmToken")
        } catch (e: Exception) {
            null
        }
    }

    // Add this function to show local notification
    private fun showLocalNotification(title: String, message: String) {
        val intent = Intent(this, ScratchCardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "cashia_notifications",
                "CasHIA Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, "cashia_notifications")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.premium_primary))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun stopCooldownTimer() {
        cooldownTimer?.cancel()
        cooldownTimer = null
        cooldownTimerText.visibility = TextView.GONE
        scratchCardButton.isEnabled = true
        scratchCardButton.text = "Scratch Card"
    }

    private fun showScratchResult(result: ScratchCardPlayResult) {
        // Animate the card
        cardImage.text = "🎴"
        cardImage.isEnabled = false

        // Show the result
        val message = when {
            result.success -> {
                cardImage.text = "✨"
                "You won ${result.coinsEarned} coins!"
            }
            else -> {
                cardImage.text = "❌"
                result.message
            }
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Reset card after delay
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500)
            cardImage.text = "🎴"
            cardImage.isEnabled = true
        }
    }

    private fun playScratchCard() {
        val userId = currentUserId ?: return
        viewModel.playScratchCard(userId)
    }

    // Simple function to update leaderboard with total earnings
    private fun updateLeaderboard() {
        val userId = currentUserId
        if (userId == null) return

        // Get current user data to find out how much they earned in this session
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = userManager.getUserData(userId)
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        // Get today's date
                        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date())

                        // Only update if they played today
                        if (user.lastJackpotDate == today ||
                            user.lastCardFlipDate == today ||
                            user.lastScratchCardDate == today ||
                            user.lastAdWatchDate == today) {

                            // Calculate total earned today from all games
                            val totalToday = user.jackpotWinningsToday +
                                    user.cardFlipCoinsEarnedToday +
                                    user.scratchCoinsEarnedToday +
                                    (user.adsWatchedToday * 2)

                            // Update leaderboard with today's total
                            userManager.updateUserActivity(userId, totalToday)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cooldownTimer?.cancel()
        updateLeaderboard()
    }

    override fun onStop() {
        super.onStop()

        val userId = currentUserId
        if (userId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                userManager.updateGameStats(userId, "scratch", 0)
            }
        }
    }
}