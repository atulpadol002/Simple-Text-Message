package com.atul.messageapp.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.atul.messageapp.R
import com.atul.messageapp.sms.DefaultSmsManager

@Composable
fun SplashScreen(
    onPermissionFlow: () -> Unit,
    onDirectHome: () -> Unit
) {
    val context = LocalContext.current

    val isDefaultSmsApp = remember(context) {
        DefaultSmsManager(context).isDefaultSmsApp()
    }

    LaunchedEffect(isDefaultSmsApp) {
        if (isDefaultSmsApp) {
            onDirectHome()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!isDefaultSmsApp) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val compactHeight = maxHeight < 620.dp
                val requiresScrolling = maxHeight < 660.dp || LocalDensity.current.fontScale > 1.2f
                val logoSize = when {
                    maxHeight >= 760.dp -> 152.dp
                    compactHeight -> 132.dp
                    else -> 144.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (requiresScrolling) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier.fillMaxHeight()
                            }
                        )
                        .padding(
                            horizontal = 24.dp,
                            vertical = if (compactHeight) 16.dp else 24.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(if (compactHeight) 4.dp else 12.dp))

                    Box(
                        modifier = Modifier
                            .size(logoSize + 28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.message_logo_symbol),
                            contentDescription = "Message logo",
                            modifier = Modifier.size(logoSize),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(if (compactHeight) 16.dp else 22.dp))

                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = buildAnnotatedString {
                            append("Fast ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append("•")
                            }
                            append(" Secure ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append("•")
                            }
                            append(" Reliable")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (requiresScrolling) {
                        Spacer(modifier = Modifier.height(if (compactHeight) 20.dp else 32.dp))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 336.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = "Your messages are always private and secure.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onPermissionFlow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 336.dp)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Get Started",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
