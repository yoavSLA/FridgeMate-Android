package com.project.fridgemate.ui.journal

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.R
import com.project.fridgemate.data.model.JournalEntry
import com.project.fridgemate.databinding.FragmentAddJournalEntryBinding
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class AddJournalEntryFragment : Fragment() {

    private var _binding: FragmentAddJournalEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JournalViewModel by activityViewModels()
    
    private var entryId: String = ""
    private var selectedImageUri: Uri? = null
    private var existingImageUrl: String? = null

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
        viewModel.resetActionState()

        entryId = AddJournalEntryFragmentArgs.fromBundle(requireArguments()).entryId

        setupToolbar()
        setupDropdowns()
        
        if (entryId.isNotEmpty()) {
            loadExistingEntry()
        }

        binding.btnAddImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            saveEntry()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.actionSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                findNavController().navigateUp()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
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
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.delete_journal_entry)
                        .setMessage(R.string.delete_journal_entry_confirmation)
                        .setIcon(R.drawable.ic_delete)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            viewModel.deleteEntry(entryId)
                        }
                        .show()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupDropdowns() {
        val mealTypes = listOf("Breakfast", "Lunch", "Dinner", "Snack")
        val mealAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mealTypes)
        binding.etMealType.setAdapter(mealAdapter)

        val moods = listOf("😊 Happy", "😌 Relaxed", "😐 Neutral", "😔 Tired", "🤩 Energized", "😞 Sad", "🤢 Sick", "😤 Stressed")
        val moodAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, moods)
        binding.etMood.setAdapter(moodAdapter)
    }

    private fun loadExistingEntry() {
        val entry = viewModel.getEntryById(entryId) ?: return
        
        binding.etTitle.setText(entry.title)
        binding.etContent.setText(entry.content)
        binding.etMealType.setText(entry.mealType, false)
        binding.etMood.setText(entry.mood, false)
        binding.etCalories.setText(entry.calories)
        
        // Parse macros string into individual fields
        parseMacros(entry.macros)
        
        existingImageUrl = entry.imageUrl
        if (!existingImageUrl.isNullOrEmpty()) {
            Picasso.get().load(existingImageUrl).into(binding.ivMealPhoto)
            binding.layoutAddImage.visibility = View.GONE
        }
    }

    private fun parseMacros(macros: String) {
        if (macros.isBlank()) return
        // Supports formats like: "20P / 50C / 10F" or "20g P / 50g C / 10g F"
        val regex = Regex("""(\d+)\s*g?\s*P""")
        val regexC = Regex("""(\d+)\s*g?\s*C""")
        val regexF = Regex("""(\d+)\s*g?\s*F""")
        regex.find(macros)?.groupValues?.getOrNull(1)?.let { binding.etProtein.setText(it) }
        regexC.find(macros)?.groupValues?.getOrNull(1)?.let { binding.etCarbs.setText(it) }
        regexF.find(macros)?.groupValues?.getOrNull(1)?.let { binding.etFat.setText(it) }
    }

    private fun buildMacrosString(): String {
        val p = binding.etProtein.text.toString().trim()
        val c = binding.etCarbs.text.toString().trim()
        val f = binding.etFat.text.toString().trim()
        val parts = mutableListOf<String>()
        if (p.isNotEmpty()) parts.add("${p}g P")
        if (c.isNotEmpty()) parts.add("${c}g C")
        if (f.isNotEmpty()) parts.add("${f}g F")
        return parts.joinToString(" / ")
    }

    private fun saveEntry() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val mealType = binding.etMealType.text.toString().trim()
        val mood = binding.etMood.text.toString().trim()
        val calories = binding.etCalories.text.toString().trim()
        val macros = buildMacrosString()

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var finalImageUrl = existingImageUrl
            
            // Upload new image if selected
            if (selectedImageUri != null) {
                try {
                    binding.loadingOverlay.visibility = View.VISIBLE
                    val inputStream = requireContext().contentResolver.openInputStream(selectedImageUri!!)
                    val bytes = inputStream?.readBytes()
                    val mimeType = requireContext().contentResolver.getType(selectedImageUri!!) ?: "image/jpeg"
                    
                    if (bytes != null) {
                        val uploadedUrl = viewModel.uploadImage(bytes, mimeType)
                        if (uploadedUrl != null) {
                            finalImageUrl = uploadedUrl
                        }
                    }
                } catch (e: Exception) {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
                    return@launch
                }
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
                    imageUrl = finalImageUrl
                ) ?: return@launch
                viewModel.updateEntry(updated)
            } else {
                val entry = JournalEntry(
                    title = title,
                    content = content,
                    mealType = mealType,
                    mood = mood,
                    calories = calories,
                    macros = macros,
                    imageUrl = finalImageUrl
                )
                viewModel.addEntry(entry)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
