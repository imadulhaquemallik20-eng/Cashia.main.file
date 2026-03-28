package com.hia.cashia

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.min

class SettingsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var avatarImageView: ImageView
    private lateinit var changeUsernameText: TextView
    private lateinit var privacyPolicyText: TextView
    private lateinit var termsText: TextView
    private lateinit var rateAppText: TextView
    private lateinit var shareAppText: TextView
    private lateinit var logoutText: TextView
    private lateinit var deleteAccountText: TextView
    private lateinit var appVersionText: TextView
    private lateinit var chooseFromGalleryText: TextView
    private lateinit var useDefaultAvatarText: TextView

    private var currentUserId: String? = null
    private var currentUser: User? = null

    // Image picker launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uploadAvatarToFirestore(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
        loadUserData()
        loadUserProfileForNav()
        setAppVersion()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        // Avatar related views
        avatarImageView = findViewById(R.id.avatarImageView)
        chooseFromGalleryText = findViewById(R.id.chooseFromGalleryText)
        useDefaultAvatarText = findViewById(R.id.useDefaultAvatarText)

        // Other settings
        changeUsernameText = findViewById(R.id.changeUsernameText)
        privacyPolicyText = findViewById(R.id.privacyPolicyText)
        termsText = findViewById(R.id.termsText)
        rateAppText = findViewById(R.id.rateAppText)
        shareAppText = findViewById(R.id.shareAppText)
        logoutText = findViewById(R.id.logoutText)
        deleteAccountText = findViewById(R.id.deleteAccountText)
        appVersionText = findViewById(R.id.appVersionText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Settings"
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

    private fun loadUserData() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = userManager.getUserData(currentUserId!!)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    currentUser = result.getOrNull()
                    currentUser?.let { user ->
                        // Load avatar from Base64 if available
                        if (user.avatarBase64.isNotEmpty()) {
                            try {
                                val imageBytes = Base64.decode(user.avatarBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                avatarImageView.setImageBitmap(bitmap)
                                // Apply circle crop
                                avatarImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                                avatarImageView.clipToOutline = true
                            } catch (e: Exception) {
                                loadDefaultAvatar()
                            }
                        } else if (user.avatar.startsWith("http")) {
                            Glide.with(this@SettingsActivity)
                                .load(user.avatar)
                                .circleCrop()
                                .placeholder(R.drawable.ic_default_avatar)
                                .into(avatarImageView)
                        } else {
                            loadDefaultAvatar()
                        }
                    }
                }
            }
        }
    }

    private fun loadDefaultAvatar() {
        Glide.with(this)
            .load(R.drawable.ic_default_avatar)
            .circleCrop()
            .into(avatarImageView)
    }

    private fun setAppVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            appVersionText.text = "v0.0.4"
        } catch (e: PackageManager.NameNotFoundException) {
            appVersionText.text = "v0.0.4"
        }
    }

    private fun setupClickListeners() {
        chooseFromGalleryText.setOnClickListener {
            openImagePicker()
        }

        useDefaultAvatarText.setOnClickListener {
            resetToDefaultAvatar()
        }

        changeUsernameText.setOnClickListener {
            showEditUsernameDialog()
        }

        privacyPolicyText.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        termsText.setOnClickListener {
            showTermsDialog()
        }

        rateAppText.setOnClickListener {
            rateApp()
        }

        shareAppText.setOnClickListener {
            shareApp()
        }

        logoutText.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        deleteAccountText.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun uploadAvatarToFirestore(imageUri: Uri) {
        val progressDialog = AlertDialog.Builder(this).setMessage("Setting avatar...").create()
        progressDialog.show()

        try {
            // Get bitmap from URI
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Compress image to reduce size (max 100KB)
            val baos = ByteArrayOutputStream()
            var quality = 80
            do {
                baos.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 10
            } while (baos.size() > 100000 && quality > 20)

            val imageBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            // Save to Firestore
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    usersCollection.document(currentUserId!!)
                        .update("avatarBase64", base64Image)
                        .await()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                        progressDialog.dismiss()
                        loadUserData()
                        loadUserProfileForNav()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                        progressDialog.dismiss()
                    }
                }
            }

        } catch (e: Exception) {
            progressDialog.dismiss()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetToDefaultAvatar() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                usersCollection.document(currentUserId!!)
                    .update("avatarBase64", "")
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Avatar reset to default", Toast.LENGTH_SHORT).show()
                    loadUserData()
                    loadUserProfileForNav()
                    loadDefaultAvatar()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Failed to reset avatar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditUsernameDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Change Username")

        val input = EditText(this)
        input.hint = "Enter new username"
        input.setText(currentUser?.username)
        input.setBackgroundResource(R.drawable.edit_text_background)
        input.setPadding(50, 30, 50, 30)

        builder.setView(input)
        builder.setPositiveButton("Save") { _, _ ->
            val newUsername = input.text.toString().trim()
            if (newUsername.length >= 3) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        usersCollection.document(currentUserId!!).update("username", newUsername).await()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Username updated!", Toast.LENGTH_SHORT).show()
                            loadUserData()
                            loadUserProfileForNav()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Failed to update username", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Username must be at least 3 characters", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showTermsDialog() {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Terms of Service")
            .setMessage("""
                CasHIA Terms of Service
                
                1. You must be at least 13 years old to use this app.
                
                2. You are responsible for maintaining the security of your account.
                
                3. We reserve the right to suspend accounts for cheating or abuse.
                
                4. All coin earnings are virtual and cannot be exchanged for real money.
                
                5. We may update these terms at any time.
                
                By using CasHIA, you agree to these terms.
            """.trimIndent())
            .setPositiveButton("I Understand") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun rateApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out CasHIA - Earn coins by playing fun games! Download it here: https://play.google.com/store/apps/details?id=$packageName")
        }
        startActivity(Intent.createChooser(shareIntent, "Share CasHIA"))
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

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action is permanent and cannot be undone. All your coins, progress, and data will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteAccount() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete user from Firestore
                usersCollection.document(currentUserId!!).delete().await()

                // Delete user from Firebase Auth
                auth.currentUser?.delete()?.await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Account deleted successfully", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Failed to delete account: ${e.message}", Toast.LENGTH_SHORT).show()
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
                startActivity(Intent(this, WithdrawalActivity::class.java))
            }
            R.id.nav_settings -> {
                // Already here
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