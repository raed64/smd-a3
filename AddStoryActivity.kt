package com.AppFlix.i220968_i228810

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.stories.StoryRepository
import com.AppFlix.i220968_i228810.stories.StoryNetworkModule
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class AddStoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_MEDIA_URI = "extra_initial_media_uri"
    }

    private val sessionManager by lazy { SessionManager(this) }
    private val storyRepository by lazy {
        StoryRepository(
            sessionManager = sessionManager,
            api = StoryNetworkModule.api,
            contentResolver = contentResolver,
            context = this
        )
    }

    private var selectedMediaUri: Uri? = null
    private var isUploading = false

    private lateinit var storyBackground: ImageView

    private var progressDialog: AlertDialog? = null
    private var progressMessage: TextView? = null

    private lateinit var pickMediaLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_story)

        pickMediaLauncher =
            registerForActivityResult(
                ActivityResultContracts.OpenDocument(),
                object : ActivityResultCallback<Uri?> {
                    override fun onActivityResult(uri: Uri?) {
                        if (uri != null) {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: SecurityException) {}

                            onMediaSelected(uri)
                        }
                    }
                }
            )

        initViews()
        setupClickListeners()

        val handled = handleInitialMediaFromIntent()
        if (!handled) openMediaPicker()
    }

    private fun initViews() {
        storyBackground = findViewById(R.id.story_background)
    }

    private fun setupClickListeners() {
        storyBackground.setOnClickListener { if (!isUploading) openMediaPicker() }
        findViewById<ImageView>(R.id.gallery_button)?.setOnClickListener { if (!isUploading) openMediaPicker() }
        findViewById<ImageView>(R.id.close_button)?.setOnClickListener { if (!isUploading) finish() }

        findViewById<ImageView>(R.id.share_story_button).setOnClickListener { shareStory() }
        findViewById<LinearLayout>(R.id.your_stories_button)?.setOnClickListener { shareStory() }

        // Placeholders
        val comingSoonListener = View.OnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.close_friends_button)?.setOnClickListener(comingSoonListener)
        findViewById<ImageView>(R.id.user_tagging_button)?.setOnClickListener(comingSoonListener)
        findViewById<ImageView>(R.id.text_tool_button)?.setOnClickListener(comingSoonListener)
        findViewById<ImageView>(R.id.sticker_button)?.setOnClickListener(comingSoonListener)
        findViewById<ImageView>(R.id.music_button)?.setOnClickListener(comingSoonListener)
        findViewById<ImageView>(R.id.effects_add_button)?.setOnClickListener(comingSoonListener)
        findViewById<ImageView>(R.id.more_tools_button)?.setOnClickListener(comingSoonListener)
    }

    private fun handleInitialMediaFromIntent(): Boolean {
        val uriString = intent.getStringExtra(EXTRA_INITIAL_MEDIA_URI) ?: return false
        return try {
            val uri = Uri.parse(uriString)
            onMediaSelected(uri)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openMediaPicker() {
        pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
    }

    private fun onMediaSelected(uri: Uri) {
        selectedMediaUri = uri
        // Use Picasso for local URI
        Picasso.get().load(uri).fit().centerCrop().into(storyBackground)
    }

    private fun shareStory() {
        if (isUploading) return

        val mediaUri = selectedMediaUri
        if (mediaUri == null) {
            Toast.makeText(this, R.string.error_select_story_media, Toast.LENGTH_SHORT).show()
            openMediaPicker()
            return
        }

        isUploading = true
        showProgressDialog(getString(R.string.uploading_story))

        val extension = try {
            resolveExtension(mediaUri)
        } catch (ex: Exception) {
            hideProgressDialog()
            Toast.makeText(this, ex.localizedMessage ?: getString(R.string.error_upload_story), Toast.LENGTH_LONG).show()
            isUploading = false
            return
        }

        lifecycleScope.launch {
            try {
                updateProgressDialog(getString(R.string.uploading_story))
                storyRepository.uploadStory(mediaUri = mediaUri, fileExtension = extension)
                hideProgressDialog()
                Toast.makeText(this@AddStoryActivity, R.string.story_uploaded_success, Toast.LENGTH_SHORT).show()
                isUploading = false
                finish()
            } catch (e: Exception) {
                hideProgressDialog()
                Toast.makeText(this@AddStoryActivity, e.message ?: getString(R.string.error_upload_story), Toast.LENGTH_LONG).show()
                isUploading = false
            }
        }
    }

    private fun resolveExtension(uri: Uri): String {
        val type = contentResolver.getType(uri) ?: return "jpg"
        return when {
            type.contains("video") -> "mp4"
            type.contains("gif") -> "gif"
            type.contains("png") -> "png"
            else -> "jpg"
        }
    }

    private fun showProgressDialog(message: String) {
        if (progressDialog == null) {
            val view = layoutInflater.inflate(R.layout.dialog_progress, null)
            progressMessage = view.findViewById(R.id.progress_message)
            progressDialog = AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create()
        }
        progressMessage?.text = message
        progressDialog?.show()
    }

    private fun updateProgressDialog(message: String) {
        progressMessage?.text = message
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressMessage = null
    }
}