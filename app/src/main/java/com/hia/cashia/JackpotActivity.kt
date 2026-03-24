package com.hia.cashia

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JackpotActivity : AppCompatActivity() {

    private lateinit var viewModel: JackpotViewModel
    private lateinit var userManager: UserManager
    private var currentUserId: String? = null

    // UI Elements
    private lateinit var coinBalanceText: TextView
    private lateinit var reel1Text: TextView
    private lateinit var reel2Text: TextView
    private lateinit var reel3Text: TextView
    private lateinit var spinButton: Button
    private lateinit var statsText: TextView
    private lateinit var resultText: TextView
    private lateinit var backButton: Button

    // Animation
    private val handler = Handler(Looper.getMainLooper())
    private var spinRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jackpot)

        userManager = UserManager()
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            finish()
            return
        }

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[JackpotViewModel::class.java]

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
        reel1Text = findViewById(R.id.reel1Text)
        reel2Text = findViewById(R.id.reel2Text)
        reel3Text = findViewById(R.id.reel3Text)
        spinButton = findViewById(R.id.spinButton)
        statsText = findViewById(R.id.statsText)
        resultText = findViewById(R.id.resultText)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupClickListeners() {
        spinButton.setOnClickListener {
            viewModel.spin()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            if (user != null) {
                coinBalanceText.text = "💰 ${user.coinBalance} coins"
            }
        }

        viewModel.gameState.observe(this) { state ->
            if (state != null) {
                updateReels(state.reels)

                if (state.isSpinning) {
                    spinButton.isEnabled = false
                    spinButton.text = "Spinning..."
                } else {
                    spinButton.isEnabled = true
                    spinButton.text = "SPIN (1 coin)"
                }

                state.lastSpinResult?.let { result ->
                    showResult(result)
                }
            }
        }

        viewModel.stats.observe(this) { stats ->
            if (stats != null) {
                updateStats(stats)
                spinButton.isEnabled = stats.canPlay && !(viewModel.gameState.value?.isSpinning == true)
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            spinButton.isEnabled = !isLoading && (viewModel.stats.value?.canPlay == true)
        }
    }

    private fun updateReels(symbols: List<SlotSymbol>) {
        if (symbols.size >= 3) {
            reel1Text.text = symbols[0].emoji
            reel2Text.text = symbols[1].emoji
            reel3Text.text = symbols[2].emoji

            // Change color based on whether it's a win
            val currentState = viewModel.gameState.value
            if (currentState?.lastSpinResult?.winAmount ?: 0 > 0) {
                // Winning spin - highlight reels
                val winColor = ContextCompat.getColor(this, R.color.success_green)
                reel1Text.setTextColor(winColor)
                reel2Text.setTextColor(winColor)
                reel3Text.setTextColor(winColor)
            } else {
                // Normal spin
                val normalColor = ContextCompat.getColor(this, R.color.black)
                reel1Text.setTextColor(normalColor)
                reel2Text.setTextColor(normalColor)
                reel3Text.setTextColor(normalColor)
            }
        }
    }

    private fun updateStats(stats: JackpotStats) {
        val statsMessage = """
            📊 Today:
            Spins: ${stats.spinsToday}/${stats.maxSpinsPerDay}
            Winnings: ${stats.winningsToday}/${stats.maxWinningsPerDay}
            Remaining: ${stats.remainingSpins} spins
        """.trimIndent()
        statsText.text = statsMessage
    }

    private fun showResult(result: JackpotSpinResult) {
        if (result.winAmount > 0) {
            // Winning message
            resultText.text = result.message
            resultText.setTextColor(ContextCompat.getColor(this, R.color.success_green))
            resultText.visibility = TextView.VISIBLE

            // Flash animation for win
            handler.postDelayed({
                resultText.visibility = TextView.GONE
            }, 3000)
        } else {
            // Losing message - show briefly
            resultText.text = result.message
            resultText.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            resultText.visibility = TextView.VISIBLE

            handler.postDelayed({
                resultText.visibility = TextView.GONE
            }, 1500)
        }

        viewModel.clearMessages()
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
        handler.removeCallbacksAndMessages(null)
        updateLeaderboard()
    }

    override fun onStop() {
        super.onStop()

        val userId = currentUserId
        if (userId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                userManager.updateGameStats(userId, "jackpot", 0)
            }
        }
    }
}