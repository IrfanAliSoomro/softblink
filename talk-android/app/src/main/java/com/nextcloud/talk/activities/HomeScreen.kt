/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.activities

import com.nextcloud.talk.conversationcreation.ConversationCreationActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.widget.ImageView
import com.nextcloud.talk.contacts.ContactsActivity
import android.content.Intent
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import com.nextcloud.talk.data.user.model.User
import androidx.work.Data
import com.nextcloud.talk.R
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nextcloud.talk.databinding.ActivityConversationsBinding
import com.nextcloud.talk.ui.HomeFragment
import com.nextcloud.talk.ui.SettingsFragment
import com.nextcloud.talk.ui.GroupFragment
import com.nextcloud.talk.ui.CallsFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import com.nextcloud.talk.models.domain.ConversationModel
import androidx.work.OneTimeWorkRequest
import com.nextcloud.talk.jobs.DeleteConversationWorker
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.nextcloud.talk.jobs.ContactAddressBookWorker
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SHARED_TEXT
import android.util.Log
import android.os.Handler
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.serverstatus.ServerStatusRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class HomeScreen : BaseActivity() {

    lateinit var bottomNav : BottomNavigationView
    private lateinit var binding: ActivityConversationsBinding
    private var searchQuery: String? = null
    private var currentUser: User? = null
    private var actionIconMenuItem: MenuItem? = null
    
    @Inject
    lateinit var serverStatusRepository: ServerStatusRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        currentUser = currentUserProvider.currentUser.blockingGet()
        
        // Check server status when HomeScreen starts
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Checking server status on HomeScreen start...")
                serverStatusRepository.getServerStatus()
                Log.d(TAG, "Server status check completed. isServerReachable: ${serverStatusRepository.isServerReachable.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check server status on HomeScreen start", e)
            }
        }

        setContentView(R.layout.activity_home)
        initSystemBars()
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        loadFragment(HomeFragment())
        bottomNav = findViewById(R.id.bottomNav) as BottomNavigationView
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_chat -> {
                    loadFragment(HomeFragment())
                    true
                }

                R.id.nav_group -> {
                    loadFragment(GroupFragment())
                    true
                }

                R.id.nav_calls -> {
                    loadFragment(CallsFragment())
                    true
                }

                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }

                else -> false // add this
            }
        }

        // Set the correct tab if coming back from ChatActivity
        val selectedTab = intent.getStringExtra("selectedTab")
        if (selectedTab == "group") {
            bottomNav.selectedItemId = R.id.nav_group
        } else {
            bottomNav.selectedItemId = R.id.nav_chat // or your default
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitleTextAppearance(this, R.style.ToolbarTitleLarge)
        setSupportActionBar(toolbar)

         //toolbar.inflateMenu(R.menu.menu_home)
         supportActionBar?.title = "Chat"

    }

    private fun showNewConversationsScreen() {
        ContactAddressBookWorker.Companion.run(context)
        val intent = Intent(context, ContactsActivity::class.java)
        startActivity(intent)
    }

    private  fun loadFragment(fragment: Fragment){
        val transaction = supportFragmentManager.beginTransaction()

        val bundle = Bundle()
        bundle.putParcelable("currentUser", currentUser)
        fragment.arguments = bundle

        transaction.replace(R.id.container,fragment)
        transaction.commit()

        when (fragment) {
            is HomeFragment -> {
                supportActionBar?.title = "Chat"
                actionIconMenuItem?.isVisible = true // Show icon
            }
            is GroupFragment -> {
                supportActionBar?.title = "Group"
                actionIconMenuItem?.isVisible = true // Show icon
            }
            is CallsFragment -> {
                supportActionBar?.title = "Calls"
                actionIconMenuItem?.isVisible = false // Hide icon for calls
            }
            is SettingsFragment -> {
                supportActionBar?.title = "Settings"
                actionIconMenuItem?.isVisible = false // Hide icon for settings
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        if (savedInstanceState.containsKey(KEY_SEARCH_QUERY)) {
            searchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "")
        }
    }

    fun showSnackbar(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    fun showDeleteConversationDialog(conversation: ConversationModel) {
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
                .setIcon(
                    viewThemeUtils.dialog
                        .colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp)
                )
                .setTitle(R.string.nc_delete_call)
                .setMessage(R.string.nc_delete_conversation_more)
                .setPositiveButton(R.string.nc_delete) { _, _ ->
                    deleteConversation(conversation)
                }
                .setNegativeButton(R.string.nc_cancel) { _, _ ->
                }

            viewThemeUtils.dialog
                .colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    // Add methods for FilterConversationFragment to call
    fun showOnlyNearFutureEvents() {
        // Find the current fragment and call the method
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.showOnlyNearFutureEvents()
            is GroupFragment -> currentFragment.showOnlyNearFutureEvents()
            else -> Log.w(TAG, "Unknown fragment type for showOnlyNearFutureEvents")
        }
    }

    fun filterConversation() {
        // Find the current fragment and call the method
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.filterConversation()
            is GroupFragment -> currentFragment.filterConversation()
            else -> Log.w(TAG, "Unknown fragment type for filterConversation")
        }
    }

    fun updateFilterState(mention: Boolean, unread: Boolean) {
        // Find the current fragment and call the method
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.updateFilterState(mention, unread)
            is GroupFragment -> currentFragment.updateFilterState(mention, unread)
            else -> Log.w(TAG, "Unknown fragment type for updateFilterState")
        }
    }

    fun setFilterableItems(items: MutableList<AbstractFlexibleItem<*>>) {
        // Find the current fragment and call the method
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.setFilterableItems(items)
            is GroupFragment -> currentFragment.setFilterableItems(items)
            else -> Log.w(TAG, "Unknown fragment type for setFilterableItems")
        }
    }

    fun updateFilterConversationButtonColor() {
        // Find the current fragment and call the method
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.updateFilterConversationButtonColor()
            is GroupFragment -> currentFragment.updateFilterConversationButtonColor()
            else -> Log.w(TAG, "Unknown fragment type for updateFilterConversationButtonColor")
        }
    }

    // Add fetchRooms method for ConversationsListBottomDialog to call
    fun fetchRooms() {
        // Find the current fragment and call the method
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.fetchRooms()
            is GroupFragment -> currentFragment.fetchRooms()
            else -> Log.w(TAG, "Unknown fragment type for fetchRooms")
        }
    }
    
    // Add test refresh method to test if refresh is working
    fun testRefresh() {
        Log.d(TAG, "Test refresh called from HomeScreen")
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.testRefresh()
            is GroupFragment -> Log.w(TAG, "GroupFragment doesn't have testRefresh")
            else -> Log.w(TAG, "Unknown fragment type for testRefresh")
        }
    }
    
    // Add EventBus status check method
    fun checkEventBusStatus() {
        Log.d(TAG, "Check EventBus status called from HomeScreen")
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.checkEventBusStatus()
            is GroupFragment -> Log.w(TAG, "GroupFragment doesn't have checkEventBusStatus")
            else -> Log.w(TAG, "Unknown fragment type for checkEventBusStatus")
        }
    }
    
    // Add force adapter refresh method
    fun forceAdapterRefresh() {
        Log.d(TAG, "Force adapter refresh called from HomeScreen")
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> currentFragment.forceAdapterRefresh()
            is GroupFragment -> Log.w(TAG, "GroupFragment doesn't have forceAdapterRefresh")
            else -> Log.w(TAG, "Unknown fragment type for forceAdapterRefresh")
        }
    }
    
    // Add full refresh cycle method
    fun forceFullRefresh() {
        Log.d(TAG, "Force full refresh called from HomeScreen")
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        when (currentFragment) {
            is HomeFragment -> {
                currentFragment.testRefresh()
                // Wait a bit then force adapter refresh
                Handler().postDelayed({
                    currentFragment.forceAdapterRefresh()
                }, 1000)
            }
            is GroupFragment -> Log.w(TAG, "GroupFragment doesn't have forceFullRefresh")
            else -> Log.w(TAG, "Unknown fragment type for forceFullRefresh")
        }
    }

    private fun deleteConversation(conversation: ConversationModel) {
        val data = Data.Builder()
        data.putLong(
            KEY_INTERNAL_USER_ID,
            currentUser?.id!!
        )
        data.putString(KEY_ROOM_TOKEN, conversation.token)

        val deleteConversationWorker =
            OneTimeWorkRequest.Builder(DeleteConversationWorker::class.java).setInputData(data.build()).build()
        WorkManager.getInstance().enqueue(deleteConversationWorker)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(deleteConversationWorker.id)
            .observe(this) { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            showSnackbar(
                                String.format(
                                    resources.getString(R.string.deleted_conversation),
                                    conversation.displayName
                                )
                            )
                        }

                        WorkInfo.State.FAILED -> {
                            showSnackbar(resources.getString(R.string.nc_common_error_sorry))
                        }

                        else -> {
                        }
                    }
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        actionIconMenuItem = menu.findItem(R.id.action_icon)

        val menuItem = menu.findItem(R.id.action_icon)
        val actionView = menuItem.actionView
        actionView?.findViewById<ImageView>(R.id.action_icon_image)?.setOnClickListener {
            showNewConversationsScreen()
        }
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_icon -> {
                // showNewConversationsScreen()
                val intent = Intent(context, ConversationCreationActivity::class.java)
                context.startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val ARG_CURRENT_USER = "arg_current_user"

        private val TAG = HomeScreen::class.java.simpleName
        const val UNREAD_BUBBLE_DELAY = 2500
        const val BOTTOM_SHEET_DELAY: Long = 2500
        private const val KEY_SEARCH_QUERY = "HomeScreen.searchQuery"
        private const val CHAT_ACTIVITY_LOCAL_NAME = "com.nextcloud.talk.chat.ChatActivity"
        const val SEARCH_DEBOUNCE_INTERVAL_MS = 300
        const val SEARCH_MIN_CHARS = 1
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CLIENT_UPGRADE_REQUIRED = 426
        const val CLIENT_UPGRADE_MARKET_LINK = "market://details?id="
        const val CLIENT_UPGRADE_GPLAY_LINK = "https://play.google.com/store/apps/details?id="
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val MAINTENANCE_MODE_HEADER_KEY = "X-Nextcloud-Maintenance-Mode"
        const val REQUEST_POST_NOTIFICATIONS_PERMISSION = 111
        const val BADGE_OFFSET = 35
        const val DAYS_FOR_NOTIFICATION_WARNING = 5L
        const val NOTIFICATION_WARNING_DATE_NOT_SET = 0L
        const val OFFSET_HEIGHT_DIVIDER: Int = 3
        const val ROOM_TYPE_ONE_ONE = "1"
        private const val SIXTEEN_HOURS_IN_SECONDS: Long = 57600
        const val LONG_1000: Long = 1000
        private const val NOTE_TO_SELF_SHORTCUT_ID = "NOTE_TO_SELF_SHORTCUT_ID"
        private const val CONVERSATION_ITEM_HEIGHT = 44
    }

}
