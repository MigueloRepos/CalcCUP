package com.example.data.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

data class BanknoteDenomination(
    val value: Int,
    val name: String,
    val portrait: String,
    val color: Color,
    val isPrimaryInCirculation: Boolean = true
) {
    val formattedName: String get() = "$$value CUP"
}

object CubanBanknotes {
    // Official CUP banknote denominations in circulation order
    val DENOMINATIONS: List<BanknoteDenomination> = listOf(
        BanknoteDenomination(20000, "20,000 Pesos", "Serie Conmemorativa", ColorDenom20000),
        BanknoteDenomination(10000, "10,000 Pesos", "Serie Conmemorativa", ColorDenom10000),
        BanknoteDenomination(5000, "5,000 Pesos", "Serie Conmemorativa", ColorDenom5000),
        BanknoteDenomination(2000, "2,000 Pesos", "Serie Conmemorativa", ColorDenom2000),
        BanknoteDenomination(1000, "1,000 Pesos", "Julio Antonio Mella", ColorDenom1000),
        BanknoteDenomination(500, "500 Pesos", "Ignacio Agramonte", ColorDenom500),
        BanknoteDenomination(200, "200 Pesos", "Marta Abreu", ColorDenom200),
        BanknoteDenomination(100, "100 Pesos", "Carlos M. de Céspedes", ColorDenom100),
        BanknoteDenomination(50, "50 Pesos", "Calixto García", ColorDenom50),
        BanknoteDenomination(20, "20 Pesos", "Félix Varela", ColorDenom20),
        BanknoteDenomination(10, "10 Pesos", "Máximo Gómez", ColorDenom10),
        BanknoteDenomination(5, "5 Pesos", "Antonio Maceo", ColorDenom5),
        BanknoteDenomination(3, "3 Pesos", "Ernesto Che Guevara", ColorDenom3, isPrimaryInCirculation = false),
        BanknoteDenomination(1, "1 Peso", "José Martí", ColorDenom1, isPrimaryInCirculation = false)
    )

    val VALUES: List<Int> = DENOMINATIONS.map { it.value }

    fun find(value: Int): BanknoteDenomination? = DENOMINATIONS.find { it.value == value }
}

data class ScanResult(
    val counts: Map<Int, Int>,
    val confidence: Float,
    val doubts: List<String>,
    val verification: String,
    val bitmap: Bitmap? = null,
    val extractedTextLines: List<String> = emptyList()
) {
    val totalBills: Int get() = counts.values.sum()
    val totalAmount: Long get() = counts.entries.sumOf { (denom, count) -> denom.toLong() * count }
}

data class HistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val amountDelta: Long,
    val billCountDelta: Int,
    val isAddition: Boolean = true,
    val breakdown: Map<Int, Int> = emptyMap()
)
