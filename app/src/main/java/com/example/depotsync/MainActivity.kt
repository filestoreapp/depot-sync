package com.example.depotsync

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("depot_sync", MODE_PRIVATE)
        webView = findViewById<WebView>(R.id.webview)

        setupWebView()

        // If access token not stored, ask user to enter it (optional)
        if (prefs.getString("access_token", null).isNullOrEmpty()) {
            showTokenDialog()
        } else {
            loadDepot()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            // For better performance
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Inject token into localStorage if we have it saved
                val token = prefs.getString("access_token", null)
                if (token != null) {
                    val escapedToken = token.replace("\\", "\\\\").replace("'", "\\'")
                    val js = "localStorage.setItem('depot_app_token', '$escapedToken');"
                    view?.evaluateJavascript(js, null)
                }
            }
        }
    }

    private fun loadDepot() {
        // Replace with your actual Depot web app URL
        val depotUrl = "https://your-app.northflank.app"
        webView.loadUrl(depotUrl)
    }

    private fun showTokenDialog() {
        val input = EditText(this)
        input.hint = "Paste your Depot access token"
        AlertDialog.Builder(this)
            .setTitle("Access Token")
            .setMessage("Enter the token from your Depot webapp (localStorage).")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val token = input.text.toString().trim()
                if (token.isNotEmpty()) {
                    prefs.edit().putString("access_token", token).apply()
                    loadDepot()
                } else {
                    Toast.makeText(this, "Token cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Still load the webapp – it will show its own token gate.
                loadDepot()
            }
            .show()
    }

    // Handle Android back button to navigate WebView history
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Enable immersive full‑screen mode
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }
}
