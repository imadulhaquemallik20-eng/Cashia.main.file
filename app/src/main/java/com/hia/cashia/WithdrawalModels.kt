package com.hia.cashia

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

// Withdrawal request data class
data class WithdrawalRequest(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val upiId: String = "",
    val amount: Int = 0, // Amount in coins
    val cashAmount: Double = 0.0, // Amount in rupees (e.g., 100 coins = ₹10)
    val status: WithdrawalStatus = WithdrawalStatus.PENDING,
    val requestDate: Long = System.currentTimeMillis(),
    val processedDate: Long = 0,
    val transactionId: String = "",
    val adminNotes: String = ""
)

// Withdrawal status enum
enum class WithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    FAILED
}

// Withdrawal statistics
data class WithdrawalStats(
    val totalWithdrawn: Int = 0,
    val pendingWithdrawals: Int = 0,
    val approvedWithdrawals: Int = 0,
    val rejectedWithdrawals: Int = 0,
    val totalWithdrawnCash: Double = 0.0
)

// Conversion rate: 10 coins = ₹1
const val COINS_TO_RUPEE_RATE = 10
const val MINIMUM_WITHDRAWAL_COINS = 100 // Minimum 100 coins (₹10)
const val MINIMUM_WITHDRAWAL_RUPEES = 10.0

// UPI ID validation regex
fun isValidUpiId(upiId: String): Boolean {
    val upiRegex = Regex("^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$")
    return upiRegex.matches(upiId)
}

// Calculate rupees from coins
fun coinsToRupees(coins: Int): Double {
    return coins.toDouble() / COINS_TO_RUPEE_RATE
}

// Calculate coins from rupees
fun rupeesToCoins(rupees: Double): Int {
    return (rupees * COINS_TO_RUPEE_RATE).toInt()
}
