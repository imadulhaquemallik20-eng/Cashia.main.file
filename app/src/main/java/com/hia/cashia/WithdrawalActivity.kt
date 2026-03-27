package com.hia.cashia

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class WithdrawalActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    // ============================================
    // TEMPORARY WITHDRAWAL BLOCK - REMOVE WHEN READY
    // ============================================
    private val isWithdrawalEnabled = false // Set to true to enable withdrawal
    // ============================================

    private lateinit var coinBalanceText: TextView
    private lateinit var withdrawalAmountEditText: EditText
    private lateinit var upiIdEditText: EditText
    private lateinit var withdrawButton: Button
    private lateinit var minWithdrawalText: TextView
    private lateinit var processingFeeText: TextView
    private lateinit var blockMessageCard: MaterialCardView
    private lateinit var blockMessageText: TextView

    private var currentUserId: String? = null
    private var currentBalance: Int = 0
    private val MIN_WITHDRAWAL = 100
    private val PROCESSING_FEE = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_withdrawal)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()
        setupToolbar()
        setupDrawer()
        setupClickListeners()

        if (isWithdrawalEnabled) {
            // Show withdrawal form
            showWithdrawalForm()
            loadUserData()
        } else {
            // Show temporary block message
            showTemporaryBlockMessage()
        }

        loadUserProfileForNav()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        coinBalanceText = findViewById(R.id.coinBalanceText)
        withdrawalAmountEditText = findViewById(R.id.withdrawalAmountEditText)
        upiIdEditText = findViewById(R.id.upiIdEditText)
        withdrawButton = findViewById(R.id.withdrawButton)
        minWithdrawalText = findViewById(R.id.minWithdrawalText)
        processingFeeText = findViewById(R.id.processingFeeText)
        blockMessageCard = findViewById(R.id.blockMessageCard)
        blockMessageText = findViewById(R.id.blockMessageText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Withdrawal"
    }

    private fun setupDrawer() {
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
    }

    private fun loadUserProfileForNav() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(currentUserId!!)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    val headerView = navView.getHeaderView(0)
                    headerView.findViewById<TextView>(R.id.navAvatar).text = user?.avatar ?: "👤"
                    headerView.findViewById<TextView>(R.id.navUsername).text = user?.username ?: "User"
                    headerView.findViewById<TextView>(R.id.navEmail).text = user?.email ?: ""
                }
            }
        }
    }

    private fun showWithdrawalForm() {
        // Hide block message, show withdrawal form
        blockMessageCard.visibility = View.GONE
        withdrawalAmountEditText.visibility = View.VISIBLE
        upiIdEditText.visibility = View.VISIBLE
        withdrawButton.visibility = View.VISIBLE
        minWithdrawalText.visibility = View.VISIBLE
        processingFeeText.visibility = View.VISIBLE
    }

    private fun showTemporaryBlockMessage() {
        // Hide withdrawal form, show block message
        withdrawalAmountEditText.visibility = View.GONE
        upiIdEditText.visibility = View.GONE
        withdrawButton.visibility = View.GONE
        minWithdrawalText.visibility = View.GONE
        processingFeeText.visibility = View.GONE
        blockMessageCard.visibility = View.VISIBLE
    }

    private fun loadUserData() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(currentUserId!!)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    currentBalance = user?.coinBalance ?: 0
                    coinBalanceText.text = "💰 $currentBalance coins"
                }
            }
        }
    }

    private fun setupClickListeners() {
        withdrawButton.setOnClickListener {
            processWithdrawal()
        }
    }

    private fun processWithdrawal() {
        val amountStr = withdrawalAmountEditText.text.toString()
        val upiId = upiIdEditText.text.toString()

        if (amountStr.isEmpty() || upiId.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toIntOrNull() ?: 0

        if (amount < MIN_WITHDRAWAL) {
            Toast.makeText(this, "Minimum withdrawal amount is $MIN_WITHDRAWAL coins", Toast.LENGTH_SHORT).show()
            return
        }

        if (!upiId.contains("@")) {
            Toast.makeText(this, "Please enter a valid UPI ID", Toast.LENGTH_SHORT).show()
            return
        }

        val totalDeduction = amount + PROCESSING_FEE

        if (currentBalance < totalDeduction) {
            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Confirm Withdrawal")
        builder.setMessage("""
            Amount: $amount coins
            Processing Fee: $PROCESSING_FEE coins
            Total Deduction: $totalDeduction coins
            UPI ID: $upiId
            
            Proceed with withdrawal?
        """.trimIndent())

        builder.setPositiveButton("Confirm") { _, _ ->
            performWithdrawal(amount, totalDeduction, upiId)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun performWithdrawal(amount: Int, totalDeduction: Int, upiId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newBalance = currentBalance - totalDeduction

                usersCollection.document(currentUserId!!)
                    .update("coinBalance", newBalance)
                    .await()

                val transaction = mapOf(
                    "type" to "withdrawal",
                    "amount" to -amount,
                    "fee" to PROCESSING_FEE,
                    "upiId" to upiId,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                usersCollection.document(currentUserId!!)
                    .collection("transactions")
                    .add(transaction)
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WithdrawalActivity,
                        "Withdrawal request submitted successfully!", Toast.LENGTH_LONG).show()

                    withdrawalAmountEditText.text.clear()
                    upiIdEditText.text.clear()
                    loadUserData()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WithdrawalActivity,
                        "Failed to process withdrawal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            R.id.nav_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.nav_leaderboard -> {
                startActivity(Intent(this, LeaderboardActivity::class.java))
            }
            R.id.nav_achievements -> {
                startActivity(Intent(this, AchievementsActivity::class.java))
            }
            R.id.nav_withdrawal -> {

            }
            R.id.nav_settings -> {
                showSettingsDialog()
            }
            R.id.nav_logout -> {
                showLogoutConfirmationDialog()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Settings")

        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val avatarPreview = view.findViewById<TextView>(R.id.avatarPreview)
        val changeAvatarButton = view.findViewById<Button>(R.id.changeAvatarButton)
        val avatarGridContainer = view.findViewById<LinearLayout>(R.id.avatarGridContainer)
        val avatarGrid = view.findViewById<RecyclerView>(R.id.avatarGridRecyclerView)
        val changeUsernameButton = view.findViewById<Button>(R.id.changeUsernameButton)

        val userId = currentUserId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(userId)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    avatarPreview.text = result.getOrNull()?.avatar ?: "👤"
                }
            }
        }

        avatarGrid.layoutManager = GridLayoutManager(this, 4)
        avatarGrid.adapter = AvatarAdapter { avatar ->
            CoroutineScope(Dispatchers.IO).launch {
                usersCollection.document(userId).update("avatar", avatar).await()
                withContext(Dispatchers.Main) {
                    avatarPreview.text = avatar
                    avatarGridContainer.visibility = View.GONE
                    changeAvatarButton.text = "🔄 Change Avatar"
                    loadUserProfileForNav()
                }
            }
        }

        changeAvatarButton.setOnClickListener {
            if (avatarGridContainer.visibility == View.VISIBLE) {
                avatarGridContainer.visibility = View.GONE
                changeAvatarButton.text = "🔄 Change Avatar"
            } else {
                avatarGridContainer.visibility = View.VISIBLE
                changeAvatarButton.text = "▼ Hide Avatar Selection"
            }
        }

        changeUsernameButton.setOnClickListener {
            showEditUsernameDialog()
        }

        builder.setView(view)
        builder.setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showEditUsernameDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Change Username")

        val input = EditText(this)
        input.hint = "Enter new username"
        input.setBackgroundResource(R.drawable.edit_text_background)
        input.setPadding(50, 30, 50, 30)

        builder.setView(input)
        builder.setPositiveButton("Save") { _, _ ->
            val newUsername = input.text.toString().trim()
            if (newUsername.length >= 3) {
                CoroutineScope(Dispatchers.IO).launch {
                    usersCollection.document(currentUserId!!).update("username", newUsername).await()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@WithdrawalActivity, "Username updated!", Toast.LENGTH_SHORT).show()
                        loadUserProfileForNav()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout? You'll need to login again to access your account.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}