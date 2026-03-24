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

class ProfileViewModel : ViewModel() {

    private val userManager = UserManager()
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")
    private val currentUserId = userManager.getCurrentUserId()

    // LiveData
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _userStats = MutableLiveData<UserStats?>()
    val userStats: LiveData<UserStats?> = _userStats

    private val _achievements = MutableLiveData<List<Achievement>>()
    val achievements: LiveData<List<Achievement>> = _achievements

    private val _transactions = MutableLiveData<List<Transaction>>()
    val transactions: LiveData<List<Transaction>> = _transactions

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _selectedAvatar = MutableLiveData<String>("👤")
    val selectedAvatar: LiveData<String> = _selectedAvatar

    init {
        loadUserData()
    }

    fun loadUserData() {
        val userId = currentUserId ?: return

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = usersCollection.document(userId).get().await()
                val user = snapshot.toObject(User::class.java)

                withContext(Dispatchers.Main) {
                    _userData.value = user
                    if (user != null) {
                        _selectedAvatar.value = user.avatar
                        calculateStats(user)
                        checkAchievements(user)
                        loadTransactions(user)
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to load profile: ${e.message}"
                }
            }
        }
    }

    private fun calculateStats(user: User) {
        val today = getTodayDate()
        val calendar = Calendar.getInstance()
        val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)

        // Calculate today's earnings
        val todayEarnings = when {
            user.lastAdWatchDate == today -> user.adsWatchedToday * 2
            else -> 0
        } + when {
            user.lastScratchCardDate == today -> user.scratchCoinsEarnedToday
            else -> 0
        } + when {
            user.lastCardFlipDate == today -> user.cardFlipCoinsEarnedToday
            else -> 0
        } + when {
            user.lastJackpotDate == today -> user.jackpotWinningsToday
            else -> 0
        }

        // Calculate today's games
        val todayGames = when {
            user.lastAdWatchDate == today -> user.adsWatchedToday
            else -> 0
        } + when {
            user.lastScratchCardDate == today -> user.scratchCardsPlayedToday
            else -> 0
        } + when {
            user.lastCardFlipDate == today -> user.cardFlipGamesCompleted
            else -> 0
        } + when {
            user.lastJackpotDate == today -> user.jackpotSpinsToday
            else -> 0
        }

        val stats = UserStats(
            totalCoinsEarned = user.totalCoinsEarned,
            currentBalance = user.coinBalance,
            loginStreak = user.loginStreak,
            totalGamesPlayed = user.totalGamesPlayed,
            adsWatched = user.adsWatchedTotal,
            scratchCardsPlayed = user.scratchCardsPlayedTotal,
            cardFlipGamesCompleted = user.cardFlipGamesCompleted,
            jackpotSpins = user.jackpotSpinsTotal,
            todayEarnings = todayEarnings,
            todayGames = todayGames,
            weeklyEarnings = user.weeklyCoins,
            weeklyGames = 0 // Calculate if needed
        )

        _userStats.value = stats
    }

    private fun checkAchievements(user: User) {
        val updatedAchievements = ACHIEVEMENTS.map { achievement ->
            val progress = when (achievement.id) {
                "first_login" -> if (user.createdAt > 0) 1 else 0
                "streak_7" -> user.loginStreak
                "streak_30" -> user.loginStreak
                "ads_100" -> user.adsWatchedTotal
                "scratch_50" -> user.scratchCardsPlayedTotal
                "cardflip_25" -> user.cardFlipGamesCompleted
                "jackpot_10" -> user.jackpotWinsTotal
                "jackpot_3sevens" -> user.jackpotSevensHit
                "earn_1000" -> user.totalCoinsEarned
                "earn_10000" -> user.totalCoinsEarned
                else -> 0
            }

            val isUnlocked = user.achievements[achievement.id] == true

            achievement.copy(
                progress = progress,
                isUnlocked = isUnlocked
            )
        }

        _achievements.value = updatedAchievements
    }

    private fun loadTransactions(user: User) {
        _transactions.value = user.recentTransactions.takeLast(20)
    }

    fun updateUsername(newUsername: String) {
        val userId = currentUserId ?: return

        if (newUsername.length < 3) {
            _errorMessage.value = "Username must be at least 3 characters"
            return
        }

        if (newUsername.length > 20) {
            _errorMessage.value = "Username must be less than 20 characters"
            return
        }

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                usersCollection.document(userId)
                    .update("username", newUsername)
                    .await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _successMessage.value = "Username updated successfully!"
                    loadUserData() // Refresh data
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to update username: ${e.message}"
                }
            }
        }
    }

    fun updateAvatar(avatar: String) {
        val userId = currentUserId ?: return

        _selectedAvatar.value = avatar
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                usersCollection.document(userId)
                    .update("avatar", avatar)
                    .await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _successMessage.value = "Avatar updated!"
                    loadUserData() // Refresh data
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to update avatar: ${e.message}"
                }
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}