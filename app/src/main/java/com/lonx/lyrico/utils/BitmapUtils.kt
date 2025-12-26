package com.lonx.lyrico.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object BitmapUtils {

    /**
     * Decodes a bitmap from a file path, down-sampling it to the requested width and height.
     * This is more memory-efficient than loading the full-resolution image.
     */
    fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                // First decode with inJustDecodeBounds=true to check dimensions
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            // Log the error or handle it as needed
            null
        }
    }

    /**
     * Calculates the inSampleSize value to down-sample the image to a size
     * that is as close as possible to the requested width and height.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
