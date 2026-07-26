package com.project.fridgemate.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project.fridgemate.data.remote.dto.ScanChangesDto
import com.project.fridgemate.databinding.DialogScanSummaryBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ScanSummaryDialog : BottomSheetDialogFragment() {

    private var _binding: DialogScanSummaryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogScanSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        val summaryJson = arguments?.getString(ARG_SUMMARY) ?: return
        val createdAt = arguments?.getString(ARG_CREATED_AT)
        
        val summaryData = try {
            com.google.gson.Gson().fromJson(summaryJson, ScanChangesDto::class.java)
        } catch (e: Exception) {
            null
        } ?: return
        
        val summaryItems = mutableListOf<ScanSummaryItem>()
        summaryData.added.forEach { item -> summaryItems.add(ScanSummaryItem.Added(item.name, item.quantity)) }
        summaryData.updated.forEach { item -> summaryItems.add(ScanSummaryItem.Updated(item.name, item.oldQuantity, item.newQuantity)) }
        summaryData.removed.forEach { item -> summaryItems.add(ScanSummaryItem.Removed(item.name, item.quantity)) }

        binding.rvSummary.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSummary.adapter = ScanSummaryAdapter(summaryItems)

        createdAt?.let {
            binding.tvDateTime.text = formatDateTime(it)
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun formatDateTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(timestamp)
            
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            outputFormat.format(date!!)
        } catch (e: Exception) {
            timestamp
        }
    }

    companion object {
        const val TAG = "ScanSummaryDialog"
        private const val ARG_SUMMARY = "arg_summary"
        private const val ARG_CREATED_AT = "arg_created_at"
        
        fun newInstance(summary: ScanChangesDto, createdAt: String): ScanSummaryDialog {
            return ScanSummaryDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SUMMARY, com.google.gson.Gson().toJson(summary))
                    putString(ARG_CREATED_AT, createdAt)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
