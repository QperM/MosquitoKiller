package com.example.mosquitokiller

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.experimental.and

class ImageProcessor {

    // You can adjust this threshold to change sensitivity. Lower value means only darker spots are highlighted.
    private val DARKNESS_THRESHOLD = 80

    fun processImage(image: ImageProxy, rotationDegrees: Int): Bitmap {
        // 1. Convert YUV to a base Bitmap
        val originalBitmap = yuvToBitmap(image)

        // 2. Create the blue-tinted grayscale background
        val backgroundBitmap = createBlueBackground(originalBitmap)

        // 3. Create the highlight layer for mosquitoes
        val highlightBitmap = createHighlightLayer(originalBitmap)

        // 4. Combine the background and highlight layers
        val combinedBitmap = combineLayers(backgroundBitmap, highlightBitmap)

        // 5. Rotate the final image for correct orientation
        return rotateBitmap(combinedBitmap, rotationDegrees)
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun createBlueBackground(originalBitmap: Bitmap): Bitmap {
        val background = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(background)
        val paint = Paint()
        val colorMatrix = ColorMatrix(floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, 0f,    // Red
            0.33f, 0.59f, 0.11f, 0f, 0f,    // Green
            0.33f, 0.59f, 0.11f, 0f, 100f,  // Blue
            0f, 0f, 0f, 1f, 0f             // Alpha
        ))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        return background
    }

    private fun createHighlightLayer(originalBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val highlight = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val originalColor = pixels[i]
            // Calculate grayscale value (luminance)
            val grayValue = (0.299 * Color.red(originalColor) + 0.587 * Color.green(originalColor) + 0.114 * Color.blue(originalColor)).toInt()

            if (grayValue < DARKNESS_THRESHOLD) {
                // This pixel is a potential mosquito part. Highlight it.
                val confidence = (DARKNESS_THRESHOLD - grayValue) / DARKNESS_THRESHOLD.toFloat()

                // Interpolate between Yellow (confidence=0) and Red (confidence=1)
                val red = 255
                val green = (255 * (1 - confidence)).toInt()
                val blue = 0
                pixels[i] = Color.argb(255, red, green, blue)
            } else {
                // This pixel is part of the background, make it transparent.
                pixels[i] = Color.TRANSPARENT
            }
        }

        highlight.setPixels(pixels, 0, width, 0, 0, width, height)
        return highlight
    }

    private fun combineLayers(background: Bitmap, highlight: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(background, 0f, 0f, null)
        canvas.drawBitmap(highlight, 0f, 0f, null)
        return result
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}