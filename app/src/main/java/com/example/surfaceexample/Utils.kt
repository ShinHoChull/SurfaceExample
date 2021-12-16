package com.example.surfaceexample

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

class Utils {

    companion object {
        fun getBytes(imageUri : Uri , resolver: ContentResolver) : ByteArray {
            val stream : InputStream? = resolver.openInputStream(imageUri)
            val byteBuffer = ByteArrayOutputStream()
            val bufferSize = 1024
            val buffer  = ByteArray(bufferSize)

            var len = 0
            //inputStream 에서 읽어올게 없을 때까지 바이트 배열에 전송.
            if (stream != null) {
                while ( {len = stream.read(buffer); len}() != -1) {
                    byteBuffer.write(buffer , 0 , len)
                }
            }
            return byteBuffer.toByteArray()
        }

         fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height
            var resizedWidth = maxDimension
            var resizedHeight = maxDimension
            if (originalHeight > originalWidth) {
                resizedHeight = maxDimension
                resizedWidth =
                    (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
            } else if (originalWidth > originalHeight) {
                resizedWidth = maxDimension
                resizedHeight =
                    (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
            } else if (originalHeight == originalWidth) {
                resizedHeight = maxDimension
                resizedWidth = maxDimension
            }
            return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
        }

    }
}