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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = applicationContext as Application
            val babyViewModel: BabyViewModel = viewModel(
                factory = BabyViewModel.provideFactory(application)
            )
            val isDarkThemeState by babyViewModel.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkThemeState) {
                MainAppNavHost(babyViewModel)
            }
        }
    }
}

@Composable
fun MainAppNavHost(babyViewModel: BabyViewModel) {
    val navController = rememberNavController()

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
