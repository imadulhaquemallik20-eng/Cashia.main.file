package com.hia.cashia

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class WatchAdsActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private var currentUserId: String? = null

    private lateinit var coinBalanceText: TextView
    private lateinit var adsRemainingText: TextView
    private lateinit var watchAdButton: Button
    private lateinit var backButton: ImageButton

    private var remainingAdsToday = 50
    private val handler = Handler(Looper.getMainLooper())
    private var adCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_ads)

        userManager = UserManager()
        currentUserId = userManager.getCurrentUserId()

        if (currentUserId == null) {
            finish()
            return
        }

        initViews()
        loadUserData()
        loadAdStatus()
        startAdCheck()
    }

    private fun initViews() {
        coinBalanceText = findViewById(R.id.coinBalanceText)
        adsRemainingText = findViewById(R.id.adsRemainingText)
        watchAdButton = findViewById(R.id.watchAdButton)
        backButton = findViewById(R.id.backButton)

        watchAdButton.setOnClickListener {
            showRewardedAd()
        }

        backButton.setOnClickListener {
            stopAdCheck()
            finish()
        }
    }

    private fun startAdCheck() {
        adCheckRunnable = object : Runnable {
            override fun run() {
                if (remainingAdsToday > 0) {
                    val available = IronSourceManager.isRewardedVideoAvailable()
                    watchAdButton.isEnabled = available
                    watchAdButton.text = if (available) "Watch Ad (+2 coins)" else "Loading Ad..."

                    Log.d("WatchAds", "Ad available: $available, remaining: $remainingAdsToday")

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

    private fun loadUserData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = userManager.getUserData(currentUserId!!)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        coinBalanceText.text = "💰 ${user.coinBalance} coins"
                    }
                }
            }
        }
    }

    private fun loadAdStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = userManager.getAdWatchStatus(currentUserId!!)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val status = result.getOrNull()
                    if (status != null) {
                        remainingAdsToday = status.remainingAds
                        updateAdsRemainingText()

                        if (remainingAdsToday <= 0) {
                            watchAdButton.isEnabled = false
                            watchAdButton.text = "Daily Limit Reached"
                            stopAdCheck()
                        }
                    }
                }
            }
        }
    }

    private fun updateAdsRemainingText() {
        adsRemainingText.text = "📊 Today: $remainingAdsToday/50 ads remaining"
    }

    private fun showRewardedAd() {
        if (remainingAdsToday <= 0) {
            Toast.makeText(this, "Daily limit reached! Come back tomorrow.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!IronSourceManager.isRewardedVideoAvailable()) {
            Toast.makeText(this, "Ad still loading. Please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        IronSourceManager.showRewardedVideo(this) {
            giveCoinsReward()
        }
    }

    private fun giveCoinsReward() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = userManager.addCoinsFromAd(currentUserId!!)

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val adResult = result.getOrNull()
                    if (adResult != null && adResult.success) {
                        remainingAdsToday = adResult.maxAdsPerDay - adResult.adsWatchedToday
                        updateAdsRemainingText()
                        loadUserData()

                        Toast.makeText(
                            this@WatchAdsActivity,
                            "+${adResult.coinsEarned} coins earned!",
                            Toast.LENGTH_SHORT
                        ).show()

                        if (remainingAdsToday <= 0) {
                            watchAdButton.isEnabled = false
                            watchAdButton.text = "Daily Limit Reached"
                            stopAdCheck()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        IronSourceManager.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        IronSourceManager.onPause(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdCheck()
    }
}