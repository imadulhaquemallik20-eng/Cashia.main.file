package com.hia.cashia

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class JackpotViewModel : ViewModel() {

    private val userManager = UserManager()
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    // Game constants
    companion object {
        const val MAX_SPINS_PER_DAY = 50
        const val MAX_WINNINGS_PER_DAY = 100
        const val BET_AMOUNT = 1
        const val SPIN_DURATION = 2000L // 2 seconds spin animation

        // Symbol definitions with WEIGHTS for probability
        // Higher weight = more common
        val SYMBOLS = listOf(
            SlotSymbol(0, "🍒", "Cherry", 2, weight = 30),    // Very common
            SlotSymbol(1, "🍋", "Lemon", 3, weight = 25),     // Common
            SlotSymbol(2, "🍊", "Orange", 4, weight = 20),    // Common
            SlotSymbol(3, "🍇", "Grapes", 5, weight = 15),    // Medium
            SlotSymbol(4, "💰", "Money Bag", 10, weight = 5), // Rare
            SlotSymbol(5, "💎", "Diamond", 15, weight = 3),   // Very Rare
            SlotSymbol(6, "7️⃣", "Seven", 20, weight = 2)      // Jackpot - Extremely Rare
        )

        // Total weight for probability calculation
        val TOTAL_WEIGHT = SYMBOLS.sumOf { it.weight }

        // Win probability adjustment - sometimes give near wins
        const val NEAR_WIN_PROBABILITY = 15 // 15% chance of getting 2 matching symbols
    }

    // LiveData
    private val _gameState = MutableLiveData<JackpotGameState>()
    val gameState: LiveData<JackpotGameState> = _gameState

    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _stats = MutableLiveData<JackpotStats?>()
    val stats: LiveData<JackpotStats?> = _stats

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        // Initialize with random symbols using weighted distribution
        _gameState.value = JackpotGameState(
            reels = getWeightedRandomSymbols(3)
        )
    }

    // Weighted random selection
    private fun getWeightedRandomSymbol(): SlotSymbol {
        val randomValue = Random().nextInt(TOTAL_WEIGHT)
        var cumulativeWeight = 0

        for (symbol in SYMBOLS) {
            cumulativeWeight += symbol.weight
            if (randomValue < cumulativeWeight) {
                return symbol
            }
        }
        return SYMBOLS[0] // Fallback
    }

    private fun getWeightedRandomSymbols(count: Int): List<SlotSymbol> {
        return List(count) { getWeightedRandomSymbol() }
    }

    // New method that sometimes creates near wins for excitement
    private fun getExcitingSpinResult(): List<SlotSymbol> {
        val random = Random().nextInt(100)

        // 10% chance of a guaranteed win (makes game more exciting)
        if (random < 10) {
            // Guaranteed win - pick a random symbol for all three reels
            val winningSymbol = getWeightedRandomSymbol()
            return List(3) { winningSymbol }
        }

        // 20% chance of a "near win" (2 matching symbols)
        if (random < 30) {  // 10% + 20% = 30%
            val symbols = getWeightedRandomSymbols(3).toMutableList()
            // Make first two symbols match
            val matchSymbol = getWeightedRandomSymbol()
            symbols[0] = matchSymbol
            symbols[1] = matchSymbol
            // Third symbol is random (likely different)
            return symbols
        }

        // 70% chance of completely random (but still using weighted distribution)
        return getWeightedRandomSymbols(3)
    }

    fun loadUserData() {
        val userId = userManager.getCurrentUserId() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val result = userManager.getUserData(userId)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    _userData.value = user
                    updateStats(user)
                }
            }
        }
    }

    private fun updateStats(user: User?) {
        if (user == null) return

        val today = getTodayDate()
        val spinsToday = if (user.lastJackpotDate == today) user.jackpotSpinsToday else 0
        val winningsToday = if (user.lastJackpotDate == today) user.jackpotWinningsToday else 0

        val remainingSpins = MAX_SPINS_PER_DAY - spinsToday
        val remainingWinnings = MAX_WINNINGS_PER_DAY - winningsToday
        val canPlay = remainingSpins > 0 && remainingWinnings > 0 && user.coinBalance >= BET_AMOUNT

        _stats.value = JackpotStats(
            spinsToday = spinsToday,
            winningsToday = winningsToday,
            remainingSpins = remainingSpins,
            remainingWinnings = remainingWinnings,
            canPlay = canPlay,
            isOnCooldown = false
        )

        // Update game state
        val currentState = _gameState.value
        if (currentState != null) {
            _gameState.value = currentState.copy(
                spinsToday = spinsToday,
                winningsToday = winningsToday
            )
        }
    }

    fun spin() {
        val userId = userManager.getCurrentUserId() ?: return
        val user = _userData.value ?: return
        val stats = _stats.value ?: return
        val currentState = _gameState.value ?: return

        // Check if can play
        if (!stats.canPlay) {
            _errorMessage.value = when {
                stats.spinsToday >= MAX_SPINS_PER_DAY -> "Daily spin limit reached!"
                stats.winningsToday >= MAX_WINNINGS_PER_DAY -> "Daily winnings limit reached!"
                user.coinBalance < BET_AMOUNT -> "Not enough coins! Need 1 coin to play."
                else -> "Cannot play right now"
            }
            return
        }

        // Don't spin if already spinning
        if (currentState.isSpinning) return

        // Start spinning animation
        _gameState.value = currentState.copy(isSpinning = true)

        viewModelScope.launch {
            // Simulate spinning reels with exciting animation
            val spinDuration = SPIN_DURATION
            val updateInterval = 100L
            val steps = spinDuration / updateInterval

            for (i in 0 until steps.toInt()) {
                delay(updateInterval)
                // During animation, show random symbols for excitement
                val spinningReels = getWeightedRandomSymbols(3)
                _gameState.value = _gameState.value?.copy(reels = spinningReels)
            }

            // Final result - using exciting spin generator
            val finalSymbols = getExcitingSpinResult()
            val winAmount = calculateWinnings(finalSymbols)

            // Process the spin result in Firebase
            processSpinResult(userId, finalSymbols, winAmount)
        }
    }

    private fun calculateWinnings(symbols: List<SlotSymbol>): Int {
        // Check if all symbols are the same
        val allSame = symbols.all { it.id == symbols[0].id }

        return if (allSame) {
            BET_AMOUNT * symbols[0].multiplier
        } else {
            0 // No win
        }
    }

    private fun processSpinResult(userId: String, symbols: List<SlotSymbol>, winAmount: Int) {
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDoc = usersCollection.document(userId)

                val result = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(userDoc)

                    // Get current values
                    val currentBalance = snapshot.getLong("coinBalance")?.toInt() ?: 0
                    val currentTotalEarned = snapshot.getLong("totalCoinsEarned")?.toInt() ?: 0
                    val spinsToday = snapshot.getLong("jackpotSpinsToday")?.toInt() ?: 0
                    val winningsToday = snapshot.getLong("jackpotWinningsToday")?.toInt() ?: 0
                    val lastPlayDate = snapshot.getString("lastJackpotDate") ?: ""

                    val today = getTodayDate()

                    // Reset if new day
                    val resetSpins = if (lastPlayDate == today) spinsToday else 0
                    val resetWinnings = if (lastPlayDate == today) winningsToday else 0

                    // Check limits
                    if (resetSpins + 1 > MAX_SPINS_PER_DAY) {
                        return@runTransaction JackpotSpinResult(
                            success = false,
                            betAmount = BET_AMOUNT,
                            winAmount = 0,
                            symbols = symbols,
                            message = "Daily spin limit reached",
                            spinsToday = resetSpins
                        )
                    }

                    if (resetWinnings + winAmount > MAX_WINNINGS_PER_DAY) {
                        return@runTransaction JackpotSpinResult(
                            success = false,
                            betAmount = BET_AMOUNT,
                            winAmount = 0,
                            symbols = symbols,
                            message = "Daily winnings limit would be exceeded",
                            spinsToday = resetSpins
                        )
                    }

                    // Check balance
                    if (currentBalance < BET_AMOUNT) {
                        return@runTransaction JackpotSpinResult(
                            success = false,
                            betAmount = BET_AMOUNT,
                            winAmount = 0,
                            symbols = symbols,
                            message = "Insufficient balance",
                            spinsToday = resetSpins
                        )
                    }

                    // Update balances
                    val newBalance = currentBalance - BET_AMOUNT + winAmount
                    val newTotalEarned = currentTotalEarned + winAmount
                    val newSpins = resetSpins + 1
                    val newWinnings = resetWinnings + winAmount

                    // Update user document
                    transaction.update(userDoc, "coinBalance", newBalance)
                    transaction.update(userDoc, "totalCoinsEarned", newTotalEarned)
                    transaction.update(userDoc, "jackpotSpinsToday", newSpins)
                    transaction.update(userDoc, "jackpotWinningsToday", newWinnings)
                    transaction.update(userDoc, "lastJackpotDate", today)

                    // Prepare exciting message based on result
                    val message = when {
                        winAmount > 0 -> {
                            if (symbols.all { it.id == 6 }) { // Jackpot (3 sevens)
                                "🎰🎰🎰 JACKPOT! You won $winAmount coins! 🎰🎰🎰"
                            } else if (winAmount >= 15) {
                                "💎 AMAZING! You won $winAmount coins! 💎"
                            } else {
                                "🎉 You won $winAmount coins! 🎉"
                            }
                        }
                        isNearWin(symbols) -> {
                            val symbol = symbols[0].emoji
                            "😲 So close! Two $symbol symbols!"
                        }
                        else -> {
                            val nearMissMessages = listOf(
                                "😢 Better luck next time!",
                                "🎲 Try again!",
                                "🍀 Almost there!",
                                "💫 Next spin could be the one!",
                                "✨ Keep spinning!"
                            )
                            nearMissMessages.random()
                        }
                    }

                    JackpotSpinResult(
                        success = true,
                        betAmount = BET_AMOUNT,
                        winAmount = winAmount,
                        symbols = symbols,
                        message = message,
                        spinsToday = newSpins
                    )

                }.await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false

                    // Update game state
                    _gameState.value = JackpotGameState(
                        reels = symbols,
                        isSpinning = false,
                        lastSpinResult = result,
                        spinsToday = result.spinsToday
                    )

                    // Refresh user data
                    loadUserData()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to process spin: ${e.message}"

                    // Reset spinning state
                    val currentState = _gameState.value
                    if (currentState != null) {
                        _gameState.value = currentState.copy(isSpinning = false)
                    }
                }
            }
        }
    }

    // Helper to detect near wins (2 matching symbols)
    private fun isNearWin(symbols: List<SlotSymbol>): Boolean {
        return symbols[0].id == symbols[1].id ||
                symbols[0].id == symbols[2].id ||
                symbols[1].id == symbols[2].id
    }

    fun clearMessages() {
        _errorMessage.value = null
        val currentState = _gameState.value
        if (currentState != null) {
            _gameState.value = currentState.copy(lastSpinResult = null)
        }
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}