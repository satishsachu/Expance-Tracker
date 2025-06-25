package com.example.expencetracker.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChatGPTService {
    private val apiKey = Config.OPENAI_API_KEY
    private val apiUrl = "https://api.openai.com/v1/chat/completions"

    suspend fun processReceiptText(ocrText: String): ExpenseDetails = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a receipt analysis expert. Analyze this receipt text and extract the following information in JSON format.
                Be very precise and careful with the extraction:

                Extract these fields:
                1. amount (number, ₹)
                2. transactionType (string, either 'debit' or 'credit')
                3. dateTime (string, date and time of transaction if available)
                4. source (string, e.g., HDFC, GPay, etc.)
                5. merchant (string, store or merchant name if available)

                Rules for extraction:
                - For amount: Look for the total amount, usually at the bottom of the receipt. Ignore tax, subtotals, or item prices. Use the highest total if multiple found.
                - For transactionType: If the receipt is a payment, use 'debit'. If it is a refund or credit, use 'credit'.
                - For dateTime: Extract the date and time of the transaction if present.
                - For source: Extract the payment source (bank, app, etc.) if present.
                - For merchant: Extract the merchant/store name if present.

                Receipt text:
                $ocrText

                Respond with a JSON object in this exact format:
                {
                    "amount": number,
                    "transactionType": "string",
                    "dateTime": "string",
                    "source": "string",
                    "merchant": "string"
                }
                Only include the JSON object, no other text or explanation.
            """.trimIndent()

            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
                put("messages", JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a precise receipt analysis expert. Extract information accurately and respond only with JSON.")
                })
                put("messages", JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
                put("temperature", 0.1) // Lower temperature for more consistent results
                put("max_tokens", 150) // Limit response length
            }.toString()

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
                os.flush()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val content = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // Clean the response to ensure it's valid JSON
            val cleanedContent = content.trim().replace(Regex("^[^{]*"), "").replace(Regex("[^}]*$"), "")
            val extractedData = JSONObject(cleanedContent)
            
            val amount = extractedData.optDouble("amount", 0.0).let {
                if (it <= 0) 0.0 else it
            }
            val transactionType = extractedData.optString("transactionType", "debit").lowercase()
            val dateTime = extractedData.optString("dateTime", "")
            val source = extractedData.optString("source", "")
            val merchant = extractedData.optString("merchant", "")

            ExpenseDetails(
                amount = amount,
                transactionType = transactionType,
                dateTime = dateTime,
                source = source,
                merchant = merchant
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExpenseDetails(0.0, "debit", "", "", "")
        }
    }
}

data class ExpenseDetails(
    val amount: Double,
    val transactionType: String,
    val dateTime: String,
    val source: String,
    val merchant: String
) 