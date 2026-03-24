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
        toolbarTitle.text = "🏆 Leaderboard"
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
        val userId = currentUserId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(userId)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    val headerView = navView.getHeaderView(0)
                    val navAvatar = headerView.findViewById<TextView>(R.id.navAvatar)
                    val navUsername = headerView.findViewById<TextView>(R.id.navUsername)
                    val navEmail = headerView.findViewById<TextView>(R.id.navEmail)
                    user?.let {
                        navAvatar.text = it.avatar
                        navUsername.text = it.username
                        navEmail.text = it.email
                    }
                }
            }
        }
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

        changeUsernameButton.setOnClickListener { showEditUsernameDialog() }
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
                        Toast.makeText(this@LeaderboardActivity, "Username updated!", Toast.LENGTH_SHORT).show()
                        loadUserProfileForNav()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
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
        private val avatarText: TextView = itemView.findViewById(R.id.avatarText)
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val coinsText: TextView = itemView.findViewById(R.id.coinsText)
        private val medalIcon: TextView = itemView.findViewById(R.id.medalIcon)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)

        fun bind(entry: LeaderboardEntry, position: Int, currentUserId: String?) {
            rankText.text = "#$position"
            avatarText.text = entry.avatar
            usernameText.text = entry.username
            coinsText.text = "${entry.totalCoins} coins"

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
    }
}