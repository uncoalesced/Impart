// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.presentation.theme.ImpartAccent
import com.uncoalesced.impart.presentation.theme.ImpartAccentShade
import com.uncoalesced.impart.presentation.theme.ImpartAccentTint
import com.uncoalesced.impart.presentation.theme.ImpartMono
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary

// The mark, drawn from the committed cell table on a Canvas. Never on the decoy or under
// the cloak (spec 09 section 8). The canvas keeps one cell of clearspace on every side.
@Composable
fun ImpartMark(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Impart" }
    ) {
        val cell = this.size.minDimension / (ImpartMarkCells.GRID + 2)
        val origin = cell
        ImpartMarkCells.drawable().forEach { mark ->
            val color = toneColor(mark.tone)
            val cx = origin + (mark.col + 0.5f) * cell
            val cy = origin + (mark.row + 0.5f) * cell
            when (mark.shape) {
                MarkShape.CIRCLE -> drawCircle(color, radius = cell * ImpartMarkCells.CIRCLE_DIAMETER / 2f, center = Offset(cx, cy))
                MarkShape.SQUARE -> {
                    val side = cell * ImpartMarkCells.SQUARE_SIDE
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cx - side / 2f, cy - side / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(side * ImpartMarkCells.SQUARE_CORNER)
                    )
                }
            }
        }
    }
}

// Lowercase "impart" in Mono SemiBold, Porcelain on dark grounds.
@Composable
fun ImpartWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 22.sp) {
    Text(
        text = "impart",
        modifier = modifier,
        color = ImpartTextPrimary,
        fontFamily = ImpartMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize
    )
}

// The mark above the wordmark: lock, legal and About screens.
@Composable
fun ImpartLockup(modifier: Modifier = Modifier, markSize: Dp = 72.dp) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ImpartMark(size = markSize)
        Spacer(modifier = Modifier.height(4.dp))
        ImpartWordmark()
    }
}

private fun toneColor(tone: MarkTone): Color = when (tone) {
    MarkTone.SHADE -> ImpartAccentShade
    MarkTone.BASE -> ImpartAccent
    MarkTone.TINT -> ImpartAccentTint
}

@Preview
@Composable
private fun ImpartLockupPreview() {
    ImpartLockup()
}
