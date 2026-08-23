package com.example.cloudrive.navigation

import com.example.cloudrive.data.model.FileItem

/**
 * Pragmatic in-memory hand-off for the preview screen's sibling image set.
 *
 * Compose Navigation can't cleanly pass a `List<FileItem>` as a nav argument, and this
 * codebase doesn't use a typed SavedStateHandle argument system elsewhere, so — matching
 * the simplicity level of the rest of the nav layer — the calling screen just stashes the
 * list it was already displaying here immediately before navigating to [Screen.Preview],
 * and [com.example.cloudrive.ui.preview.PreviewScreen] reads it back on entry.
 *
 * Screens that can't reasonably populate this (Search, Trash, deep links, etc.) leave it
 * as-is; [com.example.cloudrive.ui.preview.PreviewScreen] falls back to fetching the single
 * file directly when the tapped id isn't found in [siblingImages].
 */
object PreviewContext {
    var siblingImages: List<FileItem> = emptyList()
}
