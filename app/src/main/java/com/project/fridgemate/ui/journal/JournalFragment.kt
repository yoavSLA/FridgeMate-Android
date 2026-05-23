package com.project.fridgemate.ui.journal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.databinding.FragmentJournalBinding
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections

class JournalFragment : Fragment() {

    private var _binding: FragmentJournalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JournalViewModel by activityViewModels()
    private lateinit var adapter: JournalAdapter

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
            val action = DashboardFragmentDirections.actionDashboardFragmentToAddJournalEntryFragment(entry.id)
            findNavController().navigate(action)
        }
        binding.rvJournal.adapter = adapter

        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAddEntry.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToAddJournalEntryFragment("")
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
