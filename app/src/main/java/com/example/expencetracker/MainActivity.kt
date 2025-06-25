package com.example.expencetracker

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.expencetracker.data.Expense
import com.example.expencetracker.data.ExpenseType
import com.example.expencetracker.ui.theme.ExpenceTrackerTheme
import com.example.expencetracker.utils.ImageStorage
import com.example.expencetracker.utils.OCRProcessor
import com.example.expencetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val imageUriList = mutableStateListOf<Uri>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All permissions granted, launch camera
            launchCamera()
        } else {
            // Show error message if permissions are denied
            Toast.makeText(
                this,
                "Camera permission is required to take photos",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { capturedBitmap ->
            try {
                // Save the bitmap to internal storage
                val savedUri = ImageStorage.saveImageToInternalStorage(this, capturedBitmap)
                savedUri?.let { uri ->
                    // Store the URI in the ViewModel
                    (application as? ExpenseTrackerApplication)?.expenseViewModel?.setCurrentImageUri(uri)
                } ?: run {
                    Toast.makeText(
                        this,
                        "Failed to save image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Error saving image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenceTrackerTheme {
                MainScreen(
                    onTakePicture = { checkAndRequestCameraPermission() }
                )
            }
        }
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted, launch camera
                launchCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show explanation why the permission is needed
                Toast.makeText(
                    this,
                    "Camera permission is required to take photos of receipts",
                    Toast.LENGTH_LONG
                ).show()
                requestCameraPermission()
            }
            else -> {
                // Request the permission
                requestCameraPermission()
            }
        }
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun launchCamera() {
        try {
            takePictureLauncher.launch(null)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error launching camera: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
fun ImageCaptureScreen(
    currentImageUri: Uri?,
    onTakePicture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onTakePicture,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = "Take Photo"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Take Photo")
        }

        currentImageUri?.let { uri ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Captured Image",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Captured Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    expenseViewModel: ExpenseViewModel = (LocalContext.current.applicationContext as ExpenseTrackerApplication).expenseViewModel,
    onTakePicture: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showOcrScreen by remember { mutableStateOf(false) }
    val tabs = listOf("Home", "Add", "History")

    if (showOcrScreen || selectedTab == 1) {
        OcrScreen(
            viewModel = expenseViewModel,
            onNavigateBack = { showOcrScreen = false; selectedTab = 0 }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Expense Tracker") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (index) {
                                        0 -> Icons.Default.Home
                                        1 -> Icons.Default.Add
                                        else -> Icons.Default.List
                                    },
                                    contentDescription = title
                                )
                            },
                            label = { Text(title) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> HomeScreen(expenseViewModel)
                    2 -> HistoryScreen(expenseViewModel)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: ExpenseViewModel = (LocalContext.current.applicationContext as ExpenseTrackerApplication).expenseViewModel) {
    val balance by remember { derivedStateOf { viewModel.getTotalBalance() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Total Balance",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$${String.format("%.2f", balance)}",
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: ExpenseViewModel = (LocalContext.current.applicationContext as ExpenseTrackerApplication).expenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(expenses) { expense ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = expense.description,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = expense.category,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "${if (expense.type == ExpenseType.EXPENSE) "-" else "+"}$${String.format("%.2f", expense.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (expense.type == ExpenseType.EXPENSE)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    expense.imageUri?.let { uri ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Receipt Image",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = uri,
                                        onLoading = { /* Loading state */ },
                                        onError = { /* Error state */ }
                                    ),
                                    contentDescription = "Receipt Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}