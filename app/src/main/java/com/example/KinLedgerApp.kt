package com.example

import android.app.Application
import com.example.auth.AuthService
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.DemoLedgerRepository
import com.example.data.repository.DriveLedgerRepository
import com.example.data.repository.LedgerRepository
import com.example.export.StatementExporter

class AppContainer(application: Application) {
    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(application)
    }

    val authService: AuthService by lazy {
        AuthService(application, preferencesRepository)
    }

    val ledgerRepository: LedgerRepository by lazy {
        DriveLedgerRepository(application, authService)
    }

    fun createDemoLedgerRepository(): LedgerRepository = DemoLedgerRepository()

    val statementExporter: StatementExporter by lazy {
        StatementExporter(application)
    }
}

class KinLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
