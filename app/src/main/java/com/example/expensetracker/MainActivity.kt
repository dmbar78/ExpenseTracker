package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.TestTags
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.expensetracker.ui.dialogs.VoiceRecognitionDialogs
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    internal val viewModel: ExpenseViewModel by viewModels { ExpenseViewModel.Factory }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) Log.d("Permission", "RECORD_AUDIO permission granted")
            else Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupSpeechRecognizer()
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            ExpenseTrackerTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                // Hide FABs on Edit and Add screens to prevent overlap
                val showFabs = currentRoute == null || (!currentRoute.startsWith("edit", ignoreCase = true) && !currentRoute.startsWith("add", ignoreCase = true))

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // Listen for navigation events from the ViewModel
                LaunchedEffect(Unit) {
                    viewModel.navigateToFlow.collectLatest { route ->
                        navController.navigate(route)
                    }
                }

                VoiceRecognitionDialogs(viewModel = viewModel)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(navController = navController, drawerState = drawerState, scope = scope)
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Expense Tracker") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            if (showFabs) {
                                FloatingActionButton(onClick = { startVoiceRecognition() }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Start Recognition")
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            NavGraph(viewModel = viewModel, navController = navController)

                            // Global "+" create menu - available on every screen
                            var isPlusMenuExpanded by remember { mutableStateOf(false) }
                            if (showFabs) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                        .testTag(TestTags.GLOBAL_CREATE_MENU)
                                ) {
                                    FloatingActionButton(
                                        onClick = { isPlusMenuExpanded = true },
                                        modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_BUTTON)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Create")
                                    }
                                    DropdownMenu(
                                        expanded = isPlusMenuExpanded,
                                        onDismissRequest = { isPlusMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Create Expense") },
                                            onClick = {
                                                isPlusMenuExpanded = false
                                                navController.navigate(
                                                    "editExpense/0?type=Expense&expenseDateMillis=${System.currentTimeMillis()}"
                                                )
                                            },
                                            modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_EXPENSE)
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Create Income") },
                                            onClick = {
                                                isPlusMenuExpanded = false
                                                navController.navigate(
                                                    "editExpense/0?type=Income&expenseDateMillis=${System.currentTimeMillis()}"
                                                )
                                            },
                                            modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_INCOME)
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Create Transfer") },
                                            onClick = {
                                                isPlusMenuExpanded = false
                                                navController.navigate("editTransfer/0")
                                            },
                                            modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_TRANSFER)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    var bestResult: String? = null

                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechRecognizer", "Ready")
                        bestResult = null
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechRecognizer", "Speech started")
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrEmpty()) {
                            bestResult = partial
                            Log.d("SpeechRecognizer", "Partial: $bestResult")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val final = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        Log.d("SpeechRecognizer", "onResults received: $final")

                        val textToProcess = final ?: bestResult

                        if (!textToProcess.isNullOrEmpty()) {
                            viewModel.onVoiceRecognitionResult(textToProcess)
                        }
                    }

                    override fun onError(error: Int) {
                        Log.e("SpeechRecognizer", "Error: $error")
                        if (!bestResult.isNullOrEmpty()) {
                            viewModel.onVoiceRecognitionResult(bestResult!!)
                        }
                    }

                    override fun onEndOfSpeech() {
                        Log.d("SpeechRecognizer", "Speech ended.")
                    }

                    // Unused methods
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            Log.d("MainActivity", "SpeechRecognizer created successfully.")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating SpeechRecognizer", e)
        }
    }

    private fun startVoiceRecognition() {
        if (speechRecognizer == null) {
            Log.e("MainActivity", "SpeechRecognizer not initialized.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
        Log.d("MainActivity", "Starting to listen...")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
