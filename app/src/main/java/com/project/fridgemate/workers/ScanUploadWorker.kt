package com.project.fridgemate.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.project.fridgemate.data.local.ScanSummaryStorage
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.ScanRepository
import java.io.File

class ScanUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val scanRepository = ScanRepository()
    private val scanSummaryStorage = ScanSummaryStorage(context)
    private val gson = Gson()

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_IMAGE_PATH) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "image/jpeg"
        
        val file = File(filePath)
        if (!file.exists()) return Result.failure()
        
        val bytes = file.readBytes()
        
        return when (val result = scanRepository.uploadScan(bytes, mimeType)) {
            is FridgeResult.Success -> {
                val scan = result.data
                if (scan.status == "completed") {
                    // Save summary locally for notification popup
                    scan.changes?.let { changes ->
                        scanSummaryStorage.saveLastScanSummary(changes, scan.createdAt)
                    }
                    
                    val outputData = workDataOf(
                        KEY_SCAN_RESULT to gson.toJson(scan)
                    )
                    Result.success(outputData)
                } else {
                    Result.failure(workDataOf(KEY_ERROR to (scan.error ?: "Scan failed")))
                }
            }
            is FridgeResult.Error -> {
                Result.failure(workDataOf(KEY_ERROR to result.message))
            }
            is FridgeResult.NoFridge -> {
                Result.failure(workDataOf(KEY_ERROR to "No active fridge"))
            }
        }
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_SCAN_RESULT = "scan_result"
        const val KEY_ERROR = "error"
    }
}
