package com.isroot.lmbridge.download

import android.content.Context
import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.models.LMBridgeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * HuggingFace에서 `.litertlm` 모델을 내려받는 다운로드 관리자.
 *
 * 이어받기(resume), 원자적 쓰기(.part → 최종), SHA-256 무결성 검증을 지원한다.
 * 저장 경로는 [ModelStore]가 소유한다.
 */
class ModelDownloadManager(
    private val context: Context,
    private val store: ModelStore = ModelStore(context),
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val BUFFER_SIZE = 64 * 1024
        private const val TIMEOUT_MS = 30_000
    }

    /** 다운로드 진행 상태. */
    sealed class DownloadStatus {
        data object NotStarted : DownloadStatus()
        data class Downloading(
            val totalBytes: Long,
            val receivedBytes: Long,
            val progressPercent: Int,
        ) : DownloadStatus()

        /** 무결성 검증(SHA-256) 진행 중. */
        data object Verifying : DownloadStatus()
        data class Failed(val message: String, val error: LMBridgeError? = null) : DownloadStatus()
        data class Completed(val filePath: String) : DownloadStatus()
    }

    /**
     * 다운로드 대상 모델 정보.
     *
     * @property modelId HuggingFace 모델 ID
     * @property modelFile 내려받을 파일명
     * @property commitHash 특정 버전 커밋 해시
     * @property sizeInBytes 전체 크기(진행률/검증용, 0이면 헤더에서 추정)
     * @property sha256 무결성 검증용 SHA-256(소문자 hex). null이면 크기 검증으로 폴백.
     */
    data class ModelInfo(
        val modelId: String,
        val modelFile: String,
        val commitHash: String,
        val sizeInBytes: Long = 0L,
        val sha256: String? = null,
    ) {
        fun toDownloadUrl(): String =
            "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"

        fun toDirName(): String = "${modelId.replace("/", "_")}_${commitHash.take(8)}"
    }

    /**
     * 모델을 진행률과 함께 내려받는다.
     *
     * 중단된 다운로드가 있으면 `.part`에서 이어받는다. 완료 후 SHA-256(또는 크기)을
     * 검증한 뒤에만 최종 파일로 승격한다.
     */
    fun downloadModel(
        modelInfo: ModelInfo,
        accessToken: String? = null,
    ): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.NotStarted)

        val finalFile = store.fileFor(modelInfo)
        if (finalFile.exists()) {
            emit(DownloadStatus.Completed(finalFile.absolutePath))
            return@flow
        }

        val tempFile = store.tempFileFor(modelInfo)
        try {
            val downloaded = downloadToTemp(modelInfo, tempFile, accessToken) { emit(it) }

            // 크기 검증
            if (modelInfo.sizeInBytes > 0 && downloaded != modelInfo.sizeInBytes) {
                tempFile.delete()
                emit(
                    DownloadStatus.Failed(
                        "Downloaded size mismatch: expected ${modelInfo.sizeInBytes}, got $downloaded",
                        LMBridgeError.IntegrityCheckFailed(
                            modelInfo.sizeInBytes.toString(), downloaded.toString(),
                        ),
                    ),
                )
                return@flow
            }

            // 무결성 검증(SHA-256)
            if (modelInfo.sha256 != null) {
                emit(DownloadStatus.Verifying)
                val actual = store.sha256Of(tempFile)
                if (!actual.equals(modelInfo.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    emit(
                        DownloadStatus.Failed(
                            "SHA-256 mismatch",
                            LMBridgeError.IntegrityCheckFailed(modelInfo.sha256, actual),
                        ),
                    )
                    return@flow
                }
            } else {
                Logger.w(TAG, "No sha256 for ${modelInfo.modelId}; verified by size only")
            }

            // 원자적 승격: .part → 최종
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            emit(DownloadStatus.Completed(finalFile.absolutePath))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 취소는 실패가 아님 — .part는 보존하여 다음에 이어받음
        } catch (e: Exception) {
            Logger.e(TAG, "Download failed", e)
            emit(DownloadStatus.Failed(e.message ?: "Unknown error", LMBridgeError.from(e)))
        }
    }.flowOn(Dispatchers.IO)

    /** `.part`로 (이어)받고, 누적 다운로드 바이트 수를 반환한다. */
    private suspend fun downloadToTemp(
        modelInfo: ModelInfo,
        tempFile: File,
        accessToken: String?,
        emit: suspend (DownloadStatus) -> Unit,
    ): Long {
        val have = if (tempFile.exists()) tempFile.length() else 0L

        var connection = openConnection(modelInfo.toDownloadUrl(), accessToken, rangeFrom = have)
        var responseCode = connection.responseCode

        // 416: 요청 범위 불충족 → .part 손상/무효로 보고 처음부터 재시도
        if (responseCode == 416) {
            connection.disconnect()
            tempFile.delete()
            connection = openConnection(modelInfo.toDownloadUrl(), accessToken, rangeFrom = 0)
            responseCode = connection.responseCode
        }

        if (responseCode != HttpURLConnection.HTTP_OK &&
            responseCode != HttpURLConnection.HTTP_PARTIAL
        ) {
            connection.disconnect()
            throw LMBridgeError.DownloadFailed("HTTP error: $responseCode")
        }

        val resuming = responseCode == HttpURLConnection.HTTP_PARTIAL && have > 0
        val startBytes = if (resuming) have else 0L
        val remaining = connection.contentLengthLong.coerceAtLeast(0L)
        val total = when {
            modelInfo.sizeInBytes > 0 -> modelInfo.sizeInBytes
            remaining > 0 -> startBytes + remaining
            else -> 0L
        }

        Logger.d(TAG, "Downloading ${modelInfo.modelFile}: resume=$resuming start=$startBytes total=$total")

        var downloaded = startBytes
        connection.inputStream.use { input ->
            FileOutputStream(tempFile, resuming).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    coroutineContext.ensureActive() // 취소 반영
                    output.write(buffer, 0, read)
                    downloaded += read
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    emit(DownloadStatus.Downloading(total, downloaded, percent))
                }
                output.flush()
            }
        }
        connection.disconnect()
        return downloaded
    }

    private fun openConnection(url: String, accessToken: String?, rangeFrom: Long): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
        // 이어받기: Range 헤더는 반드시 connect() 이전에 설정해야 한다(과거 버그: connect 이후 설정으로 무시됨).
        if (rangeFrom > 0) connection.setRequestProperty("Range", "bytes=$rangeFrom-")
        connection.connect()
        return connection
    }

    fun isModelDownloaded(modelInfo: ModelInfo): Boolean = store.isPresent(modelInfo)

    fun getModelPath(modelInfo: ModelInfo): String? = store.pathIfPresent(modelInfo)

    fun deleteModel(modelInfo: ModelInfo): Boolean = store.delete(modelInfo)

    fun getAvailableSpace(): Long = store.availableSpace()
}
