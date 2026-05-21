package com.personal.focuslock.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.focuslock.Constants
import com.personal.focuslock.data.WebMonitor
import com.personal.focuslock.ui.theme.AccentAmber
import com.personal.focuslock.ui.theme.AccentAmberTint
import com.personal.focuslock.ui.theme.AccentBlue
import com.personal.focuslock.ui.theme.AccentBlueTint
import com.personal.focuslock.ui.theme.AccentCoral
import com.personal.focuslock.ui.theme.AccentCoralTint
import com.personal.focuslock.ui.theme.AccentMint
import com.personal.focuslock.ui.theme.AccentMintTint
import com.personal.focuslock.ui.theme.Bg
import com.personal.focuslock.ui.theme.Stroke
import com.personal.focuslock.ui.theme.StrokeSoft
import com.personal.focuslock.ui.theme.Surface1
import com.personal.focuslock.ui.theme.Surface2
import com.personal.focuslock.ui.theme.TextPrimary
import com.personal.focuslock.ui.theme.TextSecondary
import com.personal.focuslock.ui.theme.TextTertiary
import com.personal.focuslock.util.TimeFormat

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    statsVm: StatsViewModel,
    onAddApp: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenStats: () -> Unit,
    permissionBanner: @Composable () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val statsUi by statsVm.ui.collectAsState()
    Scaffold(
        containerColor = Bg,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddApp,
                containerColor = AccentBlue,
                contentColor = Bg,
                text = { Text("App hinzufügen", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Add, null) }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Header() }
            item { permissionBanner() }
            item {
                StreakRow(
                    streak = statsUi.streak,
                    todayMinutes = statsUi.today.totalUsedSeconds() / 60,
                    onClick = onOpenStats
                )
            }
            item { SummaryCard(rows = ui.rows, now = ui.now) }
            if (ui.rows.isNotEmpty()) {
                item {
                    Text(
                        "ÜBERWACHT",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
            }
            items(ui.rows, key = { it.key }) { row ->
                when (row) {
                    is HomeRow.Solo -> AppCard(
                        row = row.row.app,
                        now = ui.now,
                        onClick = { onOpenDetail(row.row.app.state.packageName) }
                    )
                    is HomeRow.Group -> GroupCard(
                        row = row.row,
                        now = ui.now,
                        onClick = { onOpenDetail(row.row.members.first().state.packageName) }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StreakRow(streak: Int, todayMinutes: Int, onClick: () -> Unit) {
    val tint = if (streak > 0) AccentAmberTint else Surface1
    val accent = if (streak > 0) AccentAmber else TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(tint)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocalFireDepartment,
            null,
            tint = accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$streak",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (streak == 1) "Tag Streak" else "Tage Streak",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                "Heute: $todayMinutes min · Statistik öffnen",
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = TextTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(
            "FocusLock",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "15 Min Nutzung, 2 h Sperre.",
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun SummaryCard(rows: List<HomeRow>, now: Long) {
    var tracking = 0
    var blocked = 0
    for (r in rows) {
        when (r) {
            is HomeRow.Solo -> {
                tracking += 1
                if (r.row.app.state.isBlocked(now)) blocked += 1
            }
            is HomeRow.Group -> {
                tracking += r.row.members.size
                if (r.row.group.isBlocked(now)) blocked += r.row.members.size
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "ÜBERWACHT",
            value = tracking.toString(),
            tint = AccentMintTint,
            accent = AccentMint
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "GESPERRT",
            value = blocked.toString(),
            tint = if (blocked > 0) AccentCoralTint else Surface1,
            accent = if (blocked > 0) AccentCoral else TextPrimary
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(tint)
            .padding(18.dp)
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            color = accent,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
private fun AppCard(row: AppRow, now: Long, onClick: () -> Unit) {
    val s = row.state
    val blocked = s.isBlocked(now)
    val usedPct = (s.usedMs.toFloat() / Constants.USAGE_LIMIT_MS.toFloat()).coerceIn(0f, 1f)
    val blockPct = if (blocked) {
        1f - (s.remainingBlockMs(now).toFloat() / Constants.BLOCK_DURATION_MS.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isWeb = WebMonitor.isWebKey(s.packageName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryIcon(bitmap = row.iconBitmap, isWeb = isWeb)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.label,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (isWeb) "Webseite (Browser)" else s.packageName,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        if (blocked) {
            BlockedSection(remaining = s.remainingBlockMs(now), progress = blockPct)
        } else {
            UsageSection(usedMs = s.usedMs, progress = usedPct)
        }
    }
}

@Composable
private fun GroupCard(row: GroupRow, now: Long, onClick: () -> Unit) {
    val g = row.group
    val blocked = g.isBlocked(now)
    val usedPct = (g.usedMs.toFloat() / Constants.USAGE_LIMIT_MS.toFloat()).coerceIn(0f, 1f)
    val blockPct = if (blocked) {
        1f - (g.remainingBlockMs(now).toFloat() / Constants.BLOCK_DURATION_MS.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val joinedLabels = row.members.joinToString(" · ") { it.label }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stack of up to 3 member icons.
            Box(modifier = Modifier.size(44.dp + (row.members.size.coerceAtMost(3) - 1).dp * 14)) {
                row.members.take(3).forEachIndexed { i, member ->
                    Box(
                        modifier = Modifier
                            .padding(start = (i * 14).dp)
                            .clip(CircleShape)
                            .background(Bg)
                            .padding(2.dp)
                    ) {
                        EntryIcon(
                            bitmap = member.iconBitmap,
                            isWeb = WebMonitor.isWebKey(member.state.packageName)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentBlueTint)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "GRUPPE",
                            color = AccentBlue,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    joinedLabels,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    "Geteilter Timer",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = TextTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        if (blocked) {
            BlockedSection(remaining = g.remainingBlockMs(now), progress = blockPct)
        } else {
            UsageSection(usedMs = g.usedMs, progress = usedPct)
        }
    }
}

@Composable
private fun UsageSection(usedMs: Long, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "HEUTE GENUTZT",
            color = TextTertiary,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${TimeFormat.mmss(usedMs)} / ${TimeFormat.mmss(Constants.USAGE_LIMIT_MS)}",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
        color = if (progress > 0.8f) AccentCoral else AccentBlue,
        trackColor = Surface2,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
    )
}

@Composable
private fun BlockedSection(remaining: Long, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, null, tint = AccentCoral, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Gesperrt · noch ${TimeFormat.hms(remaining)}",
            color = AccentCoral,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
        color = AccentCoral,
        trackColor = AccentCoralTint,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
    )
}

@Composable
fun AppIcon(bitmap: Bitmap?) {
    EntryIcon(bitmap = bitmap, isWeb = false)
}

@Composable
fun EntryIcon(bitmap: Bitmap?, isWeb: Boolean) {
    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
        isWeb -> Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AccentMintTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Public,
                null,
                tint = AccentMint,
                modifier = Modifier.size(22.dp)
            )
        }
        else -> Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Surface2)
        )
    }
}
