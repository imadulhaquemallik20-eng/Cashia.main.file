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

    // LiveData for user data
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    // LiveData for game state
    private val _gameState = MutableLiveData<ScratchCardGameState?>()
    val gameState: LiveData<ScratchCardGameState?> = _gameState

    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadUserData() {
        val userId = userManager.getCurrentUserId() ?: return

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = userManager.getUserData(userId)

            withContext(Dispatchers.Main) {
                _isLoading.value = false
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

    fun playScratchCard(userId: String) {
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
                    if (resetCardsPlayed >= ScratchCardActivity.MAX_SCRATCH_CARDS_PER_DAY) {
                        return@runTransaction ScratchCardPlayResult(false, 0, "Daily card limit reached (${ScratchCardActivity.MAX_SCRATCH_CARDS_PER_DAY})", false, 0L)
                    }

                    if (resetCoinsEarned >= ScratchCardActivity.MAX_COINS_PER_DAY_FROM_SCRATCH) {
                        return@runTransaction ScratchCardPlayResult(false, 0, "Daily earning limit reached (${ScratchCardActivity.MAX_COINS_PER_DAY_FROM_SCRATCH} coins)", false, 0L)
                    }

                    // Check cooldown
                    if (currentTime < cooldownUntil) {
                        val remainingMinutes = ((cooldownUntil - currentTime) / 1000) / 60
                        val remainingSeconds = ((cooldownUntil - currentTime) / 1000) % 60
                        return@runTransaction ScratchCardPlayResult(
                            false,
                            0,
                            "On cooldown! Wait ${remainingMinutes}m ${remainingSeconds}s",
                            true,
                            cooldownUntil
                        )
                    }

                    // Calculate reward
                    val coinsEarned = (ScratchCardActivity.MIN_COINS_PER_CARD..ScratchCardActivity.MAX_COINS_PER_CARD).random()

                    // Check if this would exceed daily earning limit
                    if (resetCoinsEarned + coinsEarned > ScratchCardActivity.MAX_COINS_PER_DAY_FROM_SCRATCH) {
                        return@runTransaction ScratchCardPlayResult(false, 0, "This would exceed daily earning limit", false, 0L)
                    }

                    // Update counters
                    val newCardsPlayed = resetCardsPlayed + 1
                    val newCoinsEarned = resetCoinsEarned + coinsEarned
                    val newCardsSinceCooldown = resetCardsSinceCooldown + 1

                    // Check if we need to set cooldown
                    var newCooldownUntil = 0L
                    if (newCardsSinceCooldown >= ScratchCardActivity.CARDS_BEFORE_COOLDOWN) {
                        newCooldownUntil = currentTime + ScratchCardActivity.COOLDOWN_MILLIS
                    }

                    // Update user document
                    transaction.update(userDoc, "coinBalance", currentBalance + coinsEarned)
                    transaction.update(userDoc, "totalCoinsEarned", currentTotalEarned + coinsEarned)
                    transaction.update(userDoc, "scratchCardsPlayedToday", newCardsPlayed)
                    transaction.update(userDoc, "scratchCoinsEarnedToday", newCoinsEarned)
                    transaction.update(userDoc, "lastScratchCardDate", today)
                    transaction.update(userDoc, "scratchCardsSinceCooldown",
                        if (newCooldownUntil > 0) 0 else newCardsSinceCooldown)
                    transaction.update(userDoc, "scratchCardCooldownUntil", newCooldownUntil)

                    // Return success result
                    ScratchCardPlayResult(true, coinsEarned, "Success", newCooldownUntil > 0, newCooldownUntil)

                }.await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false

                    // Update game state
                    _gameState.value = ScratchCardGameState(
                        lastPlayResult = result,
                        isOnCooldown = result.cooldownStarted,
                        cooldownEndTime = result.cooldownEndTime
                    )

                    // Refresh user data
                    loadUserData()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to play: ${e.message}"
                }
            }
        }
    }

    fun checkCooldownStatus(user: User? = null) {
        if (user != null) {
            val currentTime = System.currentTimeMillis()
            val isOnCooldown = currentTime < user.scratchCardCooldownUntil

            _gameState.value = ScratchCardGameState(
                lastPlayResult = null,
                isOnCooldown = isOnCooldown,
                cooldownEndTime = user.scratchCardCooldownUntil
            )
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
    }

    fun clearGameState() {
        _gameState.value = null
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}