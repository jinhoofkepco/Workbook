package com.mathworkbook.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathworkbook.app.core.domain.KeyboardType

@Composable
fun VirtualMathKeyboard(
    keyboardType: KeyboardType,
    onInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String = "제출",
    modifier: Modifier = Modifier
) {
    val extraKeys = when (keyboardType) {
        KeyboardType.INTEGER -> listOf("-", "⌫", "C")
        KeyboardType.DECIMAL -> listOf(".", "-", "⌫", "C")
        KeyboardType.FRACTION -> listOf("/", "-", "⌫", "C")
        KeyboardType.ANGLE -> listOf("°", "도", "⌫", "C")
        KeyboardType.MONEY -> listOf("원", "⌫", "C")
        KeyboardType.MULTIPLE_CHOICE -> listOf("⌫", "C")
        KeyboardType.MULTI_FIELD -> listOf(".", "/", "-", "원", "°", "⌫", "C")
    }
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf("0") + extraKeys.take(2)
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "C" -> onClear()
                                else -> onInput(key)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(key)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            extraKeys.drop(2).forEach { key ->
                OutlinedButton(
                    onClick = {
                        when (key) {
                            "⌫" -> onBackspace()
                            "C" -> onClear()
                            else -> onInput(key)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(key)
                }
            }
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .weight(2f)
                    .padding(start = 4.dp)
            ) {
                Text(submitLabel)
            }
        }
    }
}
