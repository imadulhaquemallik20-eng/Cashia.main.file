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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView

class CardFlipActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: CardFlipViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    // UI Elements
    private lateinit var coinBalanceText: TextView
    private lateinit var matchesFoundText: TextView
    private lateinit var coinsEarnedText: TextView
    private lateinit var cooldownTimerText: TextView
    private lateinit var cooldownCard: MaterialCardView
    private lateinit var gameGrid: GridLayout
    private lateinit var newGameButton: Button

    private var currentUserId: String? = null
    private var cooldownTimer: CountDownTimer? = null
    private var isOnCooldown = false
    private var canPlay = true
    private var cardButtons = mutableListOf<Button>()
    private var cardFrontTexts = mutableListOf<TextView>()
    private var cardBackViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_flip)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        viewModel = ViewModelProvider(this)[CardFlipViewModel::class.java]
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
        setupGrid()
        observeViewModel()
        viewModel.loadUserData()
        loadUserProfileForNav()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        coinBalanceText = findViewById(R.id.coinBalanceText)
        matchesFoundText = findViewById(R.id.matchesFoundText)
        coinsEarnedText = findViewById(R.id.coinsEarnedText)
        cooldownTimerText = findViewById(R.id.cooldownTimerText)
        cooldownCard = findViewById(R.id.cooldownCard)
        gameGrid = findViewById(R.id.gameGrid)
        newGameButton = findViewById(R.id.newGameButton)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Card Flip"
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

    private fun setupClickListeners() {
        newGameButton.setOnClickListener {
            viewModel.resetGame()
            resetAllCards()
        }
    }

    private fun setupGrid() {
        gameGrid.removeAllViews()
        cardButtons.clear()
        cardFrontTexts.clear()
        cardBackViews.clear()

        for (i in 0 until 16) {
            // Create a container for the card (front and back)
            val cardContainer = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 140
                    columnSpec = GridLayout.spec(i % 4, 1f)
                    rowSpec = GridLayout.spec(i / 4, 1f)
                    setMargins(6, 6, 6, 6)
                }
            }

            // Create back view (card back - gray with question mark)
            val backView = TextView(this).apply {
                text = "?"
                textSize = 32f
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(resources.getColor(R.color.premium_primary))
                setTextColor(resources.getColor(R.color.white))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                elevation = 4f
            }

            // Create front view (card front - will show emoji)
            val frontView = TextView(this).apply {
                text = ""
                textSize = 36f
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(resources.getColor(R.color.premium_success))
                setTextColor(resources.getColor(R.color.white))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                elevation = 4f
                visibility = View.GONE
            }

            cardContainer.addView(backView)
            cardContainer.addView(frontView)

            val finalI = i
            cardContainer.setOnClickListener {
                if (!isOnCooldown && canPlay) {
                    viewModel.onCardClick(finalI)
                }
            }

            gameGrid.addView(cardContainer)
            cardBackViews.add(backView)
            cardFrontTexts.add(frontView)
        }
    }

    private fun resetAllCards() {
        for (i in 0 until 16) {
            val backView = cardBackViews.getOrNull(i)
            val frontView = cardFrontTexts.getOrNull(i)
            backView?.visibility = View.VISIBLE
            frontView?.visibility = View.GONE
            frontView?.text = ""
        }
    }

    private fun flipCard(index: Int, showFront: Boolean) {
        val backView = cardBackViews.getOrNull(index)
        val frontView = cardFrontTexts.getOrNull(index)

        if (backView == null || frontView == null) return

        if (showFront) {
            FlipAnimation.flipCard(backView, frontView, 300) {
                // Animation complete
            }
        } else {
            FlipAnimation.flipBackCard(backView, frontView, 300) {
                // Animation complete
            }
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            user?.let {
                coinBalanceText.text = "💰 ${it.coinBalance} coins"
                updateStats(it)
            }
        }

        viewModel.gameState.observe(this) { state ->
            state?.let {
                // Update card displays
                it.cards.forEachIndexed { index, card ->
                    val frontView = cardFrontTexts.getOrNull(index)
                    if (card.isMatched) {
                        frontView?.text = "✓"
                        frontView?.setBackgroundColor(resources.getColor(R.color.premium_success))
                        flipCard(index, true)
                    } else if (card.isFlipped) {
                        frontView?.text = card.imageUrl
                        frontView?.setBackgroundColor(resources.getColor(R.color.premium_primary_light))
                        flipCard(index, true)
                    } else {
                        flipCard(index, false)
                    }
                }

                // Update matches count
                matchesFoundText.text = "${it.matchesFound}/8"

                // Update cooldown status
                isOnCooldown = it.isOnCooldown
                if (it.isOnCooldown) {
                    startCooldownTimer(it.cooldownEndTime)
                } else {
                    stopCooldownTimer()
                }

                // Show win result
                it.lastPlayResult?.let { result ->
                    if (result.success) {
                        Toast.makeText(this, "🎉 ${result.message}", Toast.LENGTH_LONG).show()
                        viewModel.resetGame()
                        resetAllCards()
                    }
                    viewModel.clearLastResult()
                }
            }
        }

        viewModel.stats.observe(this) { stats ->
            stats?.let {
                canPlay = it.canPlay
                coinsEarnedText.text = "${it.coinsEarnedToday}/${it.maxCoinsPerDay}"

                if (!it.canPlay) {
                    newGameButton.isEnabled = false
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            newGameButton.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun updateStats(user: User) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val matchesToday = if (user.lastCardFlipDate == today) user.cardFlipMatchesToday else 0
        val coinsToday = if (user.lastCardFlipDate == today) user.cardFlipCoinsEarnedToday else 0

        matchesFoundText.text = "$matchesToday/${CardFlipViewModel.MAX_MATCHES_PER_DAY}"
        coinsEarnedText.text = "$coinsToday/${CardFlipViewModel.MAX_COINS_PER_DAY}"
    }

    private fun startCooldownTimer(endTime: Long) {
        val currentTime = System.currentTimeMillis()
        val remainingTime = endTime - currentTime

        if (remainingTime <= 0) return

        cooldownCard.visibility = View.VISIBLE

        cooldownTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                cooldownTimerText.text = "${minutes}:${String.format("%02d", seconds)}"
            }

            override fun onFinish() {
                stopCooldownTimer()
                // Call checkCooldownStatus from ViewModel (make it public)
                viewModel.checkCooldownStatus()
            }
        }.start()
    }

    private fun stopCooldownTimer() {
        cooldownTimer?.cancel()
        cooldownTimer = null
        cooldownCard.visibility = View.GONE
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
                        Toast.makeText(this@CardFlipActivity, "Username updated!", Toast.LENGTH_SHORT).show()
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