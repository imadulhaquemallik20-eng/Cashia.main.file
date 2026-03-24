package com.hia.cashia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private lateinit var toolbarTitle: TextView  // Add this

    private lateinit var welcomeText: TextView
    private lateinit var usernameText: TextView
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
        requestNotificationPermission()
        checkDailyLoginReminder()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)  // Initialize toolbar title

        welcomeText = findViewById(R.id.welcomeText)
        usernameText = findViewById(R.id.usernameText)
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

    // Add this function to check and send daily login reminder
    private fun checkDailyLoginReminder() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = currentUserId ?: return@launch
                val snapshot = usersCollection.document(userId).get().await()
                val lastLogin = snapshot.getString("lastLoginDate") ?: ""
                val today = getTodayDate()
                val fcmToken = snapshot.getString("fcmToken")

                if (lastLogin != today && fcmToken != null && fcmToken.isNotEmpty()) {
                    // User hasn't logged in today, send reminder
                    val notification = mapOf(
                        "token" to fcmToken,
                        "title" to "🔔 Daily Login Bonus",
                        "body" to "Don't miss your daily streak! Login now to claim your bonus!",
                        "type" to "daily_login",
                        "timestamp" to System.currentTimeMillis()
                    )

                    usersCollection.document("notifications").collection("to_send").add(notification)

                    // Show local notification
                    showDailyLoginReminder()
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun showDailyLoginReminder() {
        // This will be handled by the notification system
        // For now, we'll just log
        android.util.Log.d("MainActivity", "Daily login reminder sent")
    }

    private fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbarTitle.text = "Home"  // Now it's properly initialized
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

                    welcomeText.text = "Welcome back,"
                    usernameText.text = user?.username ?: "User"
                }
            }
        }
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

    // Call this after user login
    private fun scheduleDailyLoginReminder() {
        CoroutineScope(Dispatchers.IO).launch {
            // Check and send reminder if needed
            val userId = currentUserId ?: return@launch
            userManager.sendDailyLoginReminder(userId)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Already here
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
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
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
                        Toast.makeText(this@MainActivity, "Username updated!", Toast.LENGTH_SHORT).show()
                        loadUserProfileForNav()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
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