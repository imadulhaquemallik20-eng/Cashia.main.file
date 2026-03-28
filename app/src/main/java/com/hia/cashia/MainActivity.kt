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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private lateinit var viewModel: MainViewModel
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    // UI Elements
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var welcomeText: TextView
    private lateinit var usernameText: TextView  // This is the username display on home page
    private lateinit var coinBalanceText: TextView
    private lateinit var streakText: TextView
    private lateinit var dailyLoginCard: MaterialCardView
    private lateinit var dailyLoginButton: TextView
    private lateinit var dailyBonusText: TextView

    // Game Cards
    private lateinit var watchAdsCard: MaterialCardView
    private lateinit var scratchCardCard: MaterialCardView
    private lateinit var cardFlipCard: MaterialCardView
    private lateinit var jackpotCard: MaterialCardView

    private var currentUserId: String? = null
    private var currentBalance: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_premium)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
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
        observeViewModel()
        viewModel.loadUserData()
        loadUserProfileForNav()
        loadUserDataForHome()  // Add this line to load username
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        welcomeText = findViewById(R.id.welcomeText)
        usernameText = findViewById(R.id.usernameText)  // This is the TextView for username
        coinBalanceText = findViewById(R.id.coinBalanceText)
        streakText = findViewById(R.id.streakText)

        dailyLoginCard = findViewById(R.id.dailyLoginCard)
        dailyLoginButton = findViewById(R.id.dailyLoginButton)
        dailyBonusText = findViewById(R.id.dailyBonusText)

        // Game Cards
        watchAdsCard = findViewById(R.id.watchAdsCard)
        scratchCardCard = findViewById(R.id.scratchCardCard)
        cardFlipCard = findViewById(R.id.cardFlipCard)
        jackpotCard = findViewById(R.id.jackpotCard)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Home"
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

    // Add this new function to load user data for home page
    private fun loadUserDataForHome() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(currentUserId!!)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    user?.let {
                        // Update username on home page
                        usernameText.text = it.username
                        welcomeText.text = "Welcome back,"
                    }
                }
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

    private fun setupClickListeners() {
        watchAdsCard.setOnClickListener {
            startActivity(Intent(this, WatchAdsActivity::class.java))
        }

        scratchCardCard.setOnClickListener {
            startActivity(Intent(this, ScratchCardActivity::class.java))
        }

        cardFlipCard.setOnClickListener {
            startActivity(Intent(this, CardFlipActivity::class.java))
        }

        jackpotCard.setOnClickListener {
            startActivity(Intent(this, JackpotActivity::class.java))
        }

        dailyLoginButton.setOnClickListener {
            viewModel.claimDailyLogin()
        }

        dailyLoginCard.setOnClickListener {
            viewModel.claimDailyLogin()
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            user?.let {
                currentBalance = it.coinBalance
                coinBalanceText.text = "💰 $currentBalance coins"
                streakText.text = "🔥 ${it.loginStreak} day streak"

                val todayBonus = when {
                    it.loginStreak >= 30 -> 100
                    it.loginStreak >= 14 -> 50
                    it.loginStreak >= 7 -> 25
                    else -> 10
                }
                dailyBonusText.text = "Claim your +$todayBonus coins"

                // Update username in case it changed
                usernameText.text = it.username
            }
        }

        viewModel.dailyLoginResult.observe(this) { result ->
            if (result != null) {
                if (result.claimed) {
                    Toast.makeText(
                        this,
                        "Daily bonus: +${result.coinsEarned} coins!",
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.loadUserData()
                } else {
                    Toast.makeText(
                        this,
                        "Already claimed today!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                viewModel.clearMessages()
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                drawerLayout.closeDrawer(GravityCompat.START)
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

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("🚪 Logout")
            .setMessage("Are you sure you want to logout? You'll need to login again to access your account.")
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
        viewModel.refreshData()
        loadUserDataForHome()  // Refresh username when returning to home
        loadUserProfileForNav()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}