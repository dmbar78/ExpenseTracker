package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.expensetracker.ui.TestTags
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.expensetracker.ui.dialogs.VoiceRecognitionDialogs
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.sharedimport.SharedFileImportProcessor
import com.example.expensetracker.sharedimport.SharedFileImportState
import com.example.expensetracker.sharedimport.SharedFileImportViewModel
import com.example.expensetracker.viewmodel.ExpenseViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    internal val viewModel: ExpenseViewModel by viewModels()
    internal val sharedFileImportViewModel: SharedFileImportViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) Log.d("Permission", "RECORD_AUDIO permission granted")
            else Toast.makeText(this, getString(R.string.err_mic_permission), Toast.LENGTH_LONG).show()
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupSpeechRecognizer()
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        submitSharedIntent(intent)

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            ExpenseTrackerTheme {
                val context = LocalContext.current
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                
                // App Lock State
                var isAppLocked by remember {
                    mutableStateOf(
                        com.example.expensetracker.data.SecurityManager.isPinSet(context) &&
                            com.example.expensetracker.data.SecurityManager.isLocked()
                    )
                }

                // Check lock status on Resume
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            com.example.expensetracker.data.SecurityManager.noteBackgroundTime()
                        } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                            // Check for timeout
                            if (com.example.expensetracker.data.SecurityManager.shouldLock(context)) {
                                com.example.expensetracker.data.SecurityManager.setLocked()
                            }
                            
                            if (com.example.expensetracker.data.SecurityManager.isPinSet(context) && 
                                com.example.expensetracker.data.SecurityManager.isLocked()) {
                                isAppLocked = true
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainContent(isAppLocked = isAppLocked)

                    if (isAppLocked) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            com.example.expensetracker.ui.screens.PinLockScreen(
                                mode = com.example.expensetracker.ui.screens.PinScreenMode.Unlock,
                                onSuccess = {
                                    isAppLocked = false
                                },
                                onCancel = {
                                    // If cancelled on unlock, exit app/minimize?
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(isAppLocked: Boolean = false) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        // Show FABs only on Home screen
        val showFabs = currentRoute == "home"

        val homeChartMode by viewModel.homeChartMode.collectAsState()

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val sharedImportState by sharedFileImportViewModel.state.collectAsState()
        var isPlusMenuExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(sharedImportState, isAppLocked, currentRoute) {
            when (val state = sharedImportState) {
                is SharedFileImportState.Ready -> {
                    if (!isAppLocked && currentRoute != null) {
                        if (currentRoute != "home") {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        isPlusMenuExpanded = true
                    }
                }
                is SharedFileImportState.Error -> {
                    if (!isAppLocked) {
                        snackbarHostState.showSnackbar(context.getString(state.messageResId))
                        sharedFileImportViewModel.acknowledgeError()
                    }
                }
                else -> Unit
            }
        }

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
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(androidx.compose.ui.res.stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = androidx.compose.ui.res.stringResource(R.string.menu_desc))
                            }
                        },
                        actions = {
                            if (showFabs) {
                                IconButton(
                                    onClick = { viewModel.toggleHomeChartMode() },
                                    modifier = Modifier.testTag(TestTags.HOME_CHART_MODE_ICON)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BarChart,
                                        contentDescription = androidx.compose.ui.res.stringResource(R.string.desc_home_chart_toggle),
                                        tint = if (homeChartMode)
                                            MaterialTheme.colorScheme.tertiary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (showFabs) {
                        FloatingActionButton(
                            onClick = { startVoiceRecognition() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = androidx.compose.ui.res.stringResource(R.string.start_recognition))
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavGraph(viewModel = viewModel, navController = navController)

                    // Global "+" create menu - available on every screen
                    if (showFabs) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .testTag(TestTags.GLOBAL_CREATE_MENU)
                        ) {
                            FloatingActionButton(
                                onClick = { isPlusMenuExpanded = true },
                                modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_BUTTON),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.Add, contentDescription = androidx.compose.ui.res.stringResource(R.string.create_desc))
                            }
                            DropdownMenu(
                                expanded = isPlusMenuExpanded,
                                onDismissRequest = {
                                    isPlusMenuExpanded = false
                                    if (sharedImportState is SharedFileImportState.Ready) {
                                        sharedFileImportViewModel.cancel()
                                    }
                                }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(R.string.action_create_expense)) },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        navController.navigate(createExpenseRoute("Expense", sharedImportState, context))
                                    },
                                    modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_EXPENSE)
                                )
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(R.string.action_create_income)) },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        navController.navigate(createExpenseRoute("Income", sharedImportState, context))
                                    },
                                    modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_INCOME)
                                )
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(R.string.action_create_transfer)) },
                                    onClick = {
                                        isPlusMenuExpanded = false
                                        val ready = sharedFileImportViewModel.consume(deleteStagedFile = true)
                                        navController.navigate(
                                            if (ready == null) "editTransfer/0"
                                            else buildString {
                                                append("editTransfer/0?transferDateMillis=${ready.dateMillis}")
                                                ready.amount?.let { append("&amount=${Uri.encode(it)}") }
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag(TestTags.GLOBAL_CREATE_TRANSFER)
                                )
                            }
                        }
                    }

                    if (sharedImportState is SharedFileImportState.Processing && !isAppLocked) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .testTag(TestTags.SHARED_IMPORT_PROCESSING)
                        )
                    }
                }
            }
        }
    }

    private fun createExpenseRoute(
        type: String,
        state: SharedFileImportState,
        context: android.content.Context
    ): String {
        val ready = state as? SharedFileImportState.Ready
            ?: return "editExpense/0?type=$type&expenseDateMillis=${System.currentTimeMillis()}"
        val isImage = ready.mimeType.startsWith("image/")
        val photoUri = if (isImage) {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", File(ready.stagedFilePath))
        } else null
        sharedFileImportViewModel.consume(deleteStagedFile = !isImage)
        return buildString {
            append("editExpense/0?type=$type&expenseDateMillis=${ready.dateMillis}")
            ready.amount?.let { append("&amount=${Uri.encode(it)}") }
            photoUri?.let { append("&initialPhotoUri=${Uri.encode(it.toString())}") }
        }
    }

    private fun submitSharedIntent(sharedIntent: Intent?) {
        if (sharedIntent?.action != Intent.ACTION_SEND) return
        val eventId = sharedIntent.getStringExtra(EXTRA_SHARED_EVENT_ID) ?: UUID.randomUUID().toString().also {
            sharedIntent.putExtra(EXTRA_SHARED_EVENT_ID, it)
        }
        sharedFileImportViewModel.handleIntent(sharedIntent, eventId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        submitSharedIntent(intent)
    }

    private companion object {
        const val EXTRA_SHARED_EVENT_ID = "com.example.expensetracker.SHARED_EVENT_ID"
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
