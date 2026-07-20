package com.project.fridgemate.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentMyProfileBinding
import com.squareup.picasso.Picasso
import java.io.ByteArrayOutputStream

class MyProfileFragment : Fragment() {

    private var _binding: FragmentMyProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var allergyAdapter: AllergyAdapter
    private val profileViewModel: ProfileViewModel by activityViewModels()

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            binding.profilePic.setImageURI(uri)
            try {
                val cr = requireContext().contentResolver
                val mimeType = cr.getType(uri) ?: "image/jpeg"
                cr.openInputStream(uri)?.use { stream ->
                    profileViewModel.uploadProfileImage(stream.readBytes(), mimeType)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) pickImageLauncher.launch("image/*")
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            bitmap ?: return@registerForActivityResult
            binding.profilePic.setImageBitmap(bitmap)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            profileViewModel.uploadProfileImage(stream.toByteArray(), "image/jpeg")
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) takePictureLauncher.launch(null)
            else Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) fetchDeviceLocation()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupAllergies()
        setupClickListeners()
        observeViewModel()
        profileViewModel.loadProfile()
    }

    override fun onResume() {
        super.onResume()
        requestLocationUpdate()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun requestLocationUpdate() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchDeviceLocation()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    profileViewModel.updateLocation(it.latitude, it.longitude)
                }
                stopLocationUpdates()
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun setupAllergies() {
        allergyAdapter = AllergyAdapter(
            profileViewModel.allergies.value ?: emptyList()
        ) { name, isChecked ->
            profileViewModel.toggleAllergy(name, isChecked)
        }
        binding.rvAllergies.apply {
            adapter = allergyAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnChangePhoto.setOnClickListener { showImageSourceDialog() }
        binding.btnSaveChanges.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            if (fullName.isEmpty()) {
                binding.etFullName.error = "Name cannot be empty"
                return@setOnClickListener
            }
            val allergies = allergyAdapter.getSelectedAllergies()
            val bio = binding.etBio.text.toString().trim()
            profileViewModel.saveProfile(fullName, allergies, bio)
        }
    }

    private fun observeViewModel() {
        profileViewModel.user.observe(viewLifecycleOwner) { user ->
            user ?: return@observe
            binding.etFullName.setText(user.displayName)
            binding.tvEmail.text = user.email ?: ""
            // Only pre-fill bio when empty to not stomp the user's typing on refresh
            if (binding.etBio.text.isNullOrEmpty()) {
                binding.etBio.setText(user.bio ?: "")
            }
            allergyAdapter.setSelectedAllergies(user.allergies)
        }

        profileViewModel.profileImageUrl.observe(viewLifecycleOwner) { url ->
            loadProfileImage(url)
        }

        profileViewModel.locationDisplay.observe(viewLifecycleOwner) { location ->
            binding.tvLocation.text = location.ifEmpty { "Detecting location…" }
        }

        profileViewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSaveChanges.isEnabled = !isLoading
            binding.btnChangePhoto.isEnabled = !isLoading
        }

        profileViewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                profileViewModel.clearSaveSuccess()
                findNavController().navigateUp()
            }
        }

        profileViewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                profileViewModel.clearError()
            }
        }
    }

    private fun loadProfileImage(url: String?) {
        if (url.isNullOrEmpty()) return
        val fullUrl = if (url.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + url else url
        Picasso.get()
            .load(fullUrl)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .into(binding.profilePic)
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Choose image source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission.launch(Manifest.permission.CAMERA)
                    1 -> requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
        _binding = null
    }
}
