/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Utility class to track video calls locally for better call history detection
 */
class VideoCallTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoCallTracker"
        private const val PREFS_NAME = "video_call_tracker"
        private const val KEY_VIDEO_CALLS = "video_calls"
        private const val SEPARATOR = "|"
        private const val MAX_ENTRIES = 100 // Keep only recent entries to avoid memory issues
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Mark a call as a video call for a specific conversation
     * @param conversationToken The conversation token
     * @param timestamp The timestamp when the call was made
     * @param isVideoCall Whether it's a video call
     */
    fun markCallAsVideoCall(conversationToken: String, timestamp: Long, isVideoCall: Boolean) {
        if (!isVideoCall) return // Only store video calls to save space
        
        val videoCalls = getVideoCalls().toMutableSet()
        val callKey = "$conversationToken$SEPARATOR$timestamp"
        videoCalls.add(callKey)
        
        // Keep only recent entries
        val sortedCalls = videoCalls.sortedByDescending { 
            it.substringAfterLast(SEPARATOR).toLongOrNull() ?: 0L 
        }.take(MAX_ENTRIES)
        
        val updatedVideoCalls = sortedCalls.joinToString(",")
        prefs.edit().putString(KEY_VIDEO_CALLS, updatedVideoCalls).apply()
        
        Log.d(TAG, "Marked call as video call: $conversationToken at $timestamp")
    }
    
    /**
     * Check if a call was a video call for a specific conversation and timestamp
     * @param conversationToken The conversation token
     * @param timestamp The timestamp when the call was made
     * @return true if it was a video call, false otherwise
     */
    fun wasVideoCall(conversationToken: String, timestamp: Long): Boolean {
        val videoCalls = getVideoCalls()
        val callKey = "$conversationToken$SEPARATOR$timestamp"
        val isVideoCall = videoCalls.contains(callKey)
        
        Log.d(TAG, "Checking if call was video call: $conversationToken at $timestamp -> $isVideoCall")
        return isVideoCall
    }
    
    /**
     * Check if any call in a conversation was a video call (for cases where exact timestamp doesn't match)
     * @param conversationToken The conversation token
     * @param timeRangeSeconds Time range in seconds to check around the call timestamp
     * @return true if any call in the range was a video call
     */
    fun wasVideoCallInRange(conversationToken: String, timestamp: Long, timeRangeSeconds: Long = 60): Boolean {
        val videoCalls = getVideoCalls()
        val timeRange = timeRangeSeconds * 1000 // Convert to milliseconds
        
        val isVideoCall = videoCalls.any { callKey ->
            if (callKey.startsWith("$conversationToken$SEPARATOR")) {
                val callTimestamp = callKey.substringAfterLast(SEPARATOR).toLongOrNull() ?: 0L
                Math.abs(callTimestamp - timestamp) <= timeRange
            } else {
                false
            }
        }
        
        Log.d(TAG, "Checking if call was video call in range: $conversationToken around $timestamp -> $isVideoCall")
        return isVideoCall
    }
    
    /**
     * Get all stored video calls
     */
    private fun getVideoCalls(): Set<String> {
        val videoCallsString = prefs.getString(KEY_VIDEO_CALLS, "") ?: ""
        return if (videoCallsString.isEmpty()) {
            emptySet()
        } else {
            videoCallsString.split(",").toSet()
        }
    }
    
    /**
     * Clear all stored video call data (useful for testing or cleanup)
     */
    fun clearAll() {
        prefs.edit().remove(KEY_VIDEO_CALLS).apply()
        Log.d(TAG, "Cleared all video call data")
    }
    
    /**
     * Get debug information about stored video calls
     */
    fun getDebugInfo(): String {
        val videoCalls = getVideoCalls()
        return "Video calls stored: ${videoCalls.size}\n" +
                videoCalls.take(10).joinToString("\n") { callKey ->
                    val parts = callKey.split(SEPARATOR)
                    if (parts.size >= 2) {
                        "Token: ${parts[0]}, Timestamp: ${parts[1]}"
                    } else {
                        "Invalid: $callKey"
                    }
                }
    }
}
