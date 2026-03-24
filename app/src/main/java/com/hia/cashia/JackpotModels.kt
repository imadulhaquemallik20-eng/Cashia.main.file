package com.hia.cashia

// Symbol data class with weight for probability
data class SlotSymbol(
    val id: Int,
    val emoji: String,
    val name: String,
    val multiplier: Int,
    val weight: Int = 1  // Default weight, higher = more common
)

// Result of a spin
data class JackpotSpinResult(
    val success: Boolean,
    val betAmount: Int,
    val winAmount: Int,
    val symbols: List<SlotSymbol>,
    val message: String,
    val cooldownStarted: Boolean = false,
    val cooldownEndTime: Long = 0L,
    val spinsToday: Int = 0
)

// Game state
data class JackpotGameState(
    val reels: List<SlotSymbol>,
    val isSpinning: Boolean = false,
    val lastSpinResult: JackpotSpinResult? = null,
    val isOnCooldown: Boolean = false,
    val cooldownEndTime: Long = 0L,
    val spinsToday: Int = 0,
    val winningsToday: Int = 0
)

// Statistics
data class JackpotStats(
    val spinsToday: Int,
    val winningsToday: Int,
    val maxSpinsPerDay: Int = 50,
    val maxWinningsPerDay: Int = 100,
    val remainingSpins: Int,
    val remainingWinnings: Int,
    val canPlay: Boolean,
    val isOnCooldown: Boolean,
    val cooldownRemainingSeconds: Long = 0
)