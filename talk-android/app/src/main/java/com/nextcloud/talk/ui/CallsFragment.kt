/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.FragmentCallsBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.adapters.CallsAdapter
import com.nextcloud.talk.models.CallHistoryItem
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.chat.ChatActivity
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import javax.inject.Inject
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.flow.first
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.data.database.model.ConversationEntity

@AutoInjector(NextcloudTalkApplication::class)
class CallsFragment : Fragment() {

    private var _binding: FragmentCallsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var callsAdapter: CallsAdapter
    private var currentUser: User? = null
    private val callHistory = mutableListOf<CallHistoryItem>()
    
    @Inject
    lateinit var conversationsDao: ConversationsDao
    
    @Inject
    lateinit var chatMessagesDao: ChatMessagesDao
    
    companion object {
        private val TAG = CallsFragment::class.java.simpleName
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Inject dependencies
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        
        // Get current user from arguments
        arguments?.let {
            currentUser = it.getParcelable("currentUser")
        }
        
        setupRecyclerView()
        loadCallHistory()
        setupEmptyState()
    }

    private fun setupRecyclerView() {
        callsAdapter = CallsAdapter(
            onCallItemClick = { callItem ->
                // Navigate to chat/conversation
                navigateToConversation(callItem.conversationToken)
            },
            onCallButtonClick = { callItem ->
                // Initiate a new call
                initiateCall(callItem.conversationToken, callItem.isVideoCall)
            }
        )
        
        binding.callsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = callsAdapter
        }
    }

    private fun loadCallHistory() {
        lifecycleScope.launch {
            try {
                // Show loading state
                binding.loadingView.visibility = View.VISIBLE
                binding.callsRecyclerView.visibility = View.GONE
                binding.emptyStateView.visibility = View.GONE
                
                // Load real call history from database
                val realCallHistory = withContext(Dispatchers.IO) {
                    loadRealCallHistory()
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingView.visibility = View.GONE
                    
                    if (realCallHistory.isNotEmpty()) {
                        binding.callsRecyclerView.visibility = View.VISIBLE
                        binding.emptyStateView.visibility = View.GONE
                        callsAdapter.submitList(realCallHistory)
                    } else {
                        binding.callsRecyclerView.visibility = View.GONE
                        binding.emptyStateView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading call history", e)
                withContext(Dispatchers.Main) {
                    binding.loadingView.visibility = View.GONE
                    binding.emptyStateView.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun loadRealCallHistory(): List<CallHistoryItem> {
        val callHistory = mutableListOf<CallHistoryItem>()
        
        if (currentUser != null) {
            // Get call messages from database
            val callMessages = chatMessagesDao.getCallMessages().first()
            
            Log.d(TAG, "Found ${callMessages.size} call messages")
            callMessages.forEach { message ->
                Log.d(TAG, "Call message: ${message.systemMessageType} at ${Date(message.timestamp)} for conversation ${message.internalConversationId}")
            }
            
            // Group call messages by conversation
            val callMessagesByConversation = callMessages.groupBy { it.internalConversationId }
            
            callMessagesByConversation.forEach { (conversationId, messages) ->
                // Get conversation details
                val conversation = conversationsDao.getConversationByInternalId(currentUser!!.id!!, conversationId).first()
                
                if (conversation != null && messages.isNotEmpty()) {
                    // Get the most recent call message for this conversation
                    val mostRecentCall = messages.first()
                    
                    // Debug logging
                    Log.d(TAG, "Processing call for conversation: ${conversation.displayName}")
                    Log.d(TAG, "Call message type: ${mostRecentCall.systemMessageType}")
                    Log.d(TAG, "Call timestamp: ${mostRecentCall.timestamp}")
                    Log.d(TAG, "Call date: ${Date(mostRecentCall.timestamp)}")
                    
                    // Determine call type based on the most recent call message
                    val callType = when (mostRecentCall.systemMessageType.toString()) {
                        "CALL_STARTED" -> CallHistoryItem.CallType.OUTGOING_AUDIO
                        "CALL_JOINED" -> CallHistoryItem.CallType.INCOMING_AUDIO
                        "CALL_LEFT" -> CallHistoryItem.CallType.OUTGOING_AUDIO
                        "CALL_ENDED" -> CallHistoryItem.CallType.OUTGOING_AUDIO
                        "CALL_MISSED" -> CallHistoryItem.CallType.MISSED_AUDIO
                        "CALL_TRIED" -> CallHistoryItem.CallType.OUTGOING_AUDIO
                        else -> CallHistoryItem.CallType.OUTGOING_AUDIO
                    }
                    
                    // Calculate duration if available
                    val duration = calculateCallDuration(messages)
                    
                    val callItem = CallHistoryItem(
                        id = conversationId,
                        conversationName = conversation.displayName,
                        conversationToken = conversation.token,
                        callType = callType,
                        duration = duration,
                        timestamp = Date(mostRecentCall.timestamp * 1000), // Convert seconds to milliseconds
                        isVideoCall = false, // TODO: Determine from call data
                        isMissed = mostRecentCall.systemMessageType.toString() == "CALL_MISSED"
                    )
                    
                    callHistory.add(callItem)
                }
            }
        }
        
        return callHistory.sortedByDescending { it.timestamp }
    }
    
    private fun calculateCallDuration(messages: List<ChatMessageEntity>): String {
        // Find CALL_STARTED and CALL_ENDED messages
        val callStarted = messages.find { it.systemMessageType.toString() == "CALL_STARTED" }
        val callEnded = messages.find { it.systemMessageType.toString() == "CALL_ENDED" }
        
        if (callStarted != null && callEnded != null) {
            // Timestamps are in seconds, so convert to milliseconds for calculation
            val durationMs = (callEnded.timestamp - callStarted.timestamp) * 1000
            val minutes = (durationMs / (1000 * 60)).toInt()
            val seconds = ((durationMs % (1000 * 60)) / 1000).toInt()
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
        
        return "0:00"
    }

    private fun setupEmptyState() {
        binding.emptyStateView.apply {
            findViewById<ImageView>(R.id.emptyStateIcon).setImageResource(R.drawable.ic_call_black_24dp)
            findViewById<TextView>(R.id.emptyStateTitle).text = getString(R.string.calls_empty_title)
            findViewById<TextView>(R.id.emptyStateDescription).text = getString(R.string.calls_empty_description)
        }
    }

    private fun navigateToConversation(conversationToken: String) {
        val intent = Intent(context, ChatActivity::class.java)
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversationToken)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun initiateCall(conversationToken: String, isVideoCall: Boolean) {
        Log.d(TAG, "Initiating ${if (isVideoCall) "video" else "audio"} call for conversation: $conversationToken")
        
        // Get conversation details
        lifecycleScope.launch {
            try {
                val conversation = conversationsDao.getConversationForUser(currentUser!!.id!!, conversationToken).first()
                if (conversation != null) {
                    startCall(conversation, isVideoCall)
                } else {
                    Log.e(TAG, "Conversation not found for token: $conversationToken")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating call", e)
            }
        }
    }
    
    private fun startCall(conversation: ConversationEntity, isVoiceOnlyCall: Boolean) {
        val bundle = Bundle()
        bundle.putString("roomToken", conversation.token)
        bundle.putString("conversationName", conversation.displayName)
        bundle.putString("modifiedBaseUrl", currentUser?.baseUrl)
        bundle.putInt("recordingState", conversation.callRecording)
        bundle.putBoolean("isModerator", false) // TODO: Get actual moderator status
        bundle.putBoolean("participantPermissionCanPublishAudio", true)
        bundle.putBoolean("participantPermissionCanPublishVideo", !isVoiceOnlyCall)
        bundle.putBoolean("roomOneToOne", conversation.actorType == "users")
        
        if (isVoiceOnlyCall) {
            bundle.putBoolean("callVoiceOnly", true)
        }
        
        val callIntent = Intent(context, CallActivity::class.java)
        callIntent.putExtras(bundle)
        startActivity(callIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
