package com.hia.cashia

// Data class for a single card
data class FlipCard(
    val id: Int,
    val imageUrl: String,  // Emoji or image identifier
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false
)

// Data class for the result of playing a match
data class CardFlipPlayResult(
    val success: Boolean,
    val coinsEarned: Int,
    val message: String,
    val cooldownStarted: Boolean = false,
    val cooldownEndTime: Long = 0L,
    val matchesCompleted: Int = 0
)

// Data class for current game state
data class CardFlipGameState(
    val cards: List<FlipCard>,
    val firstSelectedCardIndex: Int? = null,
    val secondSelectedCardIndex: Int? = null,
    val canFlip: Boolean = true,
    val matchesFound: Int = 0,
    val lastPlayResult: CardFlipPlayResult? = null,
    val isOnCooldown: Boolean = false,
    val cooldownEndTime: Long = 0L,
    val matchesToday: Int = 0,
    val coinsEarnedToday: Int = 0
)

// Data class for game statistics
data class CardFlipStats(
    val matchesPlayedToday: Int,
    val coinsEarnedToday: Int,
    val maxMatchesPerDay: Int = 20,
    val maxCoinsPerDay: Int = 50,
    val remainingMatches: Int,
    val remainingCoins: Int,
    val canPlay: Boolean,
    val isOnCooldown: Boolean,
    val cooldownRemainingSeconds: Long = 0
)