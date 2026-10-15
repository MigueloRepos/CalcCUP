package com.example.data.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.model.CubanBanknotes
import com.example.data.model.ScanResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot

class BanknoteMlKitService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val validDenominations = setOf(20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 3, 1)

    // Written Spanish denomination mapping
    private val writtenDenominations = listOf(
        "VEINTE MIL" to 20000,
        "DIEZ MIL" to 10000,
        "CINCO MIL" to 5000,
        "DOS MIL" to 2000,
        "MIL PESOS" to 1000,
        "QUINIENTOS" to 500,
        "DOSCIENTOS" to 200,
        "CIEN PESOS" to 100,
        "CINCUENTA" to 50,
        "VEINTE PESOS" to 20,
        "DIEZ PESOS" to 10,
        "CINCO PESOS" to 5,
        "TRES PESOS" to 3,
        "UN PESO" to 1
    )

    // Cuban banknote portraits & historical figures mapping
    private val portraitMap = listOf(
        listOf("JULIO ANTONIO MELLA", "MELLA") to 1000,
        listOf("IGNACIO AGRAMONTE", "AGRAMONTE") to 500,
        listOf("MARTA ABREU", "ABREU") to 200,
        listOf("CARLOS MANUEL DE CESPEDES", "CESPEDES") to 100,
        listOf("CALIXTO GARCIA", "CALIXTO") to 50,
        listOf("FELIX VARELA", "VARELA") to 20,
        listOf("MAXIMO GOMEZ", "GOMEZ") to 10,
        listOf("ANTONIO MACEO", "MACEO") to 5,
        listOf("ERNESTO CHE GUEVARA", "CHE GUEVARA", "CHE") to 3,
        listOf("JOSE MARTI", "MARTI") to 1
    )

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

            // Extract and clean raw text lines
            val extractedLines = mutableListOf<String>()
            val fullTextBuilder = StringBuilder()

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isNotEmpty()) {
                        extractedLines.add(lineText)
                        fullTextBuilder.append(lineText).append(" ")
                    }
                }
            }

            val normalizedFullText = normalize(fullTextBuilder.toString())
            val hasCubanInstitution = normalizedFullText.contains("BANCO CENTRAL DE CUBA") ||
                    normalizedFullText.contains("REPUBLICA DE CUBA") ||
                    normalizedFullText.contains("PESOS CUBANOS") ||
                    normalizedFullText.contains("BANCO NACIONAL DE CUBA")

            // Collect all detected evidence signals
            val evidenceList = mutableListOf<BillEvidence>()

            // 1. Scan text lines and blocks for written denominations & portraits
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val normLine = normalize(line.text)
                    val box = line.boundingBox ?: Rect()

                    // Check written numbers (e.g. "QUINIENTOS PESOS")
                    for ((written, denom) in writtenDenominations) {
                        if (normLine.contains(written)) {
                            evidenceList.add(
                                BillEvidence(
                                    denomination = denom,
                                    source = "Texto escrito: $written",
                                    box = box,
                                    weight = 1.0f
                                )
                            )
                        }
                    }

                    // Check portraits (e.g. "IGNACIO AGRAMONTE")
                    for ((names, denom) in portraitMap) {
                        if (names.any { normLine.contains(it) }) {
                            val matchedName = names.first { normLine.contains(it) }
                            evidenceList.add(
                                BillEvidence(
                                    denomination = denom,
                                    source = "Retrato: $matchedName",
                                    box = box,
                                    weight = 0.95f
                                )
                            )
                        }
                    }
                }
            }

            // 2. Scan individual elements for numeric denominations
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val normLine = normalize(line.text)

                    // Skip serial years like "SERIE 2021", "ANO 2014"
                    if (normLine.contains("SERIE") || normLine.contains("ANO") || normLine.contains("FECHA")) {
                        continue
                    }

                    for (element in line.elements) {
                        val elementText = element.text.trim()
                        val cleaned = cleanNumberString(elementText)
                        val denomVal = cleaned.toIntOrNull()

                        if (denomVal != null && validDenominations.contains(denomVal)) {
                            val box = element.boundingBox ?: Rect()
                            evidenceList.add(
                                BillEvidence(
                                    denomination = denomVal,
                                    source = "Número impreso: $denomVal",
                                    box = box,
                                    weight = 1.0f
                                )
                            )
                        }
                    }
                }
            }

            // 3. Cluster evidence items into Physical Banknotes
            // Banknotes of the same denomination whose bounding boxes are in close proximity
            // belong to the same physical banknote footprint on the table.
            val physicalBills = clusterEvidenceIntoBills(evidenceList, bitmap.width, bitmap.height)

            // 4. Calculate per-denomination counts
            val counts = mutableMapOf<Int, Int>()
            CubanBanknotes.VALUES.forEach { denom ->
                counts[denom] = 0
            }
            physicalBills.forEach { bill ->
                counts[bill.denomination] = (counts[bill.denomination] ?: 0) + 1
            }

            val totalBills = physicalBills.size
            val doubts = mutableListOf<String>()

            if (totalBills == 0) {
                doubts.add("No se encontraron números ni textos claros de billetes cubanos. Enfoca más de cerca y con buena luz.")
            } else if (!hasCubanInstitution) {
                doubts.add("Se identificaron denominaciones, pero no se leyó el sello institucional 'Banco Central de Cuba'. Verifica los resultados antes de sumar.")
            }

            val detectedDetails = physicalBills.groupBy { it.denomination }
                .map { (denom, list) -> "${list.size}x $$denom" }
                .joinToString(", ")

            val verification = if (totalBills > 0) {
                "Google ML Kit Text Recognition (On-Device): $totalBills billetes identificados ($detectedDetails). Se extrajeron ${extractedLines.size} líneas de texto OCR."
            } else {
                "Google ML Kit Text Recognition (On-Device): No se detectaron billetes con suficiente claridad."
            }

            val confidence = when {
                totalBills > 0 && hasCubanInstitution -> 0.94f
                totalBills > 0 -> 0.86f
                else -> 0.40f
            }

            val result = ScanResult(
                counts = counts,
                confidence = confidence,
                doubts = doubts,
                verification = verification,
                bitmap = bitmap,
                extractedTextLines = extractedLines
            )
            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun clusterEvidenceIntoBills(
        evidenceList: List<BillEvidence>,
        imageWidth: Int,
        imageHeight: Int
    ): List<PhysicalBill> {
        val bills = mutableListOf<PhysicalBill>()
        val groupedByDenom = evidenceList.groupBy { it.denomination }

        // Bounding threshold: ~35% of image width / height for same bill grouping
        val thresholdX = imageWidth * 0.35f
        val thresholdY = imageHeight * 0.35f

        groupedByDenom.forEach { (denom, items) ->
            val clusters = mutableListOf<MutableList<BillEvidence>>()

            for (item in items) {
                val matchingCluster = clusters.find { cluster ->
                    cluster.any { other ->
                        val dx = Math.abs(item.box.centerX() - other.box.centerX())
                        val dy = Math.abs(item.box.centerY() - other.box.centerY())
                        dx < thresholdX && dy < thresholdY
                    }
                }
                if (matchingCluster != null) {
                    matchingCluster.add(item)
                } else {
                    clusters.add(mutableListOf(item))
                }
            }

            clusters.forEach { cluster ->
                bills.add(
                    PhysicalBill(
                        denomination = denom,
                        evidence = cluster
                    )
                )
            }
        }

        return bills
    }

    private fun cleanNumberString(raw: String): String {
        return raw.uppercase()
            .replace("$", "")
            .replace("CUP", "")
            .replace(".", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
    }

    private fun normalize(text: String): String {
        val normalized = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD)
        return normalized.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
    }

    private data class BillEvidence(
        val denomination: Int,
        val source: String,
        val box: Rect,
        val weight: Float
    )

    private data class PhysicalBill(
        val denomination: Int,
        val evidence: List<BillEvidence>
    )
}
