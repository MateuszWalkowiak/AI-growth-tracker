package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image

/**
 * Minimalna obsługa **pogrubienia** w tekście AI.
 * Zamienia `**tekst**` i `*tekst*` na bold span
 */
fun parseBoldMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    // Regex: **bold** lub *bold* (dwa lub jdeen asterisk)
    val regex = Regex("""\*{1,2}(.+?)\*{1,2}""")
    var lastIndex = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
fun AssistantSpeechBubble(
    painter: Painter?,
    speechText: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 100.dp
) {
    if (speechText.isBlank()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom   //postać "stoi" na poziomie dołu dymka
    ) {
        //  ── Postać ──
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "Asystent",
                contentScale = ContentScale.Fit,   //bez przycinania, cała postać widoczna
                modifier = Modifier.size(avatarSize)
            )
        } else {
            // Placeholder - ikona w kwadracie z zaokrąglonymi rogami
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Asystent",
                    modifier = Modifier.size(avatarSize * 0.6f),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        //── Dymek mowy ──
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(
                topStart = 4.dp,      // mały "dziobek" skierowany w stronę postaci
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 16.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Text(
                text = parseBoldMarkdown(speechText),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

//  Wersja z ładowaniem - spinner zamiast dymka gdy tekst jest null
@Composable
fun AssistantSpeechBubbleOrLoading(
    painter: Painter?,
    speechText: String?,          //null = ładowanie, "" = brak klucza/błąd, tekst = gotowe
    loadingLabel: String = "Asystent analizuje…",
    modifier: Modifier = Modifier,
    avatarSize: Dp = 100.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        // ── Postać ──
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "Asystent",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(avatarSize)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Asystent",
                    modifier = Modifier.size(avatarSize * 0.6f),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        //── Zawartość dymka ──
        when {
            speechText == null -> {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            loadingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            speechText.isBlank() -> {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        "Brak klucza API Gemini. Dodaj go w Ustawieniach.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = parseBoldMarkdown(speechText),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
