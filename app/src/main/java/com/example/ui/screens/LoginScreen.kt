package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.auth.AuthService
import com.example.auth.DriveTokenResult
import com.example.auth.SignInCancelledException
import com.example.ui.components.GlassAmbientBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalLedgerColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authService: AuthService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ledgerColors = LocalLedgerColors.current

    var isLoading by remember { mutableStateOf(false) }
    var isAttemptingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        authService.handleDriveConsentResult(context, result.data)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        authService.attemptAutoSignIn(context)
        isAttemptingSilent = false
    }

    GlassAmbientBackdrop(
        modifier = modifier,
        showWatermark = true,
        watermarkBottomPadding = 28.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon_mark_1795010599363),
                    contentDescription = "KinLedger Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = ledgerColors.glassDropShadow)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.5.dp, ledgerColors.glassRimTopLeft, RoundedCornerShape(24.dp))
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "KinLedger",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Shared family expenses, remittances, and transfers — backed up as JSON in your Google Drive.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                GlassCard(
                    cornerRadius = 22.dp,
                    glowColor = MaterialTheme.colorScheme.primary,
                    shadowElevation = 14.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FeatureRow(
                            icon = Icons.Rounded.Groups,
                            tint = ledgerColors.income,
                            title = "Shared Family Workspace",
                            subtitle = "Create a family ledger or join via invite code or shared Google Drive file."
                        )
                        FeatureRow(
                            icon = Icons.Rounded.CloudDone,
                            tint = ledgerColors.transfer,
                            title = "Google Drive JSON Sync",
                            subtitle = "Works offline anytime and saves your ledger JSON in your Drive KinLedger folder."
                        )
                        FeatureRow(
                            icon = Icons.Rounded.SwapHoriz,
                            tint = ledgerColors.debt,
                            title = "Bank-to-Cash Transfers",
                            subtitle = "Track ATM withdrawals and account transfers without skewing reports."
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ledgerColors.expenseContainer.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, ledgerColors.expense.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = ledgerColors.expense,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            errorMessage = "Unable to open Google Sign-In from current context."
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val signInRes = authService.signInWithGoogle(activity)
                            if (signInRes.isSuccess) {
                                when (val driveRes = authService.requestDriveAccessToken(activity)) {
                                    is DriveTokenResult.NeedsConsent -> {
                                        val pending = driveRes.pendingIntent
                                        if (pending != null) {
                                            driveAuthLauncher.launch(
                                                IntentSenderRequest.Builder(pending.intentSender).build()
                                            )
                                            return@launch
                                        }
                                    }
                                    is DriveTokenResult.Token, is DriveTokenResult.Failure -> Unit
                                }
                            } else {
                                val err = signInRes.exceptionOrNull()
                                if (err != null && err !is SignInCancelledException) {
                                    errorMessage = err.localizedMessage ?: "Google Sign-In failed. Please try again."
                                }
                            }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading && !isAttemptingSilent,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading || isAttemptingSilent) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isAttemptingSilent) "Checking account..." else "Signing in...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        authService.enterDemoMode()
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ledgerColors.glassRimTopLeft),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ledgerColors.glassFillTop,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("demo_sign_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue without Google (Demo)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Explore all features locally with sample data — no Google account required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f))
                .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
