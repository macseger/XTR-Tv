package com.example.xtrtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.example.xtrtv.data.Prefs
import com.example.xtrtv.ui.main.MainScreen
import com.example.xtrtv.ui.login.LoginScreen
import com.example.xtrtv.ui.theme.XTRTvTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XTRTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Terminate app when user presses Home or switches apps
        finish()
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val navController = rememberNavController()
    
    val initialUser = remember { prefs.getUser() }

    NavHost(
        navController = navController, 
        startDestination = if (initialUser != null) "main" else "login"
    ) {
        composable("login") {
            LoginScreen(onLoginSuccess = { userData ->
                prefs.saveUser(userData)
                navController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("main") {
            val userData = remember { prefs.getUser() }
            if (userData != null) {
                MainScreen(
                    userData = userData,
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            }
        }
    }
}
