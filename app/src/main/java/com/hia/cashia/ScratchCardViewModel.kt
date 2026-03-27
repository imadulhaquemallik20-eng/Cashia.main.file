package com.hia.cashia

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ScratchCardViewModel : ViewModel() {

    private val userManager = UserManager()
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    // Constants
    companion object {
        const val MAX_CARDS_PER_DAY = 15
        const val MAX_COINS_PER_DAY = 50
        const val MIN_COINS_PER_CARD = 1
        const val MAX_COINS_PER_CARD = 5
        const val CARDS_BEFORE_COOLDOWN = 5
        const val COOLDOWN_MINUTES = 10L
        const val COOLDOWN_MILLIS = COOLDOWN_MINUTES * 60 * 1000
    }

    // LiveData
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _gameState = MutableLiveData<ScratchCardGameState?>()
    val gameState: LiveData<ScratchCardGameState?> = _gameState

    private val _playResult = MutableLiveData<ScratchCardPlayResult?>()
    val playResult: LiveData<ScratchCardPlayResult?> = _playResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadUserData() {
        val userId = userManager.getCurrentUserId() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val result = userManager.getUserData(userId)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    _userData.value = user
                    checkCooldownStatus(user)
                } else {
                    _errorMessage.value = "Failed to load user data"
                }
            }
        }
    }

    fun playScratchCard(userId: String, rewardAmount: Int) {
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDoc = usersCollection.document(userId)

                val result = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(userDoc)

                    // Get current values
                    val currentBalance = snapshot.getLong("coinBalance")?.toInt() ?: 0
                    val currentTotalEarned = snapshot.getLong("totalCoinsEarned")?.toInt() ?: 0
                    val cardsPlayedToday = snapshot.getLong("scratchCardsPlayedToday")?.toInt() ?: 0
                    val coinsEarnedToday = snapshot.getLong("scratchCoinsEarnedToday")?.toInt() ?: 0
                    val lastPlayDate = snapshot.getString("lastScratchCardDate") ?: ""
                    val cooldownUntil = snapshot.getLong("scratchCardCooldownUntil") ?: 0L
                    val cardsSinceCooldown = snapshot.getLong("scratchCardsSinceCooldown")?.toInt() ?: 0

                    val today = getTodayDate()
                    val currentTime = System.currentTimeMillis()

                    // Reset if it's a new day
                    val resetCardsPlayed = if (lastPlayDate == today) cardsPlayedToday else 0
                    val resetCoinsEarned = if (lastPlayDate == today) coinsEarnedToday else 0
                    val resetCardsSinceCooldown = if (lastPlayDate == today) cardsSinceCooldown else 0

                    // Check daily limits
                    if (resetCardsPlayed >= MAX_CARDS_PER_DAY) {
                        return@runTransaction ScratchCardPlayResult(
                            false, 0, "Daily card limit reached (${MAX_CARDS_PER_DAY})", false, 0L
                        )
                    }

                    if (resetCoinsEarned + rewardAmount > MAX_COINS_PER_DAY) {
                        return@runTransaction ScratchCardPlayResult(
                            false, 0, "This would exceed daily earning limit", false, 0L
                        )
                    }

                    // Check cooldown
                    if (currentTime < cooldownUntil) {
                        val remainingMinutes = ((cooldownUntil - currentTime) / 1000) / 60
                        val remainingSeconds = ((cooldownUntil - currentTime) / 1000) % 60
                        return@runTransaction ScratchCardPlayResult(
                            false, 0, "On cooldown! Wait ${remainingMinutes}m ${remainingSeconds}s", true, cooldownUntil
                        )
                    }

                    // Update counters
                    val newCardsPlayed = resetCardsPlayed + 1
                    val newCoinsEarned = resetCoinsEarned + rewardAmount
                    val newCardsSinceCooldown = resetCardsSinceCooldown + 1

                    // Check if we need to set cooldown
                    var newCooldownUntil = 0L
                    if (newCardsSinceCooldown >= CARDS_BEFORE_COOLDOWN) {
                        newCooldownUntil = currentTime + COOLDOWN_MILLIS
                    }

                    // Update user document
                    transaction.update(userDoc, "coinBalance", currentBalance + rewardAmount)
                    transaction.update(userDoc, "totalCoinsEarned", currentTotalEarned + rewardAmount)
                    transaction.update(userDoc, "scratchCardsPlayedToday", newCardsPlayed)
                    transaction.update(userDoc, "scratchCoinsEarnedToday", newCoinsEarned)
                    transaction.update(userDoc, "lastScratchCardDate", today)
                    transaction.update(userDoc, "scratchCardsSinceCooldown",
                        if (newCooldownUntil > 0) 0 else newCardsSinceCooldown)
                    transaction.update(userDoc, "scratchCardCooldownUntil", newCooldownUntil)

                    // Return success result
                    ScratchCardPlayResult(
                        true, rewardAmount, "Success", newCooldownUntil > 0, newCooldownUntil
                    )

                }.await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false

                    // Update game state
                    _gameState.value = ScratchCardGameState(
                        isOnCooldown = result.cooldownStarted,
                        cooldownEndTime = result.cooldownEndTime
                    )

                    // Set play result
                    _playResult.value = result

                    // Refresh user data
                    loadUserData()

                    // Update leaderboard
                    if (result.success && result.coinsEarned > 0) {
                        userManager.updateUserActivity(userId, result.coinsEarned)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to play: ${e.message}"
                    _playResult.value = ScratchCardPlayResult(false, 0, e.message ?: "Unknown error", false, 0L)
                }
            }
        }
    }

    fun checkCooldownStatus(user: User? = null) {
        if (user != null) {
            val currentTime = System.currentTimeMillis()
            val isOnCooldown = currentTime < user.scratchCardCooldownUntil

            _gameState.value = ScratchCardGameState(
                isOnCooldown = isOnCooldown,
                cooldownEndTime = user.scratchCardCooldownUntil
            )
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
    }

    fun clearPlayResult() {
        _playResult.value = null
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}