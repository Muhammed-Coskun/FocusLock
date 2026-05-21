package com.personal.focuslock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.personal.focuslock.service.BlockerService
import com.personal.focuslock.ui.AppDetailScreen
import com.personal.focuslock.ui.AppPickerScreen
import com.personal.focuslock.ui.HomeScreen
import com.personal.focuslock.ui.HomeViewModel
import com.personal.focuslock.ui.StatsScreen
import com.personal.focuslock.ui.StatsViewModel
import com.personal.focuslock.ui.theme.AccentBlue
import com.personal.focuslock.ui.theme.AccentBlueTint
import com.personal.focuslock.ui.theme.FocusLockTheme
import com.personal.focuslock.ui.theme.Stroke
import com.personal.focuslock.ui.theme.Surface1
import com.personal.focuslock.ui.theme.TextPrimary
import com.personal.focuslock.ui.theme.TextSecondary
import com.personal.focuslock.util.Permissions

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()
    private val statsVm: StatsViewModel by viewModels()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result — non-blocking */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FocusLockTheme {
                AppRoot(vm, statsVm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startServiceIfReady()
    }

    private fun startServiceIfReady() {
        if (Permissions.hasUsageAccess(this) && Permissions.hasOverlay(this)) {
            BlockerService.start(this)
        }
    }
}

@Composable
private fun AppRoot(vm: HomeViewModel, statsVm: StatsViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            vm = vm,
            statsVm = statsVm,
            onAddApp = { screen = Screen.Picker },
            onOpenDetail = { pkg -> screen = Screen.Detail(pkg) },
            onOpenStats = { screen = Screen.Stats },
            permissionBanner = { PermissionBanner() }
        )
        Screen.Picker -> AppPickerScreen(onBack = { screen = Screen.Home })
        is Screen.Detail -> AppDetailScreen(
            vm = vm,
            pkg = s.pkg,
            onBack = { screen = Screen.Home }
        )
        Screen.Stats -> StatsScreen(
            vm = statsVm,
            onBack = { screen = Screen.Home }
        )
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data object Stats : Screen
    data class Detail(val pkg: String) : Screen
}

@Composable
private fun PermissionBanner() {
    val ctx = LocalContext.current
    var usage by remember { mutableStateOf(Permissions.hasUsageAccess(ctx)) }
    var overlay by remember { mutableStateOf(Permissions.hasOverlay(ctx)) }
    var accessibility by remember { mutableStateOf(Permissions.isAccessibilityServiceEnabled(ctx)) }
    var batteryOpt by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(ctx)) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                usage = Permissions.hasUsageAccess(ctx)
                overlay = Permissions.hasOverlay(ctx)
                accessibility = Permissions.isAccessibilityServiceEnabled(ctx)
                batteryOpt = Permissions.isIgnoringBatteryOptimizations(ctx)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (usage && overlay && accessibility && batteryOpt) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Stroke, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            "Berechtigungen einrichten",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Damit FocusLock zuverlässig im Hintergrund läuft:",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        var first = true
        if (!usage) {
            PermRow("Nutzungsdatenzugriff", "Zeit pro App tracken") {
                Permissions.openUsageAccessSettings(ctx)
            }
            first = false
        }
        if (!overlay) {
            if (!first) Spacer(Modifier.height(8.dp))
            PermRow("Über anderen Apps anzeigen", "Sperrbildschirm einblenden") {
                Permissions.openOverlaySettings(ctx)
            }
            first = false
        }
        if (!accessibility) {
            if (!first) Spacer(Modifier.height(8.dp))
            PermRow(
                "Bedienungshilfe (für Webseiten)",
                "Browser-URL erkennen, um z. B. twitter.com zu sperren"
            ) {
                Permissions.openAccessibilitySettings(ctx)
            }
            first = false
        }
        if (!batteryOpt) {
            if (!first) Spacer(Modifier.height(8.dp))
            PermRow(
                "Akku-Optimierung deaktivieren",
                "Verhindert, dass Samsung den Service im Hintergrund stoppt"
            ) {
                Permissions.requestIgnoreBatteryOptimizations(ctx)
            }
        }
    }
}

@Composable
private fun PermRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccentBlueTint)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("Öffnen", color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
