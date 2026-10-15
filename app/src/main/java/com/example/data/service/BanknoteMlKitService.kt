package com.example.data.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.model.CubanBanknotes
import com.example.data.model.ScanResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

class BanknoteMlKitService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val validDenominations = setOf(20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 3, 1)

    suspend fun recognizeBanknotes(bitmap: Bitmap): Result<ScanResult> = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { text ->
                        if (continuation.isActive) continuation.resume(text)
                    }
                    .addOnFailureListener { ex ->
                        if (continuation.isActive) continuation.resumeWithException(ex)
                    }
            }

            // Find all denomination matches with their bounding boxes
            val detectedTokens = mutableListOf<DetectedDenom>()
            val fullText = visionText.text.uppercase()
            val hasCubanKeywords = fullText.contains("CUBA") ||
                    fullText.contains("PESO") ||
                    fullText.contains("BANCO") ||
                    fullText.contains("CENTRAL")

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val cleaned = element.text
                            .replace("$", "")
                            .replace(".", "")
                            .replace(",", "")
                            .trim()

                        val denomValue = cleaned.toIntOrNull()
                        if (denomValue != null && validDenominations.contains(denomValue)) {
                            val box = element.boundingBox ?: Rect()
                            detectedTokens.add(DetectedDenom(denomValue, box))
                        }
                    }
                }
            }

            // Filter out duplicate reads of the same physical bill
            // Banknotes typically print the number in 2 corners (top-left & bottom-right).
            // Cluster tokens of the same denomination that are close together.
            val clusteredCounts = mutableMapOf<Int, Int>()
            val groupedByDenom = detectedTokens.groupBy { it.value }

            val doubts = mutableListOf<String>()

            groupedByDenom.forEach { (value, tokens) ->
                val clusters = mutableListOf<MutableList<DetectedDenom>>()
                for (token in tokens) {
                    val matchingCluster = clusters.find { cluster ->
                        cluster.any { other ->
                            val distSq = (token.box.centerX() - other.box.centerX()).let { it * it } +
                                    (token.box.centerY() - other.box.centerY()).let { it * it }
                            // If distance is less than 25% of image width, they likely belong to the same banknote
                            val threshold = (bitmap.width * 0.35f).let { it * it }
                            distSq < threshold
                        }
                    }
                    if (matchingCluster != null) {
                        matchingCluster.add(token)
                    } else {
                        clusters.add(mutableListOf(token))
                    }
                }
                clusteredCounts[value] = clusters.size
            }

            // If nothing was clustered, fallback to raw count or 0
            val counts = mutableMapOf<Int, Int>()
            CubanBanknotes.VALUES.forEach { d ->
                counts[d] = clusteredCounts[d] ?: 0
            }

            val totalBills = counts.values.sum()
            val confidence = if (totalBills > 0) {
                if (hasCubanKeywords) 0.92f else 0.85f
            } else {
                0.50f
            }

            if (!hasCubanKeywords && totalBills > 0) {
                doubts.add("Se detectaron números de billetes pero no se leyó texto explícito de 'Banco Central de Cuba'. Verifica las cantidades.")
            }
            if (totalBills == 0) {
                doubts.add("No se detectaron números legibles de billetes en la imagen. Intenta enfocar más cerca y con buena iluminación.")
            }

            val verification = "Escaneo local en el dispositivo con Google ML Kit Text Recognition (Offline). " +
                    if (totalBills > 0) "Detectados $totalBills billetes reconocidos por número impreso." else "Sin billetes detectados."

            val result = ScanResult(
                counts = counts,
                confidence = confidence,
                doubts = doubts,
                verification = verification,
                bitmap = bitmap
            )
            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private data class DetectedDenom(val value: Int, val box: Rect)
}
