/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 * 
 * This fragment displays the call history tab and automatically refreshes
 * when the user returns to the app or switches to this tab to ensure
 * new calls appear immediately without requiring a visit to the chat activity.
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
import com.nextcloud.talk.utils.VideoCallTracker
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
// import com.nextcloud.talk.activities.ConversationCreationActivity
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
    
    private lateinit var videoCallTracker: VideoCallTracker
    
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
        
        // Initialize video call tracker
        videoCallTracker = VideoCallTracker(requireContext())
        
        // Get current user from arguments
        arguments?.let {
            currentUser = it.getParcelable("currentUser")
        }
        
        setupRecyclerView()
        loadCallHistory()
        setupEmptyState()
        setupFloatingActionButton()
    }

    override fun onResume() {
        super.onResume()
        // Refresh call history when fragment becomes visible
        // This ensures that new calls appear immediately when user returns to the app
        loadCallHistory()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && isResumed) {
            // Refresh call history when this tab becomes visible
            loadCallHistory()
        }
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
                // Show loading state only if this is the first load
                val isFirstLoad = callHistory.isEmpty()
                if (isFirstLoad) {
                    binding.loadingView.visibility = View.VISIBLE
                    binding.callsRecyclerView.visibility = View.GONE
                    binding.emptyStateView.visibility = View.GONE
                }
                
                // Load real call history from database
                val realCallHistory = withContext(Dispatchers.IO) {
                    loadRealCallHistory()
                }
                
                withContext(Dispatchers.Main) {
                    if (isFirstLoad) {
                        binding.loadingView.visibility = View.GONE
                    }
                    
                    if (realCallHistory.isNotEmpty()) {
                        binding.callsRecyclerView.visibility = View.VISIBLE
                        binding.emptyStateView.visibility = View.GONE
                        callHistory.clear()
                        callHistory.addAll(realCallHistory)
                        callsAdapter.submitList(realCallHistory)
                        Log.d(TAG, "Call history refreshed with ${realCallHistory.size} items")
                    } else {
                        binding.callsRecyclerView.visibility = View.GONE
                        binding.emptyStateView.visibility = View.VISIBLE
                        callHistory.clear()
                        callsAdapter.submitList(emptyList())
                        Log.d(TAG, "No call history found")
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

    /**
     * Public method to force refresh call history from external sources
     * This can be called when a call is made or ended to ensure immediate updates
     */
    fun refreshCallHistory() {
        Log.d(TAG, "Force refreshing call history")
        loadCallHistory()
    }
    
    /**
     * Method to mark a specific call as a video call for better detection
     * This can be called when a video call is initiated to help with detection
     */
    fun markCallAsVideoCall(conversationToken: String, isVideoCall: Boolean) {
        Log.d(TAG, "Marking call for conversation $conversationToken as video call: $isVideoCall")
        // This could store the information in a local cache or preferences
        // For now, we'll rely on the improved detection logic
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
                    
                    // Determine if this is a video call
                    val isVideoCall = determineIfVideoCall(messages)
                    Log.d(TAG, "Call for ${conversation.displayName} is video call: $isVideoCall")
                    
                    // Determine call type based on the most recent call message and video status
                    val callType = when (mostRecentCall.systemMessageType.toString()) {
                        "CALL_STARTED" -> if (isVideoCall) CallHistoryItem.CallType.OUTGOING_VIDEO else CallHistoryItem.CallType.OUTGOING_AUDIO
                        "CALL_JOINED" -> if (isVideoCall) CallHistoryItem.CallType.INCOMING_VIDEO else CallHistoryItem.CallType.INCOMING_AUDIO
                        "CALL_LEFT" -> if (isVideoCall) CallHistoryItem.CallType.OUTGOING_VIDEO else CallHistoryItem.CallType.OUTGOING_AUDIO
                        "CALL_ENDED" -> if (isVideoCall) CallHistoryItem.CallType.OUTGOING_VIDEO else CallHistoryItem.CallType.OUTGOING_AUDIO
                        "CALL_MISSED" -> if (isVideoCall) CallHistoryItem.CallType.MISSED_VIDEO else CallHistoryItem.CallType.MISSED_AUDIO
                        "CALL_TRIED" -> if (isVideoCall) CallHistoryItem.CallType.OUTGOING_VIDEO else CallHistoryItem.CallType.OUTGOING_AUDIO
                        else -> if (isVideoCall) CallHistoryItem.CallType.OUTGOING_VIDEO else CallHistoryItem.CallType.OUTGOING_AUDIO
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
                        isVideoCall = isVideoCall,
                        isMissed = mostRecentCall.systemMessageType.toString() == "CALL_MISSED",
                        profileImageUrl = null, // Avatar URL not available in ConversationEntity
                        isGroupCall = conversation.actorType != "users"
                    )
                    
                    callHistory.add(callItem)
                }
            }
        }
        
        return callHistory.sortedByDescending { it.timestamp }
    }
    
        private fun determineIfVideoCall(messages: List<ChatMessageEntity>): Boolean {
        Log.d(TAG, "Determining if call is video call from ${messages.size} messages")
        
        if (messages.isEmpty()) {
            Log.d(TAG, "No messages to check, assuming audio call")
            return false
        }
        
        // Get the most recent call message
        val mostRecentMessage = messages.first()
        val conversationToken = mostRecentMessage.token
        val messageTimestamp = mostRecentMessage.timestamp * 1000 // Convert to milliseconds
        
        Log.d(TAG, "Checking video call for conversation: $conversationToken at timestamp: $messageTimestamp")
        
        // First, try to use the VideoCallTracker for accurate detection
        val isVideoCallFromTracker = videoCallTracker.wasVideoCall(conversationToken, messageTimestamp) ||
                videoCallTracker.wasVideoCallInRange(conversationToken, messageTimestamp, 120) // 2 minute range
        
        if (isVideoCallFromTracker) {
            Log.d(TAG, "Found video call from VideoCallTracker")
            return true
        }
        
        // Fallback: Check message parameters for video call indicators
        for (message in messages) {
            Log.d(TAG, "Checking message: ${message.systemMessageType}, params: ${message.messageParameters}")
            
            message.messageParameters?.let { params ->
                for (key in params.keys) {
                    val individualMap = params[key]
                    if (individualMap != null) {
                        Log.d(TAG, "Message param key: $key, value: $individualMap")
                        
                        // Check if the call type is "video" in message parameters
                        if (individualMap["type"] == "video" || 
                            individualMap["name"]?.contains("video", ignoreCase = true) == true ||
                            individualMap["callType"] == "video" ||
                            individualMap["mediaType"] == "video") {
                            Log.d(TAG, "Found video call indicator in message parameters")
                            return true
                        }
                    }
                }
            }
            
            // Check the message text for video call indicators
            message.message?.let { messageText ->
                Log.d(TAG, "Checking message text: $messageText")
                if (messageText.contains("video", ignoreCase = true) ||
                    messageText.contains("camera", ignoreCase = true) ||
                    messageText.contains("video call", ignoreCase = true)) {
                    Log.d(TAG, "Found video call indicator in message text")
                    return true
                }
            }
        }
        
        // Check if any call has video-related system messages
        val hasVideoMessages = messages.any { message ->
            message.systemMessageType.toString().contains("VIDEO", ignoreCase = true) ||
            message.messageType?.contains("video", ignoreCase = true) == true
        }
        
        if (hasVideoMessages) {
            Log.d(TAG, "Found video-related system message")
            return true
        }
        
        // Check for specific call patterns that might indicate video calls
        // Look for calls that have both audio and video capabilities
        val hasAudioVideoFlags = messages.any { message ->
            message.messageParameters?.let { params ->
                params.values.any { paramMap ->
                    paramMap?.values?.any { value ->
                        value?.contains("WITH_VIDEO", ignoreCase = true) == true ||
                        value?.contains("video", ignoreCase = true) == true ||
                        value?.contains("camera", ignoreCase = true) == true
                    } == true
                }
            } == true
        }
        
        if (hasAudioVideoFlags) {
            Log.d(TAG, "Found audio/video flags in call")
            return true
        }
        
        // Check for video call indicators in message parameters more thoroughly
        val hasVideoParameters = messages.any { message ->
            message.messageParameters?.let { params ->
                params.any { (key, paramMap) ->
                    paramMap?.any { (paramKey, paramValue) ->
                        when {
                            paramKey?.contains("video", ignoreCase = true) == true -> true
                            paramValue?.contains("video", ignoreCase = true) == true -> true
                            paramKey?.contains("camera", ignoreCase = true) == true -> true
                            paramValue?.contains("camera", ignoreCase = true) == true -> true
                            paramKey?.contains("media", ignoreCase = true) == true &&
                                paramValue?.contains("video", ignoreCase = true) == true -> true
                            else -> false
                        }
                    } == true
                } == true
            } == true
        }
        
        if (hasVideoParameters) {
            Log.d(TAG, "Found video parameters in call")
            return true
        }
        
        // For debugging: Log all message details to understand the structure
        messages.forEach { message ->
            Log.d(TAG, "Message details - Type: ${message.systemMessageType}, " +
                    "Message: ${message.message}, " +
                    "Parameters: ${message.messageParameters}, " +
                    "MessageType: ${message.messageType}")
        }
        
        Log.d(TAG, "No video call indicators found, assuming audio call")
        // For now, assume audio calls by default
        return false
    }
    
    /**
     * Fallback method to determine if a call was a video call by checking conversation entity
     * This can be used when message-based detection fails
     */
    private suspend fun determineIfVideoCallFromConversation(conversation: ConversationEntity): Boolean {
        // Check if conversation has any video-related flags or properties
        // This is a fallback method when message-based detection doesn't work
        
        // For now, we'll use a simple heuristic: if the conversation has call flags
        // and we can't determine from messages, we'll assume it could be video
        // This is not perfect but better than always showing audio
        
        Log.d(TAG, "Using fallback video call detection for conversation: ${conversation.displayName}")
        Log.d(TAG, "Conversation callFlag: ${conversation.callFlag}, hasCall: ${conversation.hasCall}")
        
        // If we have call information but can't determine video status from messages,
        // we could potentially check other conversation properties here
        
        return false // Default to audio for now
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

    /**
     * Setup floating action button click listener
     */
    private fun setupFloatingActionButton() {
        binding.floatingActionButton.setOnClickListener {
            // Navigate to conversation creation
            // val intent = Intent(requireContext(), ConversationCreationActivity::class.java)
            // startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
