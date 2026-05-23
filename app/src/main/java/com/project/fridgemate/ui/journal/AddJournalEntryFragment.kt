package com.project.fridgemate.ui.journal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.R
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.databinding.FragmentAddJournalEntryBinding

class AddJournalEntryFragment : Fragment() {

    private var _binding: FragmentAddJournalEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JournalViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddJournalEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSave.setOnClickListener {
            saveEntry()
        }
    }

    private fun saveEntry() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val mealType = binding.etMealType.text.toString().trim()
        val mood = binding.etMood.text.toString().trim()
        val calories = binding.etCalories.text.toString().trim()
        val macros = binding.etMacros.text.toString().trim()

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val entry = JournalEntry(
            title = title,
            content = content,
            mealType = mealType,
            mood = mood,
            calories = calories,
            macros = macros
        )

        viewModel.addEntry(entry)
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
