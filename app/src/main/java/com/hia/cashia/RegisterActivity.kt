package com.hia.cashia

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var userManager: UserManager

    private lateinit var usernameEditText: EditText  // New field
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var goToLoginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        userManager = UserManager()

        if (auth.currentUser != null) {
            navigateToMain()
            return
        }

        initViews()
        setupClickListeners()
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
        usernameEditText = findViewById(R.id.usernameEditText)  // New
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        registerButton = findViewById(R.id.registerButton)
        goToLoginButton = findViewById(R.id.goToLoginButton)
    }

    private fun setupClickListeners() {
        registerButton.setOnClickListener {
            registerUser()
        }

        goToLoginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun registerUser() {
        val username = usernameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (username.length < 3) {
            Toast.makeText(this, "Username must be at least 3 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        registerButton.isEnabled = false
        registerButton.text = "Creating account..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                    CoroutineScope(Dispatchers.IO).launch {
                        val result = userManager.createUserProfile(userId, email, username)

                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Welcome $username! You got 50 coins!",
                                    Toast.LENGTH_LONG
                                ).show()
                                navigateToMain()
                            } else {
                                registerButton.isEnabled = true
                                registerButton.text = "Register"
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Failed to create profile",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else {
                    registerButton.isEnabled = true
                    registerButton.text = "Register"
                    Toast.makeText(
                        this,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}