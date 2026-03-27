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
import kotlinx.coroutines.tasks.await

class WatchAdsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

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
        observeViewModel()
        viewModel.loadUserData()
        loadUserProfileForNav()
        startAdCheck()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        coinBalanceText = findViewById(R.id.coinBalanceText)
        adsRemainingText = findViewById(R.id.adsRemainingText)
        watchAdButton = findViewById(R.id.watchAdButton)
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
                    headerView.findViewById<TextView>(R.id.navAvatar).text = user?.avatar ?: "👤"
                    headerView.findViewById<TextView>(R.id.navUsername).text = user?.username ?: "User"
                    headerView.findViewById<TextView>(R.id.navEmail).text = user?.email ?: ""
                }
            }
        }
    }

    private fun setupClickListeners() {
        watchAdButton.setOnClickListener {
            showRewardedAd()
        }
    }

    private fun startAdCheck() {
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
                adsRemainingText.text = "Today: ${it.remainingAds}/50 ads"

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
                    Toast.makeText(this, " ${it.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@WatchAdsActivity, "Username updated!", Toast.LENGTH_SHORT).show()
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
        IronSourceManager.onResume(this)
        viewModel.loadUserData()
        updateButtonState()
    }

    override fun onPause() {
        super.onPause()
        IronSourceManager.onPause(this)
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