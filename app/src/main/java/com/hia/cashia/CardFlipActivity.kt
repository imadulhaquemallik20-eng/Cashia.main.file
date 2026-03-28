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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

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