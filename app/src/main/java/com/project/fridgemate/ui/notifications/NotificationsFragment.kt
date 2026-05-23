package com.project.fridgemate.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.model.NotificationType
import com.project.fridgemate.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadNotifications()
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter { notification ->
            onNotificationClick(notification)
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
    }

    private fun loadNotifications() {
        // TODO: Load from API/Database
        // For now, show sample data
        val sampleNotifications = listOf(
            Notification(
                id = "1",
                type = NotificationType.LIKE,
                title = "New Like",
                message = "John Doe liked your post",
                timestamp = System.currentTimeMillis() - 120000, // 2 minutes ago
                isRead = false
            ),
            Notification(
                id = "2",
                type = NotificationType.COMMENT,
                title = "New Comment",
                message = "Sarah commented on your post",
                timestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
                isRead = false
            ),
            Notification(
                id = "3",
                type = NotificationType.SCAN_COMPLETE,
                title = "Scan Complete",
                message = "Your fridge scan is ready!",
                timestamp = System.currentTimeMillis() - 7200000, // 2 hours ago
                isRead = true
            )
        )

        adapter.submitList(sampleNotifications)

        // Show/hide empty state
        if (sampleNotifications.isEmpty()) {
            binding.rvNotifications.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.rvNotifications.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }

    private fun onNotificationClick(notification: Notification) {
        // TODO: Navigate based on notification type
        // For example: if LIKE/COMMENT -> navigate to post
        //              if SCAN_COMPLETE -> navigate to scan results
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}