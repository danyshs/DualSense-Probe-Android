package dev.dsprobe.app.debug

import androidx.annotation.StringRes

/**
 * One checkbox of the trace checklist: a row belongs to it when its control is one of [prefixes] or
 * sits under one.
 */
data class TraceGroup(
    val id: String,
    @param:StringRes val labelRes: Int,
    val prefixes: List<String>,
)
