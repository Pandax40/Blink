package com.pandina.blink

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
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

        // Solicitar permisos antes de configurar la interfaz
        requestPermissionsIfNeeded()

        enableEdgeToEdge()
        setContent {
            BlinkTheme {
                val navController = rememberNavController()
                NavigationComponent(navController = navController)
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }
        if (granted) {
            println("Permisos otorgados, inicializando WebRTC")
        } else {
            println("Permisos denegados, algunas funciones pueden no funcionar correctamente")
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