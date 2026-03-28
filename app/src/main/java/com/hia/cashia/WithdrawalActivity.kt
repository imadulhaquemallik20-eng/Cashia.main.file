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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

class WithdrawalActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    // ============================================
    // TEMPORARY WITHDRAWAL BLOCK - REMOVE WHEN READY
    // ============================================
    private val isWithdrawalEnabled = false // Set to true to enable withdrawal
    // ============================================

    private lateinit var coinBalanceText: TextView
    private lateinit var withdrawalAmountEditText: EditText
    private lateinit var upiIdEditText: EditText
    private lateinit var withdrawButton: Button
    private lateinit var minWithdrawalText: TextView
    private lateinit var processingFeeText: TextView
    private lateinit var blockMessageCard: MaterialCardView
    private lateinit var blockMessageText: TextView

    private var currentUserId: String? = null
    private var currentBalance: Int = 0
    private val MIN_WITHDRAWAL = 100
    private val PROCESSING_FEE = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_withdrawal)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()
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

        if (isWithdrawalEnabled) {
            // Show withdrawal form
            showWithdrawalForm()
            loadUserData()
        } else {
            // Show temporary block message
            showTemporaryBlockMessage()
        }

        loadUserProfileForNav()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        coinBalanceText = findViewById(R.id.coinBalanceText)
        withdrawalAmountEditText = findViewById(R.id.withdrawalAmountEditText)
        upiIdEditText = findViewById(R.id.upiIdEditText)
        withdrawButton = findViewById(R.id.withdrawButton)
        minWithdrawalText = findViewById(R.id.minWithdrawalText)
        processingFeeText = findViewById(R.id.processingFeeText)
        blockMessageCard = findViewById(R.id.blockMessageCard)
        blockMessageText = findViewById(R.id.blockMessageText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Withdrawal"
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

    private fun showWithdrawalForm() {
        // Hide block message, show withdrawal form
        blockMessageCard.visibility = View.GONE
        withdrawalAmountEditText.visibility = View.VISIBLE
        upiIdEditText.visibility = View.VISIBLE
        withdrawButton.visibility = View.VISIBLE
        minWithdrawalText.visibility = View.VISIBLE
        processingFeeText.visibility = View.VISIBLE
    }

    private fun showTemporaryBlockMessage() {
        // Hide withdrawal form, show block message
        withdrawalAmountEditText.visibility = View.GONE
        upiIdEditText.visibility = View.GONE
        withdrawButton.visibility = View.GONE
        minWithdrawalText.visibility = View.GONE
        processingFeeText.visibility = View.GONE
        blockMessageCard.visibility = View.VISIBLE
    }

    private fun loadUserData() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(currentUserId!!)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    currentBalance = user?.coinBalance ?: 0
                    coinBalanceText.text = "💰 $currentBalance coins"
                }
            }
        }
    }

    private fun setupClickListeners() {
        withdrawButton.setOnClickListener {
            processWithdrawal()
        }
    }

    private fun processWithdrawal() {
        val amountStr = withdrawalAmountEditText.text.toString()
        val upiId = upiIdEditText.text.toString()

        if (amountStr.isEmpty() || upiId.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toIntOrNull() ?: 0

        if (amount < MIN_WITHDRAWAL) {
            Toast.makeText(this, "Minimum withdrawal amount is $MIN_WITHDRAWAL coins", Toast.LENGTH_SHORT).show()
            return
        }

        if (!upiId.contains("@")) {
            Toast.makeText(this, "Please enter a valid UPI ID", Toast.LENGTH_SHORT).show()
            return
        }

        val totalDeduction = amount + PROCESSING_FEE

        if (currentBalance < totalDeduction) {
            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Confirm Withdrawal")
        builder.setMessage("""
            Amount: $amount coins
            Processing Fee: $PROCESSING_FEE coins
            Total Deduction: $totalDeduction coins
            UPI ID: $upiId
            
            Proceed with withdrawal?
        """.trimIndent())

        builder.setPositiveButton("Confirm") { _, _ ->
            performWithdrawal(amount, totalDeduction, upiId)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun performWithdrawal(amount: Int, totalDeduction: Int, upiId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newBalance = currentBalance - totalDeduction

                usersCollection.document(currentUserId!!)
                    .update("coinBalance", newBalance)
                    .await()

                val transaction = mapOf(
                    "type" to "withdrawal",
                    "amount" to -amount,
                    "fee" to PROCESSING_FEE,
                    "upiId" to upiId,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                usersCollection.document(currentUserId!!)
                    .collection("transactions")
                    .add(transaction)
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WithdrawalActivity,
                        "Withdrawal request submitted successfully!", Toast.LENGTH_LONG).show()

                    withdrawalAmountEditText.text.clear()
                    upiIdEditText.text.clear()
                    loadUserData()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WithdrawalActivity,
                        "Failed to process withdrawal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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