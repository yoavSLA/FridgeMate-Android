package com.project.fridgemate.ui.feed

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentAddPostBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

class AddPostFragment : Fragment() {

    private var _binding: FragmentAddPostBinding? = null
    private val binding get() = _binding!!
    private val feedViewModel: FeedViewModel by activityViewModels()
    private val args: AddPostFragmentArgs by navArgs()

    private var selectedImageBytes: ByteArray? = null
    private var selectedMimeType: String = "image/jpeg"

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                binding.ivPostImage.setImageURI(it)
                binding.layoutAddImage.visibility = View.GONE
                extractImageBytes(it)
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                binding.ivPostImage.setImageBitmap(it)
                binding.layoutAddImage.visibility = View.GONE
                val baos = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                selectedImageBytes = baos.toByteArray()
                selectedMimeType = "image/jpeg"
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) takePictureLauncher.launch(null)
            else Toast.makeText(context, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }

    private val requestGalleryPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) pickImageLauncher.launch("image/*")
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                submitPost()
            } else {
                Toast.makeText(context, "Location permission is required to post with location", Toast.LENGTH_SHORT).show()
                submitPost() // Still submit without location if denied? Or maybe just submit.
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAddImage.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnPost.setOnClickListener {
            submitPost()
        }

        if (args.prefillTitle.isNotEmpty()) {
            binding.etRecipeTitle.setText(args.prefillTitle)
        }
        if (args.prefillDescription.isNotEmpty()) {
            binding.etDescription.setText(args.prefillDescription)
            binding.etDescription.setSelection(binding.etDescription.text?.length ?: 0)
        }

        if (args.prefillRecipeId.isNotEmpty() && args.prefillRecipeName.isNotEmpty()) {
            binding.cardRecipePreview.visibility = View.VISIBLE
            binding.tvRecipePreviewTitle.text = args.prefillRecipeName
            val info = buildString {
                if (args.prefillRecipeTime.isNotEmpty()) append(args.prefillRecipeTime)
                if (args.prefillRecipeDifficulty.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(args.prefillRecipeDifficulty)
                }
            }
            binding.tvRecipePreviewInfo.text = info
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
                    1 -> requestGalleryPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            .show()
    }

    private fun submitPost() {
        val title = binding.etRecipeTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (title.isEmpty()) {
            binding.etRecipeTitle.error = getString(R.string.error_enter_title)
            return
        }
        if (description.isEmpty()) {
            binding.etDescription.error = getString(R.string.error_enter_description)
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }

        binding.btnPost.isEnabled = false
        binding.loadingOverlay.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            var imageUrl: String? = null
            val bytes = selectedImageBytes
            if (bytes != null) {
                imageUrl = feedViewModel.uploadImage(bytes, selectedMimeType)
                if (imageUrl == null) {
                    binding.btnPost.isEnabled = true
                    binding.loadingOverlay.visibility = View.GONE
                    return@launch
                }
            }

            var lat: Double? = null
            var lng: Double? = null
            var shortAddress: String? = null

            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
                
                val priority = if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    Priority.PRIORITY_HIGH_ACCURACY
                else
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                
                val location = fusedLocationClient.getCurrentLocation(
                    priority,
                    CancellationTokenSource().token
                ).await()

                if (location != null) {
                    lat = location.latitude
                    lng = location.longitude
                    shortAddress = getShortAddress(location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                // Silently continue without location
            }

            val recipeId = args.prefillRecipeId.ifEmpty { null }
            feedViewModel.addPost(title, description, imageUrl, recipeId, lat, lng, shortAddress)
            binding.loadingOverlay.visibility = View.GONE
            findNavController().navigateUp()
        }
    }

    private suspend fun getShortAddress(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.ENGLISH)
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: address.adminArea
                val country = address.countryCode ?: address.countryName
                if (city != null && country != null) {
                    "$city, $country"
                } else {
                    city ?: country
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractImageBytes(uri: Uri) {
        try {
            val contentResolver = requireContext().contentResolver
            selectedMimeType = contentResolver.getType(uri) ?: "image/jpeg"
            contentResolver.openInputStream(uri)?.use { stream ->
                selectedImageBytes = stream.readBytes()
            }
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.error_read_image), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
