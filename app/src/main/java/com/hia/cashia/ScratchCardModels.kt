package com.hia.cashia

// Data class for the result of playing a scratch card
data class ScratchCardPlayResult(
    val success: Boolean,
    val coinsEarned: Int,
    val message: String,
    val cooldownStarted: Boolean = false,
    val cooldownEndTime: Long = 0L
)

// Data class for the current game state
data class ScratchCardGameState(
    val lastPlayResult: ScratchCardPlayResult? = null,
    val isOnCooldown: Boolean = false,
    val cooldownEndTime: Long = 0L
)
