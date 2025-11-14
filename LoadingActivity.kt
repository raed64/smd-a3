package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.AppFlix.i220968_i228810.data.SessionManager

class LoadingActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        // Hide action bar
        supportActionBar?.hide()

        // Initialize SessionManager
        sessionManager = SessionManager(this)

        // Navigate after 3 seconds
        window.decorView.postDelayed({
            // Check if user is logged in locally via SessionManager
            val user = sessionManager.getUserProfile()

            val nextIntent = if (user != null && user.uid.isNotEmpty()) {
                // User exists -> Go to Main Feed
                Intent(this, MainFeedActivity::class.java)
            } else {
                // No user -> Go to Signup (Requirement: First-time users directed to sign up)
                Intent(this, SignupActivity::class.java)
            }

            startActivity(nextIntent)
            finish()
        }, 5000)
    }
}