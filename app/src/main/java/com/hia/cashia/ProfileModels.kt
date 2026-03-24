package com.hia.cashia

// Profile data class
data class UserProfile(
    val userId: String = "",
    val username: String = "Anonymous",
    val email: String = "",
    val avatar: String = "👤",
    val joinDate: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis()
)

// Statistics data class
data class UserStats(
    val totalCoinsEarned: Int = 0,
    val currentBalance: Int = 0,
    val loginStreak: Int = 0,
    val totalGamesPlayed: Int = 0,
    val adsWatched: Int = 0,
    val scratchCardsPlayed: Int = 0,
    val cardFlipGamesCompleted: Int = 0,
    val jackpotSpins: Int = 0,

    // Daily stats
    val todayEarnings: Int = 0,
    val todayGames: Int = 0,

    // Weekly stats
    val weeklyEarnings: Int = 0,
    val weeklyGames: Int = 0
)

// Achievement data class
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val requirement: Int,
    val progress: Int,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long = 0
)

// Transaction record
data class Transaction(
    val id: String = "",
    val userId: String = "",
    val type: TransactionType,
    val amount: Int,
    val balance: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = ""
)

enum class TransactionType {
    AD_WATCH,
    DAILY_LOGIN,
    SCRATCH_CARD,
    CARD_FLIP,
    JACKPOT_WIN,
    JACKPOT_LOSS,
    REFERRAL_BONUS,
    ACHIEVEMENT_REWARD
}

// Available avatars
val AVAILABLE_AVATARS = listOf(
    "👤", "👨", "👩", "🧑", "👦", "👧",
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊",
    "🐻", "🐼", "🐨", "🐯", "🦁", "🐮",
    "🦄", "🐧", "🐦", "🐤", "🐣", "🐥",
    "🦆", "🦅", "🦉", "🐺", "🐗", "🐴",
    "🦋", "🐞", "🐝", "🐛", "🐜", "🪰",
    "🌟", "⭐", "🌙", "☀️", "🌈", "☁️",
    "❤️", "🧡", "💛", "💚", "💙", "💜",
    "🎮", "🎲", "🎯", "🎰", "🎴", "🃏"
)

// Predefined achievements
val ACHIEVEMENTS = listOf(
    Achievement(
        id = "first_login",
        name = "Welcome!",
        description = "Log in for the first time",
        icon = "👋",
        requirement = 1,
        progress = 0
    ),
    Achievement(
        id = "streak_7",
        name = "Week Warrior",
        description = "7-day login streak",
        icon = "🔥",
        requirement = 7,
        progress = 0
    ),
    Achievement(
        id = "streak_30",
        name = "Monthly Master",
        description = "30-day login streak",
        icon = "👑",
        requirement = 30,
        progress = 0
    ),
    Achievement(
        id = "ads_100",
        name = "Ad Enthusiast",
        description = "Watch 100 ads",
        icon = "📺",
        requirement = 100,
        progress = 0
    ),
    Achievement(
        id = "scratch_50",
        name = "Scratch Master",
        description = "Play 50 scratch cards",
        icon = "🎴",
        requirement = 50,
        progress = 0
    ),
    Achievement(
        id = "cardflip_25",
        name = "Memory Champion",
        description = "Complete 25 card flip games",
        icon = "🧠",
        requirement = 25,
        progress = 0
    ),
    Achievement(
        id = "jackpot_10",
        name = "Lucky Player",
        description = "Win 10 jackpot spins",
        icon = "🍀",
        requirement = 10,
        progress = 0
    ),
    Achievement(
        id = "jackpot_3sevens",
        name = "JACKPOT!",
        description = "Hit the 7️⃣ 7️⃣ 7️⃣ jackpot",
        icon = "🎰",
        requirement = 1,
        progress = 0
    ),
    Achievement(
        id = "earn_1000",
        name = "Century Club",
        description = "Earn 1000 total coins",
        icon = "💯",
        requirement = 1000,
        progress = 0
    ),
    Achievement(
        id = "earn_10000",
        name = "Coin Millionaire",
        description = "Earn 10,000 total coins",
        icon = "💰",
        requirement = 10000,
        progress = 0
    )
)