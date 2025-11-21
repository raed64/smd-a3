package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.data.api.LoginRequest
import com.AppFlix.i220968_i228810.model.UserProfile
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class QuickLoginActivity : AppCompatActivity() {

    private lateinit var loginButtonQuick: CardView
    private lateinit var switchAccounts: TextView
    private lateinit var signUpLinkQuick: TextView
    private lateinit var usernameQuick: TextView
    private lateinit var profileImageQuick: ImageView

    private val sessionManager by lazy { SessionManager(this) }

    private var cachedProfile: UserProfile? = null
    private var loadingDialog: AlertDialog? = null
    private var loadingMessageView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_login)

        // Hide the action bar for full-screen look
        supportActionBar?.hide()

        initViews()
        loadCachedProfile()
        setupClickListeners()
    }

    private fun initViews() {
        loginButtonQuick = findViewById(R.id.login_button_quick)
        switchAccounts = findViewById(R.id.switch_accounts)
        signUpLinkQuick = findViewById(R.id.sign_up_link_quick)
        usernameQuick = findViewById(R.id.username_quick)
        profileImageQuick = findViewById(R.id.profile_image_quick)
    }

    private fun loadCachedProfile() {
        cachedProfile = sessionManager.getUserProfile()
        val profile = cachedProfile
        if (profile == null) {
            // No cached user → go back to normal login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val displayName = when {
            profile.username.isNotBlank() -> profile.username
            profile.firstName.isNotBlank() -> profile.firstName
            profile.email.isNotBlank() -> profile.email.substringBefore("@")
            else -> getString(R.string.quick_login_title)
        }
        usernameQuick.text = displayName

        if (profile.profileImageUrl.isNotBlank()) {
            val picasso = Picasso.get()
            picasso.load(profile.profileImageUrl)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .transform(CircleTransform())
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(profileImageQuick, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        // Try online if cache failed
                        picasso.load(profile.profileImageUrl)
                            .transform(CircleTransform())
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(profileImageQuick)
                    }
                })
        } else {
            profileImageQuick.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    private fun setupClickListeners() {
        // "Continue" button → ask for password
        loginButtonQuick.setOnClickListener {
            showPasswordPrompt()
        }

        // "Switch accounts" → clear cached user and open full Login screen
        switchAccounts.setOnClickListener {
            sessionManager.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // "Sign up" link → clear cached user and open Signup screen
        signUpLinkQuick.setOnClickListener {
            sessionManager.clear()
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
    }

    private fun showPasswordPrompt() {
        val profile = cachedProfile
        if (profile?.email.isNullOrBlank()) {
            Toast.makeText(this, R.string.quick_login_error_no_email, Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_quick_login_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.quick_login_password_input)
        val errorText = dialogView.findViewById<TextView>(R.id.quick_login_error)
        dialogView.findViewById<TextView>(R.id.quick_login_message).text =
            getString(R.string.quick_login_password_prompt)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.quick_login_title)
            .setView(dialogView)
            .setPositiveButton(R.string.log_in, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = passwordInput.text?.toString()?.trim().orEmpty()
                if (password.isEmpty()) {
                    errorText.text = getString(R.string.error_password_required)
                    errorText.isVisible = true
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    errorText.text = getString(R.string.error_password_length)
                    errorText.isVisible = true
                    return@setOnClickListener
                }
                errorText.isVisible = false
                dialog.dismiss()
                attemptQuickLogin(profile!!.email, password)
            }
        }

        dialog.show()
    }

    private fun attemptQuickLogin(email: String, password: String) {
        showLoadingDialog(getString(R.string.logging_in))
        loginButtonQuick.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = LoginRequest(
                    email = email,
                    password = password
                )

                val response = ApiClient.authApi.login(request)

                if (!response.isSuccessful) {
                    onQuickLoginFailed("Server error: ${response.code()}")
                    return@launch
                }

                val body = response.body()
                if (body == null || !body.success || body.user == null) {
                    onQuickLoginFailed(
                        body?.message ?: getString(R.string.error_login_failed)
                    )
                    return@launch
                }

                val backendUser = body.user

                // Build a local UserProfile from API response
                val profile = UserProfile(
                    uid = backendUser.id.toString(),
                    username = backendUser.username,
                    firstName = backendUser.first_name ?: "",
                    lastName = backendUser.last_name ?: "",
                    dateOfBirth = backendUser.dob ?: "",
                    email = backendUser.email,
                    // keep previously selected local profile image (if any)
                    profileImageUrl = cachedProfile?.profileImageUrl ?: "",
                    createdAt = System.currentTimeMillis()
                )

                sessionManager.saveUserProfile(profile)
                cachedProfile = profile

                onQuickLoginSuccess()
            } catch (e: Exception) {
                Log.e("QUICK_LOGIN_ERROR", "Quick login request failed", e)
                onQuickLoginFailed(
                    e.localizedMessage ?: getString(R.string.error_login_failed)
                )
            }
        }
    }

    private fun onQuickLoginSuccess() {
        hideLoadingDialog()
        loginButtonQuick.isEnabled = true
        navigateToMainFeed()
    }

    private fun onQuickLoginFailed(message: String) {
        hideLoadingDialog()
        loginButtonQuick.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMainFeed() {
        val intent = Intent(this, MainFeedActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
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