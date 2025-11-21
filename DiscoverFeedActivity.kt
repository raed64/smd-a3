package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class DiscoverFeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover_feed)
        
        // Set status bar color to match the app theme
        window.statusBarColor = ContextCompat.getColor(this, R.color.light_gray)
        
        // Set up header interactions
        val qrCodeIcon = findViewById<ImageView>(R.id.qr_code_icon)
        val searchBar = findViewById<androidx.cardview.widget.CardView>(R.id.search_bar)
        
        // QR Code icon click
        qrCodeIcon.setOnClickListener {
            // TODO: Open QR code scanner functionality
        }
        
        // Search bar click - navigate to search feed
        searchBar.setOnClickListener {
            val intent = Intent(this, com.AppFlix.i220968_i228810.search.UserSearchActivity::class.java)
            startActivity(intent)
        }
        
        // Set up bottom navigation
        val homeNav = findViewById<ImageView>(R.id.nav_home)
        val searchNav = findViewById<ImageView>(R.id.nav_search)
        val addNav = findViewById<ImageView>(R.id.nav_add)
        val heartNav = findViewById<ImageView>(R.id.nav_heart)
        val profileNav = findViewById<CardView>(R.id.nav_profile)
        
        // Home navigation - go back to main feed
        homeNav.setOnClickListener {
            val intent = Intent(this, MainFeedActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        // Search navigation - already here, so highlight it
        searchNav.setColorFilter(ContextCompat.getColor(this, R.color.instagram_red))
        
        // Add post navigation
        addNav.setOnClickListener {
            val intent = Intent(this, UploadPhotoActivity::class.java)
            startActivity(intent)
        }
        
        // Heart/Activity navigation
        heartNav.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }
        
        // Profile navigation
        profileNav.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }
}
