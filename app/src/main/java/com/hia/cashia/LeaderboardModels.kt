package com.hia.cashia

// Leaderboard entry
data class LeaderboardEntry(
    val userId: String = "",
    val username: String = "Anonymous",
    val avatar: String = "👤",
    val avatarBase64: String = "",
    val totalCoins: Int = 0,
    val dailyCoins: Int = 0,
    val weeklyCoins: Int = 0,
    val rank: Int = 0,
    val lastActive: Long = System.currentTimeMillis()
)

// Leaderboard category
enum class LeaderboardCategory {
    DAILY,
    WEEKLY,
    ALL_TIME
}

// Search result
data class SearchResult(
    val found: Boolean,
    val entry: LeaderboardEntry? = null,
    val message: String = ""
)