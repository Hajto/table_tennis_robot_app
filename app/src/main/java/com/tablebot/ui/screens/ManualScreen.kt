package com.tablebot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablebot.data.*
import com.tablebot.ui.components.BallSettingsDropdowns
import com.tablebot.ui.components.PlayModeSelector
import com.tablebot.ui.components.StepSlider
import com.tablebot.ui.components.TableGrid
import com.tablebot.ui.components.buildCellBallNumbers

// ── Help article data model ────────────────────────────────────────

data class HelpArticle(
    val id: String,
    val title: String,
    val sections: List<HelpSection>,
)

sealed class HelpSection {
    data class Heading(val text: String) : HelpSection()
    data class Paragraph(val text: String) : HelpSection()
    data class BulletList(val items: List<String>) : HelpSection()
    data class Illustration(val content: @Composable () -> Unit) : HelpSection()
}

// ── Illustration composables (non-interactive UI previews) ─────────

@Composable
private fun IllustrationTableGrid() {
    val points = listOf(Point(3, 2), Point(8, 2), Point(13, 2))
    val ballNumbers = remember {
        buildCellBallNumbers(listOf(
            1 to listOf(Point(3, 2)),
            2 to listOf(Point(8, 2)),
            3 to listOf(Point(13, 2)),
        ))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: 3-ball sequence on the grid",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TableGrid(
                selectedPoints = points,
                cellBallNumbers = ballNumbers,
            )
        }
    }
}

@Composable
private fun IllustrationBallSettings() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: Ball settings",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            BallSettingsDropdowns(
                ball = 1,
                spin = 1,
                power = 2,
                onBallChange = {},
                onSpinChange = {},
                onPowerChange = {},
            )
        }
    }
}

@Composable
private fun IllustrationBallCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: Ball card with reorder controls",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            IconButton(
                                onClick = {},
                                enabled = true,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(20.dp))
                            }
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(
                                onClick = {},
                                enabled = true,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(20.dp))
                            }
                        }
                        Text(
                            "Ball 2",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Normal Topspin Medium",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.ExpandMore, "Toggle")
                        }
                        IconButton(onClick = {}, enabled = false) {
                            Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IllustrationWeightedGrid() {
    // One ball card targeting 6, 8 and 10, with position 6 tapped twice (weighted x2).
    val points = listOf(Point(6, 2), Point(8, 2), Point(10, 2))
    val counts = mapOf(6 to listOf(2), 8 to listOf(1), 10 to listOf(1))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: one ball card, position 6 weighted ×2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TableGrid(
                selectedPoints = points,
                cellBallNumbers = counts,
            )
        }
    }
}

@Composable
private fun IllustrationRandomTarget() {
    // A ball card with three equally-weighted positions -> fires a random one each shot.
    val points = listOf(Point(6, 2), Point(8, 2), Point(10, 2))
    val counts = mapOf(6 to listOf(1), 8 to listOf(1), 10 to listOf(1))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: a 3-position ball card fires to a random one each shot",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TableGrid(
                selectedPoints = points,
                cellBallNumbers = counts,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Randomises target (weighted by repeats)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IllustrationRandomOrder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: Random order toggle (locked on for a multi-ball card)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Random order", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Multi-ball steps are always randomised.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = true, onCheckedChange = null, enabled = false)
            }
        }
    }
}

@Composable
private fun IllustrationTimingSlider() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: Ball interval",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            StepSlider("Ball Interval", 9, 2..30) {}
        }
    }
}

/**
 * Preview of the play-mode selector locked to a single [mode] (0 Reps, 1 Balls, 2 Time), so each
 * mode's illustration can sit next to its own description. Non-interactive (callbacks are no-ops).
 */
@Composable
private fun IllustrationPlayMode(mode: Int, label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            PlayModeSelector(
                playMode = mode,
                reps = 20,
                ballCount = 30,
                durationSec = 120,
                ballsPerPattern = 3,
                repsRange = 1..100,
                onPlayModeChange = {},
                onRepsChange = {},
                onBallCountChange = {},
                onDurationChange = {},
            )
        }
    }
}

@Composable
private fun IllustrationSearchBar() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: Search bar with filter",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = "backhand",
                    onValueChange = {},
                    placeholder = { Text("Search drills\u2026") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {}, enabled = false) {
                    BadgedBox(badge = { Badge { } }) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IllustrationFilterChips() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Example: Tag filters and skill level",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text("Skill Level", style = MaterialTheme.typography.labelLarge)
            Text(
                "Beginner \u2014 Advanced",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            RangeSlider(
                value = 1f..3f,
                onValueChange = {},
                valueRange = 0f..4f,
                steps = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("Nwb", "Bgn", "Int", "Adv", "Mst").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Tags", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val selected = setOf("Backhand", "Forehand")
                listOf("Forehand", "Backhand", "Push", "Footwork", "Smash").forEach { tag ->
                    FilterChip(
                        selected = tag in selected,
                        onClick = {},
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

// ── Article content ────────────────────────────────────────────────

val helpArticles: List<HelpArticle> = listOf(
    HelpArticle(
        id = "basic_mode",
        title = "Customizing drills in Basic mode",
        sections = listOf(
            HelpSection.Paragraph(
                "Basic mode creates a drill with a single ball configuration. " +
                "Every ball in the drill shares the same type, spin, and power settings."
            ),
            HelpSection.Heading("Ball Type"),
            HelpSection.BulletList(listOf(
                "Serve \u2014 low trajectory serve balls",
                "Normal \u2014 standard topspin/backspin balls",
                "Lob \u2014 high arc lob balls",
            )),
            HelpSection.Heading("Spin"),
            HelpSection.Paragraph(
                "Choose from Max Topspin, Topspin, Float, Backspin, or Max Backspin. " +
                "Not all spins are available for every ball type."
            ),
            HelpSection.Heading("Power"),
            HelpSection.BulletList(listOf(
                "Extreme \u2014 fastest ball speed",
                "Strong",
                "Medium",
                "Light \u2014 slowest ball speed",
            )),
            HelpSection.Illustration { IllustrationBallSettings() },
            HelpSection.Heading("Target Grid"),
            HelpSection.Paragraph(
                "The 3\u00D75 grid represents your half of the table. " +
                "Tap cells to select where balls should land. " +
                "Some positions may be unavailable for certain ball type / spin / power combinations."
            ),
            HelpSection.Illustration { IllustrationTableGrid() },
            HelpSection.Heading("Operation Mode"),
            HelpSection.BulletList(listOf(
                "Single Ball \u2014 shoots to the same position each time",
                "Sequence \u2014 balls follow a fixed order through selected positions",
                "Random \u2014 ball lands on a randomly chosen selected position each time",
            )),
            HelpSection.Heading("Ball Interval"),
            HelpSection.Paragraph(
                "Ball Interval controls the time between consecutive balls, in tenths of a second."
            ),
            HelpSection.Illustration { IllustrationTimingSlider() },
            HelpSection.Heading("Play modes"),
            HelpSection.Paragraph(
                "Choose how the drill decides when to stop. The mode is saved with the drill and " +
                "used everywhere you play it — in Quick Play and from the saved-drill list."
            ),
            HelpSection.Paragraph(
                "Reps — play a fixed number of pattern repetitions."
            ),
            HelpSection.Illustration { IllustrationPlayMode(0, "Example: Reps mode") },
            HelpSection.Paragraph(
                "Balls — play until roughly a target number of balls has been shot; " +
                "the app converts that to whole repetitions, rounding up."
            ),
            HelpSection.Illustration { IllustrationPlayMode(1, "Example: Ball count mode") },
            HelpSection.Paragraph(
                "Time — play until a countdown elapses. Drag the minutes and seconds to set it; " +
                "the countdown shows on the STOP overlay and stops the robot at zero."
            ),
            HelpSection.Illustration { IllustrationPlayMode(2, "Example: Timed mode") },
            HelpSection.Paragraph(
                "The Reps and Balls limits are capped by your Ball tray capacity (set in Settings), " +
                "so a drill never asks for more balls than the tray can hold in one load."
            ),
        ),
    ),
    HelpArticle(
        id = "advanced_mode",
        title = "Customizing drills in Advanced mode",
        sections = listOf(
            HelpSection.Paragraph(
                "Advanced (Dynamic) mode lets you create multi-ball sequences " +
                "where each ball can have different type, spin, power, and target positions."
            ),
            HelpSection.Heading("Adding and Removing Balls"),
            HelpSection.Paragraph(
                "Tap \"Add Ball\" to append a new ball to the sequence. " +
                "Tap the \u2715 button on a ball card to remove it. " +
                "You must always have at least one ball."
            ),
            HelpSection.Heading("Reordering Balls"),
            HelpSection.Paragraph(
                "Each ball card has up and down arrow buttons on the left side, " +
                "with a drag handle between them. " +
                "Tap the arrows for precise moves, or long press and drag a card to reorder."
            ),
            HelpSection.Illustration { IllustrationBallCard() },
            HelpSection.Heading("Per-Ball Settings"),
            HelpSection.Paragraph(
                "Tap a ball card to expand it. Inside you can configure its ball type, spin, power, " +
                "and ball interval independently of other balls, and choose its target positions."
            ),
            HelpSection.Heading("Target positions and weighting"),
            HelpSection.Paragraph(
                "Under \"Target Points\", tap a grid cell to add a ball at that position; " +
                "long-press a cell to remove one. A cell shows a count when it holds more than one ball. " +
                "A single ball card can hold up to 5 balls (the \"N/5\" counter tracks this)."
            ),
            HelpSection.Paragraph(
                "Tapping the same cell more than once weights it: a card with positions " +
                "{A, A, B} lands on A twice as often as B. Duplicate taps raise a spot's odds."
            ),
            HelpSection.Illustration { IllustrationWeightedGrid() },
            HelpSection.Heading("Randomising the target"),
            HelpSection.Paragraph(
                "When a ball card has more than one position it automatically fires to a randomly " +
                "chosen one of them each time (\"Randomises target, weighted by repeats\"). " +
                "A card with a single position always fires there."
            ),
            HelpSection.Illustration { IllustrationRandomTarget() },
            HelpSection.Heading("Random order"),
            HelpSection.Paragraph(
                "The \"Random order\" toggle shuffles the order the ball cards fire in, rather than " +
                "playing them top-to-bottom. Multi-position cards are always randomised, so the toggle " +
                "is shown on and locked for them; it is freely settable on single-position cards."
            ),
            HelpSection.Illustration { IllustrationRandomOrder() },
            HelpSection.Heading("Sequence Overview"),
            HelpSection.Paragraph(
                "The overview grid at the top shows all target positions with ball numbers, " +
                "so you can see the full sequence at a glance."
            ),
            HelpSection.Illustration { IllustrationTableGrid() },
            HelpSection.Heading("Play modes"),
            HelpSection.Paragraph(
                "The whole sequence plays by Reps, Balls, or Time — the same selector as Basic mode, " +
                "saved with the drill. For Balls and Time the app counts every ball across the sequence " +
                "(all positions in every ball card), not just the number of cards."
            ),
            HelpSection.Paragraph(
                "Reps — play a fixed number of full-sequence repetitions."
            ),
            HelpSection.Illustration { IllustrationPlayMode(0, "Example: Reps mode") },
            HelpSection.Paragraph(
                "Balls — play until roughly a target total number of balls has been shot, rounding up."
            ),
            HelpSection.Illustration { IllustrationPlayMode(1, "Example: Ball count mode") },
            HelpSection.Paragraph(
                "Time — play until a countdown elapses, then stop."
            ),
            HelpSection.Illustration { IllustrationPlayMode(2, "Example: Timed mode") },
            HelpSection.Heading("Repeat Delay"),
            HelpSection.Paragraph(
                "Repeat Delay adds a pause, in seconds, between repetitions of the full sequence. " +
                "It applies to every play mode."
            ),
        ),
    ),
    HelpArticle(
        id = "calibration",
        title = "Calibrating the robot",
        sections = listOf(
            HelpSection.Paragraph(
                "The calibration feature is being remade and improved. " +
                "A detailed guide will be available here soon."
            ),
            HelpSection.Paragraph(
                "In the meantime, the Calibration screen (accessible from the \u22EE menu) " +
                "lets you fine-tune motor parameters for each ball type / spin / power / position combination. " +
                "You can export and import calibration files, and reset to factory defaults."
            ),
        ),
    ),
    HelpArticle(
        id = "search_filter",
        title = "Searching and filtering drills",
        sections = listOf(
            HelpSection.Paragraph(
                "The drill library provides search and filtering to help you find the right training quickly."
            ),
            HelpSection.Heading("Opening the Library"),
            HelpSection.Paragraph(
                "Tap the training name card on the main screen to open the drill library as a bottom sheet."
            ),
            HelpSection.Heading("Search"),
            HelpSection.Paragraph(
                "Type in the search bar at the top to filter drills by name. " +
                "The search is case-insensitive and matches anywhere in the drill name."
            ),
            HelpSection.Illustration { IllustrationSearchBar() },
            HelpSection.Heading("Tag Filters"),
            HelpSection.Paragraph(
                "Tap the filter icon to open the filter dialog. " +
                "Select one or more tags (Forehand, Backhand, Push, Openup, Flick, Smash, " +
                "Serve Return, Footwork, Random, Transition) to show only matching drills. " +
                "A badge appears on the filter icon when filters are active."
            ),
            HelpSection.Heading("Skill Level"),
            HelpSection.Paragraph(
                "Use the skill level range slider in the filter dialog to show only drills " +
                "within a difficulty range: Newbie, Beginner, Intermediate, Advanced, or Master. " +
                "Drag either end of the slider to narrow the range."
            ),
            HelpSection.Illustration { IllustrationFilterChips() },
            HelpSection.Heading("Resetting Filters"),
            HelpSection.Paragraph(
                "Tap the \"Reset\" button in the filter dialog to clear all tag selections " +
                "and reset the skill level range to show all drills."
            ),
        ),
    ),
)

private fun HelpArticle.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase()
    return title.lowercase().contains(q) || sections.any { section ->
        when (section) {
            is HelpSection.Heading -> section.text.lowercase().contains(q)
            is HelpSection.Paragraph -> section.text.lowercase().contains(q)
            is HelpSection.BulletList -> section.items.any { it.lowercase().contains(q) }
            is HelpSection.Illustration -> false
        }
    }
}

// ── Manual screen (article list with search) ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(
    onBack: () -> Unit,
    onArticle: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { helpArticles.filter { it.matchesQuery(query) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search articles\u2026") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )

            if (filtered.isEmpty()) {
                Text(
                    "No articles match your search.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { article ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArticle(article.id) },
                        ) {
                            Text(
                                article.title,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Manual article detail screen ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualArticleScreen(
    articleId: String,
    onBack: () -> Unit,
) {
    val article = remember(articleId) { helpArticles.find { it.id == articleId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "Article") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (article == null) {
            Text(
                "Article not found.",
                modifier = Modifier.padding(padding).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (section in article.sections) {
                    when (section) {
                        is HelpSection.Heading -> Text(
                            section.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        is HelpSection.Paragraph -> Text(
                            section.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        is HelpSection.BulletList -> Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (item in section.items) {
                                Text(
                                    "  \u2022  $item",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        is HelpSection.Illustration -> section.content()
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
