package com.isroot.lmbridge.download

import android.content.Context
import com.isroot.lmbridge.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Model download manager for downloading LLM models from HuggingFace.
 *
 * This manager provides download functionality with progress tracking.
 * Models are downloaded to the app's external files directory.
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val DEFAULT_BUFFER_SIZE = 1024
    }

    /**
     * Download status for tracking download progress.
     */
    sealed class DownloadStatus {
        data object NotStarted : DownloadStatus()
        data class Downloading(
            val totalBytes: Long,
            val receivedBytes: Long,
            val progressPercent: Int,
        ) : DownloadStatus()
        data class Failed(val message: String) : DownloadStatus()
        data class Completed(val filePath: String) : DownloadStatus()
    }

    /**
     * Model information for download.
     *
     * @property modelId HuggingFace model ID (e.g., "litert-community/gemma-4-E2B-it-litert-lm")
     * @property modelFile File name to download (e.g., "gemma-4-E2B-it.litertlm")
     * @property commitHash Git commit hash for specific version
     * @property sizeInBytes Total size in bytes (for progress calculation)
     */
    data class ModelInfo(
        val modelId: String,
        val modelFile: String,
        val commitHash: String,
        val sizeInBytes: Long = 0L,
    ) {
        /**
         * Generate HuggingFace download URL.
         */
        fun toDownloadUrl(): String {
            return "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"
        }

        /**
         * Generate a unique directory name for this model.
         */
        fun toDirName(): String {
            return "${modelId.replace("/", "_")}_${commitHash.take(8)}"
        }
    }

    /**
     * Download a model from HuggingFace with progress tracking.
     *
     * @param modelInfo Model information (ID, file, commit hash)
     * @param accessToken Optional HuggingFace access token for private models
     * @return Flow of DownloadStatus to track progress
     */
    fun downloadModel(
        modelInfo: ModelInfo,
        accessToken: String? = null,
    ): Flow<DownloadStatus> = flow {
        Logger.d(TAG, "Starting download: ${modelInfo.modelId}")
        emit(DownloadStatus.NotStarted)

        val downloadUrl = modelInfo.toDownloadUrl()
        val dirName = modelInfo.toDirName()
        Logger.d(TAG, "Download URL: $downloadUrl")

        try {
            val url = URL(downloadUrl)
            Logger.d(TAG, "Opening connection...")
            val connection = url.openConnection() as HttpURLConnection

            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept-Encoding", "identity")

            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }

            Logger.d(TAG, "Connecting to server...")
            connection.connect()

            val responseCode = connection.responseCode
            Logger.d(TAG, "HTTP response: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                throw Exception("HTTP error: $responseCode")
            }

            // Get total size
            val totalBytes = if (modelInfo.sizeInBytes > 0) {
                modelInfo.sizeInBytes
            } else {
                connection.contentLength.toLong().coerceAtLeast(0L)
            }

            // Prepare output directory
            val outputDir = File(context.getExternalFilesDir(null), dirName)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Check for partial download
            val outputFile = File(outputDir, modelInfo.modelFile)
            var downloadedBytes = outputFile.length()

            val supportsResume = downloadedBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL

            if (supportsResume) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                Logger.d(TAG, "Resuming download from byte $downloadedBytes")
            } else if (downloadedBytes > 0) {
                Logger.d(TAG, "Previous download incomplete, restarting from beginning")
                outputFile.delete()
                downloadedBytes = 0
            }

            Logger.d(TAG, "Total size: $totalBytes bytes")
            val inputStream = connection.inputStream
            Logger.d(TAG, "Starting download to: ${outputFile.absolutePath}")
            val outputStream = FileOutputStream(outputFile, downloadedBytes > 0)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val progress = if (totalBytes > 0) {
                    ((downloadedBytes * 100) / totalBytes).toInt()
                } else {
                    0
                }

                Logger.v(TAG, "Progress: $progress% ($downloadedBytes / $totalBytes)")
                emit(
                    DownloadStatus.Downloading(
                        totalBytes = totalBytes,
                        receivedBytes = downloadedBytes,
                        progressPercent = progress,
                    ),
                )
            }

            Logger.d(TAG, "Download complete, closing streams")
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            val actualSize = outputFile.length()
            Logger.d(TAG, "Download completed: ${outputFile.absolutePath}, size: $actualSize bytes")

            if (totalBytes > 0 && actualSize != totalBytes) {
                Logger.w(TAG, "File size mismatch: expected $totalBytes, got $actualSize")
                outputFile.delete()
                throw Exception("Downloaded file size mismatch: expected $totalBytes, got $actualSize")
            }

            emit(DownloadStatus.Completed(outputFile.absolutePath))
        } catch (e: Exception) {
            Logger.e(TAG, "Download failed", e)
            emit(DownloadStatus.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Check if a model is already downloaded.
     *
     * @param modelInfo Model information
     * @return true if model file exists
     */
    fun isModelDownloaded(modelInfo: ModelInfo): Boolean {
        val dirName = modelInfo.toDirName()
        val outputDir = File(context.getExternalFilesDir(null), dirName)
        val outputFile = File(outputDir, modelInfo.modelFile)
        return outputFile.exists()
    }

    /**
     * Get the local path of a downloaded model.
     *
     * @param modelInfo Model information
     * @return File path if downloaded, null otherwise
     */
    fun getModelPath(modelInfo: ModelInfo): String? {
        val dirName = modelInfo.toDirName()
        val outputDir = File(context.getExternalFilesDir(null), dirName)
        val outputFile = File(outputDir, modelInfo.modelFile)
        return if (outputFile.exists()) outputFile.absolutePath else null
    }

    /**
     * Delete a downloaded model.
     *
     * @param modelInfo Model information
     * @return true if deleted successfully
     */
    fun deleteModel(modelInfo: ModelInfo): Boolean {
        return try {
            val dirName = modelInfo.toDirName()
            val outputDir = File(context.getExternalFilesDir(null), dirName)
            outputDir.deleteRecursively()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete model", e)
            false
        }
    }

    /**
     * Get available disk space for model downloads.
     *
     * @return Available bytes
     */
    fun getAvailableSpace(): Long {
        val externalFilesDir = context.getExternalFilesDir(null)
        return externalFilesDir?.freeSpace ?: 0L
    }
}
