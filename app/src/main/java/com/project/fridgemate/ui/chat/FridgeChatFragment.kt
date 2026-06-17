package com.project.fridgemate.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.databinding.FragmentFridgeChatBinding

class FridgeChatFragment : Fragment() {

    private var _binding: FragmentFridgeChatBinding? = null
    private val binding get() = _binding!!

    private val args: FridgeChatFragmentArgs by navArgs()
    private val viewModel: FridgeChatViewModel by viewModels()

    private lateinit var adapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFridgeChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MessageAdapter(
            currentUserId = viewModel.currentUserId,
            onRecipeClick = { recipeId ->
                val action = FridgeChatFragmentDirections
                    .actionFridgeChatFragmentToRecipeDetailFragment(
                        serverRecipeId = recipeId,
                    )
                findNavController().navigate(action)
            },
        )
        layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
        binding.rvMessages.addOnScrollListener(scrollListener)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        if (args.fridgeName.isNotBlank()) {
            binding.tvTitle.text = args.fridgeName
        }

        binding.etMessage.addTextChangedListener { editable ->
            binding.btnSend.isEnabled = !editable.isNullOrBlank()
        }
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString().orEmpty()
            viewModel.send(text)
            binding.etMessage.setText("")
        }

        setupEmojiPicker()
        observeViewModel()
        viewModel.start(args.fridgeId)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.emojiPicker.isVisible) {
                    binding.emojiPicker.isVisible = false
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupEmojiPicker() {
        binding.btnEmoji.setOnClickListener {
            if (binding.emojiPicker.isVisible) {
                showKeyboard()
            } else {
                hideKeyboard()
                binding.emojiPicker.isVisible = true
            }
        }

        binding.etMessage.setOnClickListener {
            binding.emojiPicker.isVisible = false
        }

        binding.emojiPicker.setOnEmojiPickedListener { emojiViewItem ->
            val emoji = emojiViewItem.emoji
            val currentText = binding.etMessage.text.toString()
            val selectionStart = binding.etMessage.selectionStart
            val selectionEnd = binding.etMessage.selectionEnd
            val newText = StringBuilder(currentText)
                .replace(selectionStart, selectionEnd, emoji)
                .toString()
            binding.etMessage.setText(newText)
            binding.etMessage.setSelection(selectionStart + emoji.length)
        }
    }

    private fun showKeyboard() {
        binding.etMessage.requestFocus()
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
        binding.emojiPicker.isVisible = false
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            val wasAtBottom = isAtBottom()
            val previousSize = adapter.itemCount
            val items = MessageAdapter.buildItems(requireContext(), messages)
            adapter.submitList(items) {
                updateEmptyState()
                if (wasAtBottom || previousSize == 0) {
                    binding.rvMessages.scrollToPosition((items.size - 1).coerceAtLeast(0))
                }
            }
        }

        viewModel.initialLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingInitial.isVisible = loading
            updateEmptyState()
        }

        viewModel.loadingOlder.observe(viewLifecycleOwner) { loading ->
            binding.loadingTop.isVisible = loading
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy >= 0) return
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            if (firstVisible <= 3) viewModel.loadOlder()
        }
    }

    private fun updateEmptyState() {
        val isLoading = viewModel.initialLoading.value == true
        val isEmpty = viewModel.messages.value.isNullOrEmpty()
        binding.emptyState.isVisible = isEmpty && !isLoading
    }

    private fun isAtBottom(): Boolean {
        val last = layoutManager.findLastVisibleItemPosition()
        val total = adapter.itemCount
        return total == 0 || last >= total - 2
    }

    override fun onPause() {
        super.onPause()
        viewModel.markRead()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMessages.removeOnScrollListener(scrollListener)
        _binding = null
    }
}
