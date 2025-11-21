package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start with LoadingActivity
        val intent = Intent(this, LoadingActivity::class.java)
        startActivity(intent)
        finish()
    }
}