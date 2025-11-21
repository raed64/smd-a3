package com.AppFlix.i220968_i228810

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class UploadPhotoActivity : AppCompatActivity() {

    private lateinit var mainImagePreview: ImageView
    private lateinit var photosRecyclerView: RecyclerView
    private lateinit var photoAdapter: PhotoAdapter
    private val photoList = mutableListOf<PhotoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_photo)

        initializeViews()
        setupClickListeners()
        setupPhotoGrid()
        loadSamplePhotos()
    }

    private fun initializeViews() {
        mainImagePreview = findViewById(R.id.main_image_preview)
        photosRecyclerView = findViewById(R.id.photos_recycler_view)
    }

    private fun setupClickListeners() {
        // Header buttons
        findViewById<TextView>(R.id.cancel_button).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.next_button).setOnClickListener {
            // Handle next action - navigate to post creation screen
            // For now, just finish
            finish()
        }

        findViewById<TextView>(R.id.recents_dropdown).setOnClickListener {
            // Handle dropdown menu for photo folders
        }

        // Bottom overlay buttons
        findViewById<LinearLayout>(R.id.library_button).setOnClickListener {
            // Handle library action
        }

        findViewById<LinearLayout>(R.id.multi_select_button).setOnClickListener {
            // Handle multi-select action
        }

        findViewById<LinearLayout>(R.id.select_multiple_button).setOnClickListener {
            // Handle select multiple action
        }

        // Bottom tabs
        findViewById<LinearLayout>(R.id.library_tab).setOnClickListener {
            // Already on Library tab
        }

        findViewById<LinearLayout>(R.id.photo_tab).setOnClickListener {
            // Switch to Photo (camera) tab
        }

        findViewById<LinearLayout>(R.id.video_tab).setOnClickListener {
            // Switch to Video tab
        }
    }

    private fun setupPhotoGrid() {
        photoAdapter = PhotoAdapter(photoList) { photo ->
            // Update main preview when photo is selected
            mainImagePreview.setImageResource(photo.imageRes)
        }

        photosRecyclerView.apply {
            layoutManager = GridLayoutManager(this@UploadPhotoActivity, 4)
            adapter = photoAdapter
            // Remove default item animator for smoother experience
            itemAnimator = null
        }
    }

    private fun loadSamplePhotos() {
        // Add sample photos with different colors to simulate photo library
        val samplePhotos = listOf(
            PhotoItem(1, R.drawable.sample_photo_1),
            PhotoItem(2, R.drawable.sample_photo_2),
            PhotoItem(3, R.drawable.sample_photo_3),
            PhotoItem(4, R.drawable.sample_photo_4),
            PhotoItem(5, R.drawable.sample_photo_5),
            PhotoItem(6, R.drawable.sample_photo_6),
            PhotoItem(7, R.drawable.sample_photo_7),
            PhotoItem(8, R.drawable.sample_photo_8),
            PhotoItem(9, R.drawable.sample_photo_1),
            PhotoItem(10, R.drawable.sample_photo_2),
            PhotoItem(11, R.drawable.sample_photo_3),
            PhotoItem(12, R.drawable.sample_photo_4),
            PhotoItem(13, R.drawable.sample_photo_5),
            PhotoItem(14, R.drawable.sample_photo_6),
            PhotoItem(15, R.drawable.sample_photo_7),
            PhotoItem(16, R.drawable.sample_photo_8),
            PhotoItem(17, R.drawable.sample_photo_1),
            PhotoItem(18, R.drawable.sample_photo_2),
            PhotoItem(19, R.drawable.sample_photo_3),
            PhotoItem(20, R.drawable.sample_photo_4),
            PhotoItem(21, R.drawable.sample_photo_5),
            PhotoItem(22, R.drawable.sample_photo_6),
            PhotoItem(23, R.drawable.sample_photo_7),
            PhotoItem(24, R.drawable.sample_photo_8)
        )

        photoList.addAll(samplePhotos)
        photoAdapter.notifyDataSetChanged()

        // Set first photo as main preview
        if (photoList.isNotEmpty()) {
            mainImagePreview.setImageResource(photoList[0].imageRes)
        }
    }
}