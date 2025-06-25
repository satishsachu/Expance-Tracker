package com.example.expencetracker.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expencetracker.data.Expense
import com.example.expencetracker.data.ExpenseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ExpenseViewModel : ViewModel() {
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _currentImageUri = MutableStateFlow<Uri?>(null)
    val currentImageUri: StateFlow<Uri?> = _currentImageUri.asStateFlow()

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            _expenses.update { currentList ->
                val newExpense = expense.copy(id = UUID.randomUUID().toString())
                currentList + newExpense
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            _expenses.update { currentList ->
                currentList.filterNot { it.id == expenseId }
            }
        }
    }

    fun getTotalBalance(): Double {
        return _expenses.value.sumOf { expense ->
            when (expense.type) {
                ExpenseType.INCOME -> expense.amount
                ExpenseType.EXPENSE -> -expense.amount
            }
        }
    }

    fun setCurrentImageUri(uri: Uri) {
        _currentImageUri.value = uri
    }

    fun clearCurrentImageUri() {
        _currentImageUri.value = null
    }
} 