package com.personal.focuslock.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.focuslock.data.BlockerRepository
import com.personal.focuslock.data.WebMonitor
import com.personal.focuslock.ui.theme.Accent
import com.personal.focuslock.ui.theme.AccentBlue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QUICK_DOMAINS = listOf(
    "instagram.com",
    "twitter.com",
    "x.com",
    "tiktok.com",
    "youtube.com",
    "reddit.com",
    "facebook.com"
)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { BlockerRepository.get(ctx) }
    val monitored by repo.statesFlow.collectAsState(initial = emptyList<com.personal.focuslock.data.AppState>())
    val monitoredSet = remember(monitored) { monitored.map { it.packageName }.toSet() }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppInfoLoader.listLauncherApps(ctx) }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Hinzufügen", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            SearchField(query = query, onChange = { query = it })
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else {
                val filtered = remember(query, list) {
                    if (query.isBlank()) list
                    else list.filter {
                        it.label.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        WebSection(
                            monitoredSet = monitoredSet,
                            onAddDomain = { host ->
                                scope.launch { repo.addMonitored(WebMonitor.keyFor(host)) }
                            }
                        )
                    }
                    item {
                        Text(
                            "APPS",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        val selected = monitoredSet.contains(app.packageName)
                        AppRow(app, selected) {
                            scope.launch {
                                if (selected) repo.removeMonitored(app.packageName)
                                else repo.addMonitored(app.packageName)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WebSection(
    monitoredSet: Set<String>,
    onAddDomain: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, StrokeSoft, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentMintTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Public, null, tint = AccentMint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Webseite sperren",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Erkennt Domain in Chrome, Firefox, Samsung Internet …",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Text(
            "SCHNELL HINZUFÜGEN",
            color = TextTertiary,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        // FlowRow wraps quick chips onto multiple lines automatically.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (domain in QUICK_DOMAINS) {
                val key = WebMonitor.keyFor(domain)
                val active = monitoredSet.contains(key)
                DomainChip(
                    label = domain,
                    active = active,
                    onClick = { if (!active) onAddDomain(domain) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("eigene-domain.com", color = TextTertiary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        val sanitized = sanitizeHost(input)
                        if (sanitized != null) {
                            onAddDomain(sanitized)
                            input = ""
                        }
                    },
                    enabled = sanitizeHost(input) != null
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = if (sanitizeHost(input) != null) AccentBlue else TextTertiary
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Stroke,
                cursorColor = AccentBlue,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
    }
}

@Composable
private fun DomainChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) AccentMintTint else Surface2
    val fg = if (active) AccentMint else TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(enabled = !active) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (active) {
            Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun sanitizeHost(raw: String): String? {
    val cleaned = raw.trim().lowercase()
        .removePrefix("http://").removePrefix("https://")
        .removePrefix("www.")
        .substringBefore('/')
        .substringBefore('?')
    if (cleaned.isBlank() || !cleaned.contains('.')) return null
    if (cleaned.contains(' ')) return null
    return cleaned
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)),
        placeholder = { Text("App suchen …", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Surface1,
            unfocusedContainerColor = Surface1,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun AppRow(app: InstalledApp, selected: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Stroke, RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        AppIcon(app.icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(app.packageName, color = TextSecondary, fontSize = 11.sp)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Surface2)
                    .border(1.dp, Stroke, CircleShape)
            )
        }
    }
}
