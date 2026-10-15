package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CubanBanknotes
import com.example.ui.theme.CubanBluePrimary
import com.example.ui.viewmodel.formatMoney

@Composable
fun DirectEditDialog(
    denominationValue: Int,
    currentCount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentCount.toString()) }
    val denom = CubanBanknotes.find(denominationValue)

    val countInt = textValue.toIntOrNull() ?: 0
    val subtotal = denominationValue.toLong() * countInt

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("direct_edit_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Billetes de $$denominationValue CUP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (denom != null) {
                    Text(
                        text = denom.portrait,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 7) {
                            textValue = input
                        }
                    },
                    label = { Text("Cantidad de billetes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = {
                        onConfirm(countInt)
                    }),
                    modifier = Modifier.fillMaxWidth().testTag("direct_count_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Subtotal: ${formatMoney(subtotal)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = denom?.color ?: CubanBluePrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 10, 50, 100, 500).forEach { preset ->
                        OutlinedButton(
                            onClick = { textValue = preset.toString() },
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(text = "$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = { onConfirm(countInt) },
                        modifier = Modifier.weight(1f).testTag("confirm_direct_edit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CubanBluePrimary)
                    ) {
                        Text("Establecer")
                    }
                }
            }
        }
    }
}
