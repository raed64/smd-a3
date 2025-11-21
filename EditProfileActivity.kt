package com.AppFlix.i220968_i228810

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.databinding.ActivityEditProfileBinding
import com.AppFlix.i220968_i228810.model.UserProfile
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var sessionManager: SessionManager

    private val api = ApiClient.authApi
    private val followApi = ApiClient.followApi

    private var currentProfile: UserProfile? = null
    private var selectedProfileUri: Uri? = null
    private var selectedCoverUri: Uri? = null
    private var progressDialog: ProgressDialog? = null

    private val profilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedProfileUri = uri
                // Load local URI (no offline policy needed)
                Picasso.get().load(uri).transform(CircleTransform()).into(binding.profileImage)
            }
        }
    }

    private val coverPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedCoverUri = uri
                // Load local URI (no offline policy needed)
                Picasso.get().load(uri).fit().centerCrop().into(binding.coverImage)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.cancelButton.setOnClickListener { finish() }
        binding.doneButton.setOnClickListener { saveChanges() }

        binding.changePhotoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            profilePicker.launch(intent)
        }

        binding.changeCoverButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            coverPicker.launch(intent)
        }

        loadUserData()
    }

    private fun loadUserData() {
        val cached = sessionManager.getUserProfile()
        if (cached != null) {
            currentProfile = cached
            binding.nameInput.setText("${cached.firstName} ${cached.lastName}")
            binding.usernameInput.setText(cached.username)
            binding.emailInput.setText(cached.email)
            binding.bioInput.setText(cached.bio)

            // Load Profile Image with Offline Policy
            if (cached.profileImageUrl.isNotEmpty()) {
                val picasso = Picasso.get()
                picasso.load(cached.profileImageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .transform(CircleTransform())
                    .into(binding.profileImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(cached.profileImageUrl)
                                .transform(CircleTransform())
                                .into(binding.profileImage)
                        }
                    })
            }

            // Load Cover Image with Offline Policy
            if (cached.coverPhotoUrl.isNotEmpty()) {
                val picasso = Picasso.get()
                picasso.load(cached.coverPhotoUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .fit().centerCrop()
                    .into(binding.coverImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(cached.coverPhotoUrl)
                                .fit().centerCrop()
                                .into(binding.coverImage)
                        }
                    })
            }
        }
    }

    private fun saveChanges() {
        val userId = sessionManager.getUserProfile()?.uid ?: return
        val profile = currentProfile ?: return

        showProgress("Saving profile...")
        uploadImagesRecursive(userId, profile)
    }

    private fun uploadImagesRecursive(userId: String, profile: UserProfile) {
        val onCoverDone = { coverUrl: String? ->
            val finalCoverUrl = coverUrl ?: profile.coverPhotoUrl

            val onProfileDone = { profileUrl: String? ->
                val finalProfileUrl = profileUrl ?: profile.profileImageUrl
                updateProfileData(userId, profile, finalProfileUrl, finalCoverUrl)
            }

            if (selectedProfileUri != null) {
                uploadImage(selectedProfileUri!!, userId, "profile") { onProfileDone(it) }
            } else {
                onProfileDone(null)
            }
        }

        if (selectedCoverUri != null) {
            uploadImage(selectedCoverUri!!, userId, "cover") { onCoverDone(it) }
        } else {
            onCoverDone(null)
        }
    }

    private fun updateProfileData(userId: String, oldProfile: UserProfile, profileUrl: String, coverUrl: String) {
        val fullName = binding.nameInput.text.toString().trim()
        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0) ?: oldProfile.firstName
        val lastName = nameParts.getOrNull(1) ?: oldProfile.lastName
        val bioText = binding.bioInput.text.toString().trim()

        val updatedProfile = oldProfile.copy(
            firstName = firstName,
            lastName = lastName,
            username = binding.usernameInput.text.toString().trim(),
            profileImageUrl = profileUrl,
            coverPhotoUrl = coverUrl,
            bio = bioText
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.updateProfile(
                    userId = userId.toRequestBody("text/plain".toMediaTypeOrNull()),
                    username = updatedProfile.username.toRequestBody("text/plain".toMediaTypeOrNull()),
                    firstName = updatedProfile.firstName.toRequestBody("text/plain".toMediaTypeOrNull()),
                    lastName = updatedProfile.lastName.toRequestBody("text/plain".toMediaTypeOrNull()),
                    email = binding.emailInput.text.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                    bio = bioText.toRequestBody("text/plain".toMediaTypeOrNull())
                )

                withContext(Dispatchers.Main) {
                    hideProgress()
                    if (response.isSuccessful && response.body()?.success == true) {
                        sessionManager.saveUserProfile(updatedProfile)
                        Toast.makeText(this@EditProfileActivity, "Profile saved!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@EditProfileActivity, "Failed to update info", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadImage(uri: Uri, userId: String, type: String, onComplete: (String?) -> Unit) {
        val contentResolver = contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return onComplete(null)
        val bytes = inputStream.use { it.readBytes() }

        val reqFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", "${type}_$userId.jpg", reqFile)
        val idPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val typePart = type.toRequestBody("text/plain".toMediaTypeOrNull())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = followApi.updateProfileImage(idPart, typePart, body)
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.Main) { onComplete(response.body()?.url) }
                } else {
                    withContext(Dispatchers.Main) { onComplete(null) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(null) }
            }
        }
    }

    private fun showProgress(message: String) {
        progressDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }
}