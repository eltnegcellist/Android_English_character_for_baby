package com.eltnegcellist.emma

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Runtime-composed brand mark.
 *
 * Keeping the three validated avatar resources separate avoids making app startup
 * depend on one monolithic PNG while preserving the parent-baby-AI brand concept.
 */
@Composable
internal fun MitsukotobaBrandMark(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.mitsukotoba_parent),
            contentDescription = null,
            modifier = Modifier.size(58.dp),
        )
        Image(
            painter = painterResource(R.drawable.mitsukotoba_baby),
            contentDescription = null,
            modifier = Modifier.size(62.dp),
        )
        Image(
            painter = painterResource(R.drawable.mitsukotoba_ai),
            contentDescription = null,
            modifier = Modifier.size(58.dp),
        )
    }
}
