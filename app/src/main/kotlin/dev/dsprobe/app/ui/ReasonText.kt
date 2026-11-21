package dev.dsprobe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dsprobe.app.route.Reduction

/** Why Android locks a feature, in the user's language. */
@Composable
// stringResource only takes varargs; the copy is of a two-number list
@Suppress("SpreadOperator")
fun reasonText(r: Reduction): String = stringResource(r.reasonRes, *r.reasonArgs.toTypedArray())
