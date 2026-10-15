package com.example.data.service

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.CubanBanknotes
import com.example.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class BanknoteAiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val promptText = """
        Analiza EXCLUSIVAMENTE billetes de Cuba, moneda Peso Cubano (CUP). Este contador NO acepta monedas ni billetes extranjeros.
        Denominaciones permitidas: 20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 3 y 1 CUP.
        Cuenta cada billete físico una sola vez. Identifica la denominación usando principalmente números impresos, diseño, retrato (p. ej. Mella 1000, Agramonte 500, Abreu 200, Céspedes 100, Calixto 50, Varela 20, Gómez 10, Maceo 5, Che 3, Martí 1), patrones y proporción del billete. No confíes solamente en el color.
        Ignora monedas, tarjetas, recibos, manos y objetos del fondo. No inventes billetes.
        Si un billete está parcialmente visible, cuéntalo solo si existe evidencia clara de que es cubano y puedes determinar su denominación.
        Si hay duda de si un billete es auténtico, si está cortado o irreconocible, no lo sumes a ciegas: colócalo en el arreglo de dudas.
        Devuelve SOLO JSON válido con este formato:
        {
          "billetes": {
            "20000": 0,
            "10000": 0,
            "5000": 0,
            "2000": 0,
            "1000": 0,
            "500": 0,
            "200": 0,
            "100": 0,
            "50": 0,
            "20": 0,
            "10": 0,
            "5": 0,
            "3": 0,
            "1": 0
          },
          "confianza_global": 0.95,
          "dudas": [],
          "verificacion": "Resumen breve de lo observado (ej: 4 billetes de 1000 CUP y 2 de 500 CUP ordenados sobre mesa)."
        }
    """.trimIndent()

    suspend fun analyzeBanknotes(
        bitmap: Bitmap,
        provider: String = "GEMINI",
        customApiKey: String = "",
        customOpenAiKey: String = ""
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        try {
            val base64Image = prepareImageBase64(bitmap)
            val jsonResponseString = if (provider.equals("OPENAI", ignoreCase = true) && customOpenAiKey.isNotBlank()) {
                callOpenAiApi(base64Image, customOpenAiKey)
            } else {
                val apiKey = when {
                    customApiKey.isNotBlank() -> customApiKey
                    BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
                    else -> ""
                }
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("No se ha configurado la clave API de Gemini. Ve a Ajustes para ingresarla o configúrala en AI Studio Secrets.")
                    )
                }
                callGeminiApi(base64Image, apiKey)
            }

            val scanResult = parseAiResponse(jsonResponseString, bitmap)
            Result.success(scanResult)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun prepareImageBase64(source: Bitmap): String {
        val maxDim = 1600
        val width = source.width
        val height = source.height
        val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
        val bytes = output.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun callGeminiApi(base64Image: String, apiKey: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArr = JSONArray()
            val contentObj = JSONObject()
            val partsArr = JSONArray()

            // Prompt text part
            partsArr.put(JSONObject().put("text", promptText))

            // Inline data image part
            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            partsArr.put(JSONObject().put("inlineData", inlineData))

            contentObj.put("parts", partsArr)
            contentsArr.put(contentObj)
            put("contents", contentsArr)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
            put("generationConfig", generationConfig)
        }

        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: "Error de red"
                throw IllegalStateException("Gemini API error ${response.code}: $errorMsg")
            }
            val responseBody = response.body?.string() ?: throw IllegalStateException("Respuesta vacía de Gemini")
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException("No se generó respuesta por seguridad o contenido bloqueado.")
            }
            val firstCand = candidates.getJSONObject(0)
            val parts = firstCand.getJSONObject("content").getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }

    private fun callOpenAiApi(base64Image: String, apiKey: String): String {
        val endpoint = "https://api.openai.com/v1/chat/completions"

        val requestJson = JSONObject().apply {
            put("model", "gpt-4o-mini")
            val messagesArr = JSONArray()
            val userMsg = JSONObject()
            userMsg.put("role", "user")

            val contentArr = JSONArray()
            contentArr.put(JSONObject().apply {
                put("type", "text")
                put("text", promptText)
            })
            contentArr.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64Image")
                    put("detail", "high")
                })
            })
            userMsg.put("content", contentArr)
            messagesArr.put(userMsg)
            put("messages", messagesArr)
            put("response_format", JSONObject().put("type", "json_object"))
            put("temperature", 0.1)
        }

        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: "Error HTTP ${response.code}"
                throw IllegalStateException("OpenAI API error ${response.code}: $errorMsg")
            }
            val responseBody = response.body?.string() ?: throw IllegalStateException("Respuesta vacía de OpenAI")
            val root = JSONObject(responseBody)
            val choices = root.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            return message.getString("content")
        }
    }

    private fun parseAiResponse(rawJson: String, bitmap: Bitmap): ScanResult {
        // Strip markdown code fences if present
        val cleanJson = rawJson
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val json = JSONObject(cleanJson)
        val billetesObj = json.optJSONObject("billetes") ?: JSONObject()
        val counts = mutableMapOf<Int, Int>()

        CubanBanknotes.VALUES.forEach { denom ->
            val count = billetesObj.optInt(denom.toString(), 0).coerceAtLeast(0)
            counts[denom] = count
        }

        val confidence = json.optDouble("confianza_global", 0.90).toFloat().coerceIn(0f, 1f)
        val dudasList = mutableListOf<String>()
        val dudasArr = json.optJSONArray("dudas")
        if (dudasArr != null) {
            for (i in 0 until dudasArr.length()) {
                val d = dudasArr.optString(i)
                if (d.isNotBlank()) dudasList.add(d)
            }
        }
        val verification = json.optString("verificacion", "")

        return ScanResult(
            counts = counts,
            confidence = confidence,
            doubts = dudasList,
            verification = verification,
            bitmap = bitmap
        )
    }
}
