/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.ItemCallHistoryBinding
import com.nextcloud.talk.models.CallHistoryItem
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.extensions.loadDefaultAvatar
import com.nextcloud.talk.extensions.loadDefaultGroupCallAvatar
import com.nextcloud.talk.extensions.loadFirstLetterAvatar
import java.text.SimpleDateFormat
import java.util.Locale

class CallsAdapter(
    private val onCallItemClick: (CallHistoryItem) -> Unit,
    private val onCallButtonClick: (CallHistoryItem) -> Unit
) : ListAdapter<CallHistoryItem, CallsAdapter.CallViewHolder>(CallDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = ItemCallHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CallViewHolder(
        private val binding: ItemCallHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(callItem: CallHistoryItem) {
            binding.apply {
                // Set conversation name
                conversationName.text = callItem.conversationName
                
                // Set call type and duration
                // callTypeAndDuration.text = "${getCallTypeText(callItem.callType)} • ${callItem.duration}"
                callTypeAndDuration.text = "${getCallTypeText(callItem.callType)}"
                
                // Set timestamp
                timestamp.text = callItem.getFormattedTimestamp()
                
                // Set call type icon (arrow)
                callTypeIcon.setImageResource(callItem.getCallTypeIcon())
                
                // Set call type color
                val color = itemView.context.getColor(callItem.getCallTypeColor())
                callTypeIcon.setColorFilter(color)
                
                // Set video call indicator
                videoCallIndicator.visibility = if (callItem.isVideoCall) ViewGroup.VISIBLE else ViewGroup.GONE
                
                // Debug logging for video call indicator
                if (callItem.isVideoCall) {
                    Log.d("CallsAdapter", "Showing video call indicator for: ${callItem.conversationName}")
                }
                
                // Set missed call indicator
                if (callItem.isMissed) {
                    callTypeAndDuration.setTextColor(color)
                    conversationName.setTextColor(color)
                }
                
                // Load profile image
                loadProfileImage(callItem)
                
                // Set click listeners
                root.setOnClickListener { onCallItemClick(callItem) }
                callButton.setOnClickListener { onCallButtonClick(callItem) }
                
                // Set call button icon based on call type
                callButton.setImageResource(
                    if (callItem.isVideoCall) android.R.drawable.ic_menu_camera
                    else android.R.drawable.ic_menu_call
                )
            }
        }
        
        private fun loadProfileImage(callItem: CallHistoryItem) {
            // For now, just load a default avatar since we don't have access to ViewThemeUtils
            // and the avatar URL is not available in ConversationEntity
            binding.profileImage.loadFirstLetterAvatar(callItem.conversationName)
        }

        private fun getCallTypeText(callType: CallHistoryItem.CallType): String {
            return when (callType) {
                CallHistoryItem.CallType.INCOMING_AUDIO -> "Incoming"
                CallHistoryItem.CallType.INCOMING_VIDEO -> "Incoming Video"
                CallHistoryItem.CallType.OUTGOING_AUDIO -> "Outgoing"
                CallHistoryItem.CallType.OUTGOING_VIDEO -> "Outgoing Video"
                CallHistoryItem.CallType.MISSED_AUDIO -> "Missed"
                CallHistoryItem.CallType.MISSED_VIDEO -> "Missed Video"
                CallHistoryItem.CallType.REJECTED_AUDIO -> "Rejected"
                CallHistoryItem.CallType.REJECTED_VIDEO -> "Rejected Video"
                CallHistoryItem.CallType.ONGOING_AUDIO -> "Ongoing"
                CallHistoryItem.CallType.ONGOING_VIDEO -> "Ongoing Video"
            }
        }
    }

    private class CallDiffCallback : DiffUtil.ItemCallback<CallHistoryItem>() {
        override fun areItemsTheSame(oldItem: CallHistoryItem, newItem: CallHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallHistoryItem, newItem: CallHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
