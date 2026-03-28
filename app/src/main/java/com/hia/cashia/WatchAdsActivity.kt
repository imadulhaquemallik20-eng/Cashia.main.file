package com.hia.cashia

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

class WatchAdsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // ============================================
    // TEMPORARY BLOCK - Set to true to block ads, false to enable
    // ============================================
    private val isAdsBlocked = true  // Change to false to enable ads
    // ============================================

    private lateinit var viewModel: WatchAdsViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var coinBalanceText: TextView
    private lateinit var adsRemainingText: TextView
    private lateinit var watchAdButton: Button
    private lateinit var blockMessageCard: MaterialCardView
    private lateinit var blockMessageTitle: TextView
    private lateinit var blockMessageText: TextView

    private var currentUserId: String? = null
    private var remainingAdsToday = 50
    private var adCheckRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_ads)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        viewModel = ViewModelProvider(this)[WatchAdsViewModel::class.java]
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

        if (isAdsBlocked) {
            showTemporaryBlockMessage()
        } else {
            showAdsContent()
            observeViewModel()
            viewModel.loadUserData()
            startAdCheck()
        }

        loadUserProfileForNav()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        coinBalanceText = findViewById(R.id.coinBalanceText)
        adsRemainingText = findViewById(R.id.adsRemainingText)
        watchAdButton = findViewById(R.id.watchAdButton)
        blockMessageCard = findViewById(R.id.blockMessageCard)
        blockMessageTitle = findViewById(R.id.blockMessageTitle)
        blockMessageText = findViewById(R.id.blockMessageText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Watch Ads"
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

    private fun showTemporaryBlockMessage() {
        // Hide ad-related content
        coinBalanceText.visibility = View.GONE
        adsRemainingText.visibility = View.GONE
        watchAdButton.visibility = View.GONE

        // Show block message
        blockMessageCard.visibility = View.VISIBLE
    }

    private fun showAdsContent() {
        // Hide block message
        blockMessageCard.visibility = View.GONE

        // Show ad-related content
        coinBalanceText.visibility = View.VISIBLE
        adsRemainingText.visibility = View.VISIBLE
        watchAdButton.visibility = View.VISIBLE
    }

    private fun setupClickListeners() {
        watchAdButton.setOnClickListener {
            if (!isAdsBlocked) {
                showRewardedAd()
            }
        }
    }

    private fun startAdCheck() {
        if (isAdsBlocked) return

        adCheckRunnable = object : Runnable {
            override fun run() {
                if (remainingAdsToday > 0) {
                    updateButtonState()
                    handler.postDelayed(this, 3000)
                }
            }
        }
        handler.post(adCheckRunnable!!)
    }

    private fun stopAdCheck() {
        adCheckRunnable?.let { handler.removeCallbacks(it) }
        adCheckRunnable = null
    }

    private fun updateButtonState() {
        val isAvailable = IronSourceManager.isRewardedVideoAvailable()
        watchAdButton.isEnabled = isAvailable
        watchAdButton.text = if (isAvailable) "Watch Ad (+2 coins)" else "Loading Ad..."
    }

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            user?.let {
                coinBalanceText.text = "💰 ${it.coinBalance} coins"
            }
        }

        viewModel.adStatus.observe(this) { status ->
            status?.let {
                remainingAdsToday = it.remainingAds
                adsRemainingText.text = "Today: ${it.remainingAds}/50 ads remaining"

                if (it.remainingAds <= 0) {
                    watchAdButton.isEnabled = false
                    watchAdButton.text = "Daily Limit Reached"
                    stopAdCheck()
                }
            }
        }

        viewModel.adResult.observe(this) { result ->
            result?.let {
                if (it.success) {
                    Toast.makeText(this, "🎉 ${it.message}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                }
                viewModel.clearAdResult()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                watchAdButton.isEnabled = false
                watchAdButton.text = "Processing..."
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                updateButtonState()
            }
        }
    }

    private fun showRewardedAd() {
        if (isAdsBlocked) return

        if (remainingAdsToday <= 0) {
            Toast.makeText(this, "Daily limit reached! Come back tomorrow.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!IronSourceManager.isRewardedVideoAvailable()) {
            Toast.makeText(this, "Ad still loading. Please wait...", Toast.LENGTH_SHORT).show()
            updateButtonState()
            return
        }

        IronSourceManager.showRewardedVideo(this) {
            val userId = currentUserId ?: return@showRewardedVideo
            viewModel.processAdReward(userId)
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
        if (!isAdsBlocked) {
            IronSourceManager.onResume(this)
            viewModel.loadUserData()
            updateButtonState()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isAdsBlocked) {
            IronSourceManager.onPause(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdCheck()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}