package com.project.fridgemate.ui.journal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.app.DatePickerDialog
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.R
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.databinding.FragmentJournalBinding
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

        adapter = JournalAdapter(
            onItemClick = { entry ->
                try {
                    val action = DashboardFragmentDirections.actionDashboardFragmentToAddJournalEntryFragment(entry.id)
                    requireParentFragment().findNavController().navigate(action)
                } catch (e: Exception) {
                    ToastHelper.showToast(requireContext(), "Navigation error: ${e.message}")
                }
            },
            onRecipeImageClick = { serverRecipeId ->
                try {
                    val action = DashboardFragmentDirections.actionDashboardFragmentToRecipeDetailFragment(0L, serverRecipeId)
                    requireParentFragment().findNavController().navigate(action)
                } catch (e: Exception) {
                    ToastHelper.showToast(requireContext(), "Navigation error: ${e.message}")
                }
            }
        )
        binding.rvJournal.adapter = adapter

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    binding.rvJournal.scrollToPosition(0)
                }
            }
        })

        setupSearch()
        setupFilterChips()
        setupErrorState()
        
        binding.btnCalendar.setOnClickListener {
            showDatePicker()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadEntries()
        }

        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            allEntries = entries
            applyFilters()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val hasItems = allEntries.isNotEmpty()
            val errorView = binding.root.findViewById<View>(R.id.error_state)
            
            if (isLoading) {
                binding.emptyState.visibility = View.GONE
                errorView?.visibility = View.GONE
                
                if (!hasItems) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.swipeRefresh.isRefreshing = false
                    binding.swipeRefresh.visibility = View.GONE
                    binding.headerContainer.visibility = View.GONE
                    binding.chipsScroll.visibility = View.GONE
                    binding.fabAddEntry.visibility = View.GONE
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.visibility = View.VISIBLE
                    binding.swipeRefresh.isRefreshing = true
                    binding.headerContainer.visibility = View.VISIBLE
                    binding.chipsScroll.visibility = View.VISIBLE
                    binding.fabAddEntry.visibility = View.VISIBLE
                }
            } else {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
                applyFilters()
            }
            
            errorView?.findViewById<View>(R.id.btn_retry)?.isEnabled = !isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            val errorView = binding.root.findViewById<View>(R.id.error_state)
            val isLoading = viewModel.isLoading.value == true
            val hasEntries = allEntries.isNotEmpty() || viewModel.entries.value?.isNotEmpty() == true
            
            if (errorMsg != null) {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), errorMsg)
                if (hasEntries) {
                    ToastHelper.showToast(requireContext(), userFriendly)
                    errorView?.visibility = View.GONE
                    binding.fabAddEntry.visibility = View.VISIBLE
                    binding.headerContainer.visibility = View.VISIBLE
                    binding.chipsScroll.visibility = View.VISIBLE
                    viewModel.resetActionState() // This clears the error
                } else if (!isLoading) {
                    binding.swipeRefresh.visibility = View.GONE
                    binding.emptyState.visibility = View.GONE
                    errorView?.visibility = View.VISIBLE
                    errorView?.findViewById<TextView>(R.id.tv_error_desc)?.text = userFriendly
                    binding.fabAddEntry.visibility = View.GONE
                    binding.headerContainer.visibility = View.GONE
                    binding.chipsScroll.visibility = View.GONE
                }
            } else {
                binding.swipeRefresh.visibility = View.VISIBLE
                errorView?.visibility = View.GONE
                binding.fabAddEntry.visibility = View.VISIBLE
                binding.headerContainer.visibility = View.VISIBLE
                binding.chipsScroll.visibility = View.VISIBLE
                applyFilters()
            }
        }

        binding.fabAddEntry.setOnClickListener {
            try {
                val action = DashboardFragmentDirections.actionDashboardFragmentToAddJournalEntryFragment("")
                requireParentFragment().findNavController().navigate(action)
            } catch (e: Exception) {
                ToastHelper.showToast(requireContext(), "Navigation error: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always try to refresh on resume to catch offline state or new data
        viewModel.loadEntries()
    }

    private fun setupErrorState() {
        binding.root.findViewById<View>(R.id.error_state)?.findViewById<View>(R.id.btn_retry)?.setOnClickListener {
            viewModel.loadEntries()
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
        if (viewModel.isLoading.value == true) return
        
        var filtered = allEntries
        val errorView = binding.root.findViewById<View>(R.id.error_state)
        val hasError = viewModel.error.value != null

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

        val grouped = groupEntriesByDay(filtered)
        adapter.submitList(grouped)

        val hasAnyEntries = allEntries.isNotEmpty()
        val isFiltered = currentSearchQuery.isNotEmpty() || currentMealFilter != null
        val isEmpty = grouped.isEmpty()

        binding.emptyState.visibility = if (isEmpty && !hasError) View.VISIBLE else View.GONE
        errorView?.visibility = if (hasError && !hasAnyEntries) View.VISIBLE else View.GONE

        // Show contextual empty message
        if (isEmpty && hasAnyEntries && isFiltered) {
            binding.tvEmptyText.text = getString(R.string.journal_no_results)
        } else if (isEmpty) {
            binding.tvEmptyText.text = getString(R.string.no_journal_entries)
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                scrollToDate(year, month, dayOfMonth)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun scrollToDate(year: Int, month: Int, day: Int) {
        val targetCal = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetMillis = targetCal.timeInMillis

        val position = adapter.currentList.indexOfFirst { group ->
            val groupMillis = group.id.toLongOrNull() ?: 0L
            groupMillis == targetMillis
        }

        if (position != -1) {
            binding.rvJournal.scrollToPosition(position)
        } else {
            ToastHelper.showToast(requireContext(), "No entries found for this date")
        }
    }

    private fun groupEntriesByDay(entries: List<JournalEntry>): List<JournalDayGroup> {
        val groups = entries.groupBy { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.dateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        return groups.map { (dayMillis, dayEntries) ->
            var tCal = 0
            var tP = 0
            var tC = 0
            var tF = 0

            for (entry in dayEntries) {
                tCal += entry.calories.replace(Regex("\\D"), "").toIntOrNull() ?: 0
                
                val regexP = Regex("""(\d+)\s*g?\s*P""")
                val regexC = Regex("""(\d+)\s*g?\s*C""")
                val regexF = Regex("""(\d+)\s*g?\s*F""")
                
                tP += regexP.find(entry.macros)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                tC += regexC.find(entry.macros)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                tF += regexF.find(entry.macros)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            }

            val label = when {
                DateUtils.isToday(dayMillis) -> "Today"
                DateUtils.isToday(dayMillis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
                else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(dayMillis))
            }

            JournalDayGroup(
                id = dayMillis.toString(),
                dateLabel = label,
                entries = dayEntries,
                totalCalories = tCal,
                totalProtein = tP,
                totalCarbs = tC,
                totalFat = tF
            )
        }.sortedByDescending { it.id.toLong() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
