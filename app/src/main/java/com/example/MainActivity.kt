package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AppSession
import com.example.ui.KinLedgerViewModel
import com.example.ui.navigation.KinLedgerRoot
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.KinLedgerTheme
import com.example.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KinLedgerApp
        setContent {
            val authService = app.container.authService
            val currentSession by authService.sessionFlow
                .collectAsStateWithLifecycle(initialValue = authService.currentSession)
            val themePreference by app.container.preferencesRepository.themePreferenceFlow
                .collectAsStateWithLifecycle(initialValue = ThemePreference.DARK)

            when (val session = currentSession) {
                is AppSession.SignedOut -> {
                    KinLedgerTheme(themePreference = themePreference) {
                        LoginScreen(authService = authService)
                    }
                }
                is AppSession.Google, is AppSession.LocalDemo -> {
                    key(session.sessionKey) {
                        val viewModel: KinLedgerViewModel = viewModel(
                            key = session.sessionKey,
                            factory = KinLedgerViewModel.factory(session)
                        )
                        KinLedgerRoot(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
