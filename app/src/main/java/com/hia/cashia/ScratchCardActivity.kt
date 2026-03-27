package com.hia.cashia

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
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

class ScratchCardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: ScratchCardViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    // UI Elements
    private lateinit var coinBalanceText: TextView
    private lateinit var cardsRemainingText: TextView
    private lateinit var coinsEarnedTodayText: TextView
    private lateinit var cooldownTimerText: TextView
    private lateinit var cooldownCard: MaterialCardView
    private lateinit var scratchCardView: ScratchCardView

    private var currentUserId: String? = null
    private var cooldownTimer: CountDownTimer? = null
    private var isOnCooldown = false
    private var currentReward = 0
    private var canPlay = true
    private var isProcessing = false
    private var hasScratchedCurrentCard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scratch_card)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        viewModel = ViewModelProvider(this)[ScratchCardViewModel::class.java]
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()
        setupToolbar()
        setupDrawer()
        setupScratchCard()
        observeViewModel()
        viewModel.loadUserData()
        loadUserProfileForNav()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        coinBalanceText = findViewById(R.id.coinBalanceText)
        cardsRemainingText = findViewById(R.id.cardsRemainingText)
        coinsEarnedTodayText = findViewById(R.id.coinsEarnedTodayText)
        cooldownTimerText = findViewById(R.id.cooldownTimerText)
        cooldownCard = findViewById(R.id.cooldownCard)
        scratchCardView = findViewById(R.id.scratchCardView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Scratch Cards"
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

    private fun setupScratchCard() {
        scratchCardView.setOnScratchCompleteListener { completed ->
            if (completed && canPlay && !isOnCooldown && !isProcessing && !hasScratchedCurrentCard) {
                hasScratchedCurrentCard = true
                playScratchCard()
            }
        }
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

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            user?.let {
                coinBalanceText.text = "💰 ${it.coinBalance} coins"
                updateGameStats(it)
            }
        }

        viewModel.gameState.observe(this) { state ->
            state?.let {
                updateCooldownStatus(it)
            }
        }

        viewModel.playResult.observe(this) { result ->
            result?.let {
                isProcessing = false

                if (it.success) {
                    Toast.makeText(this, "🎉 You won ${it.coinsEarned} coins!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                }

                // Reset for next card after delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    resetForNextCard()
                }, 2000)

                viewModel.clearPlayResult()
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                isProcessing = false
                resetForNextCard()
            }
        }
    }

    private fun resetForNextCard() {
        scratchCardView.resetScratch()
        isProcessing = false
        hasScratchedCurrentCard = false

        if (canPlay && !isOnCooldown) {
            // Generate new random reward for next card
            currentReward = (ScratchCardViewModel.MIN_COINS_PER_CARD..ScratchCardViewModel.MAX_COINS_PER_CARD).random()
            scratchCardView.rewardAmount = currentReward
        }
    }

    private fun updateGameStats(user: User) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val cardsPlayed = if (user.lastScratchCardDate == today) user.scratchCardsPlayedToday else 0
        val coinsEarned = if (user.lastScratchCardDate == today) user.scratchCoinsEarnedToday else 0

        cardsRemainingText.text = "$cardsPlayed/${ScratchCardViewModel.MAX_CARDS_PER_DAY}"
        coinsEarnedTodayText.text = "$coinsEarned/${ScratchCardViewModel.MAX_COINS_PER_DAY}"

        canPlay = cardsPlayed < ScratchCardViewModel.MAX_CARDS_PER_DAY &&
                coinsEarned < ScratchCardViewModel.MAX_COINS_PER_DAY

        if (!canPlay) {
            scratchCardView.isEnabled = false
        } else if (!isOnCooldown && !hasScratchedCurrentCard) {
            scratchCardView.isEnabled = true
            currentReward = (ScratchCardViewModel.MIN_COINS_PER_CARD..ScratchCardViewModel.MAX_COINS_PER_CARD).random()
            scratchCardView.rewardAmount = currentReward
        }
    }

    private fun updateCooldownStatus(state: ScratchCardGameState) {
        isOnCooldown = state.isOnCooldown

        if (state.isOnCooldown) {
            startCooldownTimer(state.cooldownEndTime)
            scratchCardView.isEnabled = false
        } else {
            stopCooldownTimer()
            if (canPlay && !hasScratchedCurrentCard) {
                scratchCardView.isEnabled = true
                currentReward = (ScratchCardViewModel.MIN_COINS_PER_CARD..ScratchCardViewModel.MAX_COINS_PER_CARD).random()
                scratchCardView.rewardAmount = currentReward
            }
        }
    }

    private fun startCooldownTimer(endTime: Long) {
        val currentTime = System.currentTimeMillis()
        val remainingTime = endTime - currentTime

        if (remainingTime <= 0) return

        cooldownCard.visibility = View.VISIBLE
        scratchCardView.isEnabled = false

        cooldownTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                cooldownTimerText.text = "${minutes}:${String.format("%02d", seconds)}"
            }

            override fun onFinish() {
                stopCooldownTimer()
                viewModel.checkCooldownStatus()
            }
        }.start()
    }

    private fun stopCooldownTimer() {
        cooldownTimer?.cancel()
        cooldownTimer = null
        cooldownCard.visibility = View.GONE

        if (canPlay && !hasScratchedCurrentCard) {
            scratchCardView.isEnabled = true
            currentReward = (ScratchCardViewModel.MIN_COINS_PER_CARD..ScratchCardViewModel.MAX_COINS_PER_CARD).random()
            scratchCardView.rewardAmount = currentReward
        }
    }

    private fun playScratchCard() {
        val userId = currentUserId ?: return
        isProcessing = true
        viewModel.playScratchCard(userId, currentReward)
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
                startActivity(Intent(this, WithdrawalActivity::class.java))
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
                        Toast.makeText(this@ScratchCardActivity, "Username updated!", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        viewModel.loadUserData()
    }

    override fun onDestroy() {
        super.onDestroy()
        cooldownTimer?.cancel()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}