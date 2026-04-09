package com.tablebot.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tablebot.data.Profile
import com.tablebot.data.ProfileIndex

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileSwitcherDialog(
    profileIndex: ProfileIndex,
    initialProfileId: String = "",
    onSelectProfile: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val profiles = profileIndex.profiles
    val targetIdx = if (initialProfileId.isNotEmpty()) {
        profiles.indexOfFirst { it.id == initialProfileId }.takeIf { it >= 0 }
    } else null
    val activeIdx = targetIdx ?: profiles.indexOfFirst { it.id == profileIndex.activeProfileId }.coerceAtLeast(0)
    val pageCount = profiles.size + 1
    val pagerState = rememberPagerState(initialPage = activeIdx, pageCount = { pageCount })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Switch Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(16.dp))

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    if (page < profiles.size) {
                        val profile = profiles[page]
                        val isActive = profile.id == profileIndex.activeProfileId
                        ProfileCarouselCard(
                            profile = profile,
                            isActive = isActive,
                            onSelect = { onSelectProfile(profile.id) },
                            onEdit = { onEditProfile(profile.id) },
                        )
                    } else {
                        AddProfileCard(onClick = onAddProfile)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(pageCount) { idx ->
                        val isCurrent = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isCurrent) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun ProfileCarouselCard(
    profile: Profile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp),
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: name + edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isActive) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit profile",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Read-only orientation preview
            OrientationPreview(
                placement = profile.robotPlacement,
                robotX = profile.robotPosition.x,
                robotY = profile.robotPosition.y,
            )

            Spacer(Modifier.height(10.dp))

            // Select button
            if (!isActive) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Select")
                }
            }
        }
    }
}

/**
 * Read-only orientation preview showing the full table layout:
 * [Behind Table area] → [Robot Side 3x5] → Net → [Player Side 3x5]
 * with the robot icon at the saved normalized (0..1) position.
 */
@Composable
fun OrientationPreview(
    placement: String,
    robotX: Float,
    robotY: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val iconSize = 20.dp
    val iconSizePx = with(density) { iconSize.toPx() }

    // Track zone bounds relative to the outer Box
    var behindSize by remember { mutableStateOf(IntSize.Zero) }
    var behindY by remember { mutableStateOf(0f) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridY by remember { mutableStateOf(0f) }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Behind table area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(Color(0xFF616161).copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (placement == "behind") MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else Color(0xFF616161).copy(alpha = 0.15f),
                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    )
                    .onGloballyPositioned {
                        behindSize = it.size
                        behindY = it.positionInParent().y
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (placement != "behind") {
                    Text(
                        "Behind",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        fontSize = 9.sp,
                    )
                }
            }

            // Robot side grid (3x5)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        gridSize = it.size
                        gridY = it.positionInParent().y
                    },
            ) {
                MiniGrid(
                    highlighted = placement == "robot",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Net
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
            )
            Text(
                "Net",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                modifier = Modifier.padding(vertical = 1.dp),
            )

            // Player side grid (3x5)
            MiniGrid(
                highlighted = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Robot icon overlay — positioned using the same math as the editor
        if (placement == "behind" && behindSize.width > 0) {
            val px = robotX * behindSize.width
            val py = behindY + robotY * behindSize.height
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = with(density) { (px - iconSizePx / 2).toDp() },
                        y = with(density) { (py - iconSizePx / 2).toDp() },
                    )
                    .size(iconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else if (placement == "robot" && gridSize.width > 0) {
            val px = robotX * gridSize.width
            val py = gridY + robotY * gridSize.height
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = with(density) { (px - iconSizePx / 2).toDp() },
                        y = with(density) { (py - iconSizePx / 2).toDp() },
                    )
                    .size(iconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MiniGrid(
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF9E9E9E).copy(alpha = 0.06f))
            .border(
                1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else Color(0xFF9E9E9E).copy(alpha = 0.12f),
                RoundedCornerShape(3.dp),
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (col in 0..4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF9E9E9E).copy(alpha = 0.08f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add profile",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "New Profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
