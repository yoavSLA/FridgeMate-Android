package com.project.fridgemate.ui.journal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.project.fridgemate.R
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.databinding.FragmentJournalBinding
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections

class JournalFragment : Fragment() {

    private var _binding: FragmentJournalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JournalViewModel by activityViewModels()
    private lateinit var adapter: JournalAdapter

    private var allEntries: List<JournalEntry> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentMealFilter: String? = null // null = "All"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJournalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = JournalAdapter { entry ->
            try {
                val action = DashboardFragmentDirections.actionDashboardFragmentToAddJournalEntryFragment(entry.id)
                requireParentFragment().findNavController().navigate(action)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvJournal.adapter = adapter

        setupSearch()
        setupFilterChips()
        setupSwipeToDelete()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadEntries()
        }

        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            allEntries = entries
            applyFilters()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.resetActionState()
            }
        }

        binding.fabAddEntry.setOnClickListener {
            try {
                val action = DashboardFragmentDirections.actionDashboardFragmentToAddJournalEntryFragment("")
                requireParentFragment().findNavController().navigate(action)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentMealFilter = when {
                checkedIds.isEmpty() || checkedIds.contains(R.id.chip_all) -> null
                checkedIds.contains(R.id.chip_breakfast) -> "Breakfast"
                checkedIds.contains(R.id.chip_lunch) -> "Lunch"
                checkedIds.contains(R.id.chip_dinner) -> "Dinner"
                checkedIds.contains(R.id.chip_snack) -> "Snack"
                else -> null
            }
            applyFilters()
        }
    }

    private fun applyFilters() {
        var filtered = allEntries

        // Apply meal type filter
        if (currentMealFilter != null) {
            filtered = filtered.filter {
                it.mealType.equals(currentMealFilter, ignoreCase = true)
            }
        }

        // Apply search query
        if (currentSearchQuery.isNotEmpty()) {
            val query = currentSearchQuery.lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(query) ||
                it.content.lowercase().contains(query) ||
                it.mood.lowercase().contains(query) ||
                it.macros.lowercase().contains(query)
            }
        }

        adapter.submitList(filtered)

        val hasAnyEntries = allEntries.isNotEmpty()
        val isFiltered = currentSearchQuery.isNotEmpty() || currentMealFilter != null
        val isEmpty = filtered.isEmpty()

        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE

        // Show contextual empty message
        if (isEmpty && hasAnyEntries && isFiltered) {
            binding.tvEmptyText.text = getString(R.string.journal_no_results)
        } else if (isEmpty) {
            binding.tvEmptyText.text = getString(R.string.no_journal_entries)
        }
    }

    private fun setupSwipeToDelete() {
        val deleteColor = ContextCompat.getColor(requireContext(), R.color.error_red)
        val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)!!
        val iconTint = ContextCompat.getColor(requireContext(), R.color.white)
        deleteIcon.setTint(iconTint)
        val cornerRadius = resources.getDimension(R.dimen.card_corner_radius_default)

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val entry = adapter.currentList[position]
                showDeleteConfirmation(entry, position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (dX < 0) {
                    // Draw red background with rounded corners
                    val paint = Paint().apply { color = deleteColor }
                    val background = RectF(
                        itemView.right + dX + 16f,
                        itemView.top.toFloat() + 8f,
                        itemView.right.toFloat() - 16f,
                        itemView.bottom.toFloat() - 8f
                    )
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint)

                    // Draw delete icon
                    val iconMargin = (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconTop = itemView.top + iconMargin
                    val iconRight = itemView.right - iconMargin
                    val iconBottom = iconTop + deleteIcon.intrinsicHeight
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    deleteIcon.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvJournal)
    }

    private fun showDeleteConfirmation(entry: JournalEntry, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_journal_entry)
            .setMessage(R.string.delete_journal_entry_confirmation)
            .setIcon(R.drawable.ic_delete)
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Restore the swiped item
                adapter.notifyItemChanged(position)
            }
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteEntry(entry.id)
            }
            .setOnCancelListener {
                // Restore if dismissed by tapping outside
                adapter.notifyItemChanged(position)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
