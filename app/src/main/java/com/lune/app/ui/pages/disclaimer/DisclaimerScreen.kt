package com.lune.app.ui.pages.disclaimer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lune.app.ui.components.PrimaryCta
import androidx.compose.ui.res.stringResource
import com.lune.app.R

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(0.5f))

        // App logo
        Image(
            androidx.compose.ui.res.painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(16.dp))

        // App name
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(32.dp))

        // Disclaimer card �X blob-like rounded shape
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .padding(28.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.disclaimer_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.disclaimer_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryCta(
            text = stringResource(R.string.disclaimer_accept),
            onClick = onAccept,
            modifier = Modifier.testTag("btn_accept"),
        )

        Spacer(Modifier.height(32.dp))
    }
    }
}
