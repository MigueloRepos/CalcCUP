package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CubanBanknotes
import com.example.data.model.HistoryItem
import com.example.data.model.ScanResult
import com.example.data.repository.BanknoteRepository
import com.example.data.service.BanknoteAiService
import com.example.data.service.BanknoteMlKitService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

data class BanknoteUiState(
    val counts: Map<Int, Int> = emptyMap(),
    val totalAmount: Long = 0L,
    val totalBills: Int = 0,
    val history: List<HistoryItem> = emptyList(),
    val isAnalyzing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val pendingScanResult: ScanResult? = null,
    val pendingCounts: Map<Int, Int> = emptyMap(),
    val showSettingsDialog: Boolean = false,
    val showBundlesDialog: Boolean = false,
    val showClearDialog: Boolean = false,
    val directEditDenomination: Int? = null,
    val customGeminiKey: String = "",
    val customOpenAiKey: String = "",
    val aiProvider: String = "GEMINI",
    val hapticsEnabled: Boolean = true,
    val showRareDenominations: Boolean = false
) {
    val formattedTotal: String get() = formatMoney(totalAmount)

    val displayedDenominations get() = if (showRareDenominations) {
        CubanBanknotes.DENOMINATIONS
    } else {
        CubanBanknotes.DENOMINATIONS.filter { it.isPrimaryInCirculation }
    }
}

fun formatMoney(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CU"))
    return "$" + formatter.format(amount) + " CUP"
}

class BanknoteCounterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BanknoteRepository(application.applicationContext)
    private val aiService = BanknoteAiService()
    private val mlKitService = BanknoteMlKitService()

    private val _isAnalyzing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>("Listo para contar o escanear.")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _pendingScanResult = MutableStateFlow<ScanResult?>(null)
    private val _pendingCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val _showSettingsDialog = MutableStateFlow(false)
    private val _showBundlesDialog = MutableStateFlow(false)
    private val _showClearDialog = MutableStateFlow(false)
    private val _directEditDenom = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<BanknoteUiState> = combine(
        repository.counts,
        repository.history,
        repository.customGeminiKey,
        repository.customOpenAiKey,
        repository.aiProvider,
        repository.hapticsEnabled,
        repository.showRareDenominations,
        _isAnalyzing,
        _statusMessage,
        _errorMessage,
        _pendingScanResult,
        _pendingCounts,
        _showSettingsDialog,
        _showBundlesDialog,
        _showClearDialog,
        _directEditDenom
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val counts = args[0] as Map<Int, Int>
        @Suppress("UNCHECKED_CAST")
        val history = args[1] as List<HistoryItem>
        val customGeminiKey = args[2] as String
        val customOpenAiKey = args[3] as String
        val aiProvider = args[4] as String
        val hapticsEnabled = args[5] as Boolean
        val showRareDenominations = args[6] as Boolean
        val isAnalyzing = args[7] as Boolean
        val statusMessage = args[8] as String?
        val errorMessage = args[9] as String?
        val pendingScanResult = args[10] as ScanResult?
        @Suppress("UNCHECKED_CAST")
        val pendingCounts = args[11] as Map<Int, Int>
        val showSettingsDialog = args[12] as Boolean
        val showBundlesDialog = args[13] as Boolean
        val showClearDialog = args[14] as Boolean
        val directEditDenom = args[15] as Int?

        val totalAmount = counts.entries.sumOf { (denom, count) -> denom.toLong() * count }
        val totalBills = counts.values.sum()

        BanknoteUiState(
            counts = counts,
            totalAmount = totalAmount,
            totalBills = totalBills,
            history = history,
            isAnalyzing = isAnalyzing,
            statusMessage = statusMessage,
            errorMessage = errorMessage,
            pendingScanResult = pendingScanResult,
            pendingCounts = pendingCounts,
            showSettingsDialog = showSettingsDialog,
            showBundlesDialog = showBundlesDialog,
            showClearDialog = showClearDialog,
            directEditDenomination = directEditDenom,
            customGeminiKey = customGeminiKey,
            customOpenAiKey = customOpenAiKey,
            aiProvider = aiProvider,
            hapticsEnabled = hapticsEnabled,
            showRareDenominations = showRareDenominations
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BanknoteUiState()
    )

    fun increment(denomination: Int, delta: Int) {
        val current = repository.counts.value[denomination] ?: 0
        val next = (current + delta).coerceAtLeast(0)
        repository.updateCount(denomination, next)
    }

    fun setCount(denomination: Int, count: Int) {
        repository.updateCount(denomination, count.coerceAtLeast(0))
        _directEditDenom.value = null
    }

    fun quickAdd(denomination: Int, delta: Int) {
        increment(denomination, delta)
    }

    fun clearAll() {
        repository.clearAll()
        _statusMessage.value = "Contador reiniciado a $0 CUP"
        _showClearDialog.value = false
    }

    fun analyzeBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _statusMessage.value = "Analizando billetes cubanos con IA…"
            _errorMessage.value = null

            val provider = repository.aiProvider.value
            val customGemini = repository.customGeminiKey.value
            val customOpenAi = repository.customOpenAiKey.value

            val result = if (provider.equals("MLKIT", ignoreCase = true)) {
                _statusMessage.value = "Reconociendo con Google ML Kit (Offline)…"
                mlKitService.recognizeBanknotes(bitmap)
            } else {
                aiService.analyzeBanknotes(
                    bitmap = bitmap,
                    provider = provider,
                    customApiKey = customGemini,
                    customOpenAiKey = customOpenAi
                )
            }

            _isAnalyzing.value = false
            result.onSuccess { scanResult ->
                _pendingScanResult.value = scanResult
                _pendingCounts.value = scanResult.counts
                val detectedBills = scanResult.totalBills
                val detectedMoney = formatMoney(scanResult.totalAmount)
                _statusMessage.value = "Se detectaron $detectedBills billetes ($detectedMoney). Revisa antes de confirmar."
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Error al analizar imagen"
                _statusMessage.value = "Error en el análisis."
            }
        }
    }

    fun updatePendingCount(denomination: Int, newCount: Int) {
        val current = _pendingCounts.value.toMutableMap()
        current[denomination] = newCount.coerceAtLeast(0)
        _pendingCounts.value = current
    }

    fun confirmAndAddPending() {
        val pending = _pendingCounts.value
        val scan = _pendingScanResult.value
        val totalDelta = pending.entries.sumOf { it.key.toLong() * it.value }
        val billsDelta = pending.values.sum()

        val reason = "📸 Foto IA: +$billsDelta billetes (${formatMoney(totalDelta)})"
        repository.addCounts(pending, reason)

        _pendingScanResult.value = null
        _pendingCounts.value = emptyMap()
        _statusMessage.value = "Billetes sumados al total con éxito."
    }

    fun confirmAndReplacePending() {
        val pending = _pendingCounts.value
        val total = pending.entries.sumOf { it.key.toLong() * it.value }
        val bills = pending.values.sum()

        val reason = "📸 Foto IA (Reemplazo total): $bills billetes (${formatMoney(total)})"
        repository.replaceAllCounts(pending, reason)

        _pendingScanResult.value = null
        _pendingCounts.value = emptyMap()
        _statusMessage.value = "Total reemplazado con el conteo de la foto."
    }

    fun dismissPendingScan() {
        _pendingScanResult.value = null
        _pendingCounts.value = emptyMap()
        _statusMessage.value = "Análisis descartado."
    }

    fun openSettings() {
        _showSettingsDialog.value = true
    }

    fun closeSettings() {
        _showSettingsDialog.value = false
    }

    fun openBundles() {
        _showBundlesDialog.value = true
    }

    fun closeBundles() {
        _showBundlesDialog.value = false
    }

    fun openClearDialog() {
        _showClearDialog.value = true
    }

    fun closeClearDialog() {
        _showClearDialog.value = false
    }

    fun openDirectEdit(denomination: Int) {
        _directEditDenom.value = denomination
    }

    fun closeDirectEdit() {
        _directEditDenom.value = null
    }

    fun cycleAiProvider() {
        val current = repository.aiProvider.value
        val next = when (current) {
            "MLKIT" -> "GEMINI"
            "GEMINI" -> if (repository.customOpenAiKey.value.isNotBlank()) "OPENAI" else "MLKIT"
            else -> "MLKIT"
        }
        repository.setAiProvider(next)
        _statusMessage.value = when (next) {
            "MLKIT" -> "Modo activado: Google ML Kit (Offline / Sin internet)"
            "GEMINI" -> "Modo activado: Google Gemini (IA en la nube)"
            else -> "Modo activado: OpenAI GPT"
        }
    }

    fun saveApiSettings(geminiKey: String, openAiKey: String, provider: String) {
        repository.setCustomGeminiKey(geminiKey)
        repository.setCustomOpenAiKey(openAiKey)
        repository.setAiProvider(provider)
        _showSettingsDialog.value = false
        _statusMessage.value = "Ajustes de IA guardados."
    }

    fun toggleHaptics(enabled: Boolean) {
        repository.setHapticsEnabled(enabled)
    }

    fun toggleRareDenominations(enabled: Boolean) {
        repository.setShowRareDenominations(enabled)
    }

    fun clearHistory() {
        repository.clearHistory()
        _statusMessage.value = "Historial borrado."
    }

    fun deleteHistoryItem(id: String) {
        repository.deleteHistoryItem(id)
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun generateShareReport(): String {
        val counts = repository.counts.value
        val total = counts.entries.sumOf { it.key.toLong() * it.value }
        val totalBills = counts.values.sum()

        val sb = StringBuilder()
        sb.append("🇨🇺 CONTADOR DE BILLETES CUBANOS (CUP)\n")
        sb.append("═══════════════════════════════\n")
        sb.append("Total: ${formatMoney(total)}\n")
        sb.append("Cantidad de billetes: $totalBills\n")
        sb.append("Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date())}\n\n")
        sb.append("DESGLOSE:\n")

        CubanBanknotes.VALUES.forEach { d ->
            val qty = counts[d] ?: 0
            if (qty > 0) {
                val sub = d.toLong() * qty
                sb.append("• $$d CUP: $qty ud. = ${formatMoney(sub)}\n")
            }
        }

        sb.append("\nFAJOS (100 billetes por fajo):\n")
        CubanBanknotes.VALUES.forEach { d ->
            val qty = counts[d] ?: 0
            if (qty > 0) {
                val fajos = qty / 100
                val sueltos = qty % 100
                if (fajos > 0) {
                    sb.append("• $$d: $fajos fajo(s) + $sueltos sueltos\n")
                }
            }
        }
        sb.append("═══════════════════════════════\n")
        sb.append("Generado con Contador CUP")
        return sb.toString()
    }
}
