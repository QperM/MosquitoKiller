package com.example.mosquitokiller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

// FINAL, STABLE, AND CORRECT IMPLEMENTATION - "BACK TO BASICS V2"
class ImageProcessor(context: Context) { // Context is no longer used, kept for constructor consistency.

    // --- PARAMETERS FOR THE STABLE ALGORITHM ---
    private val ADAPTIVE_BLOCK_SIZE_RATIO = 12
    private val ADAPTIVE_THRESHOLD_CONSTANT = 9

    fun processImage(image: ImageProxy, rotationDegrees: Int): Bitmap {
        val originalBitmap = yuvToBitmap(image)

        // The algorithm is now efficient enough to run on the full-resolution bitmap, 
        // which completely eliminates any downsampling artifacts like blurriness or jaggies.
        val finalBitmap = createBlueTintedGrayscaleBackground(originalBitmap)

        findAndOverlaySharpHighlights(finalBitmap, originalBitmap)

        return rotateBitmap(finalBitmap, rotationDegrees)
    }

    // This function now modifies the input bitmap directly to save memory.
    private fun findAndOverlaySharpHighlights(baseBitmap: Bitmap, originalBitmap: Bitmap) {
        val width = baseBitmap.width
        val height = baseBitmap.height
        val pixels = IntArray(width * height)
        baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val originalGrayPixels = IntArray(width * height)
        val tempOriginalPixels = IntArray(width * height)
        originalBitmap.getPixels(tempOriginalPixels, 0, width, 0, 0, width, height)
        for(i in originalGrayPixels.indices) {
            val c = tempOriginalPixels[i]
            originalGrayPixels[i] = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }

        val integralImage = IntArray(width * height)
        computeIntegralImage(originalGrayPixels, integralImage, width, height)

        val blockSize = width / ADAPTIVE_BLOCK_SIZE_RATIO
        val halfBlock = blockSize / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val x1 = (x - halfBlock).coerceIn(0, width - 1)
                val y1 = (y - halfBlock).coerceIn(0, height - 1)
                val x2 = (x + halfBlock).coerceIn(0, width - 1)
                val y2 = (y + halfBlock).coerceIn(0, height - 1)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integralImage[y2 * width + x2] - integralImage[y1 * width + x2] - integralImage[y2 * width + x1] + integralImage[y1 * width + x1]
                val localAverage = sum / count

                if (originalGrayPixels[i] < localAverage - ADAPTIVE_THRESHOLD_CONSTANT) {
                    val diff = (localAverage - originalGrayPixels[i]).toFloat()
                    pixels[i] = getHighContrastColor(diff)
                }
            }
        }
        baseBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    // CORRECTED High-Contrast 3-Color Gradient (Blue -> Red -> Yellow)
    private fun getHighContrastColor(difference: Float): Int {
        val confidence = (difference / 80f).coerceIn(0f, 1f)
        var r = 0; var g = 0; var b = 0
        when {
            confidence < 0.5f -> { // Blue -> Red
                val t = confidence / 0.5f
                r = (255 * t).toInt()
                b = 255 - (255 * t).toInt()
            }
            else -> { // Red -> Yellow
                val t = (confidence - 0.5f) / 0.5f
                r = 255
                g = (255 * t).toInt()
            }
        }
        return Color.rgb(r, g, b)
    }

    // --- Helper Functions ---
    private fun createBlueTintedGrayscaleBackground(originalBitmap: Bitmap): Bitmap {
        val background = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(background)
        val paint = Paint()
        val colorMatrix = ColorMatrix(floatArrayOf(0.33f, 0.59f, 0.11f, 0f, 0f, 0.33f, 0.59f, 0.11f, 0f, 0f, 0.33f, 0.59f, 0.11f, 0f, 80f, 0f, 0f, 0f, 1f, 0f))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(background, 0f, 0f, paint)
        return background
    }

    private fun computeIntegralImage(grayPixels: IntArray, integralImage: IntArray, width: Int, height: Int) { for (y in 0 until height) { var rowSum = 0; for (x in 0 until width) { rowSum += grayPixels[y * width + x]; if (y == 0) { integralImage[y * width + x] = rowSum } else { integralImage[y * width + x] = rowSum + integralImage[(y - 1) * width + x] } } } }
    private fun yuvToBitmap(image: ImageProxy): Bitmap { val yBuffer = image.planes[0].buffer; val uBuffer = image.planes[1].buffer; val vBuffer = image.planes[2].buffer; val ySize = yBuffer.remaining(); val uSize = uBuffer.remaining(); val vSize = vBuffer.remaining(); val nv21 = ByteArray(ySize + uSize + vSize); yBuffer.get(nv21, 0, ySize); vBuffer.get(nv21, ySize, vSize); uBuffer.get(nv21, ySize + vSize, uSize); val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null); val out = ByteArrayOutputStream(); yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 90, out); val imageBytes = out.toByteArray(); return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) }
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap { if (rotationDegrees == 0) return bitmap; val matrix = Matrix(); matrix.postRotate(rotationDegrees.toFloat()); return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) }
}