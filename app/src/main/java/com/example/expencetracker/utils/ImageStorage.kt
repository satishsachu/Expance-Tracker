package com.example.expencetracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageStorage {
    fun saveImageToInternalStorage(context: Context, bitmap: Bitmap): Uri? {
        return try {
            // Create a unique filename using timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "receipt_$timestamp.jpg"
            
            // Get the app's private directory
            val directory = context.filesDir
            val file = File(directory, filename)
            
            // Save the bitmap to the file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Return the URI for the saved file
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getImageFromInternalStorage(context: Context, filename: String): Bitmap? {
        return try {
            val file = File(context.filesDir, filename)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteImageFromInternalStorage(context: Context, filename: String): Boolean {
        return try {
            val file = File(context.filesDir, filename)
            file.exists() && file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
} 