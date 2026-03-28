package com.hia.cashia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.tasks.await
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

class LeaderboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: LeaderboardViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    // UI Elements
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var yourRankCard: MaterialCardView
    private lateinit var yourRankPosition: TextView
    private lateinit var yourRankName: TextView
    private lateinit var yourRankScore: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyStateText: TextView

    private lateinit var adapter: LeaderboardAdapter
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
        viewModel = ViewModelProvider(this)[LeaderboardViewModel::class.java]
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()
        setupToolbar()
        setupDrawer()
        setupTabs()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
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
        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerView)
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        yourRankCard = findViewById(R.id.yourRankCard)
        yourRankPosition = findViewById(R.id.yourRankPosition)
        yourRankName = findViewById(R.id.yourRankName)
        yourRankScore = findViewById(R.id.yourRankScore)
        loadingProgress = findViewById(R.id.loadingProgress)
        emptyStateText = findViewById(R.id.emptyStateText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        toolbarTitle.text = "Leaderboard"
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

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Daily").setIcon(R.drawable.ic_today))
        tabLayout.addTab(tabLayout.newTab().setText("Weekly").setIcon(R.drawable.ic_weekly))
        tabLayout.addTab(tabLayout.newTab().setText("All Time").setIcon(R.drawable.ic_alltime))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.loadLeaderboard(LeaderboardCategory.DAILY)
                    1 -> viewModel.loadLeaderboard(LeaderboardCategory.WEEKLY)
                    2 -> viewModel.loadLeaderboard(LeaderboardCategory.ALL_TIME)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = LeaderboardAdapter(currentUserId)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
    }

    private fun setupSearch() {
        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                viewModel.searchUser(query)
            } else {
                Toast.makeText(this, "Enter a username", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.leaderboardEntries.observe(this) { entries ->
            adapter.submitList(entries)
            emptyStateText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.currentUserRank.observe(this) { entry ->
            if (entry != null) {
                yourRankCard.visibility = View.VISIBLE
                yourRankPosition.text = "#${entry.rank}"
                yourRankName.text = entry.username
                yourRankScore.text = "${entry.totalCoins} coins"
            } else {
                yourRankCard.visibility = View.GONE
            }
        }

        viewModel.searchResult.observe(this) { result ->
            if (result != null && result.found && result.entry != null) {
                showUserProfile(result.entry)
            }
            viewModel.clearSearch()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showUserProfile(entry: LeaderboardEntry) {
        val message = """
            👤 ${entry.username} ${entry.avatar}
            
            📊 Stats:
            • Total: ${entry.totalCoins}
            • Daily: ${entry.dailyCoins}
            • Weekly: ${entry.weeklyCoins}
            • Rank: #${entry.rank}
        """.trimIndent()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

// In LeaderboardActivity.kt, update the LeaderboardAdapter class

class LeaderboardAdapter(private val currentUserId: String?) :
    RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private var entries = listOf<LeaderboardEntry>()

    fun submitList(newEntries: List<LeaderboardEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_new, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position], position + 1, currentUserId)
    }

    override fun getItemCount() = entries.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rankText: TextView = itemView.findViewById(R.id.rankText)
        private val avatarTextView: TextView = itemView.findViewById(R.id.avatarText)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val coinsText: TextView = itemView.findViewById(R.id.coinsText)
        private val medalIcon: TextView = itemView.findViewById(R.id.medalIcon)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)

        fun bind(entry: LeaderboardEntry, position: Int, currentUserId: String?) {
            rankText.text = "#$position"
            usernameText.text = entry.username
            coinsText.text = "${entry.totalCoins} coins"

            // Handle avatar display - round shape
            if (entry.avatarBase64.isNotEmpty()) {
                // Show custom image avatar
                avatarTextView.visibility = View.GONE
                avatarImageView.visibility = View.VISIBLE
                try {
                    val imageBytes = android.util.Base64.decode(entry.avatarBase64, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    // Create circular bitmap
                    val circularBitmap = getCircularBitmap(bitmap)
                    avatarImageView.setImageBitmap(circularBitmap)
                    avatarImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (e: Exception) {
                    // Fallback to emoji
                    avatarTextView.visibility = View.VISIBLE
                    avatarImageView.visibility = View.GONE
                    avatarTextView.text = entry.avatar
                }
            } else {
                // Show emoji avatar
                avatarTextView.visibility = View.VISIBLE
                avatarImageView.visibility = View.GONE
                avatarTextView.text = entry.avatar
            }

            // Set medal for top 3
            when (position) {
                1 -> {
                    medalIcon.text = "🥇"
                    medalIcon.visibility = View.VISIBLE
                    rankText.visibility = View.GONE
                    cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.gold)
                    cardView.strokeWidth = 4
                }
                2 -> {
                    medalIcon.text = "🥈"
                    medalIcon.visibility = View.VISIBLE
                    rankText.visibility = View.GONE
                    cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.silver)
                    cardView.strokeWidth = 4
                }
                3 -> {
                    medalIcon.text = "🥉"
                    medalIcon.visibility = View.VISIBLE
                    rankText.visibility = View.GONE
                    cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.bronze)
                    cardView.strokeWidth = 4
                }
                else -> {
                    medalIcon.visibility = View.GONE
                    rankText.visibility = View.VISIBLE
                    cardView.strokeWidth = 0
                }
            }

            // Highlight current user
            if (entry.userId == currentUserId) {
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.premium_primary_light)
                )
            } else {
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.premium_card)
                )
            }
        }

        // Add this helper function inside the ViewHolder class
        private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
            val size = min(bitmap.width, bitmap.height)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint()
            val rect = Rect(0, 0, size, size)

            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            val srcRect = Rect(
                (bitmap.width - size) / 2,
                (bitmap.height - size) / 2,
                (bitmap.width + size) / 2,
                (bitmap.height + size) / 2
            )
            canvas.drawBitmap(bitmap, srcRect, rect, paint)

            return output
        }
    }
}