package com.example.expencetracker.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OCRProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): OCRResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    val amount = extractAmount(extractedText)
                    val description = extractDescription(extractedText)
                    val category = extractCategory(extractedText)
                    
                    continuation.resume(OCRResult(
                        amount = amount,
                        description = description,
                        category = category,
                        rawText = extractedText
                    ))
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    private fun extractAmount(text: String): Double? {
        // Look for patterns like:
        // Total: $XX.XX
        // Amount: XX.XX
        // $XX.XX
        // XX.XX
        val amountPatterns = listOf(
            """(?i)total:?\s*[$]?(\d+[.,]\d{2})""".toRegex(),
            """(?i)amount:?\s*[$]?(\d+[.,]\d{2})""".toRegex(),
            """[$]?(\d+[.,]\d{2})""".toRegex()
        )

        for (pattern in amountPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val amountStr = match.groupValues.lastOrNull()
                return amountStr?.replace(",", ".")?.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractDescription(text: String): String {
        // Try to find the first line that might be a description
        val lines = text.split("\n")
        return lines.firstOrNull { line ->
            // Skip lines that look like amounts, dates, or common receipt headers
            !line.contains(Regex("""[$]?\d+[.,]\d{2}""")) &&
            !line.contains(Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""")) &&
            !line.contains(Regex("(?i)total|amount|subtotal|tax|receipt|invoice"))
        }?.trim() ?: "Receipt"
    }

    private fun extractCategory(text: String): String {
        // Try to identify common categories from the text
        val categoryKeywords = mapOf(
            "GROCERY" to listOf("grocery", "supermarket", "food", "market"),
            "RESTAURANT" to listOf("restaurant", "cafe", "dining", "food", "meal"),
            "SHOPPING" to listOf("store", "shop", "retail", "mall"),
            "TRANSPORT" to listOf("taxi", "uber", "lyft", "transport", "bus", "train"),
            "UTILITIES" to listOf("electric", "water", "gas", "utility", "bill")
        )

        val textLower = text.lowercase()
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { it in textLower }) {
                return category
            }
        }
        return "OTHER"
    }
}

data class OCRResult(
    val amount: Double?,
    val description: String,
    val category: String,
    val rawText: String
) 