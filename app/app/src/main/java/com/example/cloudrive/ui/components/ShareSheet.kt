package com.example.cloudrive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cloudrive.data.model.ShareToken
import kotlinx.coroutines.launch

private data class ExpiryOption(val label: String, val seconds: Int?)

private val expiryOptions = listOf(
    ExpiryOption("1 hour", 3600),
    ExpiryOption("24 hours", 86_400),
    ExpiryOption("7 days", 7 * 86_400),
    ExpiryOption("Never", null)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    fileName: String,
    onDismiss: () -> Unit,
    onCreateLink: suspend (expiresInSeconds: Int?) -> Result<ShareToken>,
    onShareVia: (url: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedIndex by remember { mutableIntStateOf(2) } // default 7 days
    var isCreating by remember { mutableStateOf(false) }
    var shareToken by remember { mutableStateOf<ShareToken?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
            Text(
                text = "Share \"$fileName\"",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Creates a public download link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Link expires",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                expiryOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = expiryOptions.size),
                        enabled = shareToken == null
                    ) {
                        Text(option.label)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Expiring links are safer on a self-hosted server. You can revoke any link later from Profile > My Shared Links.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            shareToken?.let { token ->
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = token.url,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(token.url)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (shareToken == null) {
                Button(
                    onClick = {
                        isCreating = true
                        error = null
                        scope.launch {
                            onCreateLink(expiryOptions[selectedIndex].seconds)
                                .onSuccess { shareToken = it }
                                .onFailure { error = it.message ?: "Failed to create link" }
                            isCreating = false
                        }
                    },
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create link")
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onShareVia(shareToken!!.url) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share via…")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { clipboardManager.setText(AnnotatedString(shareToken!!.url)) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Copy link")
                    }
                }
            }
        }
    }
}
