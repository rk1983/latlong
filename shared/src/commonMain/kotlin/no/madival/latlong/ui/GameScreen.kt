package no.madival.latlong.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import no.madival.latlong.domain.ContinentFilter
import org.jetbrains.compose.resources.painterResource

private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate400 = Color(0xFF94A3B8)
private val Slate100 = Color(0xFFF1F5F9)
private val Emerald500 = Color(0xFF22C55E)
private val Red500 = Color(0xFFEF4444)
private val Amber400 = Color(0xFFFBBF24)

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel { GameViewModel() }
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate900
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Emerald500)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Offline Map & Flag Data...",
                        color = Slate400,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val question = state.currentQuestion
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // 1. Top HUD with Score and Persistent Best Streak
                TopHud(
                    score = state.score,
                    streak = state.streak,
                    bestStreak = state.bestStreak
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Mode Switcher (Map Pin vs Flag Quiz)
                ModeSwitcher(
                    currentMode = state.gameMode,
                    onModeSelected = { viewModel.setGameMode(it) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Continent Filter Chips Row
                ContinentFilterRow(
                    selectedFilter = state.continentFilter,
                    onFilterSelected = { viewModel.setContinentFilter(it) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Map Canvas Container & Overlays
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                ) {
                    val targetCode = question?.targetCountry?.code
                    val selectedCode = if (state.isRevealed && state.selectedOptionIndex != null) {
                        question?.options?.getOrNull(state.selectedOptionIndex!!)?.code
                    } else null

                    // In Flag Quiz mode, do not show pin before user answers
                    val pinCoordinate = if (state.gameMode == GameMode.FLAG_QUIZ && !state.isRevealed) {
                        null
                    } else {
                        question?.coordinate
                    }

                    MapCanvas(
                        cachedCountries = state.cachedCountries,
                        targetCoordinate = pinCoordinate,
                        selectedCountryCode = selectedCode,
                        correctCountryCode = targetCode,
                        isRevealed = state.isRevealed,
                        cameraTarget = state.cameraTarget,
                        modifier = Modifier.fillMaxSize(),
                        onViewportSizeChanged = { w, h ->
                            viewModel.onViewportSizeChanged(w, h)
                        }
                    )

                    // Re-center floating button
                    IconButton(
                        onClick = { viewModel.resetCamera() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .background(Slate800.copy(alpha = 0.85f), CircleShape)
                            .border(1.dp, Slate700, CircleShape)
                    ) {
                        Text("🎯", fontSize = 15.sp)
                    }

                    // Prompt Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Slate800.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                            .border(1.dp, Slate700, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (state.gameMode == GameMode.MAP_PIN) {
                                "Which country contains the pin?"
                            } else {
                                "Which country has this flag?"
                            },
                            color = Slate100,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Flag Quiz Card (Center of Map before reveal, compact at top during reveal)
                    if (state.gameMode == GameMode.FLAG_QUIZ && question != null) {
                        val flagRes = FlagResources.getFlag(question.targetCountry.iso2)
                        if (flagRes != null) {
                            if (!state.isRevealed) {
                                // Full Flag Card in center with ContentScale.Fit for Nepal & Switzerland
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .width(230.dp)
                                        .height(148.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Slate800),
                                    border = BorderStroke(1.5.dp, Slate400.copy(alpha = 0.6f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(flagRes),
                                            contentDescription = "Mystery Flag",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            } else {
                                // Compact Flag Mini-card in corner during reveal
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .width(84.dp)
                                        .height(54.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Slate800),
                                    border = BorderStroke(1.5.dp, Slate400),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(flagRes),
                                            contentDescription = "Revealed Flag",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(3.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Reveal Banner
                AnimatedVisibility(
                    visible = state.isRevealed,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut()
                ) {
                    if (question != null) {
                        RevealBanner(
                            isCorrect = state.isCorrect,
                            countryName = question.targetCountry.name,
                            gameMode = state.gameMode,
                            onNext = { viewModel.nextQuestion() }
                        )
                    }
                }

                if (!state.isRevealed) {
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // 6. 2x2 Options Grid
                if (question != null) {
                    OptionsGrid(
                        options = question.options.map { it.name },
                        correctIndex = question.correctIndex,
                        selectedIndex = state.selectedOptionIndex,
                        isRevealed = state.isRevealed,
                        onSelect = { index ->
                            viewModel.onAnswerSelected(index)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ContinentFilterRow(
    selectedFilter: ContinentFilter,
    onFilterSelected: (ContinentFilter) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ContinentFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Slate700 else Slate800)
                    .border(
                        1.dp,
                        if (isSelected) Emerald500 else Slate700,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    color = if (isSelected) Color.White else Slate400,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ModeSwitcher(
    currentMode: GameMode,
    onModeSelected: (GameMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate800, RoundedCornerShape(12.dp))
            .border(1.dp, Slate700, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val mapSelected = currentMode == GameMode.MAP_PIN
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (mapSelected) Emerald500 else Color.Transparent)
                .clickable { onModeSelected(GameMode.MAP_PIN) }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗺️ Map Pin",
                color = if (mapSelected) Color.White else Slate400,
                fontSize = 12.sp,
                fontWeight = if (mapSelected) FontWeight.Bold else FontWeight.Medium
            )
        }

        val flagSelected = currentMode == GameMode.FLAG_QUIZ
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (flagSelected) Emerald500 else Color.Transparent)
                .clickable { onModeSelected(GameMode.FLAG_QUIZ) }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🚩 Flag Quiz",
                color = if (flagSelected) Color.White else Slate400,
                fontSize = 12.sp,
                fontWeight = if (flagSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TopHud(score: Int, streak: Int, bestStreak: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LatLong Trivia",
            color = Slate100,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Slate800, RoundedCornerShape(12.dp))
                    .border(1.dp, if (streak > 0) Amber400.copy(alpha = 0.5f) else Slate700, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "🔥 $streak • 🏆 $bestStreak",
                    color = if (streak > 0) Amber400 else Slate400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .background(Slate800, RoundedCornerShape(12.dp))
                    .border(1.dp, Emerald500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "⭐ $score",
                    color = Emerald500,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RevealBanner(
    isCorrect: Boolean,
    countryName: String,
    gameMode: GameMode,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isCorrect) Emerald500.copy(alpha = 0.15f) else Red500.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isCorrect) Emerald500.copy(alpha = 0.5f) else Red500.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isCorrect) "Correct!" else "Incorrect!",
                color = if (isCorrect) Emerald500 else Red500,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = if (gameMode == GameMode.FLAG_QUIZ) "Flag of $countryName" else "Target: $countryName",
                color = Slate100,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCorrect) Emerald500 else Slate700,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text("Next →", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OptionsGrid(
    options: List<String>,
    correctIndex: Int,
    selectedIndex: Int?,
    isRevealed: Boolean,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    val optionName = options.getOrElse(index) { "" }
                    val isThisCorrect = index == correctIndex
                    val isThisSelected = index == selectedIndex

                    val containerColor = when {
                        isRevealed && isThisCorrect -> Emerald500
                        isRevealed && isThisSelected && !isThisCorrect -> Red500
                        else -> Slate800
                    }

                    val borderColor = when {
                        isRevealed && isThisCorrect -> Emerald500
                        isRevealed && isThisSelected && !isThisCorrect -> Red500
                        else -> Slate700
                    }

                    val textColor = when {
                        isRevealed && (isThisCorrect || isThisSelected) -> Color.White
                        isRevealed -> Slate400
                        else -> Slate100
                    }

                    FilledTonalButton(
                        onClick = { if (!isRevealed) onSelect(index) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = containerColor,
                            contentColor = textColor
                        ),
                        enabled = !isRevealed || isThisCorrect || isThisSelected
                    ) {
                        Text(
                            text = optionName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
