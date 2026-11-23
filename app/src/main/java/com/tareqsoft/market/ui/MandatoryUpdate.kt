package com.tareqsoft.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tareqsoft.market.update.ReleaseInfo
import com.tareqsoft.market.update.UpdateState
import kotlinx.coroutines.launch

/**
 * Blocks the app while a mandatory update is pending. It cannot be closed
 * with Back or by tapping outside. The update downloads by itself and the
 * installer opens as soon as it is ready; the shop's data is kept.
 */
@Composable
fun MandatoryUpdateGate() {
    val updater = LocalContainer.current.updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    val release: ReleaseInfo = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    }?.takeIf { it.mandatory } ?: return

    // Download on its own, then open the installer once it is ready.
    LaunchedEffect(state) {
        when (val s = state) {
            is UpdateState.Available -> updater.download(s.release)
            is UpdateState.ReadyToInstall -> updater.install(s.file)
            else -> {}
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                }
                Text(
                    "تحديث إلزامي ${release.version}",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
                Text(
                    "يجب تثبيت هذا التحديث لمتابعة استخدام التطبيق. بياناتك محفوظة ولن تُحذف.",
                    textAlign = TextAlign.Center, color = Color.DarkGray,
                )
                if (release.notes.isNotBlank()) {
                    Text(release.notes, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Text("جارِ التنزيل ${(s.progress * 100).toInt()}%", color = Color.Gray)
                    }
                    is UpdateState.ReadyToInstall ->
                        PrimaryActionButton("تثبيت التحديث الآن", onClick = { updater.install(s.file) })
                    is UpdateState.Failed -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        PrimaryActionButton("إعادة المحاولة", onClick = { scope.launch { updater.download(release) } })
                    }
                    else -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
