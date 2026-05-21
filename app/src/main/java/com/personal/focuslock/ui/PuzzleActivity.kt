package com.personal.focuslock.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.focuslock.data.BlockerRepository
import com.personal.focuslock.data.StatsRepository
import com.personal.focuslock.ui.theme.AccentBlue
import com.personal.focuslock.ui.theme.AccentCoral
import com.personal.focuslock.ui.theme.AccentCoralTint
import com.personal.focuslock.ui.theme.Bg
import com.personal.focuslock.ui.theme.FocusLockTheme
import com.personal.focuslock.ui.theme.Stroke
import com.personal.focuslock.ui.theme.Surface1
import com.personal.focuslock.ui.theme.Surface2
import com.personal.focuslock.ui.theme.TextPrimary
import com.personal.focuslock.ui.theme.TextSecondary
import com.personal.focuslock.ui.theme.TextTertiary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class PuzzleActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE blocks screenshots, screen recording, AND Circle to Search /
        // Google Lens / accessibility-based screen readers from capturing the puzzle text.
        // Same flag banking apps use.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_UNLOCK
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE)
            ?: if (action == ACTION_REMOVE) "App entfernen" else "Notfall-Entsperrung"
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
            ?: "Tippe den Text exakt ab. Bei einem Fehler beginnt er neu."

        setContent {
            FocusLockTheme {
                PuzzleScreen(
                    title = title,
                    subtitle = subtitle,
                    onClose = { finish() },
                    onSolved = {
                        val repo = BlockerRepository.get(applicationContext)
                        val statsRepo = StatsRepository.get(applicationContext)
                        scope.launch {
                            when (action) {
                                ACTION_REMOVE -> repo.removeMonitored(pkg)
                                else -> {
                                    // Reset whichever state container governs this entry —
                                    // the group's if it's grouped, otherwise the AppState's.
                                    val state = repo.snapshot().firstOrNull { it.packageName == pkg }
                                    val groupId = state?.groupId
                                    if (groupId != null) {
                                        repo.replaceGroup(groupId) {
                                            it.copy(usedMs = 0, blockUntilMs = 0, cycleStartedMs = 0)
                                        }
                                    } else {
                                        repo.replaceState(pkg) {
                                            it.copy(usedMs = 0, blockUntilMs = 0, cycleStartedMs = 0)
                                        }
                                    }
                                    runCatching { statsRepo.recordEmergencyUnlock(pkg) }
                                }
                            }
                            finish()
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val ACTION_UNLOCK = "unlock"
        const val ACTION_REMOVE = "remove"

        fun intent(context: Context, action: String, pkg: String): Intent =
            Intent(context, PuzzleActivity::class.java).apply {
                putExtra(EXTRA_ACTION, action)
                putExtra(EXTRA_PACKAGE, pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}

private const val PUZZLE_LENGTH = 60
private const val PUZZLE_LINE_LEN = 30
private val PUZZLE_CHARS =
    ("ABCDEFGHJKLMNPQRSTUVWXYZ" +
        "abcdefghijkmnopqrstuvwxyz" +
        "23456789" +
        "!?#%&*+=@").toCharArray()

private fun generatePuzzle(length: Int = PUZZLE_LENGTH): String = buildString(length) {
    val rng = Random.Default
    repeat(length) {
        append(PUZZLE_CHARS[rng.nextInt(PUZZLE_CHARS.size)])
        if ((it + 1) % PUZZLE_LINE_LEN == 0 && it < length - 1) append('\n')
    }
}

/** No-op text toolbar — suppresses the cut/copy/paste/select-all popup so the user can't paste. */
private object NoOpTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) { /* never show */ }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PuzzleScreen(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    onSolved: () -> Unit
) {
    var target by remember { mutableStateOf(generatePuzzle()) }
    var input by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var errorShown by remember { mutableStateOf(false) }

    val targetFlat = remember(target) { target.replace("\n", "") }
    val progress = (input.length.toFloat() / targetFlat.length).coerceIn(0f, 1f)
    val canSubmit = input.length >= targetFlat.length

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Schließen", tint = TextPrimary)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            AnimatedVisibility(visible = errorShown) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AccentCoralTint)
                        .border(1.dp, AccentCoral.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Fehler. Neuer Text generiert.",
                        color = AccentCoral,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface1)
                    .padding(20.dp)
            ) {
                Text(
                    "VORLAGE",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = target,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Wrap the input in CompositionLocalProvider with a no-op TextToolbar
            // so long-press doesn't show the Cut/Copy/Paste/Select All menu.
            CompositionLocalProvider(LocalTextToolbar provides NoOpTextToolbar) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { new ->
                        // Reject any single update that would add more than 4 chars at once
                        // (typical normal typing is 1 char per event; bulk input = paste/autofill attempt).
                        val added = new.length - input.length
                        if (added > 4) return@OutlinedTextField
                        if (new.length <= targetFlat.length + 10) input = new
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Hier tippen …", color = TextTertiary) },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    shape = RoundedCornerShape(20.dp),
                    minLines = 3,
                    maxLines = 6,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        // Disable autocorrect / suggestions / capitalization so the keyboard
                        // can't propose the rest of the string.
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None
                    ),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${input.length} / ${targetFlat.length}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.size(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AccentBlue,
                    trackColor = Surface2
                )
            }

            if (attempts > 0) {
                Text(
                    "Versuche: $attempts",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    attempts += 1
                    if (input == targetFlat) {
                        onSolved()
                    } else {
                        target = generatePuzzle()
                        input = ""
                        errorShown = true
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Bg,
                    disabledContainerColor = Surface2,
                    disabledContentColor = TextTertiary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.size(8.dp))
                Text("Bestätigen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    LaunchedEffect(input) {
        if (errorShown && input.isNotEmpty()) errorShown = false
    }
}
