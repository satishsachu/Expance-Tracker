package com.example.expencetracker

import android.app.Application
import com.example.expencetracker.viewmodel.ExpenseViewModel

class ExpenseTrackerApplication : Application() {
    val expenseViewModel: ExpenseViewModel by lazy {
        ExpenseViewModel()
    }
} 