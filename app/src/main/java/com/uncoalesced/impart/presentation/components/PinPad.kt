// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.presentation.theme.ImpartMono
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.PinEntry
import com.uncoalesced.impart.presentation.theme.ImpartSurface
import com.uncoalesced.impart.presentation.theme.ImpartGround
import com.uncoalesced.impart.presentation.theme.ImpartAccentTint
import com.uncoalesced.impart.presentation.theme.ImpartTextMuted
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary
import java.security.SecureRandom

// Every PIN in Impart is typed here, never on the system keyboard, so no IME sees a digit.
// The digits live in a PinEntry the caller owns; this composable shows only how many were
// typed. Obscured touches are refused on every key.
@Composable
fun PinPad(
    entry: PinEntry,
    digitCount: Int,
    onChanged: () -> Unit,
    onSubmit: () -> Unit,
    shuffled: Boolean,
    submitEnabled: Boolean = digitCount >= DuressManager.MIN_PIN_LENGTH,
    submitLabel: String = "OK",
    modifier: Modifier = Modifier
) {
    val rejectNotifier = rememberObscuredTouchNotifier()
    // A new order every time the pad appears, never while it is on screen.
    val order = remember(shuffled) { if (shuffled) shuffledDigits() else ('0'..'9').toList() }
    val rows = listOf(order.subList(0, 3), order.subList(3, 6), order.subList(6, 9))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .rejectObscuredTouches(rejectNotifier)
            .semantics(mergeDescendants = false) { contentDescription = "PIN, $digitCount digits entered" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PinDots(count = digitCount, capacity = DuressManager.MAX_PIN_LENGTH)
        Spacer(modifier = Modifier.height(16.dp))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    PinKey(label = digit.toString(), description = "digit") {
                        if (entry.push(digit)) onChanged()
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    entry.pop()
                    onChanged()
                },
                modifier = Modifier.size(KEY_SIZE)
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete last digit", tint = ImpartTextMuted)
            }
            PinKey(label = order[9].toString(), description = "digit") {
                if (entry.push(order[9])) onChanged()
            }
            TextButton(onClick = onSubmit, enabled = submitEnabled, modifier = Modifier.size(KEY_SIZE)) {
                Text(
                    text = submitLabel,
                    color = if (submitEnabled) ImpartAccentTint else ImpartTextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PinDots(count: Int, capacity: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(capacity) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (index < count) ImpartTextPrimary else ImpartGround)
                    .border(1.dp, ImpartTextMuted, CircleShape)
            )
        }
    }
}

@Composable
private fun PinKey(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(KEY_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .background(ImpartSurface)
            .semantics { contentDescription = "$description $label" }
    ) {
        Text(
            text = label,
            color = ImpartTextPrimary,
            fontSize = 22.sp,
            fontFamily = ImpartMono,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun shuffledDigits(): List<Char> {
    val random = SecureRandom()
    val digits = ('0'..'9').toMutableList()
    for (i in digits.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = digits[i]
        digits[i] = digits[j]
        digits[j] = tmp
    }
    return digits
}

private val KEY_SIZE = 64.dp

@Preview
@Composable
private fun PinPadPreview() {
    val entry = remember { PinEntry() }
    var count by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.background(ImpartGround).padding(16.dp)) {
        PinPad(entry = entry, digitCount = count, onChanged = { count = entry.length }, onSubmit = {}, shuffled = false)
        Spacer(modifier = Modifier.width(1.dp))
    }
}
