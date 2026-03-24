package com.hia.cashia

data class NotificationData(
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    DAILY_LOGIN_REMINDER,
    COOLDOWN_COMPLETED,
    NEW_GAME_AVAILABLE,
    LEADERBOARD_RANK_UP,
    ACHIEVEMENT_UNLOCKED,
    WITHDRAWAL_COMPLETED,
    SPECIAL_OFFER
}