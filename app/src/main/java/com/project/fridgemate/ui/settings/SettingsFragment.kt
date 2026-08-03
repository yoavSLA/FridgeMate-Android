package com.project.fridgemate.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.project.fridgemate.R
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.remote.dto.ScanChangesDto
import com.project.fridgemate.data.local.entity.RecipeEntity
import kotlinx.coroutines.launch
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.databinding.DialogLeaveFridgeBinding
import com.project.fridgemate.databinding.FragmentSettingsBinding
import com.project.fridgemate.ui.fridge.FridgeViewModel
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper
import java.io.ByteArrayOutputStream

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedFridgeViewModel by activityViewModels()
    private val fridgeViewModel: FridgeViewModel by activityViewModels()

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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
        
        viewModel.scanWorkInfo.observe(viewLifecycleOwner) { workInfo ->
            workInfo?.let { viewModel.handleScanWorkInfo(it) }
        }

        viewModel.loadFridge()
    }

    private fun setupObservers() {
        viewModel.hasFridge.observe(viewLifecycleOwner) { hasFridge ->
            when (hasFridge) {
                true -> {
                    binding.cardSharedFridge.visibility = View.VISIBLE
                    binding.cardFridgeScanner.visibility = View.VISIBLE
                }
                false -> {
                    binding.cardSharedFridge.visibility = View.GONE
                    binding.cardFridgeScanner.visibility = View.GONE
                    clearRecipeCache()
                    // Redirect to onboarding
                    val navController = findNavController()
                    if (navController.currentDestination?.id == R.id.settingsFragment) {
                        navController.navigate(R.id.onboardingFragment)
                    }
                }
                null -> {}
            }
        }

        viewModel.fridgeName.observe(viewLifecycleOwner) { name ->
            binding.tvFridgeName.text = name
        }

        viewModel.inviteCode.observe(viewLifecycleOwner) { code ->
            binding.tvInviteCode.text = code
        }

        viewModel.lastScannedAt.observe(viewLifecycleOwner) { timestamp ->
            if (timestamp != null) {
                binding.lastScannedLayout.visibility = View.VISIBLE
                binding.tvLastScannedAt.text = timestamp
            } else {
                binding.lastScannedLayout.visibility = View.GONE
            }
        }

        viewModel.members.observe(viewLifecycleOwner) { members ->
            binding.tvMembersCount.text = getString(R.string.members_count, members.size)
            binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
            binding.rvMembers.adapter = MemberAdapter(members)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), it)
                ToastHelper.showToast(requireContext(), userFriendly, Toast.LENGTH_LONG)
                viewModel.clearError()
            }
        }

        viewModel.actionSuccess.observe(viewLifecycleOwner) { message ->
            message?.let {
                ToastHelper.showToast(requireContext(), it)
                viewModel.clearActionSuccess()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnLeaveFridge.isEnabled = !loading
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            binding.scanProgressLayout.visibility = if (scanning) View.VISIBLE else View.GONE
            binding.btnUploadFridgePhoto.isEnabled = !scanning
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { items ->
            if (items != null && items.isNotEmpty()) {
                binding.scanResultsLayout.visibility = View.VISIBLE
                binding.tvScanResultTitle.text = getString(R.string.detected_items, items.size)
                binding.rvScanResults.layoutManager = LinearLayoutManager(requireContext())
                binding.rvScanResults.adapter = DetectedItemAdapter(items)
            } else {
                binding.scanResultsLayout.visibility = View.GONE
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

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnUploadFridgePhoto.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnCopyCode.setOnClickListener {
            copyInviteCode(viewModel.inviteCode.value ?: "")
        }

        binding.btnLeaveFridge.setOnClickListener {
            showLeaveFridgeDialog()
        }
    }

    private fun copyInviteCode(code: String) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", code))
        ToastHelper.showToast(requireContext(), getString(R.string.code_copied))
    }

    private fun showLeaveFridgeDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogLeaveFridgeBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val fridgeName = viewModel.fridgeName.value ?: getString(R.string.shared_fridge)
        dialogBinding.tvMessage.text = getString(R.string.leave_fridge_confirmation, fridgeName)

        dialogBinding.btnConfirmLeave.setOnClickListener {
            viewModel.leaveFridge()
            // Clear fridge items state immediately
            fridgeViewModel.loadItems()
            dialog.dismiss()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

    private fun clearRecipeCache() {
        viewModel.clearRecipeCache()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
