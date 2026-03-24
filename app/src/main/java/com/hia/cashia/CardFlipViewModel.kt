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

class CardFlipViewModel : ViewModel() {

    private val userManager = UserManager()
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    // Game constants
    companion object {
        const val MAX_MATCHES_PER_DAY = 20
        const val MAX_COINS_PER_DAY = 50
        const val MIN_COINS_PER_MATCH = 2
        const val MAX_COINS_PER_MATCH = 5
        const val MATCHES_BEFORE_COOLDOWN = 10
        const val COOLDOWN_MINUTES = 5L
        const val COOLDOWN_MILLIS = COOLDOWN_MINUTES * 60 * 1000
        const val GRID_SIZE = 16  // 4x4 grid
        const val PAIRS_COUNT = 8
    }

    // Card images (using emojis for simplicity)
    private val cardImages = listOf(
        "🐶", "🐱", "🐭", "🐹",  // Pets
        "🐰", "🦊", "🐻", "🐼",  // Forest animals
        "🐨", "🐯", "🦁", "🐮",  // Big animals
        "🐸", "🐙", "🦄", "🐧"   // Misc
    ).shuffled()

    // LiveData for game state
    private val _gameState = MutableLiveData<CardFlipGameState>()
    val gameState: LiveData<CardFlipGameState> = _gameState

    // LiveData for user data
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Stats
    private val _stats = MutableLiveData<CardFlipStats?>()
    val stats: LiveData<CardFlipStats?> = _stats

    init {
        initializeGame()
    }

    private fun initializeGame() {
        val shuffledCards = createShuffledCards()
        _gameState.value = CardFlipGameState(
            cards = shuffledCards,
            firstSelectedCardIndex = null,
            secondSelectedCardIndex = null,
            canFlip = true,
            matchesFound = 0
        )
    }

    private fun createShuffledCards(): List<FlipCard> {
        // Take first 8 images for 8 pairs
        val selectedImages = cardImages.take(PAIRS_COUNT)
        // Create pairs and shuffle
        val cards = mutableListOf<FlipCard>()
        selectedImages.forEachIndexed { index, image ->
            cards.add(FlipCard(index * 2, image))
            cards.add(FlipCard(index * 2 + 1, image))
        }
        return cards.shuffled().mapIndexed { index, card ->
            card.copy(id = index)
        }
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
                    checkCooldownStatus(user)
                }
            }
        }
    }

    private fun updateStats(user: User?) {
        if (user == null) return

        val today = getTodayDate()
        val matchesToday = if (user.lastCardFlipDate == today) user.cardFlipMatchesToday else 0
        val coinsToday = if (user.lastCardFlipDate == today) user.cardFlipCoinsEarnedToday else 0

        val remainingMatches = MAX_MATCHES_PER_DAY - matchesToday
        val remainingCoins = MAX_COINS_PER_DAY - coinsToday
        val canPlay = remainingMatches > 0 && remainingCoins > 0

        val currentTime = System.currentTimeMillis()
        val isOnCooldown = currentTime < user.cardFlipCooldownUntil
        val cooldownRemaining = if (isOnCooldown)
            (user.cardFlipCooldownUntil - currentTime) / 1000 else 0

        _stats.value = CardFlipStats(
            matchesPlayedToday = matchesToday,
            coinsEarnedToday = coinsToday,
            remainingMatches = remainingMatches,
            remainingCoins = remainingCoins,
            canPlay = canPlay,
            isOnCooldown = isOnCooldown,
            cooldownRemainingSeconds = cooldownRemaining
        )
    }

    private fun checkCooldownStatus(user: User?) {
        if (user == null) return

        val currentTime = System.currentTimeMillis()
        val isOnCooldown = currentTime < user.cardFlipCooldownUntil

        val currentState = _gameState.value
        if (currentState != null) {
            _gameState.value = currentState.copy(
                isOnCooldown = isOnCooldown,
                cooldownEndTime = user.cardFlipCooldownUntil
            )
        }
    }

    fun onCardClick(index: Int) {
        val currentState = _gameState.value ?: return
        val user = _userData.value ?: return
        val stats = _stats.value ?: return

        // Check if can play
        if (!stats.canPlay) {
            _errorMessage.value = if (stats.matchesPlayedToday >= MAX_MATCHES_PER_DAY) {
                "Daily match limit reached!"
            } else {
                "Daily earning limit reached!"
            }
            return
        }

        // Check cooldown
        if (currentState.isOnCooldown) {
            _errorMessage.value = "Game is on cooldown!"
            return
        }

        // Check if can flip
        if (!currentState.canFlip) return

        val card = currentState.cards[index]

        // Can't flip if card is already matched or flipped
        if (card.isMatched || card.isFlipped) return

        // Handle card selection
        if (currentState.firstSelectedCardIndex == null) {
            // First card selected
            val newCards = currentState.cards.toMutableList()
            newCards[index] = card.copy(isFlipped = true)

            _gameState.value = currentState.copy(
                cards = newCards,
                firstSelectedCardIndex = index,
                canFlip = true
            )
        } else if (currentState.secondSelectedCardIndex == null) {
            // Second card selected
            val firstIndex = currentState.firstSelectedCardIndex!!

            // Can't select same card twice
            if (firstIndex == index) return

            val newCards = currentState.cards.toMutableList()
            newCards[index] = card.copy(isFlipped = true)

            _gameState.value = currentState.copy(
                cards = newCards,
                secondSelectedCardIndex = index,
                canFlip = false
            )

            // Check for match
            checkForMatch(firstIndex, index)
        }
    }

    private fun checkForMatch(index1: Int, index2: Int) {
        val currentState = _gameState.value ?: return
        val card1 = currentState.cards[index1]
        val card2 = currentState.cards[index2]

        viewModelScope.launch {
            if (card1.imageUrl == card2.imageUrl) {
                // Match found!
                handleMatch(index1, index2)
            } else {
                // No match, flip back after delay
                delay(1000)
                flipBackCards(index1, index2)
            }
        }
    }

    private fun handleMatch(index1: Int, index2: Int) {
        val currentState = _gameState.value ?: return
        val newCards = currentState.cards.toMutableList()

        // Mark cards as matched
        newCards[index1] = newCards[index1].copy(isMatched = true)
        newCards[index2] = newCards[index2].copy(isMatched = true)

        val newMatchesFound = currentState.matchesFound + 1
        val canFlip = newMatchesFound < PAIRS_COUNT

        _gameState.value = currentState.copy(
            cards = newCards,
            firstSelectedCardIndex = null,
            secondSelectedCardIndex = null,
            canFlip = canFlip,
            matchesFound = newMatchesFound
        )

        // If all pairs found, award coins
        if (newMatchesFound == PAIRS_COUNT) {
            awardCoinsForWin()
        }
    }

    private fun flipBackCards(index1: Int, index2: Int) {
        val currentState = _gameState.value ?: return
        val newCards = currentState.cards.toMutableList()

        newCards[index1] = newCards[index1].copy(isFlipped = false)
        newCards[index2] = newCards[index2].copy(isFlipped = false)

        _gameState.value = currentState.copy(
            cards = newCards,
            firstSelectedCardIndex = null,
            secondSelectedCardIndex = null,
            canFlip = true
        )
    }

    private fun awardCoinsForWin() {
        val userId = userManager.getCurrentUserId() ?: return
        val currentState = _gameState.value ?: return

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDoc = usersCollection.document(userId)

                val result = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(userDoc)

                    // Get current values
                    val currentBalance = snapshot.getLong("coinBalance")?.toInt() ?: 0
                    val currentTotalEarned = snapshot.getLong("totalCoinsEarned")?.toInt() ?: 0
                    val matchesToday = snapshot.getLong("cardFlipMatchesToday")?.toInt() ?: 0
                    val coinsToday = snapshot.getLong("cardFlipCoinsEarnedToday")?.toInt() ?: 0
                    val lastPlayDate = snapshot.getString("lastCardFlipDate") ?: ""
                    val cooldownUntil = snapshot.getLong("cardFlipCooldownUntil") ?: 0L
                    val matchesSinceCooldown = snapshot.getLong("cardFlipMatchesSinceCooldown")?.toInt() ?: 0

                    val today = getTodayDate()
                    val currentTime = System.currentTimeMillis()

                    // Reset if it's a new day
                    val resetMatches = if (lastPlayDate == today) matchesToday else 0
                    val resetCoins = if (lastPlayDate == today) coinsToday else 0
                    val resetMatchesSinceCooldown = if (lastPlayDate == today) matchesSinceCooldown else 0

                    // Calculate reward (8 matches = 1 game completion)
                    val coinsEarned = (MIN_COINS_PER_MATCH..MAX_COINS_PER_MATCH).random() * PAIRS_COUNT

                    // Check limits
                    if (resetMatches + PAIRS_COUNT > MAX_MATCHES_PER_DAY) {
                        return@runTransaction CardFlipPlayResult(false, 0, "Daily match limit reached", false, 0L)
                    }

                    if (resetCoins + coinsEarned > MAX_COINS_PER_DAY) {
                        return@runTransaction CardFlipPlayResult(false, 0, "Daily earning limit reached", false, 0L)
                    }

                    // Update counters
                    val newMatches = resetMatches + PAIRS_COUNT
                    val newCoins = resetCoins + coinsEarned
                    val newMatchesSinceCooldown = resetMatchesSinceCooldown + PAIRS_COUNT

                    // Check if we need to set cooldown
                    var newCooldownUntil = 0L
                    if (newMatchesSinceCooldown >= MATCHES_BEFORE_COOLDOWN) {
                        newCooldownUntil = currentTime + COOLDOWN_MILLIS
                    }

                    // Update user document
                    transaction.update(userDoc, "coinBalance", currentBalance + coinsEarned)
                    transaction.update(userDoc, "totalCoinsEarned", currentTotalEarned + coinsEarned)
                    transaction.update(userDoc, "cardFlipMatchesToday", newMatches)
                    transaction.update(userDoc, "cardFlipCoinsEarnedToday", newCoins)
                    transaction.update(userDoc, "lastCardFlipDate", today)
                    transaction.update(userDoc, "cardFlipMatchesSinceCooldown",
                        if (newCooldownUntil > 0) 0 else newMatchesSinceCooldown)
                    transaction.update(userDoc, "cardFlipCooldownUntil", newCooldownUntil)

                    CardFlipPlayResult(
                        success = true,
                        coinsEarned = coinsEarned,
                        message = "You won $coinsEarned coins!",
                        cooldownStarted = newCooldownUntil > 0,
                        cooldownEndTime = newCooldownUntil,
                        matchesCompleted = newMatches
                    )

                }.await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false

                    // Update game state with result
                    val newState = _gameState.value?.copy(
                        lastPlayResult = result,
                        isOnCooldown = result.cooldownStarted,
                        cooldownEndTime = result.cooldownEndTime,
                        matchesFound = 0  // Reset for next game
                    )
                    _gameState.value = newState

                    // Refresh user data
                    loadUserData()

                    // Initialize new game
                    initializeGame()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to award coins: ${e.message}"
                }
            }
        }
    }

    fun resetGame() {
        initializeGame()
    }

    fun clearMessages() {
        _errorMessage.value = null
        val currentState = _gameState.value
        if (currentState != null) {
            _gameState.value = currentState.copy(lastPlayResult = null)
        }
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}