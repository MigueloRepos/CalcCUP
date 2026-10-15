package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CubanBanknotes
import com.example.data.model.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class BanknoteRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("contador_cubano_prefs", Context.MODE_PRIVATE)

    private val _counts = MutableStateFlow<Map<Int, Int>>(loadCounts())
    val counts: StateFlow<Map<Int, Int>> = _counts.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(loadHistory())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _customGeminiKey = MutableStateFlow(prefs.getString("custom_gemini_key", "") ?: "")
    val customGeminiKey: StateFlow<String> = _customGeminiKey.asStateFlow()

    private val _customOpenAiKey = MutableStateFlow(prefs.getString("custom_openai_key", "") ?: "")
    val customOpenAiKey: StateFlow<String> = _customOpenAiKey.asStateFlow()

    private val _aiProvider = MutableStateFlow(prefs.getString("ai_provider", "GEMINI") ?: "GEMINI")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _showRareDenominations = MutableStateFlow(prefs.getBoolean("show_rare_denoms", false))
    val showRareDenominations: StateFlow<Boolean> = _showRareDenominations.asStateFlow()

    private fun loadCounts(): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val jsonStr = prefs.getString("banknote_counts", null)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                CubanBanknotes.VALUES.forEach { denom ->
                    result[denom] = json.optInt(denom.toString(), 0)
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        CubanBanknotes.VALUES.forEach { denom ->
            result[denom] = 0
        }
        return result
    }

    private fun saveCounts(map: Map<Int, Int>) {
        val json = JSONObject()
        map.forEach { (denom, count) ->
            json.put(denom.toString(), count)
        }
        prefs.edit().putString("banknote_counts", json.toString()).apply()
        _counts.value = map
    }

    fun updateCount(denomination: Int, newCount: Int) {
        val current = _counts.value.toMutableMap()
        current[denomination] = newCount.coerceAtLeast(0)
        saveCounts(current)
    }

    fun addCounts(additions: Map<Int, Int>, reason: String) {
        val current = _counts.value.toMutableMap()
        var deltaAmount = 0L
        var deltaBills = 0
        additions.forEach { (denom, count) ->
            if (count > 0) {
                current[denom] = (current[denom] ?: 0) + count
                deltaAmount += denom.toLong() * count
                deltaBills += count
            }
        }
        saveCounts(current)

        if (deltaBills > 0) {
            addHistoryItem(
                HistoryItem(
                    description = reason,
                    amountDelta = deltaAmount,
                    billCountDelta = deltaBills,
                    isAddition = true,
                    breakdown = additions.filter { it.value > 0 }
                )
            )
        }
    }

    fun replaceAllCounts(newCounts: Map<Int, Int>, reason: String) {
        val current = _counts.value
        val currentTotal = current.entries.sumOf { it.key.toLong() * it.value }
        val currentBills = current.values.sum()

        val fullMap = CubanBanknotes.VALUES.associateWith { newCounts[it] ?: 0 }
        saveCounts(fullMap)

        val newTotal = fullMap.entries.sumOf { it.key.toLong() * it.value }
        val newBills = fullMap.values.sum()

        addHistoryItem(
            HistoryItem(
                description = reason,
                amountDelta = newTotal - currentTotal,
                billCountDelta = newBills - currentBills,
                isAddition = newTotal >= currentTotal,
                breakdown = fullMap.filter { it.value > 0 }
            )
        )
    }

    fun clearAll() {
        val current = _counts.value
        val total = current.entries.sumOf { it.key.toLong() * it.value }
        val bills = current.values.sum()

        val empty = CubanBanknotes.VALUES.associateWith { 0 }
        saveCounts(empty)

        if (bills > 0) {
            addHistoryItem(
                HistoryItem(
                    description = "Contador reiniciado a cero",
                    amountDelta = -total,
                    billCountDelta = -bills,
                    isAddition = false
                )
            )
        }
    }

    private fun loadHistory(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val jsonStr = prefs.getString("history_items", null) ?: return list
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val breakdownMap = mutableMapOf<Int, Int>()
                val bJson = obj.optJSONObject("breakdown")
                if (bJson != null) {
                    val keys = bJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        breakdownMap[key.toInt()] = bJson.getInt(key)
                    }
                }
                list.add(
                    HistoryItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        description = obj.optString("description", ""),
                        amountDelta = obj.optLong("amountDelta", 0L),
                        billCountDelta = obj.optInt("billCountDelta", 0),
                        isAddition = obj.optBoolean("isAddition", true),
                        breakdown = breakdownMap
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun addHistoryItem(item: HistoryItem) {
        val current = _history.value.toMutableList()
        current.add(0, item) // most recent first
        val trimmed = if (current.size > 50) current.take(50) else current
        _history.value = trimmed

        val arr = JSONArray()
        trimmed.forEach { h ->
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("timestamp", h.timestamp)
            obj.put("description", h.description)
            obj.put("amountDelta", h.amountDelta)
            obj.put("billCountDelta", h.billCountDelta)
            obj.put("isAddition", h.isAddition)
            val bJson = JSONObject()
            h.breakdown.forEach { (d, c) -> bJson.put(d.toString(), c) }
            obj.put("breakdown", bJson)
            arr.put(obj)
        }
        prefs.edit().putString("history_items", arr.toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history_items").apply()
    }

    fun deleteHistoryItem(id: String) {
        val current = _history.value.filter { it.id != id }
        _history.value = current
        val arr = JSONArray()
        current.forEach { h ->
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("timestamp", h.timestamp)
            obj.put("description", h.description)
            obj.put("amountDelta", h.amountDelta)
            obj.put("billCountDelta", h.billCountDelta)
            obj.put("isAddition", h.isAddition)
            arr.put(obj)
        }
        prefs.edit().putString("history_items", arr.toString()).apply()
    }

    fun setCustomGeminiKey(key: String) {
        val clean = key.trim()
        _customGeminiKey.value = clean
        prefs.edit().putString("custom_gemini_key", clean).apply()
    }

    fun setCustomOpenAiKey(key: String) {
        val clean = key.trim()
        _customOpenAiKey.value = clean
        prefs.edit().putString("custom_openai_key", clean).apply()
    }

    fun setAiProvider(provider: String) {
        _aiProvider.value = provider
        prefs.edit().putString("ai_provider", provider).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun setShowRareDenominations(enabled: Boolean) {
        _showRareDenominations.value = enabled
        prefs.edit().putBoolean("show_rare_denoms", enabled).apply()
    }
}
