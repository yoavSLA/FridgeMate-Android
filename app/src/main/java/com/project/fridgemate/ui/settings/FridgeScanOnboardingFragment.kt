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
import java.io.ByteArrayOutputStream

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
            else Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
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
                binding.tvScanResultTitle.text = "Detected Items (${items.size})"
                binding.rvScanResults.layoutManager = LinearLayoutManager(requireContext())
                binding.rvScanResults.adapter = DetectedItemAdapter(items)
                
                // Once scanned, hide the skip button and hint, encourage finishing
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
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Choose image source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission.launch(Manifest.permission.CAMERA)
                    1 -> pickFromGalleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun handleBitmap(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        viewModel.uploadFridgeScan(stream.toByteArray(), "image/jpeg")
    }

    private fun handleUri(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        viewModel.uploadFridgeScan(bytes, mimeType)
    }

    private fun finishOnboarding() {
        // Go back to dashboard
        findNavController().popBackStack(R.id.dashboardFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearScanResult()
        _binding = null
    }
}
