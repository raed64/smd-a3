package com.AppFlix.i220968_i228810

import androidx.lifecycle.lifecycleScope
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.data.api.SignupRequest
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.activity.result.contract.ActivityResultContracts
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.model.UserProfile

class SignupActivity : AppCompatActivity() {

    private lateinit var profilePictureCard: CardView
    private lateinit var profilePicture: ImageView
    private lateinit var backArrow: ImageView
    private lateinit var createAccountCard: CardView
    private lateinit var usernameInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var dobInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordVisibilityIcon: ImageView
    private lateinit var loginLink: TextView

    private val sessionManager by lazy { SessionManager(this) }

    private var selectedImageUri: Uri? = null
    private var isPasswordVisible = false

    private var loadingDialog: AlertDialog? = null
    private var loadingMessageView: TextView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            profilePicture.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Hide action bar
        supportActionBar?.hide()

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        profilePictureCard = findViewById(R.id.profile_picture_card)
        profilePicture = findViewById(R.id.profile_picture)
        backArrow = findViewById(R.id.back_arrow)
        createAccountCard = findViewById(R.id.create_account_card)
        usernameInput = findViewById(R.id.edit_username)
        nameInput = findViewById(R.id.edit_name)
        lastNameInput = findViewById(R.id.edit_lastname)
        dobInput = findViewById(R.id.edit_dob)
        emailInput = findViewById(R.id.edit_email)
        passwordInput = findViewById(R.id.edit_password)
        passwordVisibilityIcon = findViewById(R.id.password_visibility)
    loginLink = findViewById(R.id.login_link_signup)
    }

    private fun setupClickListeners() {
        // Profile picture click to open gallery
        profilePictureCard.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Back arrow click
        backArrow.setOnClickListener {
            finish()
        }

        passwordVisibilityIcon.setOnClickListener {
            togglePasswordVisibility()
        }

        // Create account button click
        createAccountCard.setOnClickListener {
            attemptSignup()
        }

        loginLink.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val intent = Intent(this, LoginActivity::class.java)
            if (email.isNotEmpty()) {
                intent.putExtra(LoginActivity.EXTRA_EMAIL, email)
            }
            startActivity(intent)
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        passwordInput.transformationMethod = if (isPasswordVisible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        passwordInput.setSelection(passwordInput.text?.length ?: 0)
    }

    private fun attemptSignup() {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        val firstName = nameInput.text?.toString()?.trim().orEmpty()
        val lastName = lastNameInput.text?.toString()?.trim().orEmpty()
        val dob = dobInput.text?.toString()?.trim().orEmpty()
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString()?.trim().orEmpty()

        when {
            username.isEmpty() -> {
                usernameInput.error = getString(R.string.error_username_required)
                usernameInput.requestFocus()
            }
            firstName.isEmpty() -> {
                nameInput.error = getString(R.string.error_first_name_required)
                nameInput.requestFocus()
            }
            lastName.isEmpty() -> {
                lastNameInput.error = getString(R.string.error_last_name_required)
                lastNameInput.requestFocus()
            }
            dob.isEmpty() -> {
                dobInput.error = getString(R.string.error_dob_required)
                dobInput.requestFocus()
            }
            email.isEmpty() -> {
                emailInput.error = getString(R.string.error_email_required)
                emailInput.requestFocus()
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailInput.error = getString(R.string.error_email_invalid)
                emailInput.requestFocus()
            }
            password.isEmpty() -> {
                passwordInput.error = getString(R.string.error_password_required)
                passwordInput.requestFocus()
            }
            password.length < 6 -> {
                passwordInput.error = getString(R.string.error_password_length)
                passwordInput.requestFocus()
            }
            else -> {
                createAccount(username, firstName, lastName, dob, email, password)
            }
        }
    }

    private fun createAccount(
        username: String,
        firstName: String,
        lastName: String,
        dob: String,
        email: String,
        password: String
    ) {
        showLoadingDialog(getString(R.string.creating_account))
        createAccountCard.isEnabled = false

        val request = SignupRequest(
            username = username,
            email = email,
            password = password,
            first_name = firstName,
            last_name = lastName,
            dob = dob
        )

        lifecycleScope.launch {
            try {
                val response = ApiClient.authApi.signup(request)

                if (!response.isSuccessful) {
                    onSignupFailed("Server error: ${response.code()}")
                    return@launch
                }

                val body = response.body()
                if (body == null || !body.success || body.user == null) {
                    onSignupFailed(body?.message ?: getString(R.string.error_signup_failed))
                    return@launch
                }

                // Build local UserProfile from backend response
                val profile = UserProfile(
                    uid = body.user.id.toString(),
                    username = body.user.username,
                    firstName = body.user.first_name ?: "",
                    lastName = body.user.last_name ?: "",
                    dateOfBirth = body.user.dob ?: "",
                    email = body.user.email,
                    profileImageUrl = selectedImageUri?.toString() ?: "",
                    createdAt = System.currentTimeMillis()
                )

                sessionManager.saveUserProfile(profile)

                hideLoadingDialog()
                createAccountCard.isEnabled = true
                Toast.makeText(this@SignupActivity, R.string.signup_success, Toast.LENGTH_SHORT).show()

                val intent = Intent(this@SignupActivity, MainFeedActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

            } catch (e: Exception) {
                onSignupFailed(e.localizedMessage ?: getString(R.string.error_signup_failed))
            }
        }
    }

    private fun onSignupFailed(message: String, cleanupUser: Boolean = false) {
        hideLoadingDialog()
        createAccountCard.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }


    private fun showLoadingDialog(message: String) {
        if (loadingDialog == null) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
            loadingMessageView = dialogView.findViewById(R.id.progress_message)
            loadingDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        loadingMessageView?.text = message
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }
}
