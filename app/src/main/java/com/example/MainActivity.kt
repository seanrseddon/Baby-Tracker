package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.BabyViewModel
import com.example.ui.screens.AddActivityScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppNavHost()
            }
        }
    }
}

@Composable
fun MainAppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application

    // Retrieve BabyViewModel with factory
    val babyViewModel: BabyViewModel = viewModel(
        factory = BabyViewModel.provideFactory(application)
    )

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("dashboard") {
            DashboardScreen(
                viewModel = babyViewModel,
                onNavigateToAdd = { navController.navigate("add_activity") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("add_activity") {
            AddActivityScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
