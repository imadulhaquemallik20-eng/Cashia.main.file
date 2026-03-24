package com.hia.cashia

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FieldValue

class UserManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    // Maximum ads per day constant
    companion object {
        const val MAX_ADS_PER_DAY = 50
        const val COINS_PER_AD = 2
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    // In createUserProfile function in UserManager.kt, update to:
    suspend fun createUserProfile(userId: String, email: String, username: String): Result<Boolean> {
        return try {
            val today = getTodayDate()
            val weekNumber = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR).toString()

            val user = User(
                userId = userId,
                email = email,
                username = username,
                avatar = "👤",
                coinBalance = 50,
                totalCoinsEarned = 50,
                lastLoginDate = today,
                loginStreak = 1,
                createdAt = System.currentTimeMillis(),
                dailyCoins = 50,
                weeklyCoins = 50,
                lastDailyReset = today,
                lastWeeklyReset = weekNumber
            )

            usersCollection.document(userId).set(user).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserData(userId: String): Result<User?> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val user = document.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCoinBalance(userId: String, newBalance: Int): Result<Boolean> {
        return try {
            usersCollection.document(userId)
                .update("coinBalance", newBalance)
                .await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Updated addCoins method to also track ad count
    suspend fun addCoinsFromAd(userId: String): Result<AdWatchResult> {
        return try {
            val userDoc = usersCollection.document(userId)

            val result = db.runTransaction { transaction ->
                val snapshot = transaction.get(userDoc)

                val currentBalance = snapshot.getLong("coinBalance")?.toInt() ?: 0
                val currentTotalEarned = snapshot.getLong("totalCoinsEarned")?.toInt() ?: 0
                val adsWatchedToday = snapshot.getLong("adsWatchedToday")?.toInt() ?: 0
                val lastAdWatchDate = snapshot.getString("lastAdWatchDate") ?: ""
                val today = getTodayDate()

                val currentAdsCount = if (lastAdWatchDate == today) {
                    adsWatchedToday
                } else {
                    0
                }

                if (currentAdsCount >= MAX_ADS_PER_DAY) {
                    return@runTransaction AdWatchResult(false, 0, currentAdsCount, MAX_ADS_PER_DAY, "Daily limit reached")
                }

                val newAdsCount = currentAdsCount + 1
                val newBalance = currentBalance + COINS_PER_AD
                val newTotalEarned = currentTotalEarned + COINS_PER_AD

                transaction.update(userDoc, "coinBalance", newBalance)
                transaction.update(userDoc, "totalCoinsEarned", newTotalEarned)
                transaction.update(userDoc, "adsWatchedToday", newAdsCount)
                transaction.update(userDoc, "lastAdWatchDate", today)

                AdWatchResult(true, COINS_PER_AD, newAdsCount, MAX_ADS_PER_DAY, "Success")

            }.await()

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Regular addCoins for other rewards (daily login, games, etc.)
    suspend fun addCoins(userId: String, coins: Int): Result<Boolean> {
        return try {
            val userDoc = usersCollection.document(userId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(userDoc)
                val currentBalance = snapshot.getLong("coinBalance")?.toInt() ?: 0
                val currentTotalEarned = snapshot.getLong("totalCoinsEarned")?.toInt() ?: 0

                transaction.update(userDoc, "coinBalance", currentBalance + coins)
                transaction.update(userDoc, "totalCoinsEarned", currentTotalEarned + coins)
            }.await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get today's ad watch status
    suspend fun getAdWatchStatus(userId: String): Result<AdWatchStatus> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()

            val adsWatchedToday = snapshot.getLong("adsWatchedToday")?.toInt() ?: 0
            val lastAdWatchDate = snapshot.getString("lastAdWatchDate") ?: ""
            val today = getTodayDate()

            // Reset count if it's a new day
            val currentCount = if (lastAdWatchDate == today) {
                adsWatchedToday
            } else {
                0
            }

            val remainingAds = MAX_ADS_PER_DAY - currentCount
            val canWatchAds = remainingAds > 0

            Result.success(AdWatchStatus(currentCount, MAX_ADS_PER_DAY, remainingAds, canWatchAds))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkAndUpdateDailyLogin(userId: String): Result<DailyLoginResult> {
        return try {
            val userDoc = usersCollection.document(userId)
            val snapshot = userDoc.get().await()

            val lastLogin = snapshot.getString("lastLoginDate") ?: ""
            val currentStreak = snapshot.getLong("loginStreak")?.toInt() ?: 0
            val today = getTodayDate()

            val result = when {
                lastLogin == today -> {
                    DailyLoginResult(false, 0, currentStreak)
                }
                isYesterday(lastLogin) -> {
                    val newStreak = currentStreak + 1
                    val bonus = calculateDailyBonus(newStreak)

                    userDoc.update(
                        mapOf(
                            "lastLoginDate" to today,
                            "loginStreak" to newStreak
                        )
                    ).await()

                    addCoins(userId, bonus)

                    DailyLoginResult(true, bonus, newStreak)
                }
                else -> {
                    val newStreak = 1
                    val bonus = calculateDailyBonus(newStreak)

                    userDoc.update(
                        mapOf(
                            "lastLoginDate" to today,
                            "loginStreak" to newStreak
                        )
                    ).await()

                    addCoins(userId, bonus)

                    DailyLoginResult(true, bonus, newStreak)
                }
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserActivity(userId: String, coinsEarned: Int) {
        try {
            val userDoc = usersCollection.document(userId)
            val today = getTodayDate()
            val weekNumber = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR).toString()

            db.runTransaction { transaction ->
                val snapshot = transaction.get(userDoc)

                val currentDaily = snapshot.getLong("dailyCoins")?.toInt() ?: 0
                val currentWeekly = snapshot.getLong("weeklyCoins")?.toInt() ?: 0
                val lastDailyReset = snapshot.getString("lastDailyReset") ?: ""
                val lastWeeklyReset = snapshot.getString("lastWeeklyReset") ?: ""
                val gamesPlayed = snapshot.getLong("totalGamesPlayed")?.toInt() ?: 0

                // Reset daily if new day
                val newDaily = if (lastDailyReset != today) coinsEarned else currentDaily + coinsEarned

                // Reset weekly if new week
                val newWeekly = if (lastWeeklyReset != weekNumber) coinsEarned else currentWeekly + coinsEarned

                transaction.update(userDoc, "dailyCoins", newDaily)
                transaction.update(userDoc, "weeklyCoins", newWeekly)
                transaction.update(userDoc, "lastDailyReset", today)
                transaction.update(userDoc, "lastWeeklyReset", weekNumber)
                transaction.update(userDoc, "lastActive", System.currentTimeMillis())
                transaction.update(userDoc, "totalGamesPlayed", gamesPlayed + 1)

            }.await()
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    private fun calculateDailyBonus(streak: Int): Int {
        return when {
            streak >= 30 -> 100
            streak >= 14 -> 50
            streak >= 7 -> 25
            else -> 10
        }
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun isYesterday(dateString: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(dateString)
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.time

            dateFormat.format(date) == dateFormat.format(yesterday)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateGameStats(userId: String, gameType: String, coinsEarned: Int) {
        try {
            val userDoc = usersCollection.document(userId)
            val today = getTodayDate()

            // Just update everything in one go
            when (gameType) {
                "ad" -> {
                    userDoc.update(
                        "adsWatchedTotal", FieldValue.increment(1L),  // Changed to 1L
                        "totalGamesPlayed", FieldValue.increment(1L),  // Changed to 1L
                        "lastActive", System.currentTimeMillis()
                    ).await()
                }
                "scratch" -> {
                    userDoc.update(
                        "scratchCardsPlayedTotal", FieldValue.increment(1L),  // Changed to 1L
                        "scratchCoinsEarnedTotal", FieldValue.increment(coinsEarned.toLong()),  // Convert to Long
                        "totalGamesPlayed", FieldValue.increment(1L),  // Changed to 1L
                        "lastActive", System.currentTimeMillis()
                    ).await()
                }
                "cardflip" -> {
                    userDoc.update(
                        "cardFlipGamesCompleted", FieldValue.increment(1L),  // Changed to 1L
                        "cardFlipCoinsEarnedTotal", FieldValue.increment(coinsEarned.toLong()),  // Convert to Long
                        "totalGamesPlayed", FieldValue.increment(1L),  // Changed to 1L
                        "lastActive", System.currentTimeMillis()
                    ).await()
                }
                "jackpot" -> {
                    // Build updates list
                    val updates = mutableMapOf<String, Any>(
                        "jackpotSpinsTotal" to FieldValue.increment(1L),
                        "totalGamesPlayed" to FieldValue.increment(1L),
                        "lastActive" to System.currentTimeMillis()
                    )

                    // Add winnings if any
                    if (coinsEarned > 0) {
                        updates["jackpotWinningsTotal"] = FieldValue.increment(coinsEarned.toLong())
                        updates["jackpotWinsTotal"] = FieldValue.increment(1L)
                    }

                    userDoc.update(updates).await()
                }
            }

            // Always check achievements after updating
            checkAchievements(userId)

        } catch (e: Exception) {
            // Ignore errors
            e.printStackTrace()  // For debugging
        }
    }

    suspend fun checkAchievements(userId: String) {
        try {
            val userDoc = usersCollection.document(userId)
            val snapshot = userDoc.get().await()

            val achievements = mutableMapOf<String, Boolean>()
            val progress = mutableMapOf<String, Int>()

            // Get values as Long then convert to Int
            val totalEarned = (snapshot.getLong("totalCoinsEarned") ?: 0).toInt()
            val loginStreak = (snapshot.getLong("loginStreak") ?: 0).toInt()
            val adsWatched = (snapshot.getLong("adsWatchedTotal") ?: 0).toInt()
            val scratchPlayed = (snapshot.getLong("scratchCardsPlayedTotal") ?: 0).toInt()
            val cardFlipGames = (snapshot.getLong("cardFlipGamesCompleted") ?: 0).toInt()
            val jackpotWins = (snapshot.getLong("jackpotWinsTotal") ?: 0).toInt()
            val sevensHit = (snapshot.getLong("jackpotSevensHit") ?: 0).toInt()

            // First login
            achievements["first_login"] = true
            progress["first_login"] = 1

            // Streak achievements
            achievements["streak_7"] = loginStreak >= 7
            progress["streak_7"] = loginStreak

            achievements["streak_30"] = loginStreak >= 30
            progress["streak_30"] = loginStreak

            // Ad achievement
            achievements["ads_100"] = adsWatched >= 100
            progress["ads_100"] = adsWatched

            // Scratch achievement
            achievements["scratch_50"] = scratchPlayed >= 50
            progress["scratch_50"] = scratchPlayed

            // Card flip achievement
            achievements["cardflip_25"] = cardFlipGames >= 25
            progress["cardflip_25"] = cardFlipGames

            // Jackpot achievements
            achievements["jackpot_10"] = jackpotWins >= 10
            progress["jackpot_10"] = jackpotWins

            achievements["jackpot_3sevens"] = sevensHit >= 1
            progress["jackpot_3sevens"] = sevensHit

            // Earnings achievements
            achievements["earn_1000"] = totalEarned >= 1000
            progress["earn_1000"] = totalEarned

            achievements["earn_10000"] = totalEarned >= 10000
            progress["earn_10000"] = totalEarned

            // Update user document
            userDoc.update(
                mapOf(
                    "achievements" to achievements,
                    "achievementProgress" to progress
                )
            ).await()

        } catch (e: Exception) {
            // Log error
            e.printStackTrace()
        }
    }

    suspend fun trackAdWatched(userId: String, coinsEarned: Int) {
        try {
            val userDoc = usersCollection.document(userId)
            val today = getTodayDate()

            db.runTransaction { transaction ->
                val snapshot = transaction.get(userDoc)

                val currentTotal = snapshot.getLong("adsWatchedTotal")?.toInt() ?: 0
                val currentToday = snapshot.getLong("adsWatchedToday")?.toInt() ?: 0
                val lastDate = snapshot.getString("lastAdWatchDate") ?: ""

                // Reset if new day
                val newToday = if (lastDate == today) currentToday + 1 else 1

                transaction.update(userDoc, "adsWatchedTotal", currentTotal + 1)
                transaction.update(userDoc, "adsWatchedToday", newToday)
                transaction.update(userDoc, "lastAdWatchDate", today)

            }.await()
        } catch (e: Exception) {
            // Log error but don't crash
        }
    }

    // Check and send daily login reminder
    suspend fun sendDailyLoginReminder(userId: String) {
        try {
            val snapshot = usersCollection.document(userId).get().await()
            val lastLogin = snapshot.getString("lastLoginDate") ?: ""
            val today = getTodayDate()

            if (lastLogin != today) {
                // User hasn't logged in today, send reminder
                val fcmToken = snapshot.getString("fcmToken")
                if (fcmToken != null) {
                    sendFCMNotification(
                        fcmToken,
                        "Daily Login Bonus",
                        "Don't miss your daily streak! Claim your bonus now!",
                        "daily_login"
                    )
                }
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    // Send cooldown completed notification
    suspend fun sendCooldownNotification(userId: String, gameType: String) {
        try {
            val snapshot = usersCollection.document(userId).get().await()
            val fcmToken = snapshot.getString("fcmToken")

            if (fcmToken != null) {
                val message = when (gameType) {
                    "scratch" -> "Ready to scratch again! Your cooldown is over."
                    "cardflip" -> "Your Card Flip cooldown is complete! Play again!"
                    else -> "Cooldown completed! Come play again!"
                }

                sendFCMNotification(
                    fcmToken,
                    "🎮 Cooldown Completed",
                    message,
                    "cooldown"
                )
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    // Send leaderboard rank update
    suspend fun sendLeaderboardRankNotification(userId: String, newRank: Int, oldRank: Int) {
        try {
            if (newRank < oldRank) {
                val snapshot = usersCollection.document(userId).get().await()
                val fcmToken = snapshot.getString("fcmToken")

                if (fcmToken != null) {
                    sendFCMNotification(
                        fcmToken,
                        "🏆 Rank Up!",
                        "You've climbed to #$newRank on the leaderboard! Keep going!",
                        "leaderboard"
                    )
                }
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    // Send achievement unlocked notification
    suspend fun sendAchievementNotification(userId: String, achievementName: String) {
        try {
            val snapshot = usersCollection.document(userId).get().await()
            val fcmToken = snapshot.getString("fcmToken")

            if (fcmToken != null) {
                sendFCMNotification(
                    fcmToken,
                    "🏅 Achievement Unlocked!",
                    "Congratulations! You've unlocked: $achievementName",
                    "achievement"
                )
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    private fun sendFCMNotification(token: String, title: String, body: String, type: String) {
        // This is where you'd call your server or Firebase Cloud Function
        // For now, we'll store it in Firestore to be sent by a cloud function
        val db = FirebaseFirestore.getInstance()
        val notification = mapOf(
            "token" to token,
            "title" to title,
            "body" to body,
            "type" to type,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }
}

// New data classes for ad watching
data class AdWatchResult(
    val success: Boolean,
    val coinsEarned: Int,
    val adsWatchedToday: Int,
    val maxAdsPerDay: Int,
    val message: String
)

data class AdWatchStatus(
    val adsWatchedToday: Int,
    val maxAdsPerDay: Int,
    val remainingAds: Int,
    val canWatchAds: Boolean
)

data class DailyLoginResult(
    val claimed: Boolean,
    val coinsEarned: Int,
    val currentStreak: Int
)