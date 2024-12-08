package com.pandina.blink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pandina.blink.ui.screens.BlinkScreen
import com.pandina.blink.ui.screens.HomeScreen
import com.pandina.blink.ui.theme.BlinkTheme
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlinkTheme {
                val navController = rememberNavController()
                NavigationComponent(navController = navController)
            }
        }
    }
}

@Serializable
object Home

@Serializable
object Blink

@Composable
fun NavigationComponent(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen { navController.navigate(Blink) }
        }
        composable<Blink> {
            BlinkScreen { navController.popBackStack() }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NavigationComponentPreview() {
    BlinkTheme {
        val navController = rememberNavController()
        NavigationComponent(navController = navController)
    }
}