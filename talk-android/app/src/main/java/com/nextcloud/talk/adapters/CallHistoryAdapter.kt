package com.nextcloud.talk.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.CallHistoryItem
import com.nextcloud.talk.models.CallType

class CallHistoryAdapter(
    private val onCallItemClick: (CallHistoryItem) -> Unit,
    private val onPhoneIconClick: (CallHistoryItem) -> Unit
) : RecyclerView.Adapter<CallHistoryAdapter.CallHistoryViewHolder>() {

    private var callHistoryItems = mutableListOf<CallHistoryItem>()

    fun updateCallHistory(newItems: List<CallHistoryItem>) {
        callHistoryItems.clear()
        callHistoryItems.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_history, parent, false)
        return CallHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallHistoryViewHolder, position: Int) {
        val item = callHistoryItems[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = callHistoryItems.size

    inner class CallHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profilePicture: ImageView = itemView.findViewById(R.id.profile_picture)
        private val contactName: TextView = itemView.findViewById(R.id.contact_name)
        private val callStatusIcon: ImageView = itemView.findViewById(R.id.call_status_icon)
        private val callTimestamp: TextView = itemView.findViewById(R.id.call_timestamp)
        private val phoneActionIcon: ImageView = itemView.findViewById(R.id.phone_action_icon)

        fun bind(item: CallHistoryItem) {
            // Set contact name
            contactName.text = item.contactName

            // Load profile picture
            if (!item.profilePictureUrl.isNullOrEmpty()) {
                profilePicture.load(item.profilePictureUrl) {
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.account_circle_48dp)
                    error(R.drawable.account_circle_48dp)
                }
            } else {
                profilePicture.setImageResource(R.drawable.account_circle_48dp)
            }

            // Set call status icon and color
            when (item.callType) {
                CallType.INCOMING, CallType.ENDED, CallType.ENDED_EVERYONE -> {
                    callStatusIcon.setImageResource(R.drawable.ic_call_received)
                    callStatusIcon.setColorFilter(itemView.context.getColor(R.color.nc_darkGreen))
                }
                CallType.OUTGOING, CallType.TRIED -> {
                    callStatusIcon.setImageResource(R.drawable.ic_call_made)
                    callStatusIcon.setColorFilter(itemView.context.getColor(R.color.nc_darkGreen))
                }
                CallType.MISSED, CallType.LEFT -> {
                    callStatusIcon.setImageResource(R.drawable.ic_call_missed)
                    callStatusIcon.setColorFilter(itemView.context.getColor(R.color.nc_darkRed))
                }
            }

            // Set timestamp using the same format as conversation list
            // Note: message.timestamp is in seconds, so multiply by 1000 to get milliseconds
            callTimestamp.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp * 1000L, // Convert seconds to milliseconds
                System.currentTimeMillis(),
                0,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            // Set click listeners
            itemView.setOnClickListener {
                onCallItemClick(item)
            }

            phoneActionIcon.setOnClickListener {
                onPhoneIconClick(item)
            }
        }
    }
}
