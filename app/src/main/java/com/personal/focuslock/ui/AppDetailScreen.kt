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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.focuslock.Constants
import com.personal.focuslock.data.MonitorGroup
import com.personal.focuslock.data.WebMonitor
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

private data class OtherEntry(
    val row: AppRow,
    val groupId: String?,
    val effectiveBlocked: Boolean
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    vm: HomeViewModel,
    pkg: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val now = ui.now

    // Resolve this entry — search both solos and group members.
    var myRow: AppRow? = null
    var myGroup: MonitorGroup? = null
    val otherEntries = mutableListOf<OtherEntry>()
    for (r in ui.rows) {
        when (r) {
            is HomeRow.Solo -> {
                val app = r.row.app
                if (app.state.packageName == pkg) {
                    myRow = app
                } else {
                    otherEntries += OtherEntry(app, null, app.state.isBlocked(now))
                }
            }
            is HomeRow.Group -> {
                val isInThisGroup = r.row.members.any { it.state.packageName == pkg }
                val groupBlocked = r.row.group.isBlocked(now)
                for (m in r.row.members) {
                    if (m.state.packageName == pkg) {
                        myRow = m
                        myGroup = r.row.group
                    } else {
                        otherEntries += OtherEntry(
                            row = m,
                            groupId = r.row.group.id,
                            effectiveBlocked = groupBlocked
                        )
                    }
                }
                @Suppress("UNUSED_VARIABLE") val _unused = isInThisGroup
            }
        }
    }

    if (myRow == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Bg),
            contentAlignment = Alignment.Center
        ) {
            Text("Eintrag nicht gefunden.", color = TextSecondary)
        }
        return
    }

    val s = myRow.state
    val effectiveUsedMs = myGroup?.usedMs ?: s.usedMs
    val effectiveBlockUntilMs = myGroup?.blockUntilMs ?: s.blockUntilMs
    val blocked = effectiveBlockUntilMs > now
    val effectiveRemainingMs = (effectiveBlockUntilMs - now).coerceAtLeast(0)
    val usedPct = (effectiveUsedMs.toFloat() / Constants.USAGE_LIMIT_MS.toFloat()).coerceIn(0f, 1f)
    val blockPct = if (blocked) {
        1f - (effectiveRemainingMs.toFloat() / Constants.BLOCK_DURATION_MS.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isWeb = WebMonitor.isWebKey(s.packageName)

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Details", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderCard(row = myRow, isWeb = isWeb)

            StatusCard(
                blocked = blocked,
                usedMs = effectiveUsedMs,
                remainingMs = effectiveRemainingMs,
                usedPct = usedPct,
                blockPct = blockPct,
                groupName = if (myGroup != null) "Geteilter Timer" else null
            )

            Text(
                "GETEILTER TIMER",
                color = TextTertiary,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            ShareTimerCard(
                myPkg = pkg,
                myGroupId = myGroup?.id,
                myBlocked = blocked,
                others = otherEntries,
                onToggle = { otherPkg, share -> vm.setShareTimer(otherPkg, pkg, share) }
            )

            Text(
                "AKTIONEN",
                color = TextTertiary,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            if (blocked) {
                ActionButton(
                    title = "Notfall-Entsperrung",
                    subtitle = if (myGroup != null)
                        "Puzzle löst Sperre für die ganze Gruppe"
                    else "Puzzle lösen, um 15 Min freizuschalten",
                    icon = Icons.Default.LockOpen,
                    containerColor = AccentBlueTint,
                    contentColor = AccentBlue,
                    onClick = {
                        ctx.startActivity(
                            PuzzleActivity.intent(ctx, PuzzleActivity.ACTION_UNLOCK, pkg)
                        )
                    }
                )
            }

            ActionButton(
                title = "Eintrag entfernen",
                subtitle = "Nur nach Puzzle möglich",
                icon = Icons.Default.DeleteOutline,
                containerColor = AccentCoralTint,
                contentColor = AccentCoral,
                onClick = {
                    ctx.startActivity(
                        PuzzleActivity.intent(ctx, PuzzleActivity.ACTION_REMOVE, pkg)
                    )
                }
            )

            InfoNote()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderCard(row: AppRow, isWeb: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            row.iconBitmap != null -> Image(
                bitmap = row.iconBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            isWeb -> Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AccentMintTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Public, null, tint = AccentMint, modifier = Modifier.size(28.dp))
            }
            else -> Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Surface2)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (isWeb) "Webseite · Browser-Sperre" else row.state.packageName,
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatusCard(
    blocked: Boolean,
    usedMs: Long,
    remainingMs: Long,
    usedPct: Float,
    blockPct: Float,
    groupName: String?
) {
    val tint = if (blocked) AccentCoralTint else AccentMintTint
    val accent = if (blocked) AccentCoral else AccentMint
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(tint)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (blocked) Icons.Default.Lock else Icons.Default.Shield,
                null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (blocked) "GESPERRT" else "VERFÜGBAR",
                color = accent,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (groupName != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        groupName.uppercase(),
                        color = accent,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (blocked) TimeFormat.hms(remainingMs)
            else "${TimeFormat.mmss(usedMs)} / ${TimeFormat.mmss(Constants.USAGE_LIMIT_MS)}",
            color = TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (blocked) "Verbleibende Sperrzeit"
            else "Heute genutzt",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { if (blocked) blockPct else usedPct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = accent,
            trackColor = Surface2,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
        )
    }
}

@Composable
private fun ShareTimerCard(
    myPkg: String,
    myGroupId: String?,
    myBlocked: Boolean,
    others: List<OtherEntry>,
    onToggle: (otherPkg: String, share: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            "Wähle Einträge, die denselben 15/2 h-Timer teilen sollen.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        if (myBlocked) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentCoralTint)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = AccentCoral, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Während Sperre nicht änderbar.",
                    color = AccentCoral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (others.isEmpty()) {
            Text(
                "Keine anderen Einträge.",
                color = TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            return@Column
        }
        for ((index, entry) in others.withIndex()) {
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(StrokeSoft)
                )
            }
            ShareTimerRow(
                entry = entry,
                inSameGroup = myGroupId != null && entry.groupId == myGroupId,
                myBlocked = myBlocked,
                onToggle = { share -> onToggle(entry.row.state.packageName, share) }
            )
        }
    }
}

@Composable
private fun ShareTimerRow(
    entry: OtherEntry,
    inSameGroup: Boolean,
    myBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val isWeb = WebMonitor.isWebKey(entry.row.state.packageName)
    val locked = myBlocked || entry.effectiveBlocked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            entry.row.iconBitmap != null -> Image(
                bitmap = entry.row.iconBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
            isWeb -> Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentMintTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Public, null, tint = AccentMint, modifier = Modifier.size(18.dp))
            }
            else -> Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface2)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.row.label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            val sub = when {
                inSameGroup -> "Teilt deinen Timer"
                entry.groupId != null -> "In anderer Gruppe"
                else -> "Eigener Timer"
            }
            Text(sub, color = TextTertiary, fontSize = 11.sp)
        }
        Switch(
            checked = inSameGroup,
            onCheckedChange = if (locked) null else onToggle,
            enabled = !locked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentBlue,
                checkedThumbColor = Bg,
                uncheckedTrackColor = Surface2,
                uncheckedThumbColor = TextTertiary,
                uncheckedBorderColor = Stroke,
                disabledCheckedTrackColor = AccentBlueTint,
                disabledUncheckedTrackColor = Surface2
            )
        )
    }
}

@Composable
private fun ActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun InfoNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Refresh, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Das Puzzle ist bewusst anstrengend. Wenn du wirklich Zugriff brauchst, kannst du es lösen – sonst hilft Warten.",
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}
