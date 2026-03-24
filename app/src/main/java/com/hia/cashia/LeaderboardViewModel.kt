package com.hia.cashia

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class LeaderboardViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    // LiveData
    private val _leaderboardEntries = MutableLiveData<List<LeaderboardEntry>>()
    val leaderboardEntries: LiveData<List<LeaderboardEntry>> = _leaderboardEntries

    private val _currentUserRank = MutableLiveData<LeaderboardEntry?>()
    val currentUserRank: LiveData<LeaderboardEntry?> = _currentUserRank

    private val _searchResult = MutableLiveData<SearchResult?>()
    val searchResult: LiveData<SearchResult?> = _searchResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _selectedCategory = MutableLiveData(LeaderboardCategory.ALL_TIME)
    val selectedCategory: LiveData<LeaderboardCategory> = _selectedCategory

    private val currentUserId = UserManager().getCurrentUserId()
    private var previousUserRank = -1

    init {
        loadLeaderboard(LeaderboardCategory.ALL_TIME)
    }

    fun loadLeaderboard(category: LeaderboardCategory) {
        _selectedCategory.value = category
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val query = when (category) {
                    LeaderboardCategory.DAILY -> {
                        usersCollection
                            .orderBy("dailyCoins", Query.Direction.DESCENDING)
                            .limit(100)
                    }
                    LeaderboardCategory.WEEKLY -> {
                        usersCollection
                            .orderBy("weeklyCoins", Query.Direction.DESCENDING)
                            .limit(100)
                    }
                    LeaderboardCategory.ALL_TIME -> {
                        usersCollection
                            .orderBy("totalCoinsEarned", Query.Direction.DESCENDING)
                            .limit(100)
                    }
                }

                val snapshot = query.get().await()
                val entries = mutableListOf<LeaderboardEntry>()
                var rank = 1

                for (document in snapshot.documents) {
                    val userId = document.id
                    val username = document.getString("username") ?: "Anonymous"
                    val avatar = document.getString("avatar") ?: "👤"

                    val totalCoins = when (category) {
                        LeaderboardCategory.DAILY -> document.getLong("dailyCoins")?.toInt() ?: 0
                        LeaderboardCategory.WEEKLY -> document.getLong("weeklyCoins")?.toInt() ?: 0
                        LeaderboardCategory.ALL_TIME -> document.getLong("totalCoinsEarned")?.toInt() ?: 0
                    }

                    val dailyCoins = document.getLong("dailyCoins")?.toInt() ?: 0
                    val weeklyCoins = document.getLong("weeklyCoins")?.toInt() ?: 0
                    val lastActive = document.getLong("lastActive") ?: System.currentTimeMillis()

                    entries.add(
                        LeaderboardEntry(
                            userId = userId,
                            username = username,
                            avatar = avatar,
                            totalCoins = totalCoins,
                            dailyCoins = dailyCoins,
                            weeklyCoins = weeklyCoins,
                            rank = rank,
                            lastActive = lastActive
                        )
                    )
                    rank++
                }

                // Find current user's rank
                val currentUserEntry = entries.find { it.userId == currentUserId }

                if (currentUserEntry != null) {
                    val currentRank = currentUserEntry.rank
                    if (previousUserRank != -1 && currentRank < previousUserRank) {
                        // User improved their rank! Send notification
                        sendRankImprovementNotification(currentRank, previousUserRank)
                    }
                    previousUserRank = currentRank
                }

                withContext(Dispatchers.Main) {
                    _leaderboardEntries.value = entries
                    _currentUserRank.value = currentUserEntry
                    _isLoading.value = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to load leaderboard: ${e.message}"
                }
            }
        }
    }

    // Add this function to send rank improvement notification
    private fun sendRankImprovementNotification(newRank: Int, oldRank: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@launch
                val snapshot = usersCollection.document(userId).get().await()
                val fcmToken = snapshot.getString("fcmToken")

                if (fcmToken != null && fcmToken.isNotEmpty()) {
                    val notification = mapOf(
                        "token" to fcmToken,
                        "title" to "🏆 Rank Up!",
                        "body" to "Congratulations! You've climbed from #$oldRank to #$newRank on the leaderboard!",
                        "type" to "leaderboard",
                        "newRank" to newRank.toString(),
                        "oldRank" to oldRank.toString(),
                        "timestamp" to System.currentTimeMillis()
                    )

                    val db = FirebaseFirestore.getInstance()
                    db.collection("notifications").add(notification)

                    // Also show local notification
                    showLocalNotification(
                        "🏆 Rank Up!",
                        "You've climbed from #$oldRank to #$newRank on the leaderboard!"
                    )
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun showLocalNotification(title: String, message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            // This will be handled by a notification helper
            // For now, we'll just log
            android.util.Log.d("LeaderboardVM", "Notification: $title - $message")
        }
    }

    fun searchUser(username: String) {
        if (username.length < 3) {
            _searchResult.value = SearchResult(false, message = "Enter at least 3 characters")
            return
        }

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Search by username (case-insensitive)
                val snapshot = usersCollection
                    .whereGreaterThanOrEqualTo("username", username)
                    .whereLessThanOrEqualTo("username", username + '\uf8ff')
                    .limit(1)
                    .get()
                    .await()

                if (snapshot.documents.isNotEmpty()) {
                    val doc = snapshot.documents[0]

                    val entry = LeaderboardEntry(
                        userId = doc.id,
                        username = doc.getString("username") ?: "Anonymous",
                        avatar = doc.getString("avatar") ?: "👤",
                        totalCoins = doc.getLong("totalCoinsEarned")?.toInt() ?: 0,
                        dailyCoins = doc.getLong("dailyCoins")?.toInt() ?: 0,
                        weeklyCoins = doc.getLong("weeklyCoins")?.toInt() ?: 0,
                        rank = 0,
                        lastActive = doc.getLong("lastActive") ?: System.currentTimeMillis()
                    )

                    withContext(Dispatchers.Main) {
                        _searchResult.value = SearchResult(true, entry)
                        _isLoading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _searchResult.value = SearchResult(false, message = "User not found")
                        _isLoading.value = false
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Search failed: ${e.message}"
                }
            }
        }
    }

    fun clearSearch() {
        _searchResult.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
    }
}