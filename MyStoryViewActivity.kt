package com.AppFlix.i220968_i228810

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MyStoryViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_story_view)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Close my story
        findViewById<ImageView>(R.id.close_my_story_button).setOnClickListener {
            finish()
        }

        // Action buttons
        findViewById<ImageView>(R.id.create_story_button).setOnClickListener {
            // Handle create story
        }

        findViewById<ImageView>(R.id.facebook_share_button).setOnClickListener {
            // Handle Facebook share
        }

        findViewById<ImageView>(R.id.highlight_story_button).setOnClickListener {
            // Handle highlight story
        }

        findViewById<ImageView>(R.id.more_story_options_button).setOnClickListener {
            // Handle more options
        }
    }
}