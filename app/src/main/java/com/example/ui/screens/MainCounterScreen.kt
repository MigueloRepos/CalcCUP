package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.components.*
import com.example.ui.dialogs.*
import com.example.ui.theme.CubanBlueAccent
import com.example.ui.theme.CubanBluePrimary
import com.example.ui.theme.CubanGold
import com.example.ui.theme.CubanRed
import com.example.ui.viewmodel.BanknoteCounterViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCounterScreen(
    viewModel: BanknoteCounterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Temp URI for high-res camera photo capture
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    fun createTempPictureUri(): Uri {
        val file = File.createTempFile("cup_scan_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { uri ->
                val bmp = loadBitmapFromUri(uri)
                if (bmp != null) {
                    viewModel.analyzeBitmap(bmp)
                } else {
                    Toast.makeText(context, "No se pudo leer la foto tomada", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempPictureUri()
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Se necesita permiso de cámara para tomar fotos", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery picker launcher (Android Photo Picker, zero-permission)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = loadBitmapFromUri(uri)
            if (bmp != null) {
                viewModel.analyzeBitmap(bmp)
            } else {
                Toast.makeText(context, "No se pudo cargar la imagen seleccionada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareReport() {
        val report = viewModel.generateShareReport()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte Conteo CUP")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir desglose de billetes"))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🇨🇺",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Contador de Billetes Cubanos",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Reconocimiento IA · Peso Cubano (CUP)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = CubanBluePrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CubanBluePrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "SOLO CUP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CubanBluePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier.testTag("settings_top_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = if (maxWidth >= 600.dp) 2 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
            ) {
                // Total Accumulated Card (spans all columns)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AccumulatedTotalCard(
                        totalAmountFormatted = uiState.formattedTotal,
                        totalBills = uiState.totalBills,
                        onShareClick = { shareReport() }
                    )
                }

                // Action Buttons Row (Camera, Gallery, Bundles, Clear)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Camera Button
                            Button(
                                onClick = {
                                    val hasCamPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamPerm) {
                                        val uri = createTempPictureUri()
                                        tempPhotoUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("camera_scan_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CubanBluePrimary)
                            ) {
                                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Cámara", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Gallery Button
                            FilledTonalButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .testTag("gallery_scan_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Foto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Bundles Calculator Button
                            OutlinedButton(
                                onClick = { viewModel.openBundles() },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .testTag("bundles_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Fajos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Clear Button
                            IconButton(
                                onClick = { viewModel.openClearDialog() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CubanRed.copy(alpha = 0.12f))
                                    .testTag("clear_counter_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Limpiar",
                                    tint = CubanRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Status / Loading Message Banner
                        AnimatedVisibility(visible = uiState.isAnalyzing) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = CubanBluePrimary.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = CubanBluePrimary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Analizando billetes cubanos con IA…",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = CubanBluePrimary
                                    )
                                }
                            }
                        }

                        // Error Banner
                        AnimatedVisibility(visible = uiState.errorMessage != null) {
                            uiState.errorMessage?.let { error ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = CubanRed.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = error,
                                            fontSize = 12.sp,
                                            color = CubanRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.clearErrorMessage() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = CubanRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Denomination Grid Items
                items(
                    items = uiState.displayedDenominations,
                    key = { it.value }
                ) { denom ->
                    val count = uiState.counts[denom.value] ?: 0
                    DenominationCard(
                        denomination = denom,
                        count = count,
                        hapticsEnabled = uiState.hapticsEnabled,
                        onIncrement = { delta -> viewModel.increment(denom.value, delta) },
                        onDirectEditClick = { viewModel.openDirectEdit(denom.value) }
                    )
                }

                // How it works card (spans all columns)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HowItWorksCard()
                }

                // History section (spans all columns)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HistorySection(
                        history = uiState.history,
                        onClearHistory = { viewModel.clearHistory() },
                        onDeleteItem = { id -> viewModel.deleteHistoryItem(id) }
                    )
                }
            }
        }
    }

    // Review Modal Dialog
    if (uiState.pendingScanResult != null) {
        ScanReviewDialog(
            scanResult = uiState.pendingScanResult!!,
            pendingCounts = uiState.pendingCounts,
            onCountChange = { denom, count -> viewModel.updatePendingCount(denom, count) },
            onConfirmAndAdd = { viewModel.confirmAndAddPending() },
            onConfirmAndReplace = { viewModel.confirmAndReplacePending() },
            onDismiss = { viewModel.dismissPendingScan() }
        )
    }

    // Direct Edit Modal Dialog
    if (uiState.directEditDenomination != null) {
        val denomVal = uiState.directEditDenomination!!
        val count = uiState.counts[denomVal] ?: 0
        DirectEditDialog(
            denominationValue = denomVal,
            currentCount = count,
            onConfirm = { newCount -> viewModel.setCount(denomVal, newCount) },
            onDismiss = { viewModel.closeDirectEdit() }
        )
    }

    // Bundles Modal Dialog
    if (uiState.showBundlesDialog) {
        BundlesDialog(
            counts = uiState.counts,
            onDismiss = { viewModel.closeBundles() }
        )
    }

    // Settings Modal Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            initialGeminiKey = uiState.customGeminiKey,
            initialOpenAiKey = uiState.customOpenAiKey,
            initialProvider = uiState.aiProvider,
            hapticsEnabled = uiState.hapticsEnabled,
            showRareDenominations = uiState.showRareDenominations,
            onSaveApiSettings = { geminiKey, openAiKey, provider ->
                viewModel.saveApiSettings(geminiKey, openAiKey, provider)
            },
            onToggleHaptics = { enabled -> viewModel.toggleHaptics(enabled) },
            onToggleRareDenominations = { enabled -> viewModel.toggleRareDenominations(enabled) },
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { viewModel.closeSettings() }
        )
    }

    // Clear Confirmation Dialog
    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeClearDialog() },
            title = { Text("¿Borrar todo el contador?") },
            text = { Text("Se restablecerán las cantidades de todos los billetes a cero. El total acumulado volverá a $0 CUP.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = CubanRed)
                ) {
                    Text("Borrar todo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.closeClearDialog() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
