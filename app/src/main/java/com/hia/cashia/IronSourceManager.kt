package com.hia.cashia

import android.app.Activity
import android.app.Application
import android.util.Log
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.integration.IntegrationHelper
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.model.Placement
import com.ironsource.mediationsdk.sdk.RewardedVideoListener

object IronSourceManager {

    private const val TAG = "IronSourceManager"
    private const val IRONSOURCE_APP_KEY = "85460dcd"
    private const val PLACEMENT_NAME = "DefaultRewardedVideo"

    private var rewardedVideoAvailable = false
    private var isInitialized = false
    private var rewardListener: (() -> Unit)? = null
    private var isLoading = false

    fun initialize(application: Application) {
        if (isInitialized) return

        Log.d(TAG, "🚀 Initializing IronSource...")

        try {
            IntegrationHelper.validateIntegration(application)

            IronSource.setRewardedVideoListener(object : RewardedVideoListener {
                override fun onRewardedVideoAdOpened() {
                    Log.d(TAG, "📺 Ad opened")
                }

                override fun onRewardedVideoAdClosed() {
                    Log.d(TAG, "🔒 Ad closed")

                    // Give reward when ad is closed (user watched full ad)
                    rewardListener?.let {
                        Log.d(TAG, "🎁 Executing reward listener")
                        it()
                        rewardListener = null
                    }

                    // Reset availability
                    rewardedVideoAvailable = false
                    isLoading = false

                    // Load next ad immediately
                    Log.d(TAG, "📥 Loading next ad...")
                    IronSource.loadRewardedVideo()
                }

                override fun onRewardedVideoAvailabilityChanged(available: Boolean) {
                    Log.d(TAG, "📢 Availability: $available")
                    rewardedVideoAvailable = available

                    if (available) {
                        Log.d(TAG, "🎉 Ad is READY!")
                        isLoading = false
                    } else {
                        Log.d(TAG, "⏳ Ad is loading...")
                        // If not available and not loading, load
                        if (!isLoading && isInitialized) {
                            isLoading = true
                            IronSource.loadRewardedVideo()
                        }
                    }
                }

                override fun onRewardedVideoAdStarted() {
                    Log.d(TAG, "▶️ Ad started")
                }

                override fun onRewardedVideoAdEnded() {
                    Log.d(TAG, "⏹️ Ad ended")
                }

                override fun onRewardedVideoAdRewarded(placement: Placement) {
                    Log.d(TAG, "🏆 Ad rewarded: ${placement.rewardName}")
                }

                override fun onRewardedVideoAdShowFailed(error: IronSourceError) {
                    Log.e(TAG, "❌ Show failed: ${error.errorMessage}")
                    rewardedVideoAvailable = false
                    isLoading = false
                    // Retry loading
                    IronSource.loadRewardedVideo()
                }

                override fun onRewardedVideoAdClicked(placement: Placement) {
                    Log.d(TAG, "👆 Ad clicked")
                }
            })

            // Test mode
            IronSource.setMetaData("is_test_sdk", "true")

            // Initialize
            IronSource.init(application, IRONSOURCE_APP_KEY)
            isInitialized = true
            isLoading = true

            // Load first ad
            IronSource.loadRewardedVideo()

            Log.d(TAG, "✅ IronSource initialized")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: ${e.message}")
        }
    }

    fun showRewardedVideo(activity: Activity, onRewardComplete: (() -> Unit)? = null) {
        Log.d(TAG, "🎯 Show ad requested. Available: $rewardedVideoAvailable")

        if (!isInitialized) {
            Log.e(TAG, "❌ Not initialized")
            android.widget.Toast.makeText(activity,
                "Ad system initializing...",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (rewardedVideoAvailable) {
            Log.d(TAG, "✅ Ad available, showing")
            rewardListener = onRewardComplete
            IronSource.showRewardedVideo(PLACEMENT_NAME)
        } else {
            Log.d(TAG, "❌ Ad not available")
            android.widget.Toast.makeText(activity,
                "Ad is loading. Please wait a moment and try again.",
                android.widget.Toast.LENGTH_SHORT).show()

            // Force reload
            if (!isLoading) {
                isLoading = true
                IronSource.loadRewardedVideo()
            }
        }
    }

    fun isRewardedVideoAvailable(): Boolean {
        return rewardedVideoAvailable
    }

    fun onResume(activity: Activity) {
        if (isInitialized) {
            try {
                IronSource.onResume(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onResume: ${e.message}")
            }
        }
    }

    fun onPause(activity: Activity) {
        if (isInitialized) {
            try {
                IronSource.onPause(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPause: ${e.message}")
            }
        }
    }
}