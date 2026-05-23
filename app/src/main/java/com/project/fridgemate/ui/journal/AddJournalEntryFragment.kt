package com.project.fridgemate.ui.journal

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    
    private var entryId: String = ""
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.ivMealPhoto.setImageURI(it)
            binding.layoutAddImage.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddJournalEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        entryId = arguments?.getString("entryId") ?: ""

        setupToolbar()
        
        if (entryId.isNotEmpty()) {
            loadExistingEntry()
        }

        binding.btnAddImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            saveEntry()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        if (entryId.isNotEmpty()) {
            binding.toolbar.title = "Edit Journal Entry"
            binding.toolbar.inflateMenu(R.menu.menu_edit_journal)
            binding.toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_delete) {
                    viewModel.deleteEntry(entryId)
                    findNavController().navigateUp()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun loadExistingEntry() {
        val entry = viewModel.getEntryById(entryId) ?: return
        
        binding.etTitle.setText(entry.title)
        binding.etContent.setText(entry.content)
        binding.etMealType.setText(entry.mealType)
        binding.etMood.setText(entry.mood)
        binding.etCalories.setText(entry.calories)
        binding.etMacros.setText(entry.macros)
        
        if (!entry.imageUrl.isNullOrEmpty()) {
            selectedImageUri = Uri.parse(entry.imageUrl)
            binding.ivMealPhoto.setImageURI(selectedImageUri)
            binding.layoutAddImage.visibility = View.GONE
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

        if (entryId.isNotEmpty()) {
            val existing = viewModel.getEntryById(entryId)
            val updated = existing?.copy(
                title = title,
                content = content,
                mealType = mealType,
                mood = mood,
                calories = calories,
                macros = macros,
                imageUrl = selectedImageUri?.toString()
            ) ?: return
            viewModel.updateEntry(updated)
        } else {
            val entry = JournalEntry(
                title = title,
                content = content,
                mealType = mealType,
                mood = mood,
                calories = calories,
                macros = macros,
                imageUrl = selectedImageUri?.toString()
            )
            viewModel.addEntry(entry)
        }
        
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
