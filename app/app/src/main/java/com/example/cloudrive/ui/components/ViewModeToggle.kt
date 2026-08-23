package com.example.cloudrive.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.example.cloudrive.data.local.ViewMode

@Composable
fun ViewModeToggle(mode: ViewMode, onToggle: (ViewMode) -> Unit) {
    IconButton(onClick = { onToggle(if (mode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) }) {
        Icon(
            imageVector = if (mode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
            contentDescription = if (mode == ViewMode.LIST) "Switch to grid view" else "Switch to list view"
        )
    }
}
