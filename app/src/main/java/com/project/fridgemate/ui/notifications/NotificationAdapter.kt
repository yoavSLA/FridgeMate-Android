package com.project.fridgemate.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.R
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.model.NotificationType
import com.project.fridgemate.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            binding.tvNotificationTitle.text = notification.title
            binding.tvNotificationMessage.text = notification.message
            binding.tvNotificationTime.text = getTimeAgo(notification.timestamp)

            // Show/hide unread indicator
            binding.viewUnreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

            // Set icon based on type
            val iconRes = when (notification.type) {
                NotificationType.POST_LIKE -> R.drawable.ic_heart_filled
                NotificationType.POST_COMMENT -> R.drawable.ic_comments
                NotificationType.FOLLOW -> R.drawable.ic_person
                NotificationType.CHAT_MESSAGE -> R.drawable.ic_send
                NotificationType.FRIDGE_INVITE -> R.drawable.ic_group
                NotificationType.EXPIRING_ITEM -> R.drawable.ic_warning
                NotificationType.SCAN_COMPLETE -> R.drawable.ic_fridge
                NotificationType.SYSTEM -> R.drawable.ic_notifications
            }
            binding.ivNotificationIcon.setImageResource(iconRes)

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(notification)
            }
        }

        private fun getTimeAgo(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}