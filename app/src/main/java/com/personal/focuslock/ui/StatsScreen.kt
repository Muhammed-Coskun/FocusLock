package com.personal.focuslock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.personal.focuslock.ui.theme.StrokeSoft
import com.personal.focuslock.ui.theme.Surface1
import com.personal.focuslock.ui.theme.Surface2
import com.personal.focuslock.ui.theme.TextPrimary
import com.personal.focuslock.ui.theme.TextSecondary
import com.personal.focuslock.ui.theme.TextTertiary

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Statistik", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Zurück",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StreakCard(streak = ui.streak) }
            item { TodayCard(ui = ui) }
            item { SectionLabel("LETZTE 7 TAGE") }
            item { WeekChartCard(bars = ui.last7Days) }
            if (ui.perAppToday.isNotEmpty()) {
                item { SectionLabel("APPS HEUTE") }
                items(ui.perAppToday, key = { it.packageName }) { row ->
                    AppStatsRow(row = row)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextTertiary,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun StreakCard(streak: Int) {
    val tint = if (streak > 0) AccentAmberTint else Surface1
    val accent = if (streak > 0) AccentAmber else TextTertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(tint)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "STREAK",
                color = accent,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$streak",
                    color = TextPrimary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (streak == 1) "Tag" else "Tage",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (streak == 0) "Heute schon Notfall-Entsperrung benutzt"
                else "ohne Notfall-Entsperrung",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TodayCard(ui: StatsUi) {
    val totalSec = ui.today.totalUsedSeconds()
    val totalMin = totalSec / 60
    val blocks = ui.today.totalBlocks()
    val attempts = ui.today.totalAttempts()
    val unlocks = ui.today.totalEmergencyUnlocks()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            "HEUTE",
            color = TextTertiary,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "$totalMin",
                color = TextPrimary,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "min genutzt",
                color = TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniStat(
                modifier = Modifier.weight(1f),
                value = blocks.toString(),
                label = "Sperren",
                tint = AccentCoralTint,
                accent = AccentCoral
            )
            MiniStat(
                modifier = Modifier.weight(1f),
                value = attempts.toString(),
                label = "Versuche",
                tint = AccentBlueTint,
                accent = AccentBlue
            )
            MiniStat(
                modifier = Modifier.weight(1f),
                value = unlocks.toString(),
                label = "Notfall",
                tint = AccentAmberTint,
                accent = AccentAmber
            )
        }
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier,
    value: String,
    label: String,
    tint: Color,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Text(
            value,
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WeekChartCard(bars: List<DayBar>) {
    val maxValue = (bars.maxOfOrNull { it.usedMinutes } ?: 0).coerceAtLeast(1)
    val totalMin = bars.sumOf { it.usedMinutes }
    val avgMin = totalMin / bars.size.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$totalMin min",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Gesamt 7 Tage",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Ø $avgMin min",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Bar chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                BarColumn(
                    modifier = Modifier.weight(1f),
                    bar = bar,
                    maxValue = maxValue
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bars.forEach { bar ->
                Text(
                    bar.weekdayLabel,
                    color = if (bar.isToday) TextPrimary else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (bar.isToday) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BarColumn(modifier: Modifier, bar: DayBar, maxValue: Int) {
    val frac = (bar.usedMinutes.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    val color = if (bar.isToday) AccentBlue else AccentBlueTint
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bar.usedMinutes > 0 && bar.isToday) {
            Text(
                "${bar.usedMinutes}",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
        }
        // Min height when value=0 so the day is still visible as a track
        val barHeightFraction = frac.coerceAtLeast(0.04f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(barHeightFraction)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(color)
        )
    }
}

@Composable
private fun AppStatsRow(row: StatsAppRow) {
    val minutes = row.usedSeconds / 60
    val isWeb = WebMonitor.isWebKey(row.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.iconBitmap != null) {
            Image(
                bitmap = row.iconBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
        } else if (isWeb) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentMintTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Public, null, tint = AccentMint, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Surface2)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.label,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append("$minutes min")
                    if (row.blockCount > 0) append(" · ${row.blockCount}× gesperrt")
                    if (row.emergencyUnlockCount > 0) append(" · ${row.emergencyUnlockCount}× Notfall")
                },
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
