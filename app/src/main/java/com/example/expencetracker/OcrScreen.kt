package com.example.expencetracker

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.expencetracker.data.Expense
import com.example.expencetracker.data.ExpenseType
import com.example.expencetracker.utils.ChatGPTService
import com.example.expencetracker.viewmodel.ExpenseViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.regex.Pattern
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun OcrScreen(
    viewModel: ExpenseViewModel = (LocalContext.current.applicationContext as ExpenseTrackerApplication).expenseViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var scannedDetails by remember { mutableStateOf<Expense?>(null) }
    val scope = rememberCoroutineScope()
    val chatGPTService = remember { ChatGPTService() }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[android.Manifest.permission.CAMERA] ?: false
        hasStoragePermission = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imageUri = it
            isProcessing = true
            processImage(it, context) { text ->
                recognizedText = text
                scope.launch {
                    try {
                        val expenseDetails = chatGPTService.processReceiptText(text)
                        val amount = expenseDetails.amount
                        val transactionType = expenseDetails.transactionType
                        val dateTime = expenseDetails.dateTime
                        val source = expenseDetails.source
                        val merchant = expenseDetails.merchant
                        val type = if (transactionType.lowercase() == "credit") ExpenseType.INCOME else ExpenseType.EXPENSE
                        scannedDetails = Expense(
                            amount = amount,
                            description = merchant,
                            category = source,
                            type = type,
                            imageUri = imageUri,
                            transactionType = if (transactionType.lowercase() == "credit") com.example.expencetracker.data.TransactionType.CREDIT else com.example.expencetracker.data.TransactionType.DEBIT,
                            dateTime = dateTime,
                            source = source,
                            merchant = merchant
                        )
                    } catch (e: Exception) {
                        Log.e("ChatGPT", "Error processing text", e)
                        scannedDetails = null
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                onImageCaptured = { uri ->
                    imageUri = uri
                    isProcessing = true
                    processImage(uri, context) { text ->
                        recognizedText = text
                        scope.launch {
                            try {
                                val expenseDetails = chatGPTService.processReceiptText(text)
                                val amount = expenseDetails.amount
                                val transactionType = expenseDetails.transactionType
                                val dateTime = expenseDetails.dateTime
                                val source = expenseDetails.source
                                val merchant = expenseDetails.merchant
                                val type = if (transactionType.lowercase() == "credit") ExpenseType.INCOME else ExpenseType.EXPENSE
                                scannedDetails = Expense(
                                    amount = amount,
                                    description = merchant,
                                    category = source,
                                    type = type,
                                    imageUri = imageUri,
                                    transactionType = if (transactionType.lowercase() == "credit") com.example.expencetracker.data.TransactionType.CREDIT else com.example.expencetracker.data.TransactionType.DEBIT,
                                    dateTime = dateTime,
                                    source = source,
                                    merchant = merchant
                                )
                            } catch (e: Exception) {
                                Log.e("ChatGPT", "Error processing text", e)
                                scannedDetails = null
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                imagePicker.launch("image/*")
            }
        ) {
            Text("Select from Gallery")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isProcessing) {
            CircularProgressIndicator(color = Color.White)
            Text("Processing receipt...", color = Color.White)
        }

        // Review UI for scanned details
        scannedDetails?.let { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Review Scanned Details", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Amount: ₹${expense.amount}", color = Color.White)
                    Text("Transaction Type: ${if (expense.transactionType == com.example.expencetracker.data.TransactionType.CREDIT) "Credit" else "Debit"}", color = Color.White)
                    Text("Date/Time: ${expense.dateTime}", color = Color.White)
                    Text("Source: ${expense.source}", color = Color.White)
                    Text("Merchant: ${expense.merchant}", color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.addExpense(expense)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Transaction")
            }
        }

        if (recognizedText.isNotEmpty() && !isProcessing && scannedDetails == null) {
            Text(
                text = "Recognized Text:",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = recognizedText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

private fun extractExpenseDetails(text: String, onExtracted: (amount: String, description: String, category: String) -> Unit) {
    // Try to find amount using common patterns
    val amountPattern = Pattern.compile("""[$]?\d+[.,]?\d*""")
    val amountMatcher = amountPattern.matcher(text)
    val amounts = mutableListOf<Double>()
    
    while (amountMatcher.find()) {
        val amountStr = amountMatcher.group()
            .replace("$", "")
            .replace(",", ".")
        amountStr.toDoubleOrNull()?.let { amounts.add(it) }
    }
    
    // Use the highest amount that looks like a total
    val amount = amounts.maxOrNull()?.toString() ?: ""

    // Try to find description (usually the first line or a line containing keywords)
    val lines = text.split("\n")
    val description = lines.firstOrNull { line ->
        line.contains("total", ignoreCase = true) ||
        line.contains("amount", ignoreCase = true) ||
        line.contains("payment", ignoreCase = true) ||
        line.contains("store", ignoreCase = true) ||
        line.contains("merchant", ignoreCase = true)
    } ?: lines.firstOrNull() ?: ""

    // Try to find category based on common keywords
    val category = when {
        text.contains(Regex("(?i)(food|restaurant|cafe|coffee|grocery|supermarket|market)")) -> "Food"
        text.contains(Regex("(?i)(transport|uber|taxi|bus|train|metro|subway)")) -> "Transport"
        text.contains(Regex("(?i)(shop|store|mall|retail|clothing|fashion)")) -> "Shopping"
        text.contains(Regex("(?i)(bill|utility|electric|water|gas|internet|phone)")) -> "Bills"
        text.contains(Regex("(?i)(movie|theater|concert|entertainment|ticket)")) -> "Entertainment"
        else -> "Other"
    }

    onExtracted(amount, description, category)
}

@Composable
fun CameraPreview(
    onImageCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    LaunchedEffect(previewView) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                takePhoto(
                    imageCapture = imageCapture,
                    outputDirectory = context.getOutputDirectory(),
                    executor = context.executor,
                    onImageCaptured = onImageCaptured
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text("Capture")
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit
) {
    val photoFile = File(
        outputDirectory,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
            }
        }
    )
}

private fun processImage(uri: Uri, context: Context, onTextRecognized: (String) -> Unit) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Clean and format the OCR text
                val cleanedText = cleanOcrText(visionText.text)
                onTextRecognized(cleanedText)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Error processing image", e)
            }
    } catch (e: Exception) {
        Log.e("OCR", "Error loading image", e)
    }
}

private fun cleanOcrText(text: String): String {
    return text
        // Remove extra whitespace
        .replace(Regex("\\s+"), " ")
        // Remove special characters except those commonly found in receipts
        .replace(Regex("[^a-zA-Z0-9\\s\\-\\$\\.,:;()]"), "")
        // Fix common OCR mistakes
        .replace(Regex("(?i)total\\s*amount"), "Total Amount")
        .replace(Regex("(?i)subtotal"), "Subtotal")
        .replace(Regex("(?i)tax"), "Tax")
        // Fix common number formatting issues
        .replace(Regex("(?<=\\d),(?=\\d)"), ".") // Replace comma with dot in numbers
        .replace(Regex("(?<=\\d)\\.(?=\\d)"), ".") // Ensure single dot in numbers
        // Remove empty lines
        .split("\n")
        .filter { it.trim().isNotEmpty() }
        .joinToString("\n")
        .trim()
}

private fun Context.getOutputDirectory(): File {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
        File(it, "ExpenseTracker").apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
}

private val Context.executor: Executor
    get() = ContextCompat.getMainExecutor(this) 