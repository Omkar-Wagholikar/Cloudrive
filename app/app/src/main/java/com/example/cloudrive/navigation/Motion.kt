package com.example.cloudrive.navigation

import android.content.Context
import android.provider.Settings

/**
 * Design brief "Interactions & Motion" (M3 redesign, ~lines 92-95): all of the named
 * transition patterns below (shared-axis X, fade-through) must honor the OS-level
 * "Remove animations" accessibility toggle by disabling transforms and keeping only fades.
 *
 * That toggle is exposed as [Settings.Global.ANIMATOR_DURATION_SCALE] — the same setting
 * Android's own window/activity transitions read (a scale of 0 means "no animations").
 * There's no existing reduced-motion plumbing in this codebase yet, so this is a small,
 * standalone check rather than a full CompositionLocal settings system.
 */
fun isReducedMotionEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale == 0f
}
