package com.example.mimo_optical_receiver

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.example.mimo_optical_receiver.ui.AppNavigation
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenCV
        if (OpenCVLoader.initDebug()) {
            Log.i("OpenCV", "OpenCV initialization successful.")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed.")
        }

        enableEdgeToEdge()
        setContent {
            // Force dark theme as requested
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppNavigation()
            }
        }
    }
}
