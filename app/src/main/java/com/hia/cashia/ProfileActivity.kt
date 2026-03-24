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

class ProfileActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var avatarText: TextView
    private lateinit var usernameText: TextView
    private lateinit var emailText: TextView
    private lateinit var joinDateText: TextView

    private lateinit var totalCoinsValue: TextView
    private lateinit var currentBalanceValue: TextView
    private lateinit var loginStreakValue: TextView
    private lateinit var gamesPlayedValue: TextView

    private lateinit var adsWatchedValue: TextView
    private lateinit var scratchCardsValue: TextView
    private lateinit var cardFlipValue: TextView
    private lateinit var jackpotSpinsValue: TextView

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
        observeViewModel()
        viewModel.loadUserData()
        loadUserProfileForNav()
    }

    override fun onResume() {
        super.onResume()
        IronSourceManager.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        IronSourceManager.onPause(this)
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        avatarText = findViewById(R.id.avatarText)
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
                avatarText.text = it.avatar
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
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Navigate to MainActivity
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

        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(currentUserId!!)
            withContext(Dispatchers.Main) {
                avatarPreview.text = result.getOrNull()?.avatar ?: "👤"
            }
        }

        avatarGrid.layoutManager = GridLayoutManager(this, 4)
        avatarGrid.adapter = AvatarAdapter { avatar ->
            CoroutineScope(Dispatchers.IO).launch {
                usersCollection.document(currentUserId!!).update("avatar", avatar).await()
                withContext(Dispatchers.Main) {
                    avatarPreview.text = avatar
                    avatarGridContainer.visibility = View.GONE
                    changeAvatarButton.text = "🔄 Change Avatar"
                    avatarText.text = avatar
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

        builder.setView(view)
        builder.setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}