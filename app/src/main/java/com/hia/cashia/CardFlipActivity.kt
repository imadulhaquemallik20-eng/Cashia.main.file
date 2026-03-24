package com.hia.cashia

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardFlipActivity : AppCompatActivity() {

    private lateinit var viewModel: CardFlipViewModel
    private lateinit var userManager: UserManager
    private var currentUserId: String? = null

    // UI Elements
    private lateinit var coinBalanceText: TextView
    private lateinit var statsText: TextView
    private lateinit var cooldownTimerText: TextView
    private lateinit var gameGrid: GridLayout
    private lateinit var newGameButton: Button
    private lateinit var backButton: Button

    // Card buttons
    private val cardButtons = mutableListOf<Button>()

    // Cooldown timer
    private var cooldownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_flip)

        userManager = UserManager()
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            finish()
            return
        }

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[CardFlipViewModel::class.java]

        initViews()
        setupGrid()
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
        statsText = findViewById(R.id.statsText)
        cooldownTimerText = findViewById(R.id.cooldownTimerText)
        gameGrid = findViewById(R.id.gameGrid)
        newGameButton = findViewById(R.id.newGameButton)
        backButton = findViewById(R.id.backButton)

        // Initially hide cooldown timer
        cooldownTimerText.visibility = TextView.GONE
    }

    private fun setupGrid() {
        gameGrid.removeAllViews()
        cardButtons.clear()

        // Create 16 buttons (4x4 grid)
        for (i in 0 until 16) {
            val button = Button(this)
            button.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 150
                columnSpec = GridLayout.spec(i % 4, 1f)
                rowSpec = GridLayout.spec(i / 4, 1f)
                setMargins(4, 4, 4, 4)
            }
            button.text = "?"
            button.setTextSize(24f)
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
            button.tag = i

            button.setOnClickListener {
                val index = it.tag as Int
                viewModel.onCardClick(index)
            }

            gameGrid.addView(button)
            cardButtons.add(button)
        }
    }

    private fun setupClickListeners() {
        newGameButton.setOnClickListener {
            viewModel.resetGame()
            resetAllCards()
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
                updateGameBoard(state)
            }
        }

        viewModel.stats.observe(this) { stats ->
            if (stats != null) {
                updateStats(stats)

                if (stats.isOnCooldown) {
                    startCooldownTimer(stats.cooldownRemainingSeconds * 1000)
                } else {
                    stopCooldownTimer()
                }
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            newGameButton.isEnabled = !isLoading
        }
    }

    private fun updateGameBoard(state: CardFlipGameState) {
        // Update card faces
        state.cards.forEachIndexed { index, card ->
            val button = cardButtons.getOrNull(index)
            button?.let {
                if (card.isMatched) {
                    it.text = "✓"
                    it.isEnabled = false
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.success_green))
                } else if (card.isFlipped) {
                    it.text = card.imageUrl
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.teal_200))
                } else {
                    it.text = "?"
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
                }
            }
        }

        // Show win message
        state.lastPlayResult?.let { result ->
            if (result.success) {
                Toast.makeText(
                    this,
                    "🎉 ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            viewModel.clearMessages()
        }
    }

    private fun updateStats(stats: CardFlipStats) {
        val statsMessage = """
            📊 Today:
            Matches: ${stats.matchesPlayedToday}/${stats.maxMatchesPerDay}
            Coins: ${stats.coinsEarnedToday}/${stats.maxCoinsPerDay}
            Remaining: ${stats.remainingMatches} matches, ${stats.remainingCoins} coins
        """.trimIndent()
        statsText.text = statsMessage

        val canInteract = stats.canPlay && !stats.isOnCooldown
        cardButtons.forEach { it.isEnabled = canInteract }
    }

    private fun startCooldownTimer(millis: Long) {
        if (millis <= 0) return

        cooldownTimerText.visibility = TextView.VISIBLE
        cardButtons.forEach { it.isEnabled = false }

        cooldownTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                cooldownTimerText.text = "⏱️ Cooldown: $minutes:${String.format("%02d", seconds)}"
            }

            override fun onFinish() {
                stopCooldownTimer()
                viewModel.loadUserData() // Refresh to check if cooldown is over
            }
        }.start()
    }

    private fun stopCooldownTimer() {
        cooldownTimer?.cancel()
        cooldownTimer = null
        cooldownTimerText.visibility = TextView.GONE

        // Re-enable cards if within limits
        val stats = viewModel.stats.value
        if (stats?.canPlay == true) {
            cardButtons.forEach { it.isEnabled = true }
        }
    }

    private fun resetAllCards() {
        cardButtons.forEach { button ->
            button.text = "?"
            button.isEnabled = true
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
        }
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
                userManager.updateGameStats(userId, "cardflip", 0)
            }
        }
    }
}