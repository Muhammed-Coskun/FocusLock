package com.personal.focuslock.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.personal.focuslock.data.BlockerRepository
import com.personal.focuslock.data.WebMonitor
import com.personal.focuslock.ui.theme.AccentBlue
import com.personal.focuslock.ui.theme.AccentBlueTint
import com.personal.focuslock.ui.theme.AccentCoral
import com.personal.focuslock.ui.theme.Bg
import com.personal.focuslock.ui.theme.FocusLockTheme
import com.personal.focuslock.ui.theme.Stroke
import com.personal.focuslock.ui.theme.TextPrimary
import com.personal.focuslock.ui.theme.TextSecondary
import com.personal.focuslock.ui.theme.TextTertiary
import com.personal.focuslock.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finishAndGoHome(); return }
        val initialBlockUntil = intent.getLongExtra(EXTRA_BLOCK_UNTIL, 0L)
        setContent {
            FocusLockTheme {
                OverlayContent(pkg, initialBlockUntil) { finishAndGoHome() }
            }
        }
    }

    override fun onBackPressed() {
        finishAndGoHome()
    }

    private fun finishAndGoHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_BLOCK_UNTIL = "block_until"
    }
}

@Composable
private fun OverlayContent(pkg: String, initialBlockUntil: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { BlockerRepository.get(ctx.applicationContext) }
    // Use the group's blockUntilMs if this entry is grouped; otherwise the entry's own.
    val blockUntil by remember(pkg) {
        kotlinx.coroutines.flow.combine(repo.statesFlow, repo.groupsFlow) { states, groups ->
            val state = states.firstOrNull { it.packageName == pkg } ?: return@combine 0L
            val gid = state.groupId
            if (gid != null) groups.firstOrNull { it.id == gid }?.blockUntilMs ?: 0L
            else state.blockUntilMs
        }
    }.collectAsState(initial = initialBlockUntil)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(blockUntil, now) {
        if (blockUntil <= now) onDismiss()
    }

    val remaining = (blockUntil - now).coerceAtLeast(0)
    val total = com.personal.focuslock.Constants.BLOCK_DURATION_MS
    val progress = 1f - (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val isWeb = remember(pkg) { WebMonitor.isWebKey(pkg) }
    val appLabel = remember(pkg) {
        if (isWeb) WebMonitor.hostOf(pkg) else loadAppLabel(ctx.packageManager, pkg)
    }
    val appIcon = remember(pkg) {
        if (isWeb) null else loadAppIcon(ctx.packageManager, pkg)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(color = Stroke, radius = size.minDimension / 2, style = DrawStroke(width = 10f))
                    rotate(-90f) {
                        drawArc(
                            color = AccentBlue,
                            startAngle = 0f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = DrawStroke(width = 10f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.toBitmap(120, 120).asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = appLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = TimeFormat.hms(remaining),
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = TextPrimary,
                        letterSpacing = (-1).sp
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = "GESPERRT",
                fontSize = 12.sp,
                color = AccentCoral,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Kommst du wieder, wenn die Zeit um ist.",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(36.dp))

            Button(
                onClick = {
                    ctx.startActivity(
                        PuzzleActivity.intent(ctx, PuzzleActivity.ACTION_UNLOCK, pkg)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlueTint,
                    contentColor = AccentBlue
                ),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Notfall-Entsperrung (Puzzle)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Nur nutzen, wenn es wirklich wichtig ist.",
                color = TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

private fun loadAppLabel(pm: PackageManager, pkg: String): String = runCatching {
    val info = pm.getApplicationInfo(pkg, 0)
    pm.getApplicationLabel(info).toString()
}.getOrDefault(pkg)

private fun loadAppIcon(pm: PackageManager, pkg: String): Drawable? = runCatching {
    pm.getApplicationIcon(pkg)
}.getOrNull()
