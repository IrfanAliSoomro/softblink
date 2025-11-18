package com.nextcloud.talk.models

import com.nextcloud.talk.chat.data.model.ChatMessage

data class CallHistoryItem(
    val id: String,
    val contactName: String,
    val profilePictureUrl: String?,
    val callType: CallType,
    val timestamp: Long,
    val conversationId: String,
    val messageId: String
)

enum class CallType(val systemMessageType: ChatMessage.SystemMessageType) {
    INCOMING(ChatMessage.SystemMessageType.CALL_JOINED),
    OUTGOING(ChatMessage.SystemMessageType.CALL_STARTED),
    MISSED(ChatMessage.SystemMessageType.CALL_MISSED),
    ENDED(ChatMessage.SystemMessageType.CALL_ENDED),
    ENDED_EVERYONE(ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE),
    TRIED(ChatMessage.SystemMessageType.CALL_TRIED),
    LEFT(ChatMessage.SystemMessageType.CALL_LEFT)
}
