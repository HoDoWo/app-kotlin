package com.hodowo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.hodowo.R
import com.hodowo.data.ArSupport
import com.hodowo.data.ArSupportProvider
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import kotlinx.coroutines.launch

/**
 * The AR support of this phone, checked once for the whole app (#239). It starts
 * as [ArSupport.CHECKING], so no AR entry point is shown before the answer.
 */
@Composable
fun rememberArSupport(): State<ArSupport> {
    val context = LocalContext.current
    val repository = remember { ArSupportProvider.get(context) }
    LaunchedEffect(repository) { repository.refresh() }
    return repository.state.collectAsState()
}

/**
 * The AR tab (#239). It never opens a dead screen: it says whether this phone
 * can do AR and always offers the 2D guide, which works on every phone.
 * On phones that support AR, Start AR opens the AR session (#240).
 */
@Composable
fun ArCoreScreen(
    onStartAr: () -> Unit,
    onOpenGuides: () -> Unit,
    onDiagnose: () -> Unit
) {
    val support by rememberArSupport()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val retry: () -> Unit = { scope.launch { ArSupportProvider.get(context).refresh() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (support == ArSupport.CHECKING) {
                CircularProgressIndicator(color = Primary)
                Spacer(modifier = Modifier.height(16.dp))
                StatusText(
                    title = stringResource(R.string.ar_checking_title),
                    body = stringResource(R.string.ar_checking_body)
                )
                return@Column
            }

            val content = arContent(support)
            StatusIcon(content.icon)
            Spacer(modifier = Modifier.height(20.dp))
            StatusText(title = stringResource(content.title), body = stringResource(content.body))
            Spacer(modifier = Modifier.height(24.dp))

            if (support.offersAr) {
                // On "needs install" the AR screen asks to install Google Play Services for AR.
                Button(
                    onClick = onStartAr,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.ar_start)) }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (support == ArSupport.UNKNOWN) {
                Button(
                    onClick = retry,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.ar_try_again)) }
                Spacer(modifier = Modifier.height(8.dp))
            }
            // The 2D guide is the fallback on every phone, and one tap away.
            if (support.offersAr) {
                OutlinedButton(
                    onClick = onOpenGuides,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.ar_open_my_guides)) }
            } else {
                Button(
                    onClick = onOpenGuides,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.ar_open_my_guides)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDiagnose,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ar_diagnose)) }
        }
    }
}

private data class ArContent(val icon: ImageVector, @param:StringRes val title: Int, @param:StringRes val body: Int)

private fun arContent(support: ArSupport): ArContent = when (support) {
    ArSupport.READY, ArSupport.CHECKING ->
        ArContent(Icons.Filled.ViewInAr, R.string.ar_supported_title, R.string.ar_supported_body)
    ArSupport.NEEDS_INSTALL ->
        ArContent(Icons.Filled.ViewInAr, R.string.ar_supported_title, R.string.ar_needs_install_body)
    ArSupport.UNSUPPORTED ->
        ArContent(Icons.Outlined.PhonelinkErase, R.string.ar_unsupported_title, R.string.ar_unsupported_body)
    ArSupport.UNKNOWN ->
        ArContent(Icons.Outlined.CloudOff, R.string.ar_unknown_title, R.string.ar_unknown_body)
}

@Composable
private fun StatusIcon(icon: ImageVector) {
    Surface(shape = CircleShape, color = SecondaryContainer, modifier = Modifier.size(80.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = Primary, modifier = Modifier.size(40.dp))
        }
    }
}

/** Read out by TalkBack when it changes, so the result of the check is announced. */
@Composable
private fun StatusText(title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
