package com.fxxkmoondrop.secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext

internal data class MoonEarOverviewState(
    val deviceName: String = "Space Travel 2",
    val status: String = "Not connected",
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val pairingRequired: Boolean = false,
    val connectionError: String? = null,
    val leftBattery: Int = -1,
    val rightBattery: Int = -1,
    val ancMode: Int = -1,
    val gainLevel: Int = -1,
    val equalizerAvailable: Boolean = false
)

@Composable
internal fun MoonEarOverviewScreen(
    state: MoonEarOverviewState,
    palette: ThemeUtil.Palette,
    onConnect: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onDisconnect: () -> Unit,
    onAncSelected: (Int) -> Unit,
    onGainSelected: (Int) -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val surface = Color(palette.surface)
    val card = Color(palette.card)
    val onSurface = Color(palette.onSurface)
    val onVariant = Color(palette.onVariant)
    val secondaryContainer = Color(palette.secondaryContainer)
    val onSecondaryContainer = Color(palette.onSecondaryContainer)
    val primary = Color(palette.primary)

    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(palette.onPrimary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surface = card,
            background = surface,
            onSurface = onSurface,
            onSurfaceVariant = onVariant,
            outline = Color(palette.outline),
            surfaceVariant = Color(palette.surfaceContainerHighest)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color(palette.onPrimary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surface = card,
            background = surface,
            onSurface = onSurface,
            onSurfaceVariant = onVariant,
            outline = Color(palette.outline),
            surfaceVariant = Color(palette.surfaceContainerHighest)
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 148.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = state.deviceName,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.7).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.connected) Color(palette.green)
                                else if (state.connectionError != null) Color(palette.red)
                                else onVariant.copy(alpha = 0.65f)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.status, fontSize = 14.sp, color = onVariant)
                }

                Spacer(Modifier.height(22.dp))

                if (!state.connected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = secondaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                if (state.connecting) "Connecting to Space Travel 2…"
                                else if (state.connectionError != null) "Couldn't connect"
                                else "Connect your Space Travel 2",
                                color = onSecondaryContainer,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                state.connectionError
                                    ?: "Open the charging case and keep both earbuds nearby.",
                                color = onSecondaryContainer.copy(alpha = 0.82f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = if (state.pairingRequired) onBluetoothSettings else onConnect,
                                enabled = !state.connecting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    when {
                                        state.connecting -> "Connecting…"
                                        state.pairingRequired -> "Pair device"
                                        state.connectionError != null -> "Try again"
                                        else -> "Connect"
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                SectionHeading(
                    title = "Battery",
                    subtitle = "Left and right earbud charge",
                    onSurface = onSurface,
                    onVariant = onVariant
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(palette.cardStroke)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BatteryItem("Left", state.leftBattery, onSurface, onVariant, secondaryContainer, onSecondaryContainer)
                        BatteryItem("Case", -1, onSurface, onVariant, secondaryContainer, onSecondaryContainer)
                        BatteryItem("Right", state.rightBattery, onSurface, onVariant, secondaryContainer, onSecondaryContainer)
                    }
                }
                Text(
                    "Case battery is not reported by the current earbud protocol.",
                    color = onVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                if (state.connected) {
                    Spacer(Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = secondaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBluetoothSettings,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = onSecondaryContainer)
                            ) { Text("Bluetooth settings", maxLines = 1) }
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = onSecondaryContainer)
                            ) { Text("Disconnect") }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionHeading(
                        "Active noise control",
                        "Choose how much of the outside world you hear",
                        onSurface,
                        onVariant
                    )
                    val modes = listOf(
                        Triple(1, "Noise\ncancelling", "◉"),
                        Triple(0, "Off", "○"),
                        Triple(3, "Wind\nreduction", "≈"),
                        Triple(2, "Transparency", "◎")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modes.forEach { (mode, label, glyph) ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(104.dp)
                                    .clickable { onAncSelected(mode) },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (state.ancMode == mode) secondaryContainer else card
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (state.ancMode == mode) 1.5.dp else 1.dp,
                                    if (state.ancMode == mode) primary else Color(palette.cardStroke)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        glyph,
                                        color = if (state.ancMode == mode) onSecondaryContainer else onVariant,
                                        fontSize = 23.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        label,
                                        color = if (state.ancMode == mode) onSecondaryContainer else onSurface,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionHeading("Sound gain", "Set the output level", onSurface, onVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low", "Medium", "High").forEachIndexed { index, label ->
                            FilterChip(
                                selected = state.gainLevel == index,
                                onClick = { onGainSelected(index) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionHeading("More controls", "Fine-tune your listening experience", onSurface, onVariant)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.equalizerAvailable, onClick = onOpenEqualizer),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(palette.cardStroke)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("≋", color = primary, fontSize = 28.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Parametric equalizer", color = onSurface, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (state.equalizerAvailable) "9ECA service detected"
                                    else "Available after 9ECA connection",
                                    color = onVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Text("›", color = onVariant, fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
    onSurface: Color,
    onVariant: Color
) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, fontSize = 12.sp, color = onVariant)
    }
}

@Composable
private fun BatteryItem(
    label: String,
    level: Int,
    onSurface: Color,
    onVariant: Color,
    container: Color,
    onContainer: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (label) {
                    "Case" -> "▱"
                    "Left" -> "◖"
                    else -> "◗"
                },
                color = onContainer,
                fontSize = 29.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(if (level in 0..100) "$level%" else "—", color = onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = onVariant, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, name = "MoonEar · disconnected")
@Composable
private fun MoonEarDisconnectedPreview() {
    val palette = ThemeUtil.Palette(LocalContext.current)
    MoonEarOverviewScreen(
        state = MoonEarOverviewState(),
        palette = palette,
        onConnect = {},
        onBluetoothSettings = {},
        onDisconnect = {},
        onAncSelected = {},
        onGainSelected = {},
        onOpenEqualizer = {}
    )
}

@Preview(showBackground = true, name = "MoonEar · connected")
@Composable
private fun MoonEarConnectedPreview() {
    val palette = ThemeUtil.Palette(LocalContext.current)
    MoonEarOverviewScreen(
        state = MoonEarOverviewState(
            status = "Connected",
            connected = true,
            leftBattery = 82,
            rightBattery = 76,
            ancMode = 1,
            gainLevel = 1,
            equalizerAvailable = true
        ),
        palette = palette,
        onConnect = {},
        onBluetoothSettings = {},
        onDisconnect = {},
        onAncSelected = {},
        onGainSelected = {},
        onOpenEqualizer = {}
    )
}

