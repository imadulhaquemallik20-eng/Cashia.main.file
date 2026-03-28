package com.hia.cashia

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
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

class AchievementsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var recyclerView: RecyclerView
    private lateinit var statsText: TextView
    private lateinit var progressBar: ProgressBar

    private var currentUserId: String? = null
    private var previouslyUnlockedAchievements = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

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

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.achievementsRecyclerView)
        statsText = findViewById(R.id.statsText)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Achievements"
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

    private fun observeViewModel() {
        viewModel.userData.observe(this) { user ->
            user?.let {
                val unlockedCount = it.achievements.values.count { value -> value }
                val totalCount = ACHIEVEMENTS.size
                statsText.text = "🏆 $unlockedCount/$totalCount Achievements Unlocked"
                progressBar.progress = (unlockedCount * 100 / totalCount)

                val achievements = ACHIEVEMENTS.map { achievement ->
                    achievement.copy(
                        isUnlocked = it.achievements[achievement.id] == true,
                        progress = it.achievementProgress[achievement.id] ?: 0
                    )
                }
                recyclerView.adapter = AchievementsAdapter(achievements)
                checkAndNotifyNewAchievements(it)
            }
        }
    }

    // Add this function to check and notify new achievements
    private fun checkAndNotifyNewAchievements(user: User) {
        val newlyUnlocked = mutableListOf<String>()

        ACHIEVEMENTS.forEach { achievement ->
            val isNowUnlocked = user.achievements[achievement.id] == true
            val wasUnlocked = previouslyUnlockedAchievements.contains(achievement.id)

            if (isNowUnlocked && !wasUnlocked) {
                newlyUnlocked.add(achievement.name)
            }
        }

        // Update previously unlocked set
        previouslyUnlockedAchievements.clear()
        previouslyUnlockedAchievements.addAll(user.achievements.filter { it.value }.keys)

        // Send notifications for new achievements
        newlyUnlocked.forEach { achievementName ->
            sendAchievementNotification(achievementName)
        }
    }

    // Add this function to send achievement notification
    private fun sendAchievementNotification(achievementName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = currentUserId ?: return@launch
                val fcmToken = getUserFCMToken(userId)

                if (fcmToken != null && fcmToken.isNotEmpty()) {
                    val notification = mapOf(
                        "token" to fcmToken,
                        "title" to "🏅 New Achievement Unlocked!",
                        "body" to "Congratulations! You've unlocked: $achievementName",
                        "type" to "achievement",
                        "achievement" to achievementName,
                        "timestamp" to System.currentTimeMillis()
                    )

                    val db = FirebaseFirestore.getInstance()
                    db.collection("notifications").add(notification)

                    // Show local notification
                    showLocalNotification(
                        "🏅 New Achievement Unlocked!",
                        "You've unlocked: $achievementName"
                    )
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private suspend fun getUserFCMToken(userId: String): String? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("users").document(userId).get().await()
            snapshot.getString("fcmToken")
        } catch (e: Exception) {
            null
        }
    }

    private fun showLocalNotification(title: String, message: String) {
        val intent = Intent(this, AchievementsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "cashia_notifications",
                "CasHIA Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, "cashia_notifications")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.premium_primary))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}