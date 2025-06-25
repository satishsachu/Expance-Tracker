package com.example.expencetracker.data

import java.util.Date
import android.net.Uri

data class Expense(
    val id: String = "",
    val amount: Double,
    val description: String,
    val category: String,
    val date: Date = Date(),
    val type: ExpenseType,
    val imageUri: Uri? = null,
    val transactionType: TransactionType = TransactionType.DEBIT,
    val dateTime: String = "",
    val source: String = "",
    val merchant: String = ""
)

enum class ExpenseType {
    INCOME,
    EXPENSE
}

enum class TransactionType {
    DEBIT,
    CREDIT
} 