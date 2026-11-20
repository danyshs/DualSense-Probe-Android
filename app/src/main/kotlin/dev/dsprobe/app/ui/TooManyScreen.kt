package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.ui.theme.DsColors

@Composable
fun TooManyScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.route_android), color = DsColors.TextDim)
        Text(stringResource(R.string.too_many_title), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.too_many_body), color = DsColors.TextDim)
    }
}
