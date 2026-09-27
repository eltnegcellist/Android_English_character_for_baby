package com.eltnegcellist.emma

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp

/**
 * Unified Mitsukotoba brand mark.
 *
 * The start screen uses the same parent / baby / AI illustrations that appear
 * in the role cards. The colorful app name is part of this visual lockup, so
 * callers do not need to render a second "みつことば" heading.
 */
@Composable
internal fun MitsukotobaBrandMark(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-18).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.mitsukotoba_parent),
                contentDescription = "親",
                modifier = Modifier.size(104.dp),
            )
            Image(
                painter = painterResource(R.drawable.mitsukotoba_baby),
                contentDescription = "赤ちゃん",
                modifier = Modifier.size(100.dp),
            )
            Image(
                painter = painterResource(R.drawable.mitsukotoba_ai),
                contentDescription = "AI",
                modifier = Modifier.size(104.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLetter("み", Color(0xFFF06E72))
            BrandLetter("つ", Color(0xFF8E6552))
            BrandLetter("こ", Color(0xFFF3B943))
            BrandLetter("と", Color(0xFF62C7A0))
            BrandLetter("ば", Color(0xFF765548))
        }
    }
}

@Composable
private fun BrandLetter(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
    )
}
