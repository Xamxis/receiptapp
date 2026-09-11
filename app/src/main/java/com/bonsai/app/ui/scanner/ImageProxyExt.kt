package com.bonsai.app.ui.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Wandelt ein CameraX ImageProxy (JPEG) in ein korrekt rotiertes Bitmap um,
 * damit ML Kit den Beleg unabhängig von der Gerätehaltung sauber liest.
 */
fun ImageProxy.toRotatedBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val rotationDegrees = imageInfo.rotationDegrees
    if (rotationDegrees == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
