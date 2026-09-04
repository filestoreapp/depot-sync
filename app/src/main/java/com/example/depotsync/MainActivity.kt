package com.example.depotsync

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("depot_sync", MODE_PRIVATE)
        webView = findViewById(R.id.webview)

        setupWebView()

        // Check if token exists; if not, prompt for it
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
            // Optional: allow mixed content if backend is HTTP (not recommended for production)
            // mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = WebViewClient()
    }

    private fun loadDepot() {
        // Replace with your actual Depot web URL
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
                // Without token, the webapp will show the token gate; still load it.
                loadDepot()
            }
            .show()
    }
}
