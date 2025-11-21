package com.AppFlix.i220968_i228810

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var cameraExecutor: ExecutorService

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.camera_preview)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissions()
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Close camera
        findViewById<ImageView>(R.id.close_camera_button).setOnClickListener {
            finish()
        }

        // Flash toggle
        findViewById<ImageView>(R.id.flash_toggle).setOnClickListener {
            toggleFlash()
        }

        // Camera switch (front/back)
        findViewById<ImageView>(R.id.camera_switch).setOnClickListener {
            switchCamera()
        }

        // Mode selections
        findViewById<TextView>(R.id.mode_type).setOnClickListener {
            // Handle TYPE mode
        }

        findViewById<TextView>(R.id.mode_live).setOnClickListener {
            // Handle LIVE mode
        }

        findViewById<TextView>(R.id.mode_normal).setOnClickListener {
            // Handle NORMAL mode (default selected)
        }

        findViewById<TextView>(R.id.mode_boomerang).setOnClickListener {
            // Handle BOOMERANG mode
        }

        findViewById<TextView>(R.id.mode_super).setOnClickListener {
            // Handle SUPER mode
        }

        // Gallery button
        findViewById<FrameLayout>(R.id.gallery_button).setOnClickListener {
            val intent = Intent(this, UploadPhotoActivity::class.java)
            startActivity(intent)
        }

        // Capture button
        findViewById<FrameLayout>(R.id.capture_button).setOnClickListener {
            takePhoto()
        }

        // Effects button
        findViewById<ImageView>(R.id.effects_button).setOnClickListener {
            // Handle effects selection
        }
    }

    private fun hasCameraPermission(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        return cameraGranted
    }

    private fun requestPermissions() {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { builder ->
                builder.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (ex: Exception) {
                Toast.makeText(this, ex.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun toggleFlash() {
        val capture = imageCapture ?: return
        capture.flashMode = if (capture.flashMode == ImageCapture.FLASH_MODE_ON) {
            ImageCapture.FLASH_MODE_OFF
        } else {
            ImageCapture.FLASH_MODE_ON
        }
        val toastMessage = if (capture.flashMode == ImageCapture.FLASH_MODE_ON) {
            R.string.flash_enabled
        } else {
            R.string.flash_disabled
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        val photoFile = createImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@CameraActivity,
                            exception.localizedMessage ?: getString(R.string.error_capture_photo),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = FileProvider.getUriForFile(
                        this@CameraActivity,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        photoFile
                    )
                    grantUriPermission(
                        packageName,
                        savedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    runOnUiThread {
                        openAddStory(savedUri)
                    }
                }
            }
        )
    }

    private fun openAddStory(uri: Uri) {
        val intent = Intent(this, AddStoryActivity::class.java).apply {
            putExtra(AddStoryActivity.EXTRA_INITIAL_MEDIA_URI, uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun createImageFile(): File {
        val storageDir = File(cacheDir, "stories")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        return File(storageDir, "STORY_$timeStamp.jpg")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}