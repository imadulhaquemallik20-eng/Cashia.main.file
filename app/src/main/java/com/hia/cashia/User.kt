package com.hia.cashia

data class User(
    val userId: String = "",
    val email: String = "",
    val username: String = "",
    val avatar: String = "👤",
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
    // Transaction history (last 50)
    val recentTransactions: List<Transaction> = emptyList(),
    val totalWithdrawnCoins: Int = 0,
    val totalWithdrawnCash: Double = 0.0,
    val lastWithdrawalDate: Long = 0,
    val upiId: String = "",

) {
    constructor() : this(
        "", "", "", "👤", 0, 0, "", 0, 0L,
        0, 0, "", 0, 0, 0, 0, "", 0, 0,  // scratch (fixed)
        0, 0, 0, 0, 0, "", 0, 0,  // card flip (fixed)
        0, 0, 0, 0, 0, 0, "",  // jackpot
        0, 0L,  // game stats
        0, 0, "", "",  // leaderboard
        emptyMap(), emptyMap(),  // achievements
        emptyList(),  // transactions
        0, 0.0, 0, ""
    )
}