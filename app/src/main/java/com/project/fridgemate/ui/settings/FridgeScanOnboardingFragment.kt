package com.project.fridgemate.ui.settings

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.R
import com.project.fridgemate.data.remote.dto.ScanChangesDto
import com.project.fridgemate.databinding.FragmentFridgeScanOnboardingBinding
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ScanError
import com.project.fridgemate.utils.ScanErrorSnackbar
import com.project.fridgemate.utils.ScanImage
import com.project.fridgemate.utils.ToastHelper

class FridgeScanOnboardingFragment : Fragment() {

    private var _binding: FragmentFridgeScanOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedFridgeViewModel by activityViewModels()

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let { handleBitmap(it) }
        }

    private val pickFromGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleUri(it) }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) takePictureLauncher.launch(null)
            else ToastHelper.showToast(requireContext(), getString(R.string.camera_permission_denied_generic))
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFridgeScanOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()

        viewModel.scanWorkInfo.observe(viewLifecycleOwner) { workInfo ->
            workInfo?.let { viewModel.handleScanWorkInfo(it) }
        }
    }

    private fun setupListeners() {
        binding.btnUploadPhoto.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnSkipScan.setOnClickListener {
            finishOnboarding()
        }

        binding.btnFinish.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun setupObservers() {
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), it)
                ToastHelper.showToast(requireContext(), userFriendly, Toast.LENGTH_LONG)
                viewModel.clearError()
            }
        }

        viewModel.scanError.observe(viewLifecycleOwner) { scanError ->
            scanError?.let {
                showScanError(it)
                viewModel.clearScanError()
            }
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.scanProgressLayout.visibility = if (scanning) View.VISIBLE else View.GONE
            binding.btnUploadPhoto.isEnabled = !scanning
            binding.btnSkipScan.isEnabled = !scanning
            binding.tvScanLaterHint.isEnabled = !scanning
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { items ->
            if (items != null && items.isNotEmpty()) {
                binding.scanResultsLayout.visibility = View.VISIBLE
                binding.tvScanResultTitle.text = getString(R.string.detected_items, items.size)
                binding.rvScanResults.layoutManager = LinearLayoutManager(requireContext())
                binding.rvScanResults.adapter = DetectedItemAdapter(items)
                
                binding.btnSkipScan.visibility = View.GONE
                binding.tvScanLaterHint.visibility = View.GONE
            } else {
                binding.scanResultsLayout.visibility = View.GONE
                binding.btnSkipScan.visibility = View.VISIBLE
                binding.tvScanLaterHint.visibility = View.VISIBLE
            }
        }

        viewModel.scanSummary.observe(viewLifecycleOwner) { summary: ScanChangesDto? ->
            if (summary != null && (summary.added.isNotEmpty() || summary.updated.isNotEmpty() || summary.removed.isNotEmpty())) {
                binding.tvScanSummaryTitle.visibility = View.VISIBLE
                binding.rvScanSummary.visibility = View.VISIBLE
                
                val summaryItems = mutableListOf<ScanSummaryItem>()
                summary.added.forEach { item -> summaryItems.add(ScanSummaryItem.Added(item.name, item.quantity)) }
                summary.updated.forEach { item -> summaryItems.add(ScanSummaryItem.Updated(item.name, item.oldQuantity, item.newQuantity)) }
                summary.removed.forEach { item -> summaryItems.add(ScanSummaryItem.Removed(item.name, item.quantity)) }
                
                binding.rvScanSummary.layoutManager = LinearLayoutManager(requireContext())
                binding.rvScanSummary.adapter = ScanSummaryAdapter(summaryItems)
            } else {
                binding.tvScanSummaryTitle.visibility = View.GONE
                binding.rvScanSummary.visibility = View.GONE
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(
            getString(R.string.source_camera),
            getString(R.string.source_gallery)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_image_source))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission.launch(Manifest.permission.CAMERA)
                    1 -> pickFromGalleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun handleBitmap(bitmap: Bitmap) {
        viewModel.uploadFridgeScan(ScanImage.fromBitmap(bitmap), ScanImage.MIME_TYPE)
    }

    private fun handleUri(uri: Uri) {
        val bytes = ScanImage.fromUri(requireContext(), uri)
        if (bytes == null) {
            showScanError(ScanError.UNREADABLE_IMAGE)
            return
        }
        viewModel.uploadFridgeScan(bytes, ScanImage.MIME_TYPE)
    }

    private fun showScanError(error: ScanError) {
        ScanErrorSnackbar.show(
            root = binding.root,
            error = error,
            onRetry = { viewModel.retryLastScan() },
            onNewPhoto = { showImageSourceDialog() }
        )
    }

    private fun finishOnboarding() {
        findNavController().popBackStack(R.id.dashboardFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearScanResult()
        _binding = null
    }
}
