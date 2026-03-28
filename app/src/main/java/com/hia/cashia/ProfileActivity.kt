package com.hia.cashia

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

class ProfileActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    // UI Elements
    private lateinit var avatarImageView: ImageView  // Changed from TextView to ImageView
    private lateinit var usernameText: TextView
    private lateinit var emailText: TextView
    private lateinit var joinDateText: TextView

    // Statistics
    private lateinit var totalCoinsValue: TextView
    private lateinit var currentBalanceValue: TextView
    private lateinit var loginStreakValue: TextView
    private lateinit var gamesPlayedValue: TextView

    // Game Stats
    private lateinit var adsWatchedValue: TextView
    private lateinit var scratchCardsValue: TextView
    private lateinit var cardFlipValue: TextView
    private lateinit var jackpotSpinsValue: TextView

    // Today's Stats
    private lateinit var todayEarningsValue: TextView
    private lateinit var todayGamesValue: TextView
    private lateinit var weeklyEarningsValue: TextView

    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
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
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        avatarImageView = findViewById(R.id.avatarImageView)  // This must exist in XML
        usernameText = findViewById(R.id.usernameText)
        emailText = findViewById(R.id.emailText)
        joinDateText = findViewById(R.id.joinDateText)

        totalCoinsValue = findViewById(R.id.totalCoinsValue)
        currentBalanceValue = findViewById(R.id.currentBalanceValue)
        loginStreakValue = findViewById(R.id.loginStreakValue)
        gamesPlayedValue = findViewById(R.id.gamesPlayedValue)

        adsWatchedValue = findViewById(R.id.adsWatchedValue)
        scratchCardsValue = findViewById(R.id.scratchCardsValue)
        cardFlipValue = findViewById(R.id.cardFlipValue)
        jackpotSpinsValue = findViewById(R.id.jackpotSpinsValue)

        todayEarningsValue = findViewById(R.id.todayEarningsValue)
        todayGamesValue = findViewById(R.id.todayGamesValue)
        weeklyEarningsValue = findViewById(R.id.weeklyEarningsValue)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "My Profile"
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

    private fun setupClickListeners() {
        // Add any click listeners if needed
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

    private fun loadAvatar(user: User) {
        if (user.avatarBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(user.avatarBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                avatarImageView.setImageBitmap(bitmap)
                avatarImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                avatarImageView.clipToOutline = true
            } catch (e: Exception) {
                avatarImageView.setImageResource(R.drawable.ic_default_avatar)
            }
        } else {
            avatarImageView.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            user?.let {
                usernameText.text = it.username
                emailText.text = it.email
                joinDateText.text = "Member since ${android.text.format.DateFormat.format("MMM dd, yyyy", it.createdAt)}"

                totalCoinsValue.text = it.totalCoinsEarned.toString()
                currentBalanceValue.text = it.coinBalance.toString()
                loginStreakValue.text = "${it.loginStreak} days"
                gamesPlayedValue.text = it.totalGamesPlayed.toString()

                adsWatchedValue.text = it.adsWatchedTotal.toString()
                scratchCardsValue.text = it.scratchCardsPlayedTotal.toString()
                cardFlipValue.text = it.cardFlipGamesCompleted.toString()
                jackpotSpinsValue.text = it.jackpotSpinsTotal.toString()

                todayEarningsValue.text = "+${it.dailyCoins}"
                todayGamesValue.text = it.totalGamesPlayed.toString()
                weeklyEarningsValue.text = it.weeklyCoins.toString()

                // Load avatar
                loadAvatar(it)
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
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            R.id.nav_profile -> { /* Already here */ }
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
            .setTitle("Logout")
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
        viewModel.loadUserData()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}