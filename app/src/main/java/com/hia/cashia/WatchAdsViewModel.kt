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

class WatchAdsViewModel : ViewModel() {

    private val userManager = UserManager()
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    companion object {
        const val MAX_ADS_PER_DAY = 50
        const val COINS_PER_AD = 2
    }

    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _adStatus = MutableLiveData<AdStatus?>()
    val adStatus: LiveData<AdStatus?> = _adStatus

    private val _adResult = MutableLiveData<AdResult?>()
    val adResult: LiveData<AdResult?> = _adResult

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
                    loadAdStatus(user)
                } else {
                    _errorMessage.value = "Failed to load user data"
                }
            }
        }
    }

    private fun loadAdStatus(user: User?) {
        if (user == null) return

        val today = getTodayDate()
        val adsWatchedToday = if (user.lastAdWatchDate == today) user.adsWatchedToday else 0
        val remainingAds = MAX_ADS_PER_DAY - adsWatchedToday

        _adStatus.value = AdStatus(
            adsWatchedToday = adsWatchedToday,
            remainingAds = remainingAds,
            canWatch = remainingAds > 0
        )
    }

    fun processAdReward(userId: String) {
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDoc = usersCollection.document(userId)
                val today = getTodayDate()

                val result = FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(userDoc)

                    val currentBalance = snapshot.getLong("coinBalance")?.toInt() ?: 0
                    val currentTotalEarned = snapshot.getLong("totalCoinsEarned")?.toInt() ?: 0
                    val adsWatchedToday = snapshot.getLong("adsWatchedToday")?.toInt() ?: 0
                    val lastAdWatchDate = snapshot.getString("lastAdWatchDate") ?: ""

                    val resetAdsWatched = if (lastAdWatchDate == today) adsWatchedToday else 0

                    if (resetAdsWatched >= MAX_ADS_PER_DAY) {
                        return@runTransaction AdResult(
                            success = false,
                            message = "Daily ad limit reached",
                            coinsEarned = 0,
                            remainingAds = 0
                        )
                    }

                    val newAdsWatched = resetAdsWatched + 1
                    val newBalance = currentBalance + COINS_PER_AD
                    val newTotalEarned = currentTotalEarned + COINS_PER_AD

                    transaction.update(userDoc, "coinBalance", newBalance)
                    transaction.update(userDoc, "totalCoinsEarned", newTotalEarned)
                    transaction.update(userDoc, "adsWatchedToday", newAdsWatched)
                    transaction.update(userDoc, "lastAdWatchDate", today)

                    AdResult(
                        success = true,
                        message = "+$COINS_PER_AD coins earned!",
                        coinsEarned = COINS_PER_AD,
                        remainingAds = MAX_ADS_PER_DAY - newAdsWatched
                    )
                }.await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _adResult.value = result
                    loadUserData()

                    // Update leaderboard
                    userManager.updateUserActivity(userId, COINS_PER_AD)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to process reward: ${e.message}"
                }
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
    }

    fun clearAdResult() {
        _adResult.value = null
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}

data class AdStatus(
    val adsWatchedToday: Int,
    val remainingAds: Int,
    val canWatch: Boolean
)

data class AdResult(
    val success: Boolean,
    val message: String,
    val coinsEarned: Int,
    val remainingAds: Int
)