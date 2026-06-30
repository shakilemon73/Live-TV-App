package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.ChannelViewModel
import com.example.ui.ChannelViewModelFactory
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.data.ChannelValidationWorker

class MainActivity : ComponentActivity() {
    private var navController: NavController? = null
    private var viewModel: ChannelViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule periodic background channel validation & sync via WorkManager
        ChannelValidationWorker.schedulePeriodicWork(applicationContext)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val controller = rememberNavController()
                    LaunchedEffect(controller) {
                        navController = controller
                    }

                    val vm: ChannelViewModel = viewModel(
                        factory = ChannelViewModelFactory(application)
                    )
                    viewModel = vm

                    NavHost(
                        navController = controller,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onNavigateToHome = {
                                    controller.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                viewModel = vm,
                                onNavigateToPlayer = { controller.navigate("player") },
                                onNavigateToAdmin = { controller.navigate("admin") }
                            )
                        }
                        composable("player") {
                            PlayerScreen(
                                viewModel = vm,
                                onNavigateBack = { controller.popBackStack() },
                                onEnterPip = { enterPipMode() }
                            )
                        }
                        composable("admin") {
                            AdminScreen(
                                viewModel = vm,
                                onNavigateBack = { controller.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    fun enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            try {
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                try {
                    enterPictureInPictureMode()
                } catch (e2: Exception) {
                    // ignore
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            try {
                enterPictureInPictureMode()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val currentDest = navController?.currentDestination?.route
        val hasSelectedChannel = viewModel?.selectedChannel?.value != null
        if (currentDest == "player" && hasSelectedChannel) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel?.setPipMode(isInPictureInPictureMode)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        viewModel?.setPipMode(isInPictureInPictureMode)
    }
}
