/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import com.nextcloud.talk.account.AccountVerificationActivity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.runBlocking
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.account.WebViewLoginActivity
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.activities.HomeScreen
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityMainBinding
import com.nextcloud.talk.invitation.InvitationsActivity
import com.nextcloud.talk.lock.LockedActivity
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import io.reactivex.Observer
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.data.network.NetworkMonitor
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.delay

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity :
    BaseActivity(),
    ActionBarProvider {

    lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Activity: " + System.identityHashCode(this).toString())

        super.onCreate(savedInstanceState)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lockScreenIfConditionsApply()
            }
        })

        // Set the default theme to replace the launch screen theme.
        setTheme(R.style.AppTheme)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        // setSupportActionBar(binding.toolbar)



        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        
        // Setup network monitoring
        setupNetworkMonitoring()
        
        // Setup retry button
        setupRetryButton()
    }

    fun lockScreenIfConditionsApply() {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                val lockIntent = Intent(context, LockedActivity::class.java)
                startActivity(lockIntent)
            }
        }
    }

    private fun launchServerSelection() {
        // Show loading message before launching WebView
        showLoadingMessage()
        
        // Hardcoded server URL for direct login
        val hardcodedServerUrl = "https://nc.softblinktech.com"
        val intent = Intent(context, WebViewLoginActivity::class.java)
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_BASE_URL, hardcodedServerUrl)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun isBrandingUrlSet() = true // Always true since we're hardcoding the URL

    /**
     * Sets up network monitoring with debouncing and state change detection
     * to prevent the networkWarningBanner from flickering briefly on app startup.
     * 
     * Features:
     * - 1 second initial delay to allow network detection to stabilize
     * - 500ms debounce to prevent rapid state changes
     * - State change detection to only update UI when necessary
     * - Different behavior for logged-in vs non-logged-in users
     * - Logged-in users can access app without internet (offline mode)
     * - Non-logged-in users see network warning with retry button
     * - Network warning only appears when internet is required for login
     */
    private fun setupNetworkMonitoring() {
        lifecycleScope.launch {
            // Check initial network state
            val initialNetworkState = networkMonitor.isOnline.value
            
            // Add initial delay to prevent immediate banner display
            kotlinx.coroutines.delay(1000)
            
            // Check if user is already logged in
            val isUserLoggedIn = checkIfUserIsLoggedIn()
            
            if (isUserLoggedIn) {
                // User is logged in - allow access regardless of internet
                binding.centerIcon.visibility = View.GONE
                binding.networkWarningBanner.visibility = View.GONE
                binding.loadingText.visibility = View.GONE
                handleIntent(intent)
            } else {
                // User is not logged in - check internet connection
                if (initialNetworkState) {
                    // Internet is available - proceed to login
                    binding.centerIcon.visibility = View.GONE
                    binding.networkWarningBanner.visibility = View.GONE
                    binding.loadingText.visibility = View.GONE
                    handleIntent(intent)
                } else {
                    // No internet - show network warning
                    binding.centerIcon.visibility = View.GONE
                    binding.loadingText.visibility = View.GONE
                    binding.networkWarningBanner.visibility = View.VISIBLE
                }
            }
            
            var lastNetworkState = initialNetworkState
            
            networkMonitor.isOnline
                .debounce(500) // Add 500ms debounce to prevent flickering
                .collect { isOnline ->
                    // Only update UI if network state actually changed
                    if (isOnline != lastNetworkState) {
                        lastNetworkState = isOnline
                        
                        // Re-check if user is logged in (in case they logged in during this session)
                        val currentUserLoggedIn = checkIfUserIsLoggedIn()
                        
                        if (currentUserLoggedIn) {
                            // User is logged in - allow access regardless of internet
                            binding.centerIcon.visibility = View.GONE
                            binding.networkWarningBanner.visibility = View.GONE
                            binding.loadingText.visibility = View.GONE
                        } else if (isOnline) {
                            // User not logged in but internet available - proceed to login
                            binding.centerIcon.visibility = View.GONE
                            binding.networkWarningBanner.visibility = View.GONE
                            binding.loadingText.visibility = View.GONE
                            handleIntent(intent)
                        } else {
                            // User not logged in and no internet - show network warning
                            binding.centerIcon.visibility = View.GONE
                            binding.loadingText.visibility = View.GONE
                            binding.networkWarningBanner.visibility = View.VISIBLE
                        }
                    }
                }
        }
    }
    
    /**
     * Checks if user is already logged in by checking for existing users
     */
    private fun checkIfUserIsLoggedIn(): Boolean {
        return try {
            val users = userManager.users.blockingGet()
            users.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking user login status", e)
            false
        }
    }
    
    /**
     * Sets up the retry button to allow users to manually retry network connection
     * Different behavior for logged-in vs non-logged-in users
     */
    private fun setupRetryButton() {
        binding.retryButton.setOnClickListener {
            // Check if user is logged in
            val isUserLoggedIn = checkIfUserIsLoggedIn()
            
            if (isUserLoggedIn) {
                // User is logged in - allow access regardless of internet
                binding.loadingText.visibility = View.GONE
                binding.networkWarningBanner.visibility = View.GONE
                binding.centerIcon.visibility = View.GONE
                handleIntent(intent)
            } else {
                // User not logged in - show loading and retry network check
                binding.loadingText.visibility = View.VISIBLE
                binding.networkWarningBanner.visibility = View.GONE
                
                // Force a network check
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1000) // Give network time to stabilize
                    
                    val currentNetworkState = networkMonitor.isOnline.value
                    if (currentNetworkState) {
                        // Internet available - proceed to login
                        binding.loadingText.visibility = View.GONE
                        binding.centerIcon.visibility = View.GONE
                        binding.networkWarningBanner.visibility = View.GONE
                        handleIntent(intent)
                    } else {
                        // Still no internet - show network warning
                        binding.loadingText.visibility = View.GONE
                        binding.networkWarningBanner.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showLoadingMessage() {
        val loadingMessages = listOf(
            "Connecting to your secure server... 🔐",
            "Preparing your private workspace... 🚀",
            "Setting up encrypted communication... 🔒",
            "Initializing secure channels... 🌐",
            "Loading your personal dashboard... 📱",
            "Establishing secure connection... ⚡",
            "Preparing your private chat environment... 💬",
            "Loading secure video capabilities... 📹",
            "Setting up end-to-end encryption... 🛡️",
            "Initializing your private workspace... 🏠"
        )
        
        val randomMessage = loadingMessages.random()
        binding.loadingText.text = randomMessage
        binding.loadingText.visibility = View.VISIBLE
        binding.centerIcon.visibility = View.GONE
        
        // Start rotating through messages
        startMessageRotation(loadingMessages)
    }

    private fun startMessageRotation(messages: List<String>) {
        var currentIndex = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val runnable = object : Runnable {
            override fun run() {
                if (binding.loadingText.visibility == View.VISIBLE) {
                    binding.loadingText.text = messages[currentIndex]
                    currentIndex = (currentIndex + 1) % messages.size
                    handler.postDelayed(this, 3000) // Change message every 3 seconds
                }
            }
        }
        
        handler.post(runnable)
    }

    private fun hideLoadingMessage() {
        binding.loadingText.visibility = View.GONE
        binding.centerIcon.visibility = View.GONE
    }

    override fun onStart() {
        Log.d(TAG, "onStart: Activity: " + System.identityHashCode(this).toString())
        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume: Activity: " + System.identityHashCode(this).toString())
        super.onResume()

        if (appPreferences.isScreenLocked) {
            SecurityUtils.createKey(appPreferences.screenLockTimeout)
        }
        
        // Hide loading message when returning from WebView
        hideLoadingMessage()
    }

    override fun onPause() {
        Log.d(TAG, "onPause: Activity: " + System.identityHashCode(this).toString())
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop: Activity: " + System.identityHashCode(this).toString())
        super.onStop()
    }

    private fun openHomeScreen() {
        val intent = Intent(this, HomeScreen::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtras(Bundle())
        startActivity(intent)
        finish() // Ensure MainActivity is removed from the stack
    }

    private fun openConversationList() {
        val intent = Intent(this, HomeScreen::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtras(Bundle())
        startActivity(intent)
        finish() // Ensure MainActivity is removed from the stack
    }

    private fun handleActionFromContact(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val cursor = contentResolver.query(intent.data!!, null, null, null, null)

            var userId = ""
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    // userId @ server
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1))
                }

                cursor.close()
            }

            when (intent.type) {
                "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat" -> {
                    val user = userId.substringBeforeLast("@")
                    val baseUrl = userId.substringAfterLast("@")

                    if (currentUserProvider.currentUser.blockingGet()?.baseUrl!!.endsWith(baseUrl) == true) {
                        startConversation(user)
                    } else {
                        Snackbar.make(
                            binding.root,
                            R.string.nc_phone_book_integration_account_not_found,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun startConversation(userId: String) {
        val roomType = "1"

        val currentUser = currentUserProvider.currentUser.blockingGet()

        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, 1))
        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = currentUser?.baseUrl!!,
            roomType = roomType,
            invite = userId
        )

        ncApi.createRoom(
            credentials,
            retrofitBucket.url,
            retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)

                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(bundle)
                    startActivity(chatIntent)
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent Activity: " + System.identityHashCode(this).toString())
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        handleActionFromContact(intent)

        val internalUserId = intent.extras?.getLong(BundleKeys.KEY_INTERNAL_USER_ID)

        var user: User? = null
        if (internalUserId != null) {
            user = userManager.getUserWithId(internalUserId).blockingGet()
        }

        if (user != null && userManager.setUserAsActive(user).blockingGet()) {
            if (intent.hasExtra(BundleKeys.KEY_REMOTE_TALK_SHARE)) {
                if (intent.getBooleanExtra(BundleKeys.KEY_REMOTE_TALK_SHARE, false)) {
                    val invitationsIntent = Intent(this, InvitationsActivity::class.java)
                    startActivity(invitationsIntent)
                }
            } else {
                val chatIntent = Intent(context, ChatActivity::class.java)
                chatIntent.putExtras(intent.extras!!)
                startActivity(chatIntent)
            }
        } else {
            userManager.users.subscribe(object : SingleObserver<List<User>> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onSuccess(users: List<User>) {
                    if (users.isNotEmpty()) {
                        ClosedInterfaceImpl().setUpPushTokenRegistration()
                        
                        // Validate and refresh push token if needed for the first user
                        // try {
                        //     val firstUser = users.first()
                        //     val pushUtils = PushUtils()
                        //     pushUtils.validateAndRefreshPushTokenIfNeeded(firstUser)
                        // } catch (e: Exception) {
                        //     Log.w(TAG, "Failed to validate push token", e)
                        // }

                        runOnUiThread {
                            openHomeScreen()
                        }
                    } else {
                        runOnUiThread {
                            launchServerSelection()
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error loading existing users", e)
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.nc_common_error_sorry),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
