package com.isroot.lmbridge.models

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * [ImageEncoding] 정책에 따라 비트맵을 바이트로 인코딩한다.
 *
 * 기본값(무손실 PNG, 다운스케일 없음)은 과거 동작과 동일하다. [ImageEncoding.maxDimension]이
 * 지정되고 긴 변이 이를 초과할 때만 비율을 유지하며 축소한다(작은 이미지는 그대로).
 *
 * 어댑터에서 분리된 순수 로직으로, 계측 테스트가 가능하다(엔진/모델 불필요).
 */
internal object ImageEncoder {
    fun encode(bitmap: Bitmap, encoding: ImageEncoding): ByteArray {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (encoding.maxDimension in 1 until longest) {
            val ratio = encoding.maxDimension.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val format = when (encoding.format) {
            ImageEncoding.Format.PNG -> Bitmap.CompressFormat.PNG
            ImageEncoding.Format.JPEG -> Bitmap.CompressFormat.JPEG
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(format, encoding.quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        return stream.toByteArray()
    }
}
