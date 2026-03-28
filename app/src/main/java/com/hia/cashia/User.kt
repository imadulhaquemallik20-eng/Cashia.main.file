package com.hia.cashia

data class User(
    val userId: String = "",
    val email: String = "",
    val username: String = "",
    val avatar: String = "👤",
    val avatarBase64: String = "",  // For custom image avatars
    val coinBalance: Int = 0,
    val totalCoinsEarned: Int = 0,
    val lastLoginDate: String = "",
    val loginStreak: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    // Ad watching fields
    val adsWatchedToday: Int = 0,
    val adsWatchedTotal: Int = 0,
    val lastAdWatchDate: String = "",
    // Scratch card fields
    val scratchCardsPlayedToday: Int = 0,
    val scratchCardsPlayedTotal: Int = 0,
    val scratchCoinsEarnedToday: Int = 0,
    val scratchCoinsEarnedTotal: Int = 0,
    val lastScratchCardDate: String = "",
    val scratchCardCooldownUntil: Long = 0,
    val scratchCardsSinceCooldown: Int = 0,
    // Card flip fields
    val cardFlipMatchesToday: Int = 0,
    val cardFlipMatchesTotal: Int = 0,
    val cardFlipGamesCompleted: Int = 0,
    val cardFlipCoinsEarnedToday: Int = 0,
    val cardFlipCoinsEarnedTotal: Int = 0,
    val lastCardFlipDate: String = "",
    val cardFlipCooldownUntil: Long = 0,
    val cardFlipMatchesSinceCooldown: Int = 0,
    // Jackpot fields
    val jackpotSpinsToday: Int = 0,
    val jackpotSpinsTotal: Int = 0,
    val jackpotWinsTotal: Int = 0,
    val jackpotWinningsToday: Int = 0,
    val jackpotWinningsTotal: Int = 0,
    val jackpotSevensHit: Int = 0,
    val lastJackpotDate: String = "",
    // Game stats
    val totalGamesPlayed: Int = 0,
    val lastActive: Long = System.currentTimeMillis(),
    // Leaderboard fields
    val dailyCoins: Int = 0,
    val weeklyCoins: Int = 0,
    val lastDailyReset: String = "",
    val lastWeeklyReset: String = "",
    // Achievement fields
    val achievements: Map<String, Boolean> = emptyMap(),
    val achievementProgress: Map<String, Int> = emptyMap(),
    // Transaction history
    val recentTransactions: List<Transaction> = emptyList()
) {
    // Empty constructor needed for Firestore
    constructor() : this(
        "", "", "", "👤", "", 0, 0, "", 0, 0L,
        0, 0, "",  // ad fields
        0, 0, 0, 0, "", 0, 0,  // scratch card fields
        0, 0, 0, 0, 0, "", 0, 0,  // card flip fields
        0, 0, 0, 0, 0, 0, "",  // jackpot fields
        0, 0L,  // game stats
        0, 0, "", "",  // leaderboard fields
        emptyMap(), emptyMap(),  // achievements
        emptyList()  // transactions
    )
}