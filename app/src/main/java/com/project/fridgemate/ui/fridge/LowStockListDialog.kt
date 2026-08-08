package com.project.fridgemate.ui.fridge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.fridgemate.R
import com.project.fridgemate.databinding.DialogLowStockListBinding

class LowStockListDialog : BottomSheetDialogFragment() {

    private var _binding: DialogLowStockListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLowStockListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString(ARG_ITEMS) ?: return
        val type = object : TypeToken<List<FridgeItem.Product>>() {}.type
        val items: List<FridgeItem.Product> = try {
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        binding.tvTitle.text = resources.getQuantityString(
            R.plurals.running_low_title, items.size, items.size
        )
        binding.rvLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLowStock.adapter = LowStockAdapter(items)
        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LowStockListDialog"
        private const val ARG_ITEMS = "arg_items"

        fun newInstance(items: List<FridgeItem.Product>) = LowStockListDialog().apply {
            arguments = Bundle().apply { putString(ARG_ITEMS, Gson().toJson(items)) }
        }
    }
}
