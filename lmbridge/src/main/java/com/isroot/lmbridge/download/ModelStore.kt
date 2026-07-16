package com.isroot.lmbridge.download

import android.content.Context
import com.isroot.lmbridge.Logger
import java.io.File
import java.security.MessageDigest

/**
 * 모델 파일 저장소. **경로 규칙을 단일하게 소유**하여 다운로드·초기화·삭제가
 * 서로 다른 위치를 참조하던 문제(저장 위치 이원화)를 해결한다.
 *
 * 저장 위치는 `getExternalFilesDir` 우선, null이면 `filesDir`로 폴백한다.
 */
class ModelStore(private val context: Context) {

    private companion object {
        const val TAG = "ModelStore"
        const val PART_SUFFIX = ".part"
        const val HASH_BUFFER = 8 * 1024
    }

    /** 모델 저장 루트(외부 우선, 없으면 내부). */
    private fun root(): File = context.getExternalFilesDir(null) ?: context.filesDir

    /** 모델 전용 디렉터리. */
    fun dirFor(model: ModelDownloadManager.ModelInfo): File =
        File(root(), model.toDirName()).apply { if (!exists()) mkdirs() }

    /** 최종 모델 파일. */
    fun fileFor(model: ModelDownloadManager.ModelInfo): File =
        File(dirFor(model), model.modelFile)

    /** 다운로드 임시 파일(.part) — 검증 성공 후 원자적으로 [fileFor]로 rename. */
    fun tempFileFor(model: ModelDownloadManager.ModelInfo): File =
        File(dirFor(model), model.modelFile + PART_SUFFIX)

    fun isPresent(model: ModelDownloadManager.ModelInfo): Boolean = fileFor(model).exists()

    fun pathIfPresent(model: ModelDownloadManager.ModelInfo): String? =
        fileFor(model).takeIf { it.exists() }?.absolutePath

    fun availableSpace(): Long = root().freeSpace

    fun delete(model: ModelDownloadManager.ModelInfo): Boolean = try {
        dirFor(model).deleteRecursively()
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to delete model ${model.modelId}", e)
        false
    }

    /** 파일의 SHA-256을 스트리밍 계산하여 소문자 hex로 반환. */
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(HASH_BUFFER)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
