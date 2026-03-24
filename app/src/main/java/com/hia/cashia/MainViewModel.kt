package com.hia.cashia

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class MainViewModel : ViewModel() {
    private val userManager = UserManager()
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")
    private val currentUserId = userManager.getCurrentUserId()

    // LiveData for user data
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    // LiveData for daily login result
    private val _dailyLoginResult = MutableLiveData<DailyLoginResult?>()
    val dailyLoginResult: LiveData<DailyLoginResult?> = _dailyLoginResult

    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    init {
        loadUserData()
    }

    fun loadUserData() {
        val userId = currentUserId ?: return

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = userManager.getUserData(userId)

            withContext(Dispatchers.Main) {
                _isLoading.value = false
                if (result.isSuccess) {
                    _userData.value = result.getOrNull()
                } else {
                    _errorMessage.value = "Failed to load user data"
                }
            }
        }
    }

    fun checkDailyLogin() {
        val userId = currentUserId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val result = userManager.checkAndUpdateDailyLogin(userId)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val loginResult = result.getOrNull()
                    _dailyLoginResult.value = loginResult

                    // If claimed, refresh user data to update balance
                    if (loginResult?.claimed == true) {
                        loadUserData()
                    }
                } else {
                    _errorMessage.value = "Failed to check daily login"
                }
            }
        }
    }

    fun claimDailyLogin() {
        checkDailyLogin() // Same function since it already claims
    }

    fun refreshData() {
        loadUserData()
    }

    fun updateAvatar(avatar: String) {
        val userId = userManager.getCurrentUserId() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update avatar in Firebase
                usersCollection.document(userId)
                    .update("avatar", avatar)
                    .await()

                withContext(Dispatchers.Main) {
                    _successMessage.value = "Avatar updated successfully!"
                    loadUserData() // Refresh user data
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to update avatar: ${e.message}"
                }
            }
        }
    }

    fun updateUsername(newUsername: String) {
        val userId = userManager.getCurrentUserId() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update username in Firebase
                usersCollection.document(userId)
                    .update("username", newUsername)
                    .await()

                withContext(Dispatchers.Main) {
                    _successMessage.value = "Username updated successfully!"
                    loadUserData() // Refresh user data
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to update username: ${e.message}"
                }
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
        _dailyLoginResult.value = null
    }
}