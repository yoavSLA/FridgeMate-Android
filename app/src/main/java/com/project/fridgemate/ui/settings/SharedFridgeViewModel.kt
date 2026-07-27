package com.project.fridgemate.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.switchMap
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.data.remote.dto.DetectedItemDto
import com.project.fridgemate.data.remote.dto.ScanChangesDto
import com.project.fridgemate.data.remote.dto.FridgeMemberDetailDto
import com.project.fridgemate.data.remote.dto.ScanDto
import com.project.fridgemate.data.repository.FridgeRepository
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.R
import com.project.fridgemate.workers.ScanUploadWorker
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class SharedFridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FridgeRepository(application.applicationContext)
    private val workManager = WorkManager.getInstance(application.applicationContext)
    private val gson = Gson()

    private val _hasFridge = MutableLiveData<Boolean?>(null)
    val hasFridge: LiveData<Boolean?> = _hasFridge

    private val _fridgeName = MutableLiveData<String>()
    val fridgeName: LiveData<String> = _fridgeName

    private val _inviteCode = MutableLiveData<String>()
    val inviteCode: LiveData<String> = _inviteCode

    private val _lastScannedAt = MutableLiveData<String?>(null)
    val lastScannedAt: LiveData<String?> = _lastScannedAt

    private val _members = MutableLiveData<List<FridgeMemberDetailDto>>(emptyList())
    val members: LiveData<List<FridgeMemberDetailDto>> = _members

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _actionSuccess = MutableLiveData<String?>()
    val actionSuccess: LiveData<String?> = _actionSuccess

    fun loadFridge() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.getMyFridge()) {
                is FridgeResult.Success -> {
                    _hasFridge.value = true
                    _fridgeName.value = result.data.name
                    _inviteCode.value = result.data.inviteCode
                    _lastScannedAt.value = result.data.lastScannedAt
                    loadMembers()
                }
                is FridgeResult.NoFridge -> {
                    _hasFridge.value = false
                    _isLoading.value = false
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun loadMembers() {
        when (val result = repository.getMembers()) {
            is FridgeResult.Success -> _members.value = result.data
            is FridgeResult.Error -> _error.value = result.message
            is FridgeResult.NoFridge -> {}
        }
        _isLoading.value = false
    }

    fun createFridge(name: String) {
        if (name.isBlank()) {
            _error.value = getApplication<Application>().getString(R.string.error_enter_fridge_name)
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.createFridge(name)) {
                is FridgeResult.Success -> {
                    loadFridge()
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _isLoading.value = false
                }
                is FridgeResult.NoFridge -> {}
            }
        }
    }

    fun joinFridge(inviteCode: String) {
        if (inviteCode.isBlank()) {
            _error.value = getApplication<Application>().getString(R.string.error_enter_invite_code)
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.joinFridge(inviteCode)) {
                is FridgeResult.Success -> {
                    loadFridge()
                }
                is FridgeResult.Error -> {
                    if (result.message.contains("already member", ignoreCase = true) || 
                        result.message.contains("already joined", ignoreCase = true)) {
                        loadFridge()
                    } else {
                        _error.value = result.message
                        _isLoading.value = false
                    }
                }
                is FridgeResult.NoFridge -> {}
            }
        }
    }

    fun leaveFridge() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.leaveFridge()) {
                is FridgeResult.Success -> {
                    _hasFridge.value = false
                    _members.value = emptyList()
                    _isLoading.value = false
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _isLoading.value = false
                }
                is FridgeResult.NoFridge -> {}
            }
        }
    }

    // ── Scan ──────────────────────────────────────────────────────────────

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _scanResult = MutableLiveData<List<DetectedItemDto>?>()
    val scanResult: LiveData<List<DetectedItemDto>?> = _scanResult

    private val _scanSummary = MutableLiveData<ScanChangesDto?>(null)
    val scanSummary: LiveData<ScanChangesDto?> = _scanSummary

    private val _activeScanId = MutableLiveData<UUID?>(null)
    val scanWorkInfo: LiveData<WorkInfo?> = _activeScanId.switchMap { id ->
        if (id == null) MutableLiveData(null)
        else workManager.getWorkInfoByIdLiveData(id)
    }

    fun uploadFridgeScan(imageBytes: ByteArray, mimeType: String) {
        _isScanning.value = true
        _scanResult.value = null
        _scanSummary.value = null

        // Cancel previous scan if any
        _activeScanId.value?.let { workManager.cancelWorkById(it) }

        viewModelScope.launch {
            val file = saveImageToTempFile(imageBytes) ?: run {
                _error.value = "Failed to prepare image for upload"
                _isScanning.value = false
                return@launch
            }

            val inputData = Data.Builder()
                .putString(ScanUploadWorker.KEY_IMAGE_PATH, file.absolutePath)
                .putString(ScanUploadWorker.KEY_MIME_TYPE, mimeType)
                .build()

            val uploadRequest = OneTimeWorkRequestBuilder<ScanUploadWorker>()
                .addTag("fridge_scan")
                .setInputData(inputData)
                .build()

            _activeScanId.value = uploadRequest.id
            workManager.enqueue(uploadRequest)
        }
    }

    private fun saveImageToTempFile(bytes: ByteArray): File? {
        return try {
            val tempFile = File(getApplication<Application>().cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { it.write(bytes) }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    fun handleScanWorkInfo(workInfo: WorkInfo) {
        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                val resultJson = workInfo.outputData.getString(ScanUploadWorker.KEY_SCAN_RESULT)
                if (resultJson != null) {
                    val scan = gson.fromJson(resultJson, ScanDto::class.java)
                    _scanResult.value = scan.detectedItems
                    _scanSummary.value = scan.changes
                    _lastScannedAt.value = scan.createdAt
                }
                _isScanning.value = false
                _activeScanId.value = null
            }
            WorkInfo.State.FAILED -> {
                _error.value = workInfo.outputData.getString(ScanUploadWorker.KEY_ERROR) ?: "Scan failed"
                _isScanning.value = false
                _activeScanId.value = null
            }
            WorkInfo.State.CANCELLED -> {
                _isScanning.value = false
                _activeScanId.value = null
            }
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                _isScanning.value = true
            }
            else -> {}
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
        _scanSummary.value = null
    }

    fun clearRecipeCache() {
        viewModelScope.launch {
            val dao = AppDatabase.getInstance(getApplication<Application>()).recipeDao()
            dao.deleteByType(RecipeEntity.TYPE_RECOMMENDED)
        }
    }

    fun clearError() { _error.value = null }
    fun clearActionSuccess() { _actionSuccess.value = null }
}
