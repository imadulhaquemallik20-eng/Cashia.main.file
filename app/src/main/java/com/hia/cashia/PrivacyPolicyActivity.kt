package com.hia.cashia

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        initViews()
        setupToolbar()
        loadPrivacyPolicy()
        setupClickListeners()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarTitle).text = "Privacy Policy"
    }

    private fun loadPrivacyPolicy() {
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        // Load from assets
        webView.loadUrl("file:/android_asset/privacy_policy.html")

        // Open links in webview, not browser
        webView.webViewClient = WebViewClient()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
    }
}