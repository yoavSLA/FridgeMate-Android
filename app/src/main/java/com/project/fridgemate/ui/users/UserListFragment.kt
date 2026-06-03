package com.project.fridgemate.ui.users

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentUserListBinding
import com.project.fridgemate.ui.profile.UserProfileFragmentDirections

/**
 * Generic list screen for [UserListMode] = followers / following / search.
 * Mode is selected by the `type` nav argument.
 */
class UserListFragment : Fragment() {

    private var _binding: FragmentUserListBinding? = null
    private val binding get() = _binding!!

    private val args: UserListFragmentArgs by navArgs()
    private val viewModel: UserListViewModel by viewModels()
    private lateinit var adapter: UserListAdapter

    private val mode: UserListMode by lazy {
        when (args.type.lowercase()) {
            "search" -> UserListMode.SEARCH
            "following" -> UserListMode.FOLLOWING
            else -> UserListMode.FOLLOWERS
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvTitle.setText(
            when (mode) {
                UserListMode.FOLLOWERS -> R.string.followers_label
                UserListMode.FOLLOWING -> R.string.following_label
                UserListMode.SEARCH -> R.string.discover_people
            }
        )

        setupList()
        observe()
        loadInitial()
    }

    private fun setupList() {
        adapter = UserListAdapter(
            currentUserId = viewModel.meId,
            onUserClick = { user ->
                findNavController().navigate(
                    UserProfileFragmentDirections.actionUserProfileFragmentSelf(user.id)
                )
            },
            onFollowClick = { user -> viewModel.toggleFollow(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter
    }

    private fun loadInitial() {
        when (mode) {
            UserListMode.FOLLOWERS -> viewModel.loadFollowers(args.userId)
            UserListMode.FOLLOWING -> viewModel.loadFollowing(args.userId)
            UserListMode.SEARCH -> {
                binding.tilSearch.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                binding.etSearch.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        viewModel.searchDebounced((s ?: "").toString().trim())
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
        }
    }

    private fun observe() {
        viewModel.users.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
            val empty = users.isEmpty() && viewModel.isLoading.value != true
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.tvEmpty.setText(
                when (mode) {
                    UserListMode.FOLLOWERS -> R.string.no_followers_yet
                    UserListMode.FOLLOWING -> R.string.no_following_yet
                    UserListMode.SEARCH -> R.string.no_users_found
                }
            )
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
