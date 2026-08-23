package com.example.cloudrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.cloudrive.navigation.CloudriveNavHost
import com.example.cloudrive.ui.theme.CloudriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloudriveTheme {
                val navController = rememberNavController()
                CloudriveNavHost(navController = navController)
            }
        }
    }
}
