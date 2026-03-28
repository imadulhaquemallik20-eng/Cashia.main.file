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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

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
                    val navAvatarEmoji = headerView.findViewById<TextView>(R.id.navAvatarEmoji)
                    val navAvatarImage = headerView.findViewById<ImageView>(R.id.navAvatarImage)
                    val navUsername = headerView.findViewById<TextView>(R.id.navUsername)
                    val navEmail = headerView.findViewById<TextView>(R.id.navEmail)

                    user?.let {
                        navUsername.text = it.username
                        navEmail.text = it.email

                        // Handle avatar display - round shape
                        if (it.avatarBase64.isNotEmpty()) {
                            // Show custom image avatar
                            navAvatarEmoji.visibility = View.GONE
                            navAvatarImage.visibility = View.VISIBLE
                            try {
                                val imageBytes = android.util.Base64.decode(it.avatarBase64, android.util.Base64.DEFAULT)
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                                // Create circular bitmap for better quality
                                val circularBitmap = getCircularBitmap(bitmap)
                                navAvatarImage.setImageBitmap(circularBitmap)
                                navAvatarImage.scaleType = ImageView.ScaleType.CENTER_CROP
                            } catch (e: Exception) {
                                // Fallback to emoji
                                navAvatarEmoji.visibility = View.VISIBLE
                                navAvatarImage.visibility = View.GONE
                                navAvatarEmoji.text = it.avatar
                            }
                        } else {
                            // Show emoji avatar
                            navAvatarEmoji.visibility = View.VISIBLE
                            navAvatarImage.visibility = View.GONE
                            navAvatarEmoji.text = it.avatar
                        }
                    }
                }
            }
        }
    }

    // Helper function to create circular bitmap
    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint()
        val rect = android.graphics.Rect(0, 0, size, size)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)

        val srcRect = android.graphics.Rect(
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            (bitmap.width + size) / 2,
            (bitmap.height + size) / 2
        )
        canvas.drawBitmap(bitmap, srcRect, rect, paint)

        return output
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
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
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