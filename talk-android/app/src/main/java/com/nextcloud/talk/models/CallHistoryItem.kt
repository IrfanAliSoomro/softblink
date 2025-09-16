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
    val hasCall: Boolean = false
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
            CallType.INCOMING_AUDIO, CallType.OUTGOING_AUDIO, CallType.ONGOING_AUDIO -> android.R.drawable.ic_menu_call
            CallType.INCOMING_VIDEO, CallType.OUTGOING_VIDEO, CallType.ONGOING_VIDEO -> android.R.drawable.ic_menu_camera
            CallType.MISSED_AUDIO, CallType.REJECTED_AUDIO -> android.R.drawable.ic_menu_call
            CallType.MISSED_VIDEO, CallType.REJECTED_VIDEO -> android.R.drawable.ic_menu_camera
        }
    }

    fun getCallTypeColor(): Int {
        return when (callType) {
            CallType.INCOMING_AUDIO, CallType.INCOMING_VIDEO -> android.R.color.holo_green_dark
            CallType.OUTGOING_AUDIO, CallType.OUTGOING_VIDEO -> android.R.color.holo_blue_dark
            CallType.MISSED_AUDIO, CallType.MISSED_VIDEO -> android.R.color.holo_red_dark
            CallType.REJECTED_AUDIO, CallType.REJECTED_VIDEO -> android.R.color.holo_orange_dark
            CallType.ONGOING_AUDIO, CallType.ONGOING_VIDEO -> android.R.color.holo_green_light
        }
    }

    fun getFormattedTimestamp(): String {
        val now = Date()
        val diffInMillis = now.time - timestamp.time
        val diffInMinutes = diffInMillis / (1000 * 60)
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24

        return when {
            diffInMinutes < 1 -> "Just now"
            diffInMinutes < 60 -> "$diffInMinutes min ago"
            diffInHours < 24 -> "$diffInHours hr ago"
            diffInDays < 7 -> "$diffInDays day ago"
            else -> {
                val dateFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
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
                hasCall = conversation.hasCall
            )
        }
    }
}
