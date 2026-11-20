package com.unshoo.pixelmusic.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.subcomps.MaterialYouVectorDrawable
import com.unshoo.pixelmusic.presentation.components.subcomps.SineWaveLine
import com.unshoo.pixelmusic.presentation.viewmodel.MainViewModel
import com.unshoo.pixelmusic.utils.AppLocaleManager

@Composable
fun TvWelcomeScreen(
    mainViewModel: MainViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val defaultLangFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            defaultLangFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header with App Identity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_music_note_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.setup_welcome_prefix),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tv_welcome_edition),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.tv_welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            // Language Selection Bar (MD3 Expressive pills)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tv_welcome_select_language),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                val currentLang = remember(context) { AppLocaleManager.currentLanguageTag(context) }
                val languages = listOf(
                    "en" to "🇬🇧 English",
                    "ru" to "🇷🇺 Русский",
                    "de" to "🇩🇪 Deutsch",
                    "es" to "🇪🇸 Español",
                    "fr" to "🇫🇷 Français",
                    "" to "⚙️ " + stringResource(R.string.tv_welcome_system_lang)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    languages.forEach { (tag, name) ->
                        val isSelected = (tag.isEmpty() && currentLang.isEmpty()) || (tag.isNotEmpty() && currentLang.startsWith(tag))
                        TvFocusableBox(
                            onClick = {
                                AppLocaleManager.applyLanguage(context, tag)
                                (context as? android.app.Activity)?.recreate()
                            },
                            focusRequester = if (isSelected) defaultLangFocusRequester else null,
                            shape = RoundedCornerShape(12.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        when {
                                            isFocused -> MaterialTheme.colorScheme.primary
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = when {
                                        isFocused -> MaterialTheme.colorScheme.onPrimary
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Central Art Area with SineWaveLine
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.50f)
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialYouVectorDrawable(
                    modifier = Modifier.fillMaxSize(),
                    drawableResId = R.drawable.welcome_art
                )

                SineWaveLine(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(30.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    animate = true,
                    color = MaterialTheme.colorScheme.primary,
                    alpha = 0.85f,
                    strokeWidth = 6.dp,
                    amplitude = 5.dp,
                    waves = 6.5f,
                    phase = 0f
                )
            }

            // Status message (if scanning or installing demo music)
            AnimatedVisibility(
                visible = scanMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = scanMessage ?: "",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            // Action Cards (D-pad focusable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card 1: Start Now (Main Action)
                TvFocusableBox(
                    onClick = onComplete,
                    modifier = Modifier.width(230.dp),
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.06f,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ) { isFocused ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_play_arrow_filled_24),
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_welcome_start),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.tv_welcome_start_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Card 2: Scan Disk
                TvFocusableBox(
                    onClick = {
                        if (!isScanning) {
                            isScanning = true
                            scanMessage = "..."
                            scope.launch {
                                TvUtils.scanStorageForMusic(context) {
                                    isScanning = false
                                    scanMessage = ""
                                    mainViewModel.retrySync()
                                }
                            }
                        }
                    },
                    modifier = Modifier.width(230.dp),
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.06f,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ) { isFocused ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_folder_24),
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_welcome_scan),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.tv_welcome_scan_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Card 3: Demo Tracks
                TvFocusableBox(
                    onClick = {
                        scanMessage = "..."
                        scope.launch {
                            TvUtils.installDemoMusic(context) {
                                scanMessage = ""
                                mainViewModel.retrySync()
                            }
                        }
                    },
                    modifier = Modifier.width(230.dp),
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.06f,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ) { isFocused ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_music_note_24),
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = stringResource(R.string.tv_welcome_demo),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.tv_welcome_demo_desc),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
