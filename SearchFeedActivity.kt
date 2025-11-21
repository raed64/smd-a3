package com.AppFlix.i220968_i228810

import android.graphics.Typeface
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SearchFeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_feed)
        
        // Set status bar color to match the app theme
        window.statusBarColor = ContextCompat.getColor(this, R.color.light_gray)
        
        // Set up search functionality
        val searchEditText = findViewById<EditText>(R.id.search_edit_text)
        val clearSearchIcon = findViewById<ImageView>(R.id.clear_search_icon)
        val clearText = findViewById<TextView>(R.id.clear_text)
        
        // Clear search icon click
        clearSearchIcon.setOnClickListener {
            searchEditText.text.clear()
        }
        
        // Clear text click
        clearText.setOnClickListener {
            searchEditText.text.clear()
            finish() // Go back to discover feed
        }
        
        // Set up tab navigation
        val tabTop = findViewById<TextView>(R.id.tab_top)
        val tabAccounts = findViewById<TextView>(R.id.tab_accounts)
        val tabTags = findViewById<TextView>(R.id.tab_tags)
        val tabPlaces = findViewById<TextView>(R.id.tab_places)
        
        // Tab click listeners
        tabTop.setOnClickListener {
            selectTab(tabTop, tabAccounts, tabTags, tabPlaces)
        }
        
        tabAccounts.setOnClickListener {
            selectTab(tabAccounts, tabTop, tabTags, tabPlaces)
        }
        
        tabTags.setOnClickListener {
            selectTab(tabTags, tabTop, tabAccounts, tabPlaces)
        }
        
        tabPlaces.setOnClickListener {
            selectTab(tabPlaces, tabTop, tabAccounts, tabTags)
        }
    }
    
    private fun selectTab(selectedTab: TextView, vararg otherTabs: TextView) {
        // Highlight selected tab
        selectedTab.setTextColor(ContextCompat.getColor(this, R.color.black))
        selectedTab.setTypeface(null, Typeface.BOLD)
        
        // Reset other tabs
        otherTabs.forEach { tab ->
            tab.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            tab.setTypeface(null, Typeface.NORMAL)
        }
    }
}
