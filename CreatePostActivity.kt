package com.AppFlix.i220968_i228810.posts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.data.SessionManager
import com.squareup.picasso.Picasso
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CreatePostActivity : AppCompatActivity() {

    private val postRepository by lazy {
        PostRepository(
            SessionManager(this),
            PostNetworkModule.api,
            contentResolver = contentResolver,
            context = this
        )
    }

    private var selectedMediaUri: Uri? = null
    private var isUploading = false

    private lateinit var mediaPreview: ImageView
    private lateinit var captionInput: EditText
    private lateinit var pickMediaButton: Button
    private lateinit var shareButton: Button
    private lateinit var cancelButton: ImageView

    private var progressDialog: AlertDialog? = null
    private var progressMessage: TextView? = null
    private var progressBar: ProgressBar? = null

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            showMediaPreview(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        mediaPreview = findViewById(R.id.create_post_media_preview)
        captionInput = findViewById(R.id.create_post_caption)
        pickMediaButton = findViewById(R.id.create_post_pick_button)
        shareButton = findViewById(R.id.create_post_share_button)
        cancelButton = findViewById(R.id.create_post_close_button)

        pickMediaButton.setOnClickListener { openMediaPicker() }
        mediaPreview.setOnClickListener { if (!isUploading) openMediaPicker() }

        cancelButton.setOnClickListener {
            if (!isUploading) finish()
        }

        shareButton.setOnClickListener { sharePost() }
    }

    private fun openMediaPicker() {
        pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
    }

    private fun showMediaPreview(uri: Uri) {
        selectedMediaUri = uri
        // Use Picasso instead of Glide
        Picasso.get()
            .load(uri)
            .fit()
            .centerCrop()
            .into(mediaPreview)
    }

    private fun sharePost() {
        if (isUploading) return

        val mediaUri = selectedMediaUri
        if (mediaUri == null) {
            Toast.makeText(this, R.string.post_upload_error_media, Toast.LENGTH_SHORT).show()
            openMediaPicker()
            return
        }

        val caption = captionInput.text?.toString()?.trim().orEmpty()

        // 1. DISABLE BUTTON IMMEDIATELY
        isUploading = true
        shareButton.isEnabled = false // <--- Add this
        showProgressDialog(getString(R.string.post_upload_in_progress))

        lifecycleScope.launch {
            try {
                postRepository.createPost(mediaUri, caption)
                hideProgressDialog()
                Toast.makeText(this@CreatePostActivity, R.string.post_upload_success, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                hideProgressDialog()
                // 2. RE-ENABLE IF FAILED
                isUploading = false
                shareButton.isEnabled = true // <--- Add this
                Toast.makeText(
                    this@CreatePostActivity,
                    e.localizedMessage ?: getString(R.string.error_upload_story),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showProgressDialog(message: String) {
        if (progressDialog == null) {
            val view = layoutInflater.inflate(R.layout.dialog_progress, null)
            progressMessage = view.findViewById(R.id.progress_message)
            progressBar = view.findViewById(R.id.progress_bar)
            progressBar?.isIndeterminate = false
            progressBar?.max = 100
            progressBar?.progress = 0
            progressDialog = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create()
        }
        progressMessage?.text = message
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressMessage = null
        progressBar = null
        progressDialog = null
    }
}