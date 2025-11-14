package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.data.api.LoginRequest
import com.AppFlix.i220968_i228810.data.api.ResetPasswordRequest   // <-- create this DTO if not present
import com.AppFlix.i220968_i228810.model.UserProfile
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var backArrowLogin: ImageView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var forgotPassword: TextView
    private lateinit var loginButtonMain: CardView
    private lateinit var signUpLinkLogin: TextView

    private val sessionManager by lazy { SessionManager(this) }

    private var loadingDialog: AlertDialog? = null
    private var loadingMessageView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Hide action bar
        supportActionBar?.hide()

        initViews()
        setupClickListeners()
        prefillEmail()
    }

    private fun initViews() {
        backArrowLogin = findViewById(R.id.back_arrow_login)
        emailInput = findViewById(R.id.edit_username_login)
        passwordInput = findViewById(R.id.edit_password_login)
        forgotPassword = findViewById(R.id.forgot_password)
        loginButtonMain = findViewById(R.id.login_button_main)
        signUpLinkLogin = findViewById(R.id.sign_up_link_login)
    }

    private fun setupClickListeners() {
        backArrowLogin.setOnClickListener {
            finish()
        }

        loginButtonMain.setOnClickListener {
            attemptLogin()
        }

        forgotPassword.setOnClickListener {
            showResetPasswordDialog()
        }

        signUpLinkLogin.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun prefillEmail() {
        val prefilledEmail = intent?.getStringExtra(EXTRA_EMAIL)
            ?: sessionManager.getUserProfile()?.email
        if (!prefilledEmail.isNullOrBlank()) {
            emailInput.setText(prefilledEmail)
            emailInput.setSelection(prefilledEmail.length)
        }
    }

    // ---------------- LOGIN FLOW (API-BASED) ----------------

    private fun attemptLogin() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString()?.trim().orEmpty()

        when {
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
                loginViaApi(email, password)
            }
        }
    }

    private fun loginViaApi(email: String, password: String) {
        showLoadingDialog(getString(R.string.logging_in))
        loginButtonMain.isEnabled = false

        val request = LoginRequest(
            email = email,
            password = password
        )

        lifecycleScope.launch {
            try {
                val response = ApiClient.authApi.login(request)

                if (!response.isSuccessful) {
                    onLoginFailed("Server error: ${response.code()}")
                    return@launch
                }

                val body = response.body()
                if (body == null || !body.success || body.user == null) {
                    onLoginFailed(body?.message ?: getString(R.string.error_login_failed))
                    return@launch
                }

                // Build local UserProfile from backend user object
                val user = body.user
                val profile = UserProfile(
                    uid = user.id.toString(),
                    username = user.username,
                    firstName = user.first_name ?: "",
                    lastName = user.last_name ?: "",
                    dateOfBirth = user.dob ?: "",
                    email = user.email,
                    createdAt = System.currentTimeMillis()
                )

                // Save in SessionManager (this is now your "logged in" state)
                sessionManager.saveUserProfile(profile)

                onLoginSuccess()

            } catch (e: Exception) {
                // <-- THIS is exactly where that catch block goes
                hideLoadingDialog()
                loginButtonMain.isEnabled = true
                Log.e("LOGIN_ERROR", "Login request failed", e)
                Toast.makeText(
                    this@LoginActivity,
                    e.localizedMessage ?: "Login failed due to network error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onLoginSuccess() {
        hideLoadingDialog()
        loginButtonMain.isEnabled = true
        val intent = Intent(this, MainFeedActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun onLoginFailed(message: String) {
        hideLoadingDialog()
        loginButtonMain.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ---------------- RESET PASSWORD (API-BASED) ----------------

    private fun showResetPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_password, null)
        val emailField = dialogView.findViewById<EditText>(R.id.reset_password_email_input)
        val errorText = dialogView.findViewById<TextView>(R.id.reset_password_error)
        dialogView.findViewById<TextView>(R.id.reset_password_message).text =
            getString(R.string.reset_password_message)

        val currentEmail = emailInput.text?.toString()?.trim()
        if (!currentEmail.isNullOrBlank()) {
            emailField.setText(currentEmail)
            emailField.setSelection(currentEmail.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.reset_password_title)
            .setView(dialogView)
            .setPositiveButton(R.string.send, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = emailField.text?.toString()?.trim().orEmpty()
                if (email.isEmpty()) {
                    errorText.text = getString(R.string.error_email_required)
                    errorText.visibility = android.view.View.VISIBLE
                    return@setOnClickListener
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    errorText.text = getString(R.string.error_email_invalid)
                    errorText.visibility = android.view.View.VISIBLE
                    return@setOnClickListener
                }
                dialog.dismiss()
                sendPasswordReset(email)
            }
        }

        dialog.show()
    }

    private fun sendPasswordReset(email: String) {
        showLoadingDialog(getString(R.string.sending_reset_email))

        lifecycleScope.launch {
            try {
                // You need a resetPassword.php + ResetPasswordRequest + endpoint in AuthApi
                val request = ResetPasswordRequest(email = email)
                val response = ApiClient.authApi.resetPassword(request)

                hideLoadingDialog()

                if (!response.isSuccessful) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Server error: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val body = response.body()
                if (body == null || !body.success) {
                    Toast.makeText(
                        this@LoginActivity,
                        body?.message ?: getString(R.string.error_reset_email),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.reset_email_sent),
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                hideLoadingDialog()
                Log.e("RESET_ERROR", "Reset password request failed", e)
                Toast.makeText(
                    this@LoginActivity,
                    e.localizedMessage ?: "Failed to send reset email (network error)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------- LOADING DIALOG ----------------

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
