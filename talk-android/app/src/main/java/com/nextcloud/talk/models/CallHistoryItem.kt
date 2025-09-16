/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date
import com.nextcloud.talk.R

@Parcelize
data class CallHistoryItem(
    val id: String,
    val conversationName: String,
    val conversationToken: String,
    val callType: CallType,
    val duration: String,
    val timestamp: Date,
    val isVideoCall: Boolean,
    val isMissed: Boolean,
    val callFlag: Int = 0,
    val callStartTime: Long = 0,
    val hasCall: Boolean = false,
    val profileImageUrl: String? = null,
    val isGroupCall: Boolean = false
) : Parcelable {

    enum class CallType {
        INCOMING_AUDIO,
        INCOMING_VIDEO,
        OUTGOING_AUDIO,
        OUTGOING_VIDEO,
        MISSED_AUDIO,
        MISSED_VIDEO,
        REJECTED_AUDIO,
        REJECTED_VIDEO,
        ONGOING_AUDIO,
        ONGOING_VIDEO
    }

    fun getCallTypeIcon(): Int {
        return when (callType) {
            CallType.INCOMING_AUDIO, CallType.INCOMING_VIDEO -> R.drawable.ic_call_received
            CallType.OUTGOING_AUDIO, CallType.OUTGOING_VIDEO -> R.drawable.ic_call_made
            CallType.MISSED_AUDIO, CallType.MISSED_VIDEO -> R.drawable.ic_call_missed
            CallType.REJECTED_AUDIO, CallType.REJECTED_VIDEO -> R.drawable.ic_call_missed
            CallType.ONGOING_AUDIO, CallType.ONGOING_VIDEO -> R.drawable.ic_call_received
        }
    }

    fun getCallTypeColor(): Int {
        return when (callType) {
            CallType.INCOMING_AUDIO, CallType.INCOMING_VIDEO -> R.color.call_incoming_color
            CallType.OUTGOING_AUDIO, CallType.OUTGOING_VIDEO -> R.color.call_outgoing_color
            CallType.MISSED_AUDIO, CallType.MISSED_VIDEO -> R.color.call_missed_color
            CallType.REJECTED_AUDIO, CallType.REJECTED_VIDEO -> R.color.call_missed_color
            CallType.ONGOING_AUDIO, CallType.ONGOING_VIDEO -> R.color.call_incoming_color
        }
    }

    fun getFormattedTimestamp(): String {
        val now = Date()
        val diffInMillis = now.time - timestamp.time
        val diffInMinutes = diffInMillis / (1000 * 60)
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24

        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val timeString = timeFormat.format(timestamp)

        return when {
            diffInMinutes < 1 -> "Just now"
            diffInMinutes < 60 -> "$diffInMinutes min ago"
            diffInHours < 24 -> {
                if (diffInHours < 1) {
                    "$diffInMinutes min ago"
                } else {
                    "$diffInHours hr ago"
                }
            }
            diffInDays == 1L -> "Yesterday $timeString"
            diffInDays < 7 -> {
                val dayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
                val dayName = dayFormat.format(timestamp)
                "$dayName $timeString"
            }
            else -> {
                val dateFormat = java.text.SimpleDateFormat("MMM dd h:mm a", java.util.Locale.getDefault())
                dateFormat.format(timestamp)
            }
        }
    }

    companion object {
        fun fromConversationEntity(
            conversation: com.nextcloud.talk.data.database.model.ConversationEntity,
            isVideoCall: Boolean = false
        ): CallHistoryItem? {
            // Only create call history items for conversations that have had calls
            if (!conversation.hasCall && conversation.callStartTime == 0L) {
                return null
            }

            val callType = when {
                conversation.callFlag > 0 -> {
                    if (isVideoCall) CallType.ONGOING_VIDEO else CallType.ONGOING_AUDIO
                }
                conversation.callStartTime > 0 -> {
                    if (isVideoCall) CallType.INCOMING_VIDEO else CallType.INCOMING_AUDIO
                }
                else -> {
                    if (isVideoCall) CallType.MISSED_VIDEO else CallType.MISSED_AUDIO
                }
            }

            return CallHistoryItem(
                id = conversation.internalId,
                conversationName = conversation.displayName,
                conversationToken = conversation.token,
                callType = callType,
                duration = "0:00", // Duration not stored in conversation entity
                timestamp = Date(conversation.callStartTime),
                isVideoCall = isVideoCall,
                isMissed = conversation.callStartTime == 0L,
                callFlag = conversation.callFlag,
                callStartTime = conversation.callStartTime,
                hasCall = conversation.hasCall,
                profileImageUrl = null, // Avatar URL not available in ConversationEntity
                isGroupCall = conversation.actorType != "users"
            )
        }
    }
}
