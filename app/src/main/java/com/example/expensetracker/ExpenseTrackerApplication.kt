package com.example.expensetracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for ExpenseTracker.
 * 
 * Annotated with @HiltAndroidApp to enable Hilt's code generation
 * and serve as the application-level dependency container.
 */
@HiltAndroidApp
class ExpenseTrackerApplication : Application()
