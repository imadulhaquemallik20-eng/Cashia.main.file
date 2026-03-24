package com.hia.cashia

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.*

class WithdrawalViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val withdrawalsCollection = db.collection("withdrawals")
    private val usersCollection = db.collection("users")
    private val userManager = UserManager()
    private val currentUserId = userManager.getCurrentUserId()

    // LiveData
    private val _userWithdrawals = MutableLiveData<List<WithdrawalRequest>>()
    val userWithdrawals: LiveData<List<WithdrawalRequest>> = _userWithdrawals

    private val _withdrawalStats = MutableLiveData<WithdrawalStats?>()
    val withdrawalStats: LiveData<WithdrawalStats?> = _withdrawalStats

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _conversionResult = MutableLiveData<Pair<Int, Double>?>()
    val conversionResult: LiveData<Pair<Int, Double>?> = _conversionResult

    init {
        loadUserWithdrawals()
        loadWithdrawalStats()
    }

    fun loadUserWithdrawals() {
        val userId = currentUserId ?: return

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = withdrawalsCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("requestDate", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                val withdrawals = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WithdrawalRequest::class.java)
                }

                withContext(Dispatchers.Main) {
                    _userWithdrawals.value = withdrawals
                    _isLoading.value = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to load withdrawals: ${e.message}"
                }
            }
        }
    }

    fun loadWithdrawalStats() {
        val userId = currentUserId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDoc = usersCollection.document(userId).get().await()
                val totalWithdrawnCoins = userDoc.getLong("totalWithdrawnCoins")?.toInt() ?: 0
                val totalWithdrawnCash = userDoc.getDouble("totalWithdrawnCash") ?: 0.0

                // Get pending withdrawals count
                val pendingSnapshot = withdrawalsCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", WithdrawalStatus.PENDING.name)
                    .get()
                    .await()

                val pendingCount = pendingSnapshot.documents.size

                val stats = WithdrawalStats(
                    totalWithdrawn = totalWithdrawnCoins,
                    totalWithdrawnCash = totalWithdrawnCash,
                    pendingWithdrawals = pendingCount
                )

                withContext(Dispatchers.Main) {
                    _withdrawalStats.value = stats
                }

            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun calculateWithdrawal(coins: Int) {
        if (coins < MINIMUM_WITHDRAWAL_COINS) {
            _errorMessage.value = "Minimum withdrawal is $MINIMUM_WITHDRAWAL_COINS coins (₹$MINIMUM_WITHDRAWAL_RUPEES)"
            _conversionResult.value = null
            return
        }

        val rupees = coinsToRupees(coins)
        _conversionResult.value = Pair(coins, rupees)
    }

    fun requestWithdrawal(upiId: String, coins: Int) {
        val userId = currentUserId ?: return

        // Validate UPI ID
        if (!isValidUpiId(upiId)) {
            _errorMessage.value = "Invalid UPI ID format. Example: name@okhdfcbank"
            return
        }

        // Validate minimum amount
        if (coins < MINIMUM_WITHDRAWAL_COINS) {
            _errorMessage.value = "Minimum withdrawal is $MINIMUM_WITHDRAWAL_COINS coins"
            return
        }

        // Check if coins amount is valid (multiple of rate?)
        if (coins % COINS_TO_RUPEE_RATE != 0) {
            _errorMessage.value = "Withdrawal amount must be in multiples of $COINS_TO_RUPEE_RATE coins"
            return
        }

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check user balance first
                val userDoc = usersCollection.document(userId).get().await()
                val currentBalance = userDoc.getLong("coinBalance")?.toInt() ?: 0
                val username = userDoc.getString("username") ?: "User"

                if (currentBalance < coins) {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _errorMessage.value = "Insufficient balance! You have $currentBalance coins"
                    }
                    return@launch
                }

                // Check if user has pending withdrawals
                val pendingSnapshot = withdrawalsCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", WithdrawalStatus.PENDING.name)
                    .get()
                    .await()

                if (pendingSnapshot.documents.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _errorMessage.value = "You already have a pending withdrawal request"
                    }
                    return@launch
                }

                // Calculate cash amount
                val cashAmount = coinsToRupees(coins)

                // Create withdrawal request
                val withdrawalId = UUID.randomUUID().toString()
                val withdrawal = WithdrawalRequest(
                    id = withdrawalId,
                    userId = userId,
                    username = username,
                    upiId = upiId,
                    amount = coins,
                    cashAmount = cashAmount,
                    status = WithdrawalStatus.PENDING,
                    requestDate = System.currentTimeMillis()
                )

                // Save to Firebase
                withdrawalsCollection.document(withdrawalId).set(withdrawal).await()

                // Deduct coins from user balance (they will be credited back if rejected)
                usersCollection.document(userId)
                    .update("coinBalance", FieldValue.increment(-coins.toLong()))
                    .await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _successMessage.value = "Withdrawal request submitted! You'll receive ₹$cashAmount to $upiId"
                    _conversionResult.value = null
                    loadUserWithdrawals()
                    loadWithdrawalStats()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to process withdrawal: ${e.message}"
                }
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
        _conversionResult.value = null
    }
}